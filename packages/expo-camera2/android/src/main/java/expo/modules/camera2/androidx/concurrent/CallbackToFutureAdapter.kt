package expo.modules.camera2.androidx.concurrent

import expo.modules.camera2.guava.concurrent.ListenableFuture

import java.lang.ref.WeakReference
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * A utility that provides safety checks as an alternative to [androidx.concurrent.futures.ResolvableFuture],
 * failing the future if it will never complete.
 * Useful for adapting interfaces that take callbacks into interfaces that return [ListenableFuture].
 *
 * Try to avoid creating references from listeners on the returned [Future] to the [Completer]
 * or the passed-in `tag` object, as this will defeat the best-effort early failure
 * detection based on garbage collection.
 *
 * Copied and adapted from [androidx.concurrent:concurrent-futures]
 */
object CallbackToFutureAdapter {

  /**
   * Returns a Future that will be completed by the {@link Completer} provided in from [Resolver.getCompleter].
   *
   * The provided callback is invoked immediately inline. Any exceptions thrown by it will fail the returned [Future].
   */
  fun <T> getFuture(callback: Resolver<T>): ListenableFuture<T> {
    val completer = Completer<T>()
    val safeFuture = SafeFuture(completer)
    completer.future = safeFuture

    // Set something as the tag, so that we can hopefully identify the call site from the toString() of the future.
    // Retaining the instance could potentially cause a leak (if it's an inner class)
    // and it's probably a lambda anyway so retaining the class provides just as much information.
    completer.tag = callback.javaClass

    // Start timeout before invoking the callback
    try {
      callback.attachCompleter(completer).let {
        if (it != null) {
          completer.tag = it
        }
      }
    } catch (e: Exception) {
      safeFuture.setException(e)
    }

    return safeFuture
  }

  /**
   * Called by [getFuture].
   */
  interface Resolver<T> {
    /**
     * Create your callback object and start whatever operations are required to trigger it here.
     *
     * @param completer Call one of the set methods on this object to complete the returned Future.
     * @return an object to use as the human-readable description of what is expected to complete
     * this future. In error cases, its toString() will be included in the message.
     */
    @Throws(Exception::class)
    fun attachCompleter(completer: Completer<T>): Any?
  }

  // TODO(b/119308748): Implement InternalFutureFailureAccess
  internal class SafeFuture<T> internal constructor(completer: Completer<T>) : ListenableFuture<T> {
    internal val completerWeakReference: WeakReference<Completer<T>> = WeakReference(completer)

    private val delegate = object : AbstractResolvableFuture<T>() {
      override fun pendingToString(): String {
        val completerInstance = completerWeakReference.get()
        return if (completerInstance == null) {
          "Completer object has been garbage collected, future will fail soon"
        } else {
          "tag=[" + completerInstance.tag + "]"
        }
      }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      // Obtain reference to completer before setting; a listener might make completer weakly
      // weakly reachable.
      val completer = completerWeakReference.get()
      val cancelled = delegate.cancel(mayInterruptIfRunning)
      if (cancelled && completer != null) {
        // If the completer was null here, that means it will be finalized in the future.
        completer.fireCancellationListeners()
      }
      return cancelled
    }

    internal fun cancelWithoutNotifyingCompleter(shouldInterrupt: Boolean): Boolean {
      return delegate.cancel(shouldInterrupt)
    }

    // setFuture intentionally omitted, because it interacts badly with timeouts

    internal fun set(value: T): Boolean {
      return delegate.set(value)
    }

    internal fun setException(t: Throwable): Boolean {
      return delegate.setException(t)
    }

    override fun isCancelled(): Boolean {
      return delegate.isCancelled
    }

    override fun isDone(): Boolean {
      return delegate.isDone
    }

    @Throws(InterruptedException::class, ExecutionException::class)
    override fun get(): T? {
      return delegate.get()
    }

    @Throws(InterruptedException::class, ExecutionException::class, TimeoutException::class)
    override operator fun get(timeout: Long, unit: TimeUnit): T? {
      return delegate[timeout, unit]
    }

    override fun addListener(listener: Runnable, executor: Executor) {
      delegate.addListener(listener, executor)
    }

    override fun toString(): String {
      return delegate.toString()
    }
  }

  /** Used to complete the future returned by [.getFuture]  */
  class Completer<T> internal constructor() {
    // synthetic access
    internal var tag: Any? = null
    // synthetic access
    internal var future: SafeFuture<T>? = null
    private var cancellationFuture: ResolvableFuture<Void>? = ResolvableFuture.create()

    /**
     * Tracks whether a caller ever attempted to complete the future. If they did, we won't
     * invoke
     * cancellation listeners if this object is GCed.
     */
    private var attemptedSetting: Boolean = false

    /**
     * Sets the result of the `Future` unless the `Future` has already been
     * cancelled or
     * set. When a call to this method returns, the `Future` is guaranteed to be done.
     *
     * @param value the value to be used as the result
     * @return true if this attempt completed the `Future`, false if it was already
     * complete
     */
    fun set(value: T): Boolean {
      attemptedSetting = true
      val localFuture = future
      val wasSet = localFuture != null && localFuture.set(value)
      if (wasSet) {
        setCompletedNormally()
      }
      return wasSet
    }

    /**
     * Sets the failed result of the `Future` unless the `Future` has already been
     * cancelled or set. When a call to this method returns, the `Future` is guaranteed
     * to be
     * done.
     *
     * @param t the exception to be used as the failed result
     * @return true if this attempt completed the `Future`, false if it was already
     * complete
     */
    fun setException(t: Throwable): Boolean {
      attemptedSetting = true
      val localFuture = future
      val wasSet = localFuture != null && localFuture.setException(t)
      if (wasSet) {
        setCompletedNormally()
      }
      return wasSet
    }

    /**
     * Cancels `Future` unless the `Future` has already been cancelled or set.
     * When a
     * call to this method returns, the `Future` is guaranteed to be done.
     *
     * @return true if this attempt completed the `Future`, false if it was already
     * complete
     */
    fun setCancelled(): Boolean {
      attemptedSetting = true
      val localFuture = future
      val wasSet = localFuture != null && localFuture.cancelWithoutNotifyingCompleter(
          true)
      if (wasSet) {
        setCompletedNormally()
      }
      return wasSet
    }

    /**
     * Use to propagate cancellation from the future to whatever operation is using this
     * Completer.
     *
     *
     * Will be called when the returned Future is cancelled by
     * [Future.cancel] or this `Completer` object is garbage collected
     * before the future completes.
     * Not triggered by [.setCancelled].
     */
    fun addCancellationListener(runnable: Runnable, executor: Executor) {
      cancellationFuture?.addListener(runnable, executor)
    }

    internal fun fireCancellationListeners() {
      tag = null
      future = null
      cancellationFuture!!.set(null)
    }

    private fun setCompletedNormally() {
      // Null out, so that GC does not retain the future and its value even if the callback
      // retains
      // the completer object
      tag = null
      future = null
      cancellationFuture = null
    }

    // toString intentionally left omitted, so that if the tag object (which holds this object
    // as a field) includes it in its toString, we won't infinitely recurse.

    protected fun finalize() {
      val localFuture = future
      // Complete the future with an error before any cancellation listeners try to set the
      // future.
      // Also avoid allocating the exception if we know we won't actually be able to set it.
      if (localFuture != null && !localFuture.isDone) {
        localFuture.setException(
            FutureGarbageCollectedException(
                "The completer object was garbage collected - this future would "
                    + "otherwise never "
                    + "complete. The tag was: "
                    + tag))
      }
      if (!attemptedSetting) {
        val localCancellationFuture = cancellationFuture
        if (localCancellationFuture != null) {
          // set is idempotent, so even if this was already invoked it won't run
          // listeners twice
          localCancellationFuture.set(null)
        }
      }
    }
  }

  internal class FutureGarbageCollectedException(message: String) : Throwable(message) {

    @Synchronized
    override fun fillInStackTrace(): Throwable {
      return this // no stack trace, wouldn't be useful anyway
    }
  }
}
