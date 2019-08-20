package expo.modules.camera2.camera

import android.support.annotation.RestrictTo

/**
 * A callback object for tracking the progress of a capture request submitted to the camera device.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class CameraCaptureCallback {

  /**
   * This method is called when an image capture has fully completed and all the result metadata
   * is available.
   *
   * @param cameraCaptureResult The output metadata from the capture.
   */
  fun onCaptureCompleted(cameraCaptureResult: CameraCaptureResult = CameraCaptureResult()) {}

  /**
   * This method is called instead of [onCaptureCompleted] when the camera device failed to
   * produce a [CameraCaptureResult] for the request.
   *
   * @param failure The output failure from the capture, including the failure reason.
   */
  fun onCaptureFailed(failure: CameraCaptureFailure = CameraCaptureFailure()) {}
}
