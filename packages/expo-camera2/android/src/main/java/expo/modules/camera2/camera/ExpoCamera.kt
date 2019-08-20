package expo.modules.camera2.camera

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Main interface for accessing ExpoCamera library.
 */
object ExpoCamera {
  private lateinit var cameraRepository: CameraRepository
  private lateinit var device: Device

  val initialized = AtomicBoolean(false)


  fun initialize(context: Context) {
    if (initialized.getAndSet(true)) {
      return
    }

    cameraRepository = CameraRepository(context)

  }

}