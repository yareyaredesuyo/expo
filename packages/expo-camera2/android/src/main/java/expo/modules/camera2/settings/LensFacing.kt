package expo.modules.camera2.settings

import android.hardware.camera2.CameraCharacteristics

/**
 * LensFacing determines which camera sensor should be used
 *
 * @see CameraCharacteristics.LENS_FACING
 *
 * note: {@link CameraDeviceCharacteristics.LENS_FACING_EXTERNAL} is not handled
 */
enum class LensFacing(
    private val value: Int,
    val lensFacing: Int
) : Option {

  BACK(0, CameraCharacteristics.LENS_FACING_BACK),
  FRONT(1, CameraCharacteristics.LENS_FACING_FRONT);

  companion object {

    val DEFAULT = BACK

    @Throws(InvalidOptionException::class)
    internal fun fromValue(value: Int): LensFacing {
      return values().find { it.value == value }
          ?: throw InvalidOptionException("Invalid facing mode provided: '$value'. Available modes are:  ${values().map { it.value }}.")
    }

    @Throws(InvalidOptionException::class)
    internal fun fromLensFacing(lensFacing: Int): LensFacing {
      return values().find { it.lensFacing == lensFacing }
          ?: throw InvalidOptionException("Invalid lens facing provided: '$lensFacing'. Available facings are: ${values().map { it.lensFacing }}")
    }
  }

  class InvalidOptionException(s: String) : Exception(s)
}
