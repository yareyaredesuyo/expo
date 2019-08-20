package expo.modules.camera2.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import expo.modules.camera2.camera.configs.CaptureConfig

import expo.modules.camera2.camera.executors.CameraExecutors
import java.util.ArrayList

import java.util.concurrent.atomic.AtomicReference

class Camera(
    private val cameraManager: CameraManager,
    private val cameraId: String,
    private val handler: Handler
) {
  private val TAG = "Camera"

  private val state = AtomicReference(State.INITIALIZED)
  private val executorScheduler = CameraExecutors.getNewHandlerExecutor(handler)
  private val cameraController = CameraController(executorScheduler, executorScheduler)

  private var captureSession = CaptureSession(handler)
  private var cameraDevice: CameraDevice? = null
  private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
    override fun onOpened(cameraDevice: CameraDevice) {
      Log.d(TAG, "CameraDevice.onOpened(): ${cameraDevice.id}")
      when (this@Camera.state.get()) {
        State.CLOSING,
        State.RELEASING -> {
          cameraDevice.close()
          this@Camera.cameraDevice = null
        }
        State.OPENING,
        State.REOPENING -> {
          this@Camera.state.set(State.OPENED)
          this@Camera.cameraDevice = cameraDevice
          this@Camera.openCaptureSession()
        }
        else -> throw IllegalStateException("CameraDevice.onOpened() should not be possible from state: ${this@Camera.state.get()}")
      }
    }

    override fun onClosed(cameraDevice: CameraDevice) {
      Log.d(TAG, "CameraDevice.onClosed(): ${cameraDevice.id}")

      this@Camera.resetCaptureSession()
      when (this@Camera.state.get()) {
        State.CLOSING -> {
          this@Camera.state.set(State.INITIALIZED)
          this@Camera.cameraDevice = null
        }
        State.REOPENING -> {
          this@Camera.state.set(State.OPENING)
          openCameraDevice()
        }
        State.RELEASING -> {
          this@Camera.state.set(State.RELEASED)
          this@Camera.cameraDevice = null
        }
        else -> throw IllegalStateException("CameraDevice.onClosed() should not be possible from  state: ${this@Camera.state.get()}")
      }
    }

    override fun onDisconnected(cameraDevice: CameraDevice) {
      Log.d(TAG, "CameraDevice.onDisconnected(): ${cameraDevice.id}")
      this@Camera.resetCaptureSession()
      when (this@Camera.state.get()) {
        State.CLOSING -> {
          this@Camera.state.set(State.INITIALIZED)
          this@Camera.cameraDevice = null
        }
        State.REOPENING,
        State.OPENED,
        State.OPENING -> {
          this@Camera.state.set(State.CLOSING)
          this@Camera.cameraDevice?.close()
          this@Camera.cameraDevice = null
        }
        State.RELEASING -> {
          this@Camera.state.set(State.RELEASED)
          this@Camera.cameraDevice?.close()
          this@Camera.cameraDevice = null
        }
        else -> throw IllegalStateException("CameraDevice.onDisconnected() should not be possible from state: ${this@Camera.state.get()}")
      }
    }

    private fun getErrorMessage(errorCode: Int): String = when (errorCode) {
      ERROR_CAMERA_DEVICE -> "ERROR_CAMERA_DEVICE"
      ERROR_CAMERA_DISABLED -> "ERROR_CAMERA_DISABLED"
      ERROR_CAMERA_IN_USE -> "ERROR_CAMERA_IN_USE"
      ERROR_CAMERA_SERVICE -> "ERROR_CAMERA_SERVICE"
      ERROR_MAX_CAMERAS_IN_USE -> "ERROR_MAX_CAMERAS_IN_USE"
      else -> "UNKNOWN ERROR"
    }

    override fun onError(cameraDevice: CameraDevice, error: Int) {
      Log.e(TAG,"CameraDevice.onError(): ${cameraDevice.id} with error: ${getErrorMessage(error)}")
      this@Camera.resetCaptureSession()
      when (this@Camera.state.get()) {
        State.CLOSING -> {
          this@Camera.state.set(State.INITIALIZED)
          this@Camera.cameraDevice = null
        }
        State.REOPENING,
        State.OPENED,
        State.OPENING -> {
          this@Camera.state.set(State.CLOSING)
          this@Camera.cameraDevice?.close()
          this@Camera.cameraDevice = null
        }
        State.RELEASING -> {
          this@Camera.state.set(State.RELEASED)
          this@Camera.cameraDevice?.close()
          this@Camera.cameraDevice = null
        }
        else -> throw IllegalStateException("CameraDevice.onError() should not be possible from state: ${this@Camera.state.get()}")
      }
    }
  }

  /**
   * Open the camera asynchronously
   */
  fun open() {
    if (Looper.myLooper() != handler.looper) {
      handler.post { this@Camera.open() }
      return
    }

    when (state.get()) {
      State.INITIALIZED -> openCameraDevice()
      State.CLOSING -> state.set(State.REOPENING)
      else -> Log.d(TAG, "open() ignored due to being in state: $state")
    }
  }

  @SuppressLint("MissingPermission")
  private fun openCameraDevice() {
    state.set(State.OPENING)

    Log.d(TAG, "Opening camera: $cameraId")

    try {
      cameraManager.openCamera(cameraId, cameraDeviceStateCallback, handler)
    } catch (e: CameraAccessException) {
      Log.e(TAG, "Unable to open camera $cameraId due to ${e.message}")
      state.set(State.INITIALIZED)
    }
  }

  /**
   * Opens new capture session.
   * The previously opened session will be silently disposed of before the new session opened.
   */
  private fun openCaptureSession() {
    if (state.get() != State.OPENED) {
      Log.d(TAG, "openCaptureSession() ignored due to being called in state: ${state.get()}")
      return
    }

    if (cameraDevice == null) {
      Log.d(TAG, "cameraDevice is null")
      return
    }

    // When the previous capture session has not reached the open state, the issued single
    // capture requests will still be in request queue and will need to be passed to the next
    // capture session.
    val unissuedCaptureConfigs = captureSession.getCaptureConfigs()
    resetCaptureSession()

    val sessionConfig = validatingBuilder.build()

    if (!unissuedCaptureConfigs.isEmpty()) {
      val reissuedCaptureConfigs = ArrayList<CaptureConfig>()
      // Filters out requests that has unconfigured surface (probably caused by removeOnline)
      for (unissuedCaptureConfig in unissuedCaptureConfigs) {
        if (sessionConfig.getSurfaces().containsAll(unissuedCaptureConfig.getSurfaces())) {
          reissuedCaptureConfigs.add(unissuedCaptureConfig)
        }
      }

      if (!reissuedCaptureConfigs.isEmpty()) {
        Log.d(TAG, "reissuedCaptureConfigs")
        mCaptureSession.issueCaptureRequests(reissuedCaptureConfigs)
      }
    }

    try {
      mCaptureSession.open(sessionConfig, mCameraDevice)
    } catch (e: CameraAccessException) {
      Log.d(TAG, "Unable to configure camera " + mCameraId + " due to " + e.message)
    }
  }

  /**
   * Closes the currently opened capture session, so it can be safely disposed.
   * Replaces the old session with w new session initialized with the old sessions's configuration.
   */
  private fun resetCaptureSession() {
    Log.d(TAG, "Closing Capture Session: $cameraId")

    // Recreate an initialized (but not opened) capture session from the previous configuration
    val previousSessionConfig = captureSession.getSessionConfig()

    captureSession.close()
    captureSession.release()

    // TODO(bbarthec: inspect this fragment)
    // Saves the closed CaptureSessions if device is not closed yet.
    // We need to notify camera device closed event to these CaptureSessions.
    if (cameraDevice != null) {
      synchronized(mClosedCaptureSessions) {
        mClosedCaptureSessions.add(mCaptureSession)
      }
    }

    captureSession = CaptureSession(handler)
    captureSession.setSessionConfig(previousSessionConfig)
  }


  internal enum class State {
    /** The default state of the camera before construction.  */
    UNINITIALIZED,
    /**
     * Stable state once the camera has been constructed.
     *
     *
     * At this state the [CameraDevice] should be invalid, but threads should be still
     * in a valid state. Whenever a camera device is fully closed the camera should return to
     * this state.
     *
     *
     * After an error occurs the camera returns to this state so that the device can be
     * cleanly reopened.
     */
    INITIALIZED,
    /**
     * A transitional state where the camera device is currently opening.
     *
     *
     * At the end of this state, the camera should move into either the OPENED or CLOSING
     * state.
     */
    OPENING,
    /**
     * A stable state where the camera has been opened.
     *
     *
     * During this state the camera device should be valid. It is at this time a valid
     * capture session can be active. Capture requests should be issued during this state only.
     */
    OPENED,
    /**
     * A transitional state where the camera device is currently closing.
     *
     *
     * At the end of this state, the camera should move into the INITIALIZED state.
     */
    CLOSING,
    /**
     * A transitional state where the camera was previously closing, but not fully closed before
     * a call to open was made.
     *
     *
     * At the end of this state, the camera should move into one of two states. The OPENING
     * state if the device becomes fully closed, since it must restart the process of opening a
     * camera. The OPENED state if the device becomes opened, which can occur if a call to close
     * had been done during the OPENING state.
     */
    REOPENING,
    /**
     * A transitional state where the camera will be closing permanently.
     *
     *
     * At the end of this state, the camera should move into the RELEASED state.
     */
    RELEASING,
    /**
     * A stable state where the camera has been permanently closed.
     *
     *
     * During this state all resources should be released and all operations on the camera
     * will do nothing.
     */
    RELEASED
  }
}