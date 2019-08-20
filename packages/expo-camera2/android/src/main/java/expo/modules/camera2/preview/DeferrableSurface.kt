package expo.modules.camera2.preview

/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.support.annotation.GuardedBy
import android.view.Surface

import java.util.concurrent.Executor

/**
 * A reference to a [Surface] whose creation can be deferred to a later time.
 *
 * A [OnSurfaceDetachedListener] can also be set to be notified of surface detach event. It
 * can be used to safely close the surface.
 *
 * @hide
 */
abstract class DeferrableSurface {
  // The count of attachment.
  @GuardedBy("mLock")
  private var mAttachedCount = 0

  // Listener to be called when surface is detached totally.
  @GuardedBy("mLock")
  private var mOnSurfaceDetachedListener: OnSurfaceDetachedListener? = null

  @GuardedBy("mLock")
  private var mListenerExecutor: Executor? = null

  // Lock used for accessing states.
  private val mLock = Any()

  /** Returns a [Surface] that is wrapped in a [ListenableFuture].  */
//  abstract val surface: ListenableFuture<Surface>

  internal val attachedCount: Int
    get() = synchronized(mLock) {
      return mAttachedCount
    }

  /** Refreshes the [DeferrableSurface] before attach if needed.  */
  fun refresh() {}

  /** Notifies this surface is attached  */
  fun notifySurfaceAttached() {
    synchronized(mLock) {
      mAttachedCount++
    }
  }

  /**
   * Notifies this surface is detached. OnSurfaceDetachedListener will be called if it is detached
   * totally
   */
  fun notifySurfaceDetached() {
    var listener: OnSurfaceDetachedListener? = null
    var listenerExecutor: Executor? = null

    synchronized(mLock) {
      if (mAttachedCount == 0) {
        throw IllegalStateException("Detaching occurs more times than attaching")
      }

      mAttachedCount--
      if (mAttachedCount == 0) {
        listener = mOnSurfaceDetachedListener
        listenerExecutor = mListenerExecutor
      }
    }

    if (listener != null && listenerExecutor != null) {
      callOnSurfaceDetachedListener(listener!!, listenerExecutor!!)
    }
  }

  /**
   * Sets the listener to be called when surface is detached totally.
   *
   *
   * If the surface is currently not attached, the listener will be called immediately. When
   * clearing resource like ImageReader, to close it safely you can call this method and close the
   * resources in the listener. This can ensure the surface is closed after it is no longer held
   * in camera.
   */
  fun setOnSurfaceDetachedListener(executor: Executor,
                                   listener: OnSurfaceDetachedListener) {
    var shouldCallListenerNow = false
    synchronized(mLock) {
      mOnSurfaceDetachedListener = listener
      mListenerExecutor = executor
      // Calls the listener immediately if the surface is not attached right now.
      if (mAttachedCount == 0) {
        shouldCallListenerNow = true
      }
    }

    if (shouldCallListenerNow) {
      callOnSurfaceDetachedListener(listener, executor)
    }
  }

  private fun callOnSurfaceDetachedListener(listener: OnSurfaceDetachedListener, executor: Executor) {
    executor.execute { listener.onSurfaceDetached() }
  }

  /**
   * The listener to be called when surface is detached totally.
   */
  interface OnSurfaceDetachedListener {
    /**
     * Called when surface is totally detached.
     */
    fun onSurfaceDetached()
  }

}
