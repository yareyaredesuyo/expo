package expo.modules.camera2.camera

import android.support.annotation.NonNull
import android.support.annotation.RestrictTo


/**
 * The CameraControlInternal Interface.
 *
 * CameraControlInternal is used for global camera operations like zoom, focus, flash and
 * triggering
 * AF/AE.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface CameraControlInternal : CameraControl {

  /** Returns the current flash mode.  */
  /**
   * Sets current flash mode
   *
   * @param flashMode the [FlashMode].
   */
  @get:NonNull
  var flashMode: FlashMode

  /** Returns if current torch is enabled or not.  */
  val isTorchOn: Boolean

  /**
   * Set the desired crop region of the sensor to read out for all capture requests.
   *
   *
   * This crop region can be used to implement digital zoom. It is applied to every single and
   * re peating requests.
   *
   * @param crop rectangle with dimensions in sensor pixel coordinate.
   */
  fun setCropRegion(@Nullable crop: Rect)

  /**
   * Enable the torch or disable the torch
   *
   * @param torch true to open the torch, false to close it.
   */
  fun enableTorch(torch: Boolean)

  /** Performs a AF trigger.  */
  fun triggerAf()

  /** Performs a AE Precapture trigger.  */
  fun triggerAePrecapture()

  /** Cancel AF trigger AND/OR AE Precapture trigger.*  */
  fun cancelAfAeTrigger(cancelAfTrigger: Boolean, cancelAePrecaptureTrigger: Boolean)

  /**
   * Performs capture requests.
   */
  fun submitCaptureRequests(@NonNull captureConfigs: List<CaptureConfig>)

  /** Listener called when CameraControlInternal need to notify event.  */
  interface ControlUpdateListener {

    /** Called when CameraControlInternal has updated session configuration.  */
    fun onCameraControlUpdateSessionConfig(@NonNull sessionConfig: SessionConfig)

    /** Called when CameraControlInternal need to send capture requests.  */
    fun onCameraControlCaptureRequests(@NonNull captureConfigs: List<CaptureConfig>)
  }

  companion object {

    val DEFAULT_EMPTY_INSTANCE: CameraControlInternal = object : CameraControlInternal {

      override var flashMode: FlashMode
        @NonNull
        get() = FlashMode.OFF
        set(@NonNull flashMode) {}

      override val isTorchOn: Boolean
        get() = false

      override fun setCropRegion(@Nullable crop: Rect) {}

      override fun enableTorch(torch: Boolean) {}

      override fun triggerAf() {}

      override fun triggerAePrecapture() {}

      override fun cancelAfAeTrigger(cancelAfTrigger: Boolean, cancelAePrecaptureTrigger: Boolean) {

      }

      override fun submitCaptureRequests(@NonNull captureConfigs: List<CaptureConfig>) {}

      fun startFocusAndMetering(@NonNull action: FocusMeteringAction) {

      }

      fun cancelFocusAndMetering() {

      }
    }
  }
}
