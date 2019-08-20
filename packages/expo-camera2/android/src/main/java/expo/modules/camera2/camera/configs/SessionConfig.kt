package expo.modules.camera2.camera.configs

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.support.annotation.RestrictTo
import android.util.Log
import android.view.Surface
import expo.modules.camera2.camera.CameraCaptureCallback
import expo.modules.camera2.camera.DeferrableSurface
import expo.modules.camera2.camera.MutableOptionsBundle

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * Configurations needed for a capture session.
 *
 * In practice, the [SessionConfig.BaseBuilder] will be used to construct a SessionConfig.
 *
 * The SessionConfig contains all the [android.hardware.camera2] parameters that are
 * required to initialize a [android.hardware.camera2.CameraCaptureSession] and issue a [CaptureRequest].
 *
 * @param surfaces                            The set of [Surface] where data will be put into.
 * @param cameraDeviceStateCallback           The state callbacks for a [CameraDevice].
 * @param cameraCaptureSessionStateCallbacks  The state callbacks for a [CameraCaptureSession].
 * @param singleCameraCaptureCallbacks        The callbacks that would be used for single requests.
 * @param repeatingCaptureConfig              The configuration for building the [CaptureRequest] used for repeating requests
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
open class SessionConfig private constructor(
    surfaces: List<DeferrableSurface>,
    cameraDeviceStateCallback: List<CameraDevice.StateCallback>,
    cameraCaptureSessionStateCallbacks: List<CameraCaptureSession.StateCallback>,
    singleCameraCaptureCallbacks: List<CameraCaptureCallback>,
    val repeatingCaptureConfig: CaptureConfig
) {
  /**
   * All the surfaces that the request will write data to.
   */
  val surfaces: List<DeferrableSurface> = Collections.unmodifiableList(surfaces)

  /**
   * The state callback for a [CameraDevice].
   * Initialized as all registered [CameraDevice.StateCallback] callbacks.
   */
  val cameraDeviceStateCallbacks: List<CameraDevice.StateCallback> = Collections.unmodifiableList(cameraDeviceStateCallback)

  /**
   * The state callback for a [CameraCaptureSession].
   * Initialized as all registered [CameraCaptureSession.StateCallback] callbacks.
   */
  val sessionStateCallbacks: List<CameraCaptureSession.StateCallback> = Collections.unmodifiableList(cameraCaptureSessionStateCallbacks)

  /**
   * The callbacks used in single requests.
   * Obtains all registered [CameraCaptureCallback] callbacks for single requests.
   */
  val singleCameraCaptureCallbacks: List<CameraCaptureCallback> = Collections.unmodifiableList(singleCameraCaptureCallbacks)

  val implementationOptions: Config
    get() = repeatingCaptureConfig.implementationOptions

  val templateType: Int
    get() = repeatingCaptureConfig.templateType

  /** Obtains all registered [CameraCaptureCallback] callbacks for repeating requests.  */
  val repeatingCameraCaptureCallbacks: List<CameraCaptureCallback>
    get() = repeatingCaptureConfig.cameraCaptureCallbacks


  /**
   * Interface for unpacking a configuration into a SessionConfig.Builder
   * TODO(b/120949879): This will likely be removed once SessionConfig is refactored to remove camera2 dependencies.
   */
  interface OptionUnpacker {
    /**
     * Apply the options from the config onto the builder
     * @param config the set of options to apply
     * @param builder the builder on which to apply the options
     */
    fun unpack(config: UseCaseConfig<*>, builder: Builder)
  }

  /**
   * Base builder for easy modification/rebuilding of a [SessionConfig].
   */
  open class BaseBuilder {
    protected val surfaces: MutableSet<DeferrableSurface> = HashSet()
    protected val captureConfigBuilder = CaptureConfig.Builder()
    protected var cameraDeviceStateCallbacks: MutableList<CameraDevice.StateCallback> = ArrayList()
    protected var cameraCaptureSessionStateCallbacks: MutableList<CameraCaptureSession.StateCallback> = ArrayList()
    protected val interopCameraCaptureCallbacks: MutableList<CameraCaptureCallback> = ArrayList()
  }

  /**
   * Builder for easy modification/rebuilding of a [SessionConfig].
   */
  class Builder : BaseBuilder() {

    /** Obtain all [CameraCaptureCallback] callbacks for single requests.  */
    val singleCameraCaptureCallbacks: List<CameraCaptureCallback>
      get() = Collections.unmodifiableList(interopCameraCaptureCallbacks)

    /**
     * Set the template characteristics of the SessionConfig.
     *
     * @param templateType Template constant that must match those defined by [                     ]
     *
     * TODO(b/120949879): This is camera2 implementation detail that should be moved
     */
    fun setTemplateType(templateType: Int) {
      captureConfigBuilder.templateType = templateType
    }

    /**
     * Set the tag of the SessionConfig. For tracking the source.
     */
    fun setTag(tag: Any) {
      captureConfigBuilder.tag = tag
    }

    /**
     * Adds a [CameraDevice.StateCallback] callback.
     * @throws IllegalArgumentException if the callback already exists in the configuration.
     *
     * TODO(b/120949879): This is camera2 implementation detail that should be moved
     */
    @Throws(IllegalArgumentException::class)
    fun addDeviceStateCallback(deviceStateCallback: CameraDevice.StateCallback) {
      if (cameraDeviceStateCallbacks.contains(deviceStateCallback)) {
        throw IllegalArgumentException("Duplicate device state callback.")
      }
      cameraDeviceStateCallbacks.add(deviceStateCallback)
    }

    /**
     * Adds all [CameraDevice.StateCallback] callbacks.
     * @throws IllegalArgumentException if any callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addAllDeviceStateCallbacks(deviceStateCallbacks: Collection<CameraDevice.StateCallback>) {
      for (callback in deviceStateCallbacks) {
        addDeviceStateCallback(callback)
      }
    }

    /**
     * Adds a [CameraCaptureSession.StateCallback] callback.
     * @throws IllegalArgumentException if the callback already exists in the configuration.
     *
     * // TODO(b/120949879): This is camera2 implementation detail that should be moved
     */
    @Throws(IllegalArgumentException::class)
    fun addSessionStateCallback(sessionStateCallback: CameraCaptureSession.StateCallback) {
      if (cameraCaptureSessionStateCallbacks.contains(sessionStateCallback)) {
        throw IllegalArgumentException("Duplicate session state callback.")
      }
      cameraCaptureSessionStateCallbacks.add(sessionStateCallback)
    }

    /**
     * Adds all [CameraCaptureSession.StateCallback] callbacks.
     * @throws IllegalArgumentException if any callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addAllSessionStateCallbacks(sessionStateCallbacks: List<CameraCaptureSession.StateCallback>) {
      for (callback in sessionStateCallbacks) {
        addSessionStateCallback(callback)
      }
    }

    /**
     * Adds a [CameraCaptureCallback] callback for repeating requests.
     *
     * This callback does not call for single requests.
     * @throws IllegalArgumentException if the callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addRepeatingCameraCaptureCallback(cameraCaptureCallback: CameraCaptureCallback) {
      captureConfigBuilder.addCameraCaptureCallback(cameraCaptureCallback)
    }

    /**
     * Adds all [CameraCaptureCallback] callbacks.
     *
     * These callbacks do not call for single requests.
     * @throws IllegalArgumentException if any callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addAllRepeatingCameraCaptureCallbacks(cameraCaptureCallbacks: Collection<CameraCaptureCallback>) {
      captureConfigBuilder.addAllCameraCaptureCallbacks(cameraCaptureCallbacks)
    }

    /**
     * Adds a [CameraCaptureCallback] callback for single and repeating requests.
     *
     * Listeners added here are available in both the
     * [getRepeatingCameraCaptureCallbacks] and
     * [getSingleCameraCaptureCallbacks] methods.
     * @throws IllegalArgumentException if the callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addCameraCaptureCallback(cameraCaptureCallback: CameraCaptureCallback) {
      captureConfigBuilder.addCameraCaptureCallback(cameraCaptureCallback)
      interopCameraCaptureCallbacks.add(cameraCaptureCallback)
    }

    /**
     * Adds all [CameraCaptureCallback] callbacks for single and repeating requests.
     *
     * Listeners added here are available in both the
     * [.getRepeatingCameraCaptureCallbacks] and
     * [.getSingleCameraCaptureCallbacks] methods.
     * @throws IllegalArgumentException if any callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addAllCameraCaptureCallbacks(cameraCaptureCallbacks: Collection<CameraCaptureCallback>) {
      captureConfigBuilder.addAllCameraCaptureCallbacks(cameraCaptureCallbacks)
      interopCameraCaptureCallbacks.addAll(cameraCaptureCallbacks)
    }

    /**
     * Add a surface to the set that the session repeatedly writes data to.
     */
    fun addSurface(surface: DeferrableSurface) {
      surfaces.add(surface)
      captureConfigBuilder.addSurface(surface)
    }

    /**
     * Add a surface for the session which only used for single captures.
     */
    fun addNonRepeatingSurface(surface: DeferrableSurface) {
      surfaces.add(surface)
    }

    /**
     * Remove a surface from the set which the session repeatedly writes to.
     */
    fun removeSurface(surface: DeferrableSurface) {
      surfaces.remove(surface)
      captureConfigBuilder.removeSurface(surface)
    }

    /**
     * Clears all surfaces from the set which the session writes to.
     */
    fun clearSurfaces() {
      surfaces.clear()
      captureConfigBuilder.clearSurfaces()
    }

    /**
     * Set the [Config] for options that are implementation specific.
     */
    fun setImplementationOptions(config: Config) {
      captureConfigBuilder.setImplementationOptions(config)
    }

    /**
     * Add a set of [Config] to the implementation specific options.
     */
    fun addImplementationOptions(config: Config) {
      captureConfigBuilder.addImplementationOptions(config)
    }

    /**
     * Builds an instance of a SessionConfig that has all the combined parameters of the
     * SessionConfig that have been added to the Builder.
     */
    fun build() = SessionConfig(
        ArrayList(surfaces),
        cameraDeviceStateCallbacks,
        cameraCaptureSessionStateCallbacks,
        interopCameraCaptureCallbacks,
        captureConfigBuilder.build())

    companion object {
      /**
       * Creates a [Builder] from a [UseCaseConfig].
       *
       * Populates the builder with all the properties defined in the base configuration.
       */
      fun createFrom(config: UseCaseConfig<*>): Builder {
        val unpacker = config.getSessionOptionUnpacker(null) ?: throw IllegalStateException(
            "Implementation is missing option unpacker for " + config.getTargetName(config.toString()))

        val builder = Builder()

        // Unpack the configuration into this builder
        unpacker.unpack(config, builder)
        return builder
      }
    }
  }

  /**
   * Builder for combining multiple instances of [SessionConfig].
   * This will check if all the parameters for the [SessionConfig] are compatible with each other.
   */
  class ValidatingBuilder : BaseBuilder() {
    private val mDeviceStateCallbacks = ArrayList<CameraDevice.StateCallback>()
    private val mSessionStateCallbacks = ArrayList<CameraCaptureSession.StateCallback>()
    private val mSingleCameraCaptureCallbacks = ArrayList<CameraCaptureCallback>()
    private var valid = true
    private var templateSet = false

    /** Check if the set of SessionConfig that have been combined are valid  */
    val isValid: Boolean
      get() = templateSet && valid

    /**
     * Add the SessionConfig to the set of SessionConfig that have been aggregated by the
     * ValidatingBuilder
     */
    fun add(sessionConfig: SessionConfig) {
      val captureConfig = sessionConfig.repeatingCaptureConfig

      // Check template
      if (!templateSet) {
        captureConfigBuilder.templateType = captureConfig.templateType
        templateSet = true
      } else if (captureConfigBuilder.templateType != captureConfig.templateType) {
        val errorMessage = ("Invalid configuration due to template type: "
            + captureConfigBuilder.templateType
            + " != "
            + captureConfig.templateType)
        Log.d(TAG, errorMessage)
        valid = false
      }

      val tag = sessionConfig.repeatingCaptureConfig.tag
      captureConfigBuilder.tag = tag

      // Check device state callbacks
      mDeviceStateCallbacks.addAll(sessionConfig.cameraDeviceStateCallbacks)

      // Check session state callbacks
      mSessionStateCallbacks.addAll(sessionConfig.sessionStateCallbacks)

      // Check camera capture callbacks for repeating requests.
      captureConfigBuilder.addAllCameraCaptureCallbacks(
          sessionConfig.repeatingCameraCaptureCallbacks)

      // Check camera capture callbacks for single requests.
      mSingleCameraCaptureCallbacks.addAll(sessionConfig.singleCameraCaptureCallbacks)

      // Check surfaces
      surfaces.addAll(sessionConfig.surfaces)

      // Check capture request surfaces
      captureConfigBuilder.surfaces.addAll(captureConfig.surfaces)

      if (!surfaces.containsAll(captureConfigBuilder.surfaces)) {
        val errorMessage = "Invalid configuration due to capture request surfaces are not a subset " + "of surfaces"
        Log.d(TAG, errorMessage)
        valid = false
      }

      // Check options
      val newOptions = captureConfig.implementationOptions
      val oldOptions = captureConfigBuilder.implementationOptions
      val addedOptions = MutableOptionsBundle.create()
      for (option in newOptions.listOptions()) {
        @Suppress("UNCHECKED_CAST")
        val typeErasedOption = option as Config.Option<Any>
        val newValue = newOptions.retrieveOption(typeErasedOption, null)
        if (newValue !is MultiValueSet<*> && oldOptions.containsOption(typeErasedOption)) {
          val oldValue = oldOptions.retrieveOption(typeErasedOption, null)
          if (newValue != oldValue) {
            val errorMessage = ("Invalid configuration due to conflicting option: "
                + typeErasedOption.id
                + " : "
                + newValue
                + " != "
                + oldValue)
            Log.d(TAG, errorMessage)
            valid = false
          }
        } else {
          addedOptions.insertOption(typeErasedOption,
              newOptions.retrieveOption(typeErasedOption))
        }
      }
      captureConfigBuilder.addImplementationOptions(addedOptions)
    }

    /**
     * Builds an instance of a SessionConfig that has all the combined parameters of the
     * SessionConfig that have been added to the ValidatingBuilder.
     */
    fun build(): SessionConfig {
      if (!valid) {
        throw IllegalArgumentException("Unsupported session configuration combination")
      }
      return SessionConfig(
          ArrayList(surfaces),
          mDeviceStateCallbacks,
          mSessionStateCallbacks,
          mSingleCameraCaptureCallbacks,
          captureConfigBuilder.build())
    }

    companion object {
      private const val TAG = "ValidatingBuilder"
    }
  }

  companion object {
    /** Returns an instance of a session configuration with minimal configurations.  */
    fun defaultEmptySessionConfig() = SessionConfig(
        ArrayList(),
        ArrayList(0),
        ArrayList(0),
        ArrayList<CameraCaptureCallback>(0),
        CaptureConfig.Builder().build())
  }
}
