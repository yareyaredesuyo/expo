package expo.modules.camera2.camera

import android.hardware.camera2.CameraDevice

/**
 * Wrapper around collection of [CameraDevice.StateCallback]s
 */
class CameraDeviceStateCallbacks(vararg val callbacks: CameraDevice.StateCallback) : CameraDevice.StateCallback() {
  override fun onOpened(camera: CameraDevice) {
    callbacks.forEach {it.onOpened(camera) }
  }

  override fun onClosed(camera: CameraDevice) {
    callbacks.forEach {it.onClosed(camera) }
  }

  override fun onDisconnected(camera: CameraDevice) {
    callbacks.forEach {it.onDisconnected(camera) }
  }

  override fun onError(camera: CameraDevice, error: Int) {
    callbacks.forEach {it.onError(camera) }
  }
}
