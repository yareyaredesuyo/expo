package expo.modules.camera2.androidx.concurrent

import expo.modules.camera2.guava.concurrent.ListenableFuture

/**
 * An AndroidX version of Guava's `SettableFuture`.
 *
 *
 * A [ListenableFuture] whose result can be set by a [.set], [ ][.setException] or [.setFuture] call. It can also, like any
 * other `Future`, be [cancelled][.cancel].
 *
 *
 * If your needs are more complex than `ResolvableFuture` supports, use [ ], which offers an extensible version of the API.
 *
 * @author Sven Mawson
 */
class ResolvableFuture<V> private constructor() : AbstractResolvableFuture<V>() {

  companion object {
    /**
     * Creates a new `ResolvableFuture` that can be completed or cancelled by a later method
     * call.
     */
    fun <V> create(): ResolvableFuture<V> {
      return ResolvableFuture()
    }
  }
}

