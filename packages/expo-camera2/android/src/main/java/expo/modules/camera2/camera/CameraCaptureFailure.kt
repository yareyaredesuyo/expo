package expo.modules.camera2.camera

import android.support.annotation.RestrictTo

/**
 * A report of failed capture for a single image capture.
 * @param reason Determine why the request was dropped, whether due to an error or to a user action.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class CameraCaptureFailure constructor(
    val reason: Reason = Reason.ERROR
) {
  /**
   * The capture result has been dropped this frame only due to an error in the framework.
   */
  enum class Reason {
    ERROR
  }
}
