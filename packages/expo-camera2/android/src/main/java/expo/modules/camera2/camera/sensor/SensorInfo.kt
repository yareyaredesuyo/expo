package expo.modules.camera2.camera.sensor

import android.view.Surface

import expo.modules.camera2.settings.LensFacing
import expo.modules.camera2.utils.RotationValue

/**
 * Interface for retrieving camera sensor information.
 */
interface SensorInfo {

    /**
     * Returns the LensFacing of this camera.
     *
     * @return One of [LensFacing].
     */
    val lensFacing: LensFacing

    /**
     * Returns the sensor rotation, in degrees, relative to the device's "natural" rotation.
     *
     * @return The sensor orientation in degrees.
     * @see Surface.ROTATION_0, the natural orientation of the device.
     */
    val sensorRotationDegrees: Int

    /**
     * Returns hardware level that is supported by this sensor
     *
     * @return One of [SupportedHardwareLevel].
     */
    val supportedHardwareLevel: SupportedHardwareLevel

    /**
     * Returns the sensor rotation, in degrees, relative to the given rotation value.
     *
     * Valid values for the relative rotation are:
     *  [Surface.ROTATION_0],
     *  [Surface.ROTATION_90],
     *  [Surface.ROTATION_180],
     *  [Surface.ROTATION_270].
     *
     * @param relativeRotation The rotation relative to which the output will be calculated.
     * @return The sensor orientation in degrees.
     */
    fun getSensorRotationDegrees(@RotationValue relativeRotation: Int): Int


    /**
     * An exception thrown when unable to retrieve information about a camera sensor.
     * */
    class UnavailableException(s: String, e: Throwable? = null) : Exception(s, e)
}
