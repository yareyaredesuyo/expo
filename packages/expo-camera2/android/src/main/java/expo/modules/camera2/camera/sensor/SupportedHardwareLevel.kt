package expo.modules.camera2.camera.sensor

import android.hardware.camera2.CameraCharacteristics

/**
 * Supported hardware level determines capabilities of given camera sensor.
 *
 * @see [CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]
 */
enum class SupportedHardwareLevel(
    private val supportedHardwareLevel: Int
) {

  LEGACY(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY),
  LIMITED(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED),
  FULL(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL),
  LEVEL3(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3),
  EXTERNAL(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL);

  companion object {

    @Throws(InvalidOptionException::class)
    internal fun fromSupportedHardwareLevel(supportedHardwareLevel: Int): SupportedHardwareLevel {
      return values().find { it.supportedHardwareLevel == supportedHardwareLevel }
          ?: throw InvalidOptionException("Invalid supported hardware level provided: '$supportedHardwareLevel'. Available levels are: ${values().map { it.supportedHardwareLevel }}")
    }

  }

  class InvalidOptionException(s: String) : Exception(s)
}
