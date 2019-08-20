package expo.modules.camera2.preview

import android.util.Size
import java.lang.IllegalStateException

class PreviewSurfaceManager {
  private var isInitialized = false

  companion object {
    private val TAG = this::class.java.simpleName
    private val MAXIMUM_PREVIEW_SIZE = Size(1920, 1080)

  }

  /**
   * Retrieves the preview size, choosing the smaller of the display size and [MAXIMUM_PREVIEW_SIZE]
   */
  fun getPreviewSize() {
    if (!isInitialized) {
      throw IllegalStateException("$TAG is not initialized")
    }

    var previewSize = MAXIMUM_PREVIEW_SIZE
  }

}