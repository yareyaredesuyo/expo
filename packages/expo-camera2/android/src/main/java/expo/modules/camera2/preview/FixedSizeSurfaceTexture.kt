package expo.modules.camera2.preview

import android.graphics.SurfaceTexture
import android.os.Build.VERSION_CODES
import android.support.annotation.RequiresApi
import android.util.Size
import android.view.Surface

/**
 * An implementation of [SurfaceTexture] with a fixed default buffer size.
 *
 * The fixed default buffer size used at construction time cannot be changed through the [ ][.setDefaultBufferSize] method.
 */
internal class FixedSizeSurfaceTexture : SurfaceTexture {

  companion object {
    private val SELF_OWNER = object : Owner {
      override fun requestRelease(): Boolean {
        return true
      }
    }
  }

  private val owner: Owner

  /**
   * Construct a new SurfaceTexture to stream images to a given OpenGL texture.
   *
   * @param texName   the OpenGL texture object name (e.g. generated via glGenTextures)
   * @param fixedSize the fixed default buffer size
   * @param owner     the [Owner] which owns this instance.
   * @throws android.view.Surface.OutOfResourcesException If the SurfaceTexture cannot be created.
   */
  @Throws(Surface.OutOfResourcesException::class)
  constructor(texName: Int, fixedSize: Size, owner: Owner = SELF_OWNER) : super(texName) {
    super.setDefaultBufferSize(fixedSize.width, fixedSize.height)
    this.owner = owner
  }

  /**
   * Construct a new SurfaceTexture to stream images to a given OpenGL texture.
   *
   * In single buffered mode the application is responsible for serializing access to the image
   * content buffer. Each time the image content is to be updated, the [.releaseTexImage]
   * method must be called before the image content producer takes ownership of the buffer. For
   * example, when producing image content with the NDK ANativeWindow_lock and
   * ANativeWindow_unlockAndPost functions, [.releaseTexImage] must be called before each
   * ANativeWindow_lock, or that call will fail. When producing image content with OpenGL ES,
   * [.releaseTexImage] must be called before the first OpenGL ES function call each
   * frame.
   *
   * @param texName          the OpenGL texture object name (e.g. generated via glGenTextures)
   * @param singleBufferMode whether the SurfaceTexture will be in single buffered mode.
   * @param fixedSize        the fixed default buffer size
   * @throws android.view.Surface.OutOfResourcesException If the SurfaceTexture cannot be created.
   */
  @Throws(Surface.OutOfResourcesException::class)
  constructor(texName: Int, singleBufferMode: Boolean, fixedSize: Size) : super(texName, singleBufferMode) {
    super.setDefaultBufferSize(fixedSize.width, fixedSize.height)
    owner = SELF_OWNER
  }

  /**
   * Construct a new SurfaceTexture to stream images to a given OpenGL texture.
   *
   *
   * In single buffered mode the application is responsible for serializing access to the image
   * content buffer. Each time the image content is to be updated, the [.releaseTexImage]
   * method must be called before the image content producer takes ownership of the buffer. For
   * example, when producing image content with the NDK ANativeWindow_lock and
   * ANativeWindow_unlockAndPost functions, [.releaseTexImage] must be called before each
   * ANativeWindow_lock, or that call will fail. When producing image content with OpenGL ES,
   * [.releaseTexImage] must be called before the first OpenGL ES function call each
   * frame.
   *
   *
   * Unlike [], which takes an OpenGL texture object name,
   * this constructor creates the SurfaceTexture in detached mode. A texture name must be passed
   * in using [.attachToGLContext] before calling [.releaseTexImage] and producing
   * image content using OpenGL ES.
   *
   * @param singleBufferMode whether the SurfaceTexture will be in single buffered mode.
   * @param fixedSize        the fixed default buffer size
   * @throws Surface.OutOfResourcesException If the SurfaceTexture cannot be created.
   */
  @RequiresApi(api = VERSION_CODES.O)
  @Throws(Surface.OutOfResourcesException::class)
  constructor(singleBufferMode: Boolean, fixedSize: Size) : super(singleBufferMode) {
    super.setDefaultBufferSize(fixedSize.width, fixedSize.height)
    owner = SELF_OWNER
  }

  /**
   * This method has no effect.
   *
   * Unlike [SurfaceTexture], this method does not affect the default buffer size. The
   * default buffer size will remain what it was set to during construction.
   *
   * @param width  ignored width
   * @param height ignored height
   */
  override fun setDefaultBufferSize(width: Int, height: Int) {
    // No-op
  }

  /**
   * Overrides the release() to request Owner's permission before releasing it.
   */
  override fun release() {
    if (owner.requestRelease()) {
      super.release()
    }
  }

  /**
   * An interface for specifying the ownership of a resource.
   *
   * It is used in condition that some resource cannot be released by itself and would like to be
   * controlled by a OWNER. The resource can only be release()'d when Owner's requestRelease
   * returns
   * true.
   */
  internal interface Owner {
    /** request release permission from owner  */
    fun requestRelease(): Boolean
  }
}
/**
 * Construct a new SurfaceTexture to stream images to a given OpenGL texture.
 *
 * @param texName   the OpenGL texture object name (e.g. generated via glGenTextures)
 * @param fixedSize the fixed default buffer size
 * @throws android.view.Surface.OutOfResourcesException If the SurfaceTexture cannot be created.
 */

