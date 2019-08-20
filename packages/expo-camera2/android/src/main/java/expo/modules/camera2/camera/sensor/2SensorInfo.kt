package expo.modules.camera2.camera.sensor

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.view.Surface

import expo.modules.camera2.settings.LensFacing
import expo.modules.camera2.utils.RotationValue

class `2SensorInfo`(
    cameraManager: CameraManager,
    cameraId: String
) : SensorInfo {
  private val cameraCharacteristics = try {
    cameraManager.getCameraCharacteristics(cameraId)
  } catch (e: CameraAccessException) {
    throw SensorInfo.UnavailableException("Unable to retrieve info for camera $cameraId", e)
  }

  init {
    checkCharacteristicAvailable(CameraCharacteristics.SENSOR_ORIENTATION, "Sensor orientation")
    checkCharacteristicAvailable(CameraCharacteristics.LENS_FACING, "Lens facing direction")
    checkCharacteristicAvailable(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL, "Supported hardware level")
  }

  override val lensFacing: LensFacing
    get() = LensFacing.fromLensFacing(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING))

  override val sensorRotationDegrees: Int
    get() = getSensorRotationDegrees(Surface.ROTATION_0)

  override val supportedHardwareLevel: SupportedHardwareLevel
    get() = SupportedHardwareLevel.fromSupportedHardwareLevel(cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL))

  override fun getSensorRotationDegrees(@RotationValue relativeRotation: Int): Int {
    val sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
//    val relativeRotationDegrees = CameraOrientationUtil.surfaceRotationToDegrees(relativeRotation)
    val isOppositeFacingScreen = LensFacing.BACK == lensFacing

//    return CameraOrientationUtil.getRelativeImageRotation(
//        relativeRotationDegrees,
//        sensorOrientation,
//        isOppositeFacingScreen
//    )
      return 0
  }

  @Throws(SensorInfo.UnavailableException::class)
  private fun checkCharacteristicAvailable(key: CameraCharacteristics.Key<*>, readableName: String) {
    if (cameraCharacteristics.get(key) == null) {
      throw SensorInfo.UnavailableException("Camera characteristics map is missing value for characteristic: $readableName")
    }
  }

  companion object {
    private const val TAG = "Camera2CameraInfo"
  }
}