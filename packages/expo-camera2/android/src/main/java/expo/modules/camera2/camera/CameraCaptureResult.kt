package expo.modules.camera2.camera

import android.hardware.camera2.CaptureResult
import android.support.annotation.RestrictTo
import android.util.Log

/**
 * The capture result of a single image capture.
 * @param tag
 * @param captureResult The actual camera2 [CaptureResult].
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class CameraCaptureResult(
    val tag: Any? = null,
    private val captureResult: CaptureResult? = null
) {
  /**
   * Converts the camera2 [CaptureResult.CONTROL_AF_MODE] to [AfMode].
   * @return the [AfMode].
   */
  // fall out
  val afMode: AfMode
    get() = when (val mode = captureResult?.get(CaptureResult.CONTROL_AF_MODE)) {
      CaptureResult.CONTROL_AF_MODE_OFF,
      CaptureResult.CONTROL_AF_MODE_EDOF -> AfMode.OFF
      CaptureResult.CONTROL_AF_MODE_AUTO,
      CaptureResult.CONTROL_AF_MODE_MACRO -> AfMode.ON_MANUAL_AUTO
      CaptureResult.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
      CaptureResult.CONTROL_AF_MODE_CONTINUOUS_VIDEO -> AfMode.ON_CONTINUOUS_AUTO
      null -> AfMode.UNKNOWN
      else -> {
        Log.e(TAG, "Undefined af mode: $mode")
        AfMode.UNKNOWN
      }
    }

  /**
   * Converts the camera2 [CaptureResult.CONTROL_AF_STATE] to [AfState].
   * @return the [AfState].
   */
  val afState: AfState
    get() = when (val state = captureResult?.get(CaptureResult.CONTROL_AF_STATE)) {
      CaptureResult.CONTROL_AF_STATE_INACTIVE -> AfState.INACTIVE
      CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN,
      CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
      CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> AfState.SCANNING
      CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> AfState.LOCKED_FOCUSED
      CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> AfState.LOCKED_NOT_FOCUSED
      CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> AfState.FOCUSED
      null -> AfState.UNKNOWN
      else -> {
        Log.e(TAG, "Undefined af state: $state")
        AfState.UNKNOWN
      }
    }

  /**
   * Converts the camera2 [CaptureResult.CONTROL_AE_STATE] to [AeState].
   * @return the [AeState].
   */
  val aeState: AeState
    get() = when (val state = captureResult?.get(CaptureResult.CONTROL_AE_STATE)) {
      CaptureResult.CONTROL_AE_STATE_INACTIVE -> AeState.INACTIVE
      CaptureResult.CONTROL_AE_STATE_SEARCHING,
      CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> AeState.SEARCHING
      CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> AeState.FLASH_REQUIRED
      CaptureResult.CONTROL_AE_STATE_CONVERGED -> AeState.CONVERGED
      CaptureResult.CONTROL_AE_STATE_LOCKED -> AeState.LOCKED
      null -> AeState.UNKNOWN
      else -> {
        Log.e(TAG, "Undefined ae state: $state")
        AeState.UNKNOWN
      }
    }

  /**
   * Converts the camera2 [CaptureResult.CONTROL_AWB_STATE] to [AwbState].
   * @return the [AwbState].
   */
  val awbState: AwbState
    get() = when (val state = captureResult?.get(CaptureResult.CONTROL_AWB_STATE)) {
      CaptureResult.CONTROL_AWB_STATE_INACTIVE -> AwbState.INACTIVE
      CaptureResult.CONTROL_AWB_STATE_SEARCHING -> AwbState.METERING
      CaptureResult.CONTROL_AWB_STATE_CONVERGED -> AwbState.CONVERGED
      CaptureResult.CONTROL_AWB_STATE_LOCKED -> AwbState.LOCKED
      null -> AwbState.UNKNOWN
      else -> {
        Log.e(TAG, "Undefined awb state: $state")
        AwbState.UNKNOWN
      }
    }

  /**
   * Converts the camera2 [CaptureResult.FLASH_STATE] to [FlashState].
   * @return the [FlashState].
   */
  val flashState: FlashState
    get() = when (val state = captureResult?.get(CaptureResult.FLASH_STATE)) {
      CaptureResult.FLASH_STATE_UNAVAILABLE,
      CaptureResult.FLASH_STATE_CHARGING -> FlashState.NONE
      CaptureResult.FLASH_STATE_READY -> FlashState.READY
      CaptureResult.FLASH_STATE_FIRED,
      CaptureResult.FLASH_STATE_PARTIAL -> FlashState.FIRED
      null -> FlashState.UNKNOWN
      else -> {
        Log.e(TAG, "Undefined flash state: $state")
        FlashState.UNKNOWN
      }
    }

  /**
   * Returns the timestamp in nanoseconds.
   * If the timestamp was unavailable then it will return `-1L`.
   */
  val timestamp: Long = captureResult?.get(CaptureResult.SENSOR_TIMESTAMP) ?: -1L

  companion object {
    private const val TAG = "CameraCaptureResult"
  }
}


