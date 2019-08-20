package expo.modules.camera2.camera

import android.support.annotation.RestrictTo
import android.util.Log
import android.util.Size

import expo.modules.camera2.camera.configs.UseCaseConfig

import java.util.HashMap
import java.util.HashSet

/**
 * The use case which all other use cases are built on top of.
 *
 * A UseCase provides functionality to map the set of arguments in a use case to arguments
 * that are usable by a camera. UseCase also will communicate of the active/inactive state to
 * the Camera.
 *
 * @param useCaseConfig the configuration object used for this use case
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class UseCase protected constructor(useCaseConfig: UseCaseConfig<*>) {

  /**
   * The set of [StateChangeListener] that are currently listening state transitions of this
   * use case.
   */
  private val mListeners = HashSet<StateChangeListener>()

  /**
   * A map of camera id and CameraControl. A CameraControl will be attached into the usecase after
   * usecase is bound to lifecycle. It is used for controlling zoom/focus/flash/triggering Af or
   * AE.
   */
  private val mAttachedCameraControlMap = HashMap<String, CameraControl>()

  /**
   * A map of the names of the [android.hardware.camera2.CameraDevice] to the [ ] that have been attached to this UseCase
   */
  private val mAttachedCameraIdToSessionConfigMap = HashMap<String, SessionConfig>()

  /**
   * A map of the names of the [android.hardware.camera2.CameraDevice] to the surface
   * resolution that have been attached to this UseCase
   */
  private val mAttachedSurfaceResolutionMap = HashMap<String, Size>()

  private var mState = State.INACTIVE

  /**
   * Retrieves the configuration used by this use case.
   *
   * @return the configuration used by this use case.
   */
  var useCaseConfig: UseCaseConfig<*>? = null
    private set

  /**
   * Except for ImageFormat.JPEG or ImageFormat.YUV, other image formats like SurfaceTexture or
   * MediaCodec classes will be mapped to internal format HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED
   * (0x22) in StreamConfigurationMap.java. 0x22 is also the code for ImageFormat.PRIVATE. But
   * there is no ImageFormat.PRIVATE supported before Android level 23. There is same internal
   * code 0x22 for internal corresponding format HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED.
   * Therefore, setting 0x22 as default image format.
   */
  /**
   * Get image format for the use case.
   *
   * @return image format for the use case
   * @hide
   */
  @get:RestrictTo(Scope.LIBRARY_GROUP)
  var imageFormat = ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE
    protected set

  /**
   * Get the names of the cameras which are attached to this use case.
   *
   *
   * The names will correspond to those of the camera as defined by [ ].
   *
   * @hide
   */
  val attachedCameraIds: Set<String>
    @RestrictTo(Scope.LIBRARY_GROUP)
    get() = mAttachedCameraIdToSessionConfigMap.keys

  val name: String
    get() = useCaseConfig!!.getTargetName("<UnknownUseCase-" + this.hashCode() + ">")

  private val cameraIdUnchecked: String?
    get() {
      var cameraId: String? = null
      val lensFacing = useCaseConfig!!.retrieveOption(
          CameraDeviceConfig.OPTION_LENS_FACING)
      try {
        cameraId = CameraX.getCameraWithLensFacing(lensFacing)
      } catch (e: Exception) {
        throw IllegalArgumentException("Invalid camera lens facing: $lensFacing", e)
      }

      return cameraId
    }

  init {
    updateUseCaseConfig(useCaseConfig)
  }

  /**
   * Returns a use case configuration pre-populated with default configuration
   * options.
   *
   *
   * This is used to generate a final configuration by combining the user-supplied
   * configuration with the default configuration. Subclasses can override this method to provide
   * the pre-populated builder. If `null` is returned, then the user-supplied
   * configuration will be used directly.
   *
   * @param lensFacing The [LensFacing] that the default builder will target to.
   * @return A builder pre-populated with use case default options.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  @Nullable
  protected fun getDefaultBuilder(
      lensFacing: CameraX.LensFacing): UseCaseConfig.Builder<*, *, *>? {
    return null
  }

  /**
   * Updates the stored use case configuration.
   *
   *
   * This configuration will be combined with the default configuration that is contained in
   * the pre-populated builder supplied by [.getDefaultBuilder], if it exists and the
   * behavior of [.applyDefaults] is not overridden. Once this
   * method returns, the combined use case configuration can be retrieved with
   * [.getUseCaseConfig].
   *
   *
   * This method alone will not make any changes to the [SessionConfig], it is up to
   * the use case to decide when to modify the session configuration.
   *
   * @param useCaseConfig Configuration which will be applied on top of use case defaults, if a
   * default builder is provided by [.getDefaultBuilder].
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun updateUseCaseConfig(useCaseConfig: UseCaseConfig<*>) {
    val lensFacing = (useCaseConfig as CameraDeviceConfig).getLensFacing(null)

    val defaultBuilder = getDefaultBuilder(lensFacing)
    if (defaultBuilder == null) {
      Log.w(
          TAG,
          "No default configuration available. Relying solely on user-supplied options.")
      this.useCaseConfig = useCaseConfig
    } else {
      this.useCaseConfig = applyDefaults(useCaseConfig, defaultBuilder)
    }
  }

  /**
   * Combines user-supplied configuration with use case default configuration.
   *
   *
   * This is called during initialization of the class. Subclassess can override this method to
   * modify the behavior of combining user-supplied values and default values.
   *
   * @param userConfig    The user-supplied configuration.
   * @param defaultConfigBuilder A builder containing use-case default values.
   * @return The configuration that will be used by this use case.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun applyDefaults(
      userConfig: UseCaseConfig<*>,
      defaultConfigBuilder: UseCaseConfig.Builder<*, *, *>): UseCaseConfig<*> {

    // If any options need special handling, this is the place to do it. For now we'll just copy
    // over all options.
    for (opt in userConfig.listOptions()) {
      val objectOpt = opt as Option<Any>// Options/values are being copied directly

      defaultConfigBuilder.getMutableConfig().insertOption(
          objectOpt, userConfig.retrieveOption(objectOpt))
    }

// UseCaseConfig
    return defaultConfigBuilder.build()
  }

  /**
   * Attaches the UseCase to a [android.hardware.camera2.CameraDevice] with the
   * corresponding name.
   *
   * @param cameraId The name of the camera as defined by [                 ][android.hardware.camera2.CameraManager.getCameraIdList].
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun attachToCamera(cameraId: String, sessionConfig: SessionConfig) {
    mAttachedCameraIdToSessionConfigMap.put(cameraId, sessionConfig)
  }

  /**
   * Add a [StateChangeListener], which listens to this UseCase's active and inactive
   * transition events.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  fun addStateChangeListener(listener: StateChangeListener) {
    mListeners.add(listener)
  }

  /**
   * Attach a CameraControl to this use case.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  fun attachCameraControl(cameraId: String, cameraControl: CameraControl) {
    mAttachedCameraControlMap.put(cameraId, cameraControl)
    onCameraControlReady(cameraId)
  }

  /** Detach a CameraControl from this use case.  */
  internal fun detachCameraControl(cameraId: String) {
    mAttachedCameraControlMap.remove(cameraId)
  }

  /**
   * Remove a [StateChangeListener] from listening to this UseCase's active and inactive
   * transition events.
   *
   *
   * If the listener isn't currently listening to the UseCase then this call does nothing.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  fun removeStateChangeListener(listener: StateChangeListener) {
    mListeners.remove(listener)
  }

  /**
   * Get the [SessionConfig] for the specified camera id.
   *
   * @param cameraId the id of the camera as referred to be [                 ]
   * @throws IllegalArgumentException if no camera with the specified cameraId is attached
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  fun getSessionConfig(cameraId: String): SessionConfig {
    val sessionConfig = mAttachedCameraIdToSessionConfigMap.get(cameraId)
    return if (sessionConfig == null) {
      throw IllegalArgumentException("Invalid camera: $cameraId")
    } else {
      sessionConfig
    }
  }

  /**
   * Notify all [StateChangeListener] that are listening to this UseCase that it has
   * transitioned to an active state.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun notifyActive() {
    mState = State.ACTIVE
    notifyState()
  }

  /**
   * Notify all [StateChangeListener] that are listening to this UseCase that it has
   * transitioned to an inactive state.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun notifyInactive() {
    mState = State.INACTIVE
    notifyState()
  }

  /**
   * Notify all [StateChangeListener] that are listening to this UseCase that the
   * settings have been updated.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun notifyUpdated() {
    for (listener in mListeners) {
      listener.onUseCaseUpdated(this)
    }
  }

  /**
   * Notify all [StateChangeListener] that are listening to this UseCase that the use
   * case needs to be completely reset.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun notifyReset() {
    for (listener in mListeners) {
      listener.onUseCaseReset(this)
    }
  }

  /**
   * Notify all [StateChangeListener] that are listening to this UseCase of its current
   * state.
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun notifyState() {
    when (mState) {
      UseCase.State.INACTIVE -> for (listener in mListeners) {
        listener.onUseCaseInactive(this)
      }
      UseCase.State.ACTIVE -> for (listener in mListeners) {
        listener.onUseCaseActive(this)
      }
    }
  }

  /** Clears internal state of this use case.  */
  @CallSuper
  protected fun clear() {
    val eventListener = useCaseConfig!!.getUseCaseEventListener(null)
    if (eventListener != null) {
      eventListener!!.onUnbind()
    }

    mListeners.clear()
  }

  /**
   * Retrieves the currently attached surface resolution.
   *
   * @param cameraId the camera id for the desired surface.
   * @return the currently attached surface resolution for the given camera id.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  fun getAttachedSurfaceResolution(cameraId: String): Size {
    return mAttachedSurfaceResolutionMap[cameraId]
  }

  /**
   * Offers suggested resolutions.
   *
   *
   * The keys of suggestedResolutionMap should only be cameraIds that are valid for this use
   * case.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  fun updateSuggestedResolution(suggestedResolutionMap: Map<String, Size>) {
    val resolutionMap = onSuggestedResolutionUpdated(suggestedResolutionMap)

    for ((key, value) in resolutionMap) {
      mAttachedSurfaceResolutionMap[key] = value
    }
  }

  /**
   * Called when binding new use cases via [CameraX.bindToLifecycle]. Override to create necessary objects like [android.media.ImageReader]
   * depending on the resolution.
   *
   * @param suggestedResolutionMap A map of the names of the [                               ] to the suggested
   * resolution that depends on camera
   * device capability and what and how many use cases will be
   * bound.
   * @return The map with the resolutions that finally used to create the SessionConfig to
   * attach to the camera device.
   */
  protected abstract fun onSuggestedResolutionUpdated(
      suggestedResolutionMap: Map<String, Size>): Map<String, Size>

  /**
   * Called when CameraControl is attached into the UseCase. UseCase may need to override this
   * method to configure the CameraControl here. Ex. Setting correct flash mode by
   * CameraControl.setFlashMode to enable correct AE mode and flash state.
   *
   * @hide
   */
  @RestrictTo(Scope.LIBRARY_GROUP)
  protected fun onCameraControlReady(cameraId: String) {
  }

  /**
   * Called when use case is binding to life cycle via
   * [CameraX.bindToLifecycle].
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  protected fun onBind() {
    val eventListener = useCaseConfig!!.getUseCaseEventListener(null)
    if (eventListener != null) {
      eventListener!!.onBind(cameraIdUnchecked)
    }
  }

  /**
   * Retrieves a previously attached [CameraControl].
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  protected fun getCameraControl(cameraId: String): CameraControl {
    return mAttachedCameraControlMap.get(cameraId) ?: return CameraControl.DEFAULT_EMPTY_INSTANCE
  }

  internal enum class State {
    /** Currently waiting for image data.  */
    ACTIVE,
    /** Currently not waiting for image data.  */
    INACTIVE
  }

  /**
   * Listener called when a [UseCase] transitions between active/inactive states.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface StateChangeListener {
    /**
     * Called when a [UseCase] becomes active.
     *
     * When a UseCase is active it expects that all data producers attached to itself
     * should start producing data for it to consume. In addition the UseCase will start
     * producing data that other classes can be consumed.
     */
    fun onUseCaseActive(useCase: UseCase)

    /**
     * Called when a [UseCase] becomes inactive.
     *
     *
     * When a UseCase is inactive it no longer expects data to be produced for it. In
     * addition the UseCase will stop producing data for other classes to consume.
     */
    fun onUseCaseInactive(useCase: UseCase)

    /**
     * Called when a [UseCase] has updated settings.
     *
     * When a [UseCase] has updated settings, it is expected that the listener will
     * use these updated settings to reconfigure the listener's own state. A settings update is
     * orthogonal to the active/inactive state change.
     */
    fun onUseCaseUpdated(useCase: UseCase)

    /**
     * Called when a [UseCase] has updated settings that require complete reset of the
     * camera.
     *
     * Updating certain parameters of the use case require a full reset of the camera. This
     * includes updating the [android.view.Surface] used by the use case.
     */
    fun onUseCaseReset(useCase: UseCase)
  }

  /**
   * Listener called when a [UseCase] transitions between bind/unbind states.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface EventListener {

    /**
     * Called when use case was bound to the life cycle.
     * @param cameraId that current used.
     */
    fun onBind(cameraId: String?)

    /**
     * Called when use case was unbind from the life cycle and clear the resource of the use
     * case.
     */
    fun onUnbind()
  }

  companion object {
    private const val TAG = "UseCase"
  }

}
