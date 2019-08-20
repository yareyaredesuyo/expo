package expo.modules.camera2.preview

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.support.annotation.WorkerThread
import android.util.Size
import android.view.Surface
import android.view.TextureView

import expo.modules.camera2.exception.UnavailableSurfaceException

import java.util.concurrent.CountDownLatch

import expo.modules.camera2.hardware.orientation.Orientation
import expo.modules.camera2.utils.swapDimensions
import java.util.*

class PreviewTextureView(context: Context) : TextureView(context) {

  /**
   * CountDownLatch that waits until surfaceTexture is available what is signaled after view is mounted
   */
  private val surfaceTextureLatch = CountDownLatch(1)

  init {
    surfaceTextureListener = object : SurfaceTextureListener {
      override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
      }

      override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
      }

      override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        return true
      }

      override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        surfaceTextureLatch.countDown()
      }
    }
  }

  private var surface: Surface? = null
  internal val surfaceSize: Size
    get() = Size(width, height)

  internal var previewSize: Size? = null
  private var availablePreviewSizes: Array<Size>? = null

  /**
   * Get [Surface] that wraps [SurfaceTexture]
   * If [SurfaceTexture] is not yet available wait for it until it becomes available
   */
  @WorkerThread
  fun getSurface(): Surface {
    // returns immediately if countdownLatch reached 0
    surfaceTextureLatch.await()
    return surface ?: (surfaceTexture ?: throw UnavailableSurfaceException())
        .let(::Surface)
        .also {
          surface = it
        }
  }

  fun setOrientation(orientation: Orientation) {
    previewSize = when (orientation) {
      is Orientation.Horizontal -> availablePreviewSizes?.get(0)?.swapDimensions()
      else -> availablePreviewSizes?.get(0)
    }
  }

  fun setAvailablePreviewSizes(availablePreviewSizes: Array<Size>) {
    this.availablePreviewSizes = availablePreviewSizes
  }

  /** Helper function that fits a camera preview into the given [TextureView] */
//  private fun updateTransform(rotation: Int?, newBufferDimens: Size, newViewFinderDimens: Size) {
//
//    if (rotation == viewFinderRotation &&
//        Objects.equals(newBufferDimens, bufferDimens) &&
//        Objects.equals(newViewFinderDimens, viewFinderDimens)) {
//      // Nothing has changed, no need to transform output again
//      return
//    }
//
//    if (rotation == null) {
//      // Invalid rotation - wait for valid inputs before setting matrix
//      return
//    } else {
//      // Update internal field with new inputs
//      viewFinderRotation = rotation
//    }
//
//    if (newBufferDimens.width == 0 || newBufferDimens.height == 0) {
//      // Invalid buffer dimens - wait for valid inputs before setting matrix
//      return
//    } else {
//      // Update internal field with new inputs
//      bufferDimens = newBufferDimens
//    }
//
//    if (newViewFinderDimens.width == 0 || newViewFinderDimens.height == 0) {
//      // Invalid view finder dimens - wait for valid inputs before setting matrix
//      return
//    } else {
//      // Update internal field with new inputs
//      viewFinderDimens = newViewFinderDimens
//    }
//
//    val matrix = Matrix()
//
//    // Compute the center of the view finder
//    val centerX = viewFinderDimens.width / 2f
//    val centerY = viewFinderDimens.height / 2f
//
//    // Correct preview output to account for display rotation
//    matrix.postRotate(-viewFinderRotation!!.toFloat(), centerX, centerY)
//
//    // Buffers are rotated relative to the device's 'natural' orientation: swap width and height
//    val bufferRatio = bufferDimens.height / bufferDimens.width.toFloat()
//
//    val scaledWidth: Int
//    val scaledHeight: Int
//    // Match longest sides together -- i.e. apply center-crop transformation
//    if (viewFinderDimens.width > viewFinderDimens.height) {
//      scaledHeight = viewFinderDimens.width
//      scaledWidth = Math.round(viewFinderDimens.width * bufferRatio)
//    } else {
//      scaledHeight = viewFinderDimens.height
//      scaledWidth = Math.round(viewFinderDimens.height * bufferRatio)
//    }
//
//    // Compute the relative scale value
//    val xScale = scaledWidth / viewFinderDimens.width.toFloat()
//    val yScale = scaledHeight / viewFinderDimens.height.toFloat()
//
//    // Scale input buffers to fill the view finder
//    matrix.preScale(xScale, yScale, centerX, centerY)
//
//    // Finally, apply transformations to our TextureView
//    textureView.setTransform(matrix)
//  }
}