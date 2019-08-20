package expo.modules.camera2.camera

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.os.Build
import android.os.Handler
import android.util.Log
import android.view.Surface
import expo.modules.camera2.camera.configs.CaptureConfig
import expo.modules.camera2.camera.configs.Config


import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedList
import java.util.concurrent.Executor

/**
 * A session for capturing images from the camera which is tied to a specific [CameraDevice].
 *
 * A session can only be opened a single time. Once has [CaptureSession.close] been
 * called then it is permanently closed so a new session has to be created for capturing images.
 */
internal class CaptureSession
/**
 * Constructor for CaptureSession.
 *
 * @param handler The handler is responsible for queuing up callbacks from capture requests. If
 * this is null then when asynchronous methods are called on this session they
 * will attempt
 * to use the current thread's looper.
 */
(
    /** Handler for all the callbacks from the [CameraCaptureSession].  */
    @param:Nullable @field:Nullable
    private val mHandler: Handler?) {
  /** An adapter to pass the task to the handler.  */
  @Nullable
  private val mExecutor: Executor?
  /** The configuration for the currently issued single capture requests.  */
  private val mCaptureConfigs = ArrayList<CaptureConfig>()
  /** Lock on whether the camera is open or closed.  */
  val mStateLock = Any()
  /** Callback for handling image captures.  */
  private val mCaptureCallback = object : CaptureCallback() {
    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult) {
    }
  }
  private val mCaptureSessionStateCallback = StateCallback()
  /** The framework camera capture session held by this session.  */
  @Nullable
  var mCameraCaptureSession: CameraCaptureSession? = null
  /** The configuration for the currently issued capture requests.  */
  @Nullable
  @Volatile
  var mSessionConfig: SessionConfig? = null
  /** The capture options from CameraEventCallback.onRepeating().  */
  @Nullable
  @Volatile
  var mCameraEventOnRepeatingOptions: Config? = null
  /**
   * The map of DeferrableSurface to Surface. It is both for restoring the surfaces used to
   * configure the current capture session and for getting the configured surface from a
   * DeferrableSurface.
   */
  private val mConfiguredSurfaceMap = HashMap<DeferrableSurface, Surface>()


  /** The list of DeferrableSurface used to notify surface detach events  */
  @GuardedBy("mConfiguredDeferrableSurfaces")
  var mConfiguredDeferrableSurfaces: MutableList<DeferrableSurface> = emptyList<DeferrableSurface>()
  /** Tracks the current state of the session.  */
  @GuardedBy("mStateLock")
  var mState = State.UNINITIALIZED
  /* synthetic accessor */
  @GuardedBy("mStateLock")
  var mReleaseFuture: ListenableFuture<Void>? = null
  /* synthetic accessor */
  @GuardedBy("mStateLock")
  var mReleaseCompleter: CallbackToFutureAdapter.Completer<Void>? = null

  /**
   * Returns the configurations of the capture session, or null if it has not yet been set
   * or if the capture session has been closed.
   */
  /**
   * Sets the active configurations for the capture session.
   *
   *
   * Once both the session configuration has been set and the session has been opened, then the
   * capture requests will immediately be issued.
   *
   * @param sessionConfig has the configuration that will currently active in issuing capture
   * request. The surfaces contained in this must be a subset of the
   * surfaces that were used to open this capture session.
   */
  var sessionConfig: SessionConfig?
    @Nullable
    get() = synchronized(mStateLock) {
      return mSessionConfig
    }
    set(sessionConfig) = synchronized(mStateLock) {
      when (mState) {
        State.UNINITIALIZED -> throw IllegalStateException(
            "setSessionConfig() should not be possible in state: $mState")
        CaptureSession.State.INITIALIZED, CaptureSession.State.OPENING -> mSessionConfig = sessionConfig
        CaptureSession.State.OPENED -> {
          mSessionConfig = sessionConfig

          if (!mConfiguredSurfaceMap.keys.containsAll(sessionConfig.getSurfaces())) {
            Log.e(TAG, "Does not have the proper configured lists")
            return
          }

          Log.d(TAG, "Attempting to submit CaptureRequest after setting")
          issueRepeatingCaptureRequests()
        }
        CaptureSession.State.CLOSED, CaptureSession.State.RELEASING, CaptureSession.State.RELEASED -> throw IllegalStateException(
            "Session configuration cannot be set on a closed/released session.")
      }
    }

  /** Returns the configurations of the capture requests.  */
  val captureConfigs: List<CaptureConfig>
    get() = synchronized(mStateLock) {
      return Collections.unmodifiableList(mCaptureConfigs)
    }

  /** Returns the current state of the session.  */
  val state: State
    get() = synchronized(mStateLock) {
      return mState
    }

  // TODO: We should enforce that mExecutor is never null.
  //  We can remove this method once that is the case.
  private val executor: Executor
    get() = if (mExecutor == null) {
      CameraXExecutors.myLooperExecutor()
    } else mExecutor

  init {
    mState = State.INITIALIZED
    mExecutor = if (mHandler != null) CameraXExecutors.newHandlerExecutor(mHandler) else null
  }

  /**
   * Opens the capture session synchronously.
   *
   *
   * When the session is opened and the configurations have been set then the capture requests
   * will be issued.
   *
   * @param sessionConfig which is used to configure the camera capture session. This contains
   * configurations which may or may not be currently active in issuing
   * capture requests.
   * @param cameraDevice  the camera with which to generate the capture session
   * @throws CameraAccessException if the camera is in an invalid start state
   */
  @Throws(CameraAccessException::class)
  fun open(sessionConfig: SessionConfig, cameraDevice: CameraDevice) {
    synchronized(mStateLock) {
      when (mState) {
        State.UNINITIALIZED -> throw IllegalStateException(
            "open() should not be possible in state: $mState")
        CaptureSession.State.INITIALIZED -> {
          val surfaces = sessionConfig.getSurfaces()

          // Before creating capture session, some surfaces may need to refresh.
          DeferrableSurfaces.refresh(surfaces)

          mConfiguredDeferrableSurfaces = ArrayList(surfaces)

          val configuredSurfaces = ArrayList(
              DeferrableSurfaces.surfaceList(mConfiguredDeferrableSurfaces))
          if (configuredSurfaces.isEmpty()) {
            Log.e(TAG, "Unable to open capture session with no surfaces. ")
            return
          }

          // Establishes the mapping of DeferrableSurface to Surface. Capture request will
          // use this mapping to get the Surface from DeferrableSurface.
          mConfiguredSurfaceMap.clear()
          for (i in configuredSurfaces.indices) {
            mConfiguredSurfaceMap[mConfiguredDeferrableSurfaces.get(i)] = configuredSurfaces.get(i)
          }

          // Some DeferrableSurfaces might actually point to the same Surface. And we need
          // to pass the unique Surface list to createCaptureSession.
          val uniqueConfiguredSurface = ArrayList<Surface>(
              HashSet<Surface>(configuredSurfaces))

          notifySurfaceAttached()
          mState = State.OPENING
          Log.d(TAG, "Opening capture session.")
          val callbacks = ArrayList(sessionConfig.getSessionStateCallbacks())
          callbacks.add(mCaptureSessionStateCallback)
          val comboCallback = CameraCaptureSessionStateCallbacks.createComboCallback(callbacks)

          // Start check preset CaptureStage information.
          val eventCallbacks = Camera2Config(
              sessionConfig.getImplementationOptions()).getCameraEventCallback(
              CameraEventCallbacks.createEmptyCallback())
          val presetList = eventCallbacks.createComboCallback().onPresetSession()

          if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P && !presetList.isEmpty()) {

            // Generate the CaptureRequest builder from repeating request since Android
            // recommend use the same template type as the initial capture request. The
            // tag and output targets would be ignored by default.
            val captureConfigBuilder = CaptureConfig.Builder.from(
                sessionConfig.getRepeatingCaptureConfig())

            for (config in presetList) {
              captureConfigBuilder.addImplementationOptions(
                  config.getImplementationOptions())
            }

            val captureRequest = Camera2CaptureRequestBuilder.buildWithoutTarget(
                captureConfigBuilder.build(),
                cameraDevice)

            if (captureRequest != null) {
              val outputConfigList = LinkedList<OutputConfiguration>()
              for (surface in uniqueConfiguredSurface) {
                outputConfigList.add(OutputConfiguration(surface))
              }

              val sessionParameterConfiguration = SessionConfiguration(SessionConfiguration.SESSION_REGULAR,
                  outputConfigList, executor, comboCallback)
              sessionParameterConfiguration.sessionParameters = captureRequest
              cameraDevice.createCaptureSession(sessionParameterConfiguration)
            } else {
              cameraDevice.createCaptureSession(uniqueConfiguredSurface,
                  comboCallback,
                  mHandler)
            }
          } else {
            cameraDevice.createCaptureSession(uniqueConfiguredSurface, comboCallback,
                mHandler)
          }
        }
        else -> Log.e(TAG, "Open not allowed in state: $mState")
      }
    }
  }

  /**
   * Closes the capture session.
   *
   *
   * Close needs be called on a session in order to safely open another session. However, this
   * stops minimal resources so that another session can be quickly opened.
   *
   *
   * Once a session is closed it can no longer be opened again. After the session is closed all
   * method calls on it do nothing.
   */
  fun close() {
    synchronized(mStateLock) {
      when (mState) {
        State.UNINITIALIZED -> throw IllegalStateException(
            "close() should not be possible in state: $mState")
        CaptureSession.State.INITIALIZED -> mState = State.RELEASED
        CaptureSession.State.OPENED -> {
          // Only issue onDisableSession requests at OPENED state.
          if (mSessionConfig != null) {
            val eventCallbacks = Camera2Config(
                mSessionConfig!!.getImplementationOptions()).getCameraEventCallback(
                CameraEventCallbacks.createEmptyCallback())
            val configList = eventCallbacks.createComboCallback().onDisableSession()
            if (!configList.isEmpty()) {
              issueCaptureRequests(setupConfiguredSurface(configList))
            }
          }
          mState = State.CLOSED
          mSessionConfig = null
          mCameraEventOnRepeatingOptions = null
        }
        // Not break close flow.
        CaptureSession.State.OPENING -> {
          mState = State.CLOSED
          mSessionConfig = null
          mCameraEventOnRepeatingOptions = null
        }
        CaptureSession.State.CLOSED, CaptureSession.State.RELEASING, CaptureSession.State.RELEASED -> {
        }
      }
    }
  }

  /**
   * Releases the capture session.
   *
   *
   * This releases all of the sessions resources and should be called when ready to close the
   * camera.
   *
   *
   * Once a session is released it can no longer be opened again. After the session is released
   * all method calls on it do nothing.
   */
  fun release(): ListenableFuture<Void>? {
    synchronized(mStateLock) {
      when (mState) {
        State.UNINITIALIZED -> throw IllegalStateException(
            "release() should not be possible in state: $mState")
        CaptureSession.State.OPENED, CaptureSession.State.CLOSED -> {
          if (mCameraCaptureSession != null) {
            mCameraCaptureSession!!.close()
          }
          mState = State.RELEASING
          if (mReleaseFuture == null) {
            mReleaseFuture = CallbackToFutureAdapter.getFuture(
                object : CallbackToFutureAdapter.Resolver<Void>() {
                  fun attachCompleter(@NonNull
                                      completer: CallbackToFutureAdapter.Completer<Void>): Any {
                    Preconditions.checkState(Thread.holdsLock(mStateLock))
                    Preconditions.checkState(mReleaseCompleter == null,
                        "Release completer expected to be null")
                    mReleaseCompleter = completer
                    return "Release[session=" + this@CaptureSession + "]"
                  }
                })
          }

          return mReleaseFuture
        }
        // Fall through
        CaptureSession.State.OPENING -> {
          mState = State.RELEASING
          if (mReleaseFuture == null) {
            mReleaseFuture = CallbackToFutureAdapter.getFuture(object : CallbackToFutureAdapter.Resolver<Void>() {
              fun attachCompleter(@NonNull
                                  completer: CallbackToFutureAdapter.Completer<Void>): Any {
                Preconditions.checkState(Thread.holdsLock(mStateLock))
                Preconditions.checkState(mReleaseCompleter == null, "Release completer expected to be null")
                mReleaseCompleter = completer
                return "Release[session=" + this@CaptureSession + "]"
              }
            })
          }
          return mReleaseFuture
        }
        // Fall through
        CaptureSession.State.RELEASING -> {
          if (mReleaseFuture == null) {
            mReleaseFuture = CallbackToFutureAdapter.getFuture(object : CallbackToFutureAdapter.Resolver<Void>() {
              fun attachCompleter(@NonNull
                                  completer: CallbackToFutureAdapter.Completer<Void>): Any {
                Preconditions.checkState(Thread.holdsLock(mStateLock))
                Preconditions.checkState(mReleaseCompleter == null, "Release completer expected to be null")
                mReleaseCompleter = completer
                return "Release[session=" + this@CaptureSession + "]"
              }
            })
          }
          return mReleaseFuture
        }
        CaptureSession.State.INITIALIZED -> mState = State.RELEASED
        // Fall through
        CaptureSession.State.RELEASED -> {
        }
      }
    }

    // Already released. Return success immediately.
    return Futures.immediateFuture(null)
  }

  // Notify the surface is attached to a new capture session.
  fun notifySurfaceAttached() {
    synchronized(mConfiguredDeferrableSurfaces) {
      for (deferrableSurface in mConfiguredDeferrableSurfaces) {
        deferrableSurface.notifySurfaceAttached()
      }
    }
  }

  // Notify the surface is detached from current capture session.
  fun notifySurfaceDetached() {
    synchronized(mConfiguredDeferrableSurfaces) {
      for (deferredSurface in mConfiguredDeferrableSurfaces) {
        deferredSurface.notifySurfaceDetached()
      }
      // Clears the mConfiguredDeferrableSurfaces to prevent from duplicate
      // notifySurfaceDetached calls.
      mConfiguredDeferrableSurfaces.clear()
    }
  }

  /**
   * Issues capture requests.
   *
   * @param captureConfigs which is used to construct [CaptureRequest].
   */
  fun issueCaptureRequests(captureConfigs: List<CaptureConfig>) {
    synchronized(mStateLock) {
      when (mState) {
        State.UNINITIALIZED -> throw IllegalStateException("issueCaptureRequests() should not be possible in state: $mState")
        State.INITIALIZED,
        State.OPENING -> mCaptureConfigs.addAll(captureConfigs)
        State.OPENED -> {
          mCaptureConfigs.addAll(captureConfigs)
          issueBurstCaptureRequest()
        }
        State.CLOSED,
        State.RELEASING,
        State.RELEASED -> throw IllegalStateException(
            "Cannot issue capture request on a closed/released session.")
      }
    }
  }

  /**
   * Sets the [CaptureRequest] so that the camera will start producing data.
   *
   * Will skip setting requests if there are no surfaces since it is illegal to do so.
   */
  fun issueRepeatingCaptureRequests() {
    if (mSessionConfig == null) {
      Log.d(TAG, "Skipping issueRepeatingCaptureRequests for no configuration case.")
      return
    }

    val captureConfig = mSessionConfig!!.getRepeatingCaptureConfig()

    try {
      Log.d(TAG, "Issuing request for session.")

      // The override priority for implementation options
      // P1 CameraEventCallback onRepeating options
      // P2 SessionConfig options
      val captureConfigBuilder = CaptureConfig.Builder.from(captureConfig)

      val eventCallbacks = Camera2Config(
          mSessionConfig!!.getImplementationOptions()).getCameraEventCallback(
          CameraEventCallbacks.createEmptyCallback())

      mCameraEventOnRepeatingOptions = mergeOptions(
          eventCallbacks.createComboCallback().onRepeating())
      if (mCameraEventOnRepeatingOptions != null) {
        captureConfigBuilder.addImplementationOptions(mCameraEventOnRepeatingOptions)
      }

      val captureRequest = Camera2CaptureRequestBuilder.build(
          captureConfigBuilder.build(), mCameraCaptureSession!!.device,
          mConfiguredSurfaceMap)
      if (captureRequest == null) {
        Log.d(TAG, "Skipping issuing empty request for session.")
        return
      }

      val comboCaptureCallback = createCamera2CaptureCallback(
          captureConfig.getCameraCaptureCallbacks(),
          mCaptureCallback)

      mCameraCaptureSession!!.setRepeatingRequest(
          captureRequest!!, comboCaptureCallback, mHandler)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Unable to access camera: " + e.message)
      Thread.dumpStack()
    }
  }

  /**
   * Issues mCaptureConfigs to [CameraCaptureSession].
   */
  fun issueBurstCaptureRequest() {
    if (mCaptureConfigs.isEmpty()) {
      return
    }
    try {
      val callbackAggregator = CameraBurstCaptureCallback()
      val captureRequests = ArrayList<CaptureRequest>()
      Log.d(TAG, "Issuing capture request.")
      for (captureConfig in mCaptureConfigs) {
        if (captureConfig.getSurfaces().isEmpty()) {
          Log.d(TAG, "Skipping issuing empty capture request.")
          continue
        }

        val captureConfigBuilder = CaptureConfig.Builder.from(
            captureConfig)

        // The override priority for implementation options
        // P1 Single capture options
        // P2 CameraEventCallback onRepeating options
        // P3 SessionConfig options
        if (mSessionConfig != null) {
          captureConfigBuilder.addImplementationOptions(
              mSessionConfig!!.getRepeatingCaptureConfig().getImplementationOptions())
        }

        if (mCameraEventOnRepeatingOptions != null) {
          captureConfigBuilder.addImplementationOptions(mCameraEventOnRepeatingOptions)
        }

        // Need to override again since single capture options has highest priority.
        captureConfigBuilder.addImplementationOptions(
            captureConfig.getImplementationOptions())

        val captureRequest = Camera2CaptureRequestBuilder.build(
            captureConfigBuilder.build(),
            mCameraCaptureSession!!.device, mConfiguredSurfaceMap)
        if (captureRequest == null) {
          Log.d(TAG, "Skipping issuing request without surface.")
          return
        }

        val cameraCallbacks = ArrayList<CameraCaptureSession.CaptureCallback>()
        for (callback in captureConfig.getCameraCaptureCallbacks()) {
          CaptureCallbackConverter.toCaptureCallback(callback, cameraCallbacks)
        }
        callbackAggregator.addCamera2Callbacks(captureRequest, cameraCallbacks)
        captureRequests.add(captureRequest)

      }
      mCameraCaptureSession!!.captureBurst(captureRequests,
          callbackAggregator,
          mHandler)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Unable to access camera: " + e.message)
      Thread.dumpStack()
    } finally {
      mCaptureConfigs.clear()
    }
  }

  private fun createCamera2CaptureCallback(
      cameraCaptureCallbacks: List<CameraCaptureCallback>,
      vararg additionalCallbacks: CaptureCallback
  ): CaptureCallback {
    val camera2Callbacks = ArrayList<CaptureCallback>(cameraCaptureCallbacks.size + additionalCallbacks.size)
    for (callback in cameraCaptureCallbacks) {
      camera2Callbacks.add(CaptureCallbackConverter.toCaptureCallback(callback))
    }
    Collections.addAll(camera2Callbacks, *additionalCallbacks)
    return Camera2CaptureCallbacks.createComboCallback(camera2Callbacks)
  }

  internal enum class State {
    /**
     * The default state of the session before construction.
     */
    UNINITIALIZED,

    /**
     * Stable state once the session has been constructed, but prior to the [ ] being opened.
     */
    INITIALIZED,

    /**
     * Transitional state when the [CameraCaptureSession] is in the process of being
     * opened.
     */
    OPENING,

    /**
     * Stable state where the [CameraCaptureSession] has been successfully opened. During
     * this state if a valid [SessionConfig] has been set then the [ ] will be issued.
     */
    OPENED,

    /**
     * Stable state where the session has been closed. However the [CameraCaptureSession]
     * is still valid. It will remain valid until a new instance is opened at which point [ ][CameraCaptureSession.StateCallback.onClosed] will be called to do
     * final cleanup.
     */
    CLOSED,

    /**
     * Transitional state where the resources are being cleaned up.
     */
    RELEASING,

    /**
     * Terminal state where the session has been cleaned up. At this point the session should
     * not be used as nothing will happen in this state.
     */
    RELEASED
  }

  /**
   * Callback for handling state changes to the [CameraCaptureSession].
   *
   *
   * State changes are ignored once the CaptureSession has been closed.
   */
  internal inner class StateCallback : CameraCaptureSession.StateCallback() {
    /**
     * {@inheritDoc}
     *
     *
     * Once the [CameraCaptureSession] has been configured then the capture request
     * will be immediately issued.
     */
    override fun onConfigured(session: CameraCaptureSession) {
      synchronized(mStateLock) {
        when (mState) {
          State.OPENING -> {
            mState = State.OPENED
            mCameraCaptureSession = session

            // Issue capture request of enableSession if exists.
            if (mSessionConfig != null) {
              val implOptions = mSessionConfig!!.getImplementationOptions()
              val eventCallbacks = Camera2Config(implOptions).getCameraEventCallback(CameraEventCallbacks.createEmptyCallback())
              val list = eventCallbacks.createComboCallback().onEnableSession()
              if (!list.isEmpty()) {
                issueCaptureRequests(setupConfiguredSurface(list))
              }
            }

            Log.d(TAG, "Attempting to send capture request onConfigured")
            issueRepeatingCaptureRequests()
            issueBurstCaptureRequest()
          }
          State.CLOSED -> mCameraCaptureSession = session
          State.RELEASING -> session.close()
          else -> throw IllegalStateException("onConfigured() should not be possible in state: $mState")
        }
        Log.d(TAG, "CameraCaptureSession.onConfigured() mState=$mState")
      }
    }

    override fun onReady(session: CameraCaptureSession) {
      synchronized(mStateLock) {
        when (mState) {
          State.UNINITIALIZED -> throw IllegalStateException("onReady() should not be possible in state: $mState")
          else -> {}
        }
        Log.d(TAG, "CameraCaptureSession.onReady()")
      }
    }

    override fun onClosed(session: CameraCaptureSession) {
      synchronized(mStateLock) {
        if (mState == State.UNINITIALIZED) {
          throw IllegalStateException(
              "onClosed() should not be possible in state: $mState")
        }

        Log.d(TAG, "CameraCaptureSession.onClosed()")

        mState = State.RELEASED
        mCameraCaptureSession = null

        notifySurfaceDetached()

        if (mReleaseCompleter != null) {
          mReleaseCompleter!!.set(null)
          mReleaseCompleter = null
        }
      }
    }

    override fun onConfigureFailed(session: CameraCaptureSession) {
      synchronized(mStateLock) {
        when (mState) {
          State.OPENING,
          State.CLOSED -> {
            mState = State.CLOSED
            mCameraCaptureSession = session
          }
          State.RELEASING -> {
            mState = State.RELEASING
            session.close()
          }
          else -> throw IllegalStateException("onConfiguredFailed() should not be possible in state: $mState")
        }
        Log.e(TAG, "CameraCaptureSession.onConfiguredFailed()")
      }
    }
  }

  /** Also notify the surface detach event if receives camera device close event  */
  fun notifyCameraDeviceClose() {
    notifySurfaceDetached()
  }

  fun setupConfiguredSurface(list: List<CaptureConfig>): List<CaptureConfig> {
    val ret = ArrayList<CaptureConfig>()
    for (c in list) {
      val builder = CaptureConfig.Builder.from(c)
      builder.templateType = CameraDevice.TEMPLATE_PREVIEW
      for (deferrableSurface in mSessionConfig!!.getRepeatingCaptureConfig().getSurfaces()) {
        builder.addSurface(deferrableSurface)
      }
      ret.add(builder.build())
    }

    return ret
  }

  companion object {
    private const val TAG = "CaptureSession"

    /**
     * Merges the implementation options from the input [CaptureConfig] list.
     *
     * It will retain the first option if a conflict is detected.
     *
     * @param captureConfigList CaptureConfig list to be merged.
     * @return merged options.
     */
    private fun mergeOptions(captureConfigList: List<CaptureConfig>): Config {
      val options = MutableOptionsBundle.create()
      for (captureConfig in captureConfigList) {
        val newOptions = captureConfig.implementationOptions
        for (option in newOptions.listOptions()) {
          // Options/values are being copied directly
          @Suppress("UNCHECKED_CAST")
          val objectOpt = option as Config.Option<Any>
          val newValue: Any? = newOptions.retrieveOption(objectOpt, null)
          if (options.containsOption(option)) {
            val oldValue = options.retrieveOption(objectOpt, null)
            if (oldValue != newValue) {
              Log.d(TAG, "Detect conflicting option "
                  + objectOpt.id
                  + " : "
                  + newValue
                  + " != "
                  + oldValue)
            }
          } else {
            options.insertOption(objectOpt, newValue as Any)
          }
        }
      }
      return options
    }
  }
}
