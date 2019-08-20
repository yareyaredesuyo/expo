//package expo.modules.camera2.previewTextureView
//
//import android.graphics.SurfaceTexture
//import android.opengl.GLES20
//import android.os.Looper
//import android.support.annotation.GuardedBy
//import android.support.annotation.UiThread
//import android.util.Size
//import android.view.Surface
//
//import java.nio.IntBuffer
//import java.util.ArrayList
//import java.util.HashMap
//
///**
// * A [DeferrableSurface] which verifies the [SurfaceTexture] that backs the [ ] is unreleased before returning the Surface.
// */
//internal class CheckedSurfaceTexture(private val mOutputChangedListener: OnTextureChangedListener) : DeferrableSurface() {
//  val mSurfaceToReleaseList: MutableList<Surface> = ArrayList()
//  var mSurfaceTexture: FixedSizeSurfaceTexture? = null
//  var mSurface: Surface? = null
//  private var mResolution: Size? = null
//
//  var mLock = Any()
//
//  @GuardedBy("mLock")
//  val mResourceMap: MutableMap<SurfaceTexture, Resource> = HashMap()
//
//  /**
//   * Returns the [Surface] that is backed by a [SurfaceTexture].
//   *
//   *
//   * If the [SurfaceTexture] has already been released then the surface will be reset
//   * using a new [SurfaceTexture].
//   */
//  // Reset the surface texture and notify the listener
//  val surface: ListenableFuture<Surface>
//    get() = CallbackToFutureAdapter.getFuture(
//        object : CallbackToFutureAdapter.Resolver<Surface>() {
//          fun attachCompleter(
//              @NonNull completer: CallbackToFutureAdapter.Completer<Surface>): Any {
//            val checkAndSetRunnable = Runnable {
//              if (isSurfaceTextureReleasing(mSurfaceTexture)) {
//                this@CheckedSurfaceTexture.resetSurfaceTexture()
//              }
//
//              if (mSurface == null) {
//                mSurface = createSurfaceFrom(mSurfaceTexture)
//              }
//              completer.set(mSurface)
//            }
//            runOnMainThread(checkAndSetRunnable)
//            return "CheckSurfaceTexture"
//          }
//        })
//
//  private fun createDetachedSurfaceTexture(resolution: Size): FixedSizeSurfaceTexture {
//    val buffer = IntBuffer.allocate(1)
//    GLES20.glGenTextures(1, buffer)
//    val resource = Resource()
//    val surfaceTexture = FixedSizeSurfaceTexture(buffer.get(),
//        resolution, resource)
//    surfaceTexture.detachFromGLContext()
//    resource.setSurfaceTexture(surfaceTexture)
//
//    synchronized(mLock) {
//      mResourceMap.put(surfaceTexture, resource)
//    }
//
//    return surfaceTexture
//  }
//
//  @UiThread
//  fun setResolution(resolution: Size) {
//    mResolution = resolution
//  }
//
//  @UiThread
//  fun resetSurfaceTexture() {
//    if (mResolution == null) {
//      throw IllegalStateException(
//          "setResolution() must be called before resetSurfaceTexture()")
//    }
//
//    release()
//
//    mSurfaceTexture = createDetachedSurfaceTexture(mResolution)
//    mOutputChangedListener.onTextureChanged(mSurfaceTexture, mResolution)
//  }
//
//
//  @UiThread
//  fun isSurfaceTextureReleasing(surfaceTexture: FixedSizeSurfaceTexture?): Boolean {
//    synchronized(mLock) {
//      val resource = mResourceMap.get(surfaceTexture) ?: return true
//
//      return resource.isReleasing
//    }
//  }
//
//  @UiThread
//  fun createSurfaceFrom(surfaceTexture: FixedSizeSurfaceTexture?): Surface {
//    val surface = Surface(surfaceTexture)
//
//    synchronized(mLock) {
//      var resource: Resource? = mResourceMap.get(surfaceTexture)
//      if (resource == null) {
//        resource = Resource()
//        resource.setSurfaceTexture(surfaceTexture)
//        mResourceMap[surfaceTexture] = resource
//      }
//
//      resource.setSurface(surface)
//    }
//    return surface
//  }
//
//  fun refresh() {
//    runOnMainThread(Runnable {
//      if (isSurfaceTextureReleasing(mSurfaceTexture)) {
//        // Reset the surface texture and notify the listener
//        this@CheckedSurfaceTexture.resetSurfaceTexture()
//      }
//      // To fix the incorrect previewTextureView orientation for devices running on legacy camera,
//      // it needs to attach a new Surface instance to the newly created camera capture
//      // session.
//      if (mSurface != null) {
//        mSurfaceToReleaseList.add(mSurface)
//      }
//      mSurface = createSurfaceFrom(mSurfaceTexture)
//    })
//  }
//
//  @UiThread
//  fun release() {
//    if (mSurface == null && mSurfaceTexture == null) {
//      return
//    }
//
//    val resource: Resource?
//    synchronized(mLock) {
//      resource = mResourceMap.get(mSurfaceTexture)
//    }
//
//    if (resource != null) {
//      releaseResourceWhenDetached(resource)
//    }
//    mSurfaceTexture = null
//    mSurface = null
//
//    for (surface in mSurfaceToReleaseList) {
//      surface.release()
//    }
//    mSurfaceToReleaseList.clear()
//  }
//
//  fun releaseResourceWhenDetached(resource: Resource) {
//    synchronized(mLock) {
//      resource.isReleasing = true
//    }
//
//    setOnSurfaceDetachedListener(CameraXExecutors.mainThreadExecutor(),
//        object : OnSurfaceDetachedListener() {
//          fun onSurfaceDetached() {
//            val resourcesToRelease = ArrayList<Resource>()
//
//            synchronized(mLock) {
//              for (resource in mResourceMap.values) {
//                if (resource.isReleasing) {
//                  resourcesToRelease.add(resource)
//                }
//              }
//
//              // Removes the resource from the map since it is of no use.
//              for (resourceToRemove in resourcesToRelease) {
//                mResourceMap.remove(resourceToRemove.mSurfaceTexture)
//              }
//            }
//
//            for (resource in resourcesToRelease) {
//              resource.release()
//            }
//          }
//        })
//  }
//
//  fun runOnMainThread(runnable: Runnable) {
//    val executor = if (Looper.myLooper() == Looper.getMainLooper())
//      CameraXExecutors.directExecutor()
//    else
//      CameraXExecutors.mainThreadExecutor()
//    executor.execute(runnable)
//  }
//
//  internal interface OnTextureChangedListener {
//    fun onTextureChanged(newOutput: SurfaceTexture?, newResolution: Size)
//  }
//
//  /**
//   * Contains a pair of SurfaceTexture and Surface and also implements
//   * FixedSizeSurfaceTexture.Owner interface to control the release timing of
//   * FixedSizeSurfaceTexture.
//   */
//  internal inner class Resource : FixedSizeSurfaceTexture.Owner {
//    var mSurfaceTexture: FixedSizeSurfaceTexture? = null
//    var mSurface: Surface? = null
//    @get:Synchronized
//    @set:Synchronized
//    var isReleasing = false
//    var mIsReadyToRelease = false
//
//    @UiThread
//    fun setSurfaceTexture(surfaceTexture: FixedSizeSurfaceTexture?) {
//      mSurfaceTexture = surfaceTexture
//    }
//
//    @UiThread
//    fun setSurface(surface: Surface) {
//      mSurface = surface
//    }
//
//    @Synchronized
//    fun requestRelease(): Boolean {
//      if (mIsReadyToRelease) {
//        return true
//      }
//
//      releaseResourceWhenDetached(this)
//      return false
//    }
//
//    @UiThread
//    @Synchronized
//    fun release() {
//      mIsReadyToRelease = true
//
//      if (mSurfaceTexture != null) {
//        mSurfaceTexture!!.release()
//        mSurfaceTexture = null
//      }
//
//      if (mSurface != null) {
//        mSurface!!.release()
//        mSurface = null
//      }
//    }
//  }
//}
