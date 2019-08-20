package expo.modules.camera2.hardware.device

import expo.modules.camera2.hardware.camera.CameraController
import expo.modules.camera2.settings.LensFacing

/**
 * Switches to a new [LensFacing] camera. Will do nothing if [LensFacing] is same.
 * Will restart preview automatically if existing camera has started its preview.
 */
internal fun Device.switchCamera(
  newFacing: LensFacing
) {
  // No previously selected camera
  if (!hasSelectedCamera()) {
    updateFacing(newFacing)
    return
  }

  else if (getFacing() != newFacing) {
    updateFacing(newFacing)

    restartPreview(getSelectedCamera())
  }
}

internal fun Device.restartPreview(oldCameraController: CameraController) {
  stop(oldCameraController)
  startCamera()
}