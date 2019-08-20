package expo.modules.camera2.camera

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import expo.modules.camera2.utils.getCameraManager

/**
 * A collection of all available [Camera] instances on the device.
 */
class CameraRepository(context: Context) {

  private val cameraManager = context.getCameraManager()

  val cameras = HashMap<String, Camera>().apply {
    cameraManager.cameraIdList.forEach {
      put(it,
          Camera(
              cameraManager = cameraManager,
              cameraId = it,
              handler = handler
              )
      )
    }
  }

  companion object {
    private val handlerThread = HandlerThread("CamerasThread")
    private val handler: Handler
    init {
      handlerThread.start()
      handler = Handler(handlerThread.looper)
    }
  }
}