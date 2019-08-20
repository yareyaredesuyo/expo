package expo.modules.camera2.guava.concurrent

import java.util.concurrent.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Implementations of `Futures.immediate*`.
 *
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/ImmediateFuture.java)
 */
internal abstract class ImmediateFuture<V> : ListenableFuture<V> {

  override fun addListener(listener: Runnable, executor: Executor) {
    try {
      executor.execute(listener)
    } catch (e: RuntimeException) {
      // ListenableFuture's contract is that it will not throw unchecked exceptions, so log the bad
      // runnable and/or executor and swallow it.
      log.log(
          Level.SEVERE,
          "RuntimeException while executing runnable $listener with executor $executor",
          e)
    }
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    return false
  }

  @Throws(ExecutionException::class)
  abstract override fun get(): V

  @Throws(ExecutionException::class)
  override operator fun get(timeout: Long, unit: TimeUnit): V {
    return get()
  }

  override fun isCancelled(): Boolean {
    return false
  }

  override fun isDone(): Boolean {
    return true
  }

  internal class ImmediateSuccessfulFuture<V>(private val value: V?) : ImmediateFuture<V?>() {

    override fun get(): V? = value

    override fun toString(): String = "${super.toString()}[status=SUCCESS, result=[$value]]"

    companion object {
      val NULL_FUTURE = ImmediateSuccessfulFuture(null)
    }
  }

  internal open class ImmediateFailedFuture<V>(private val cause: Throwable) : ImmediateFuture<V>() {

    @Throws(ExecutionException::class)
    override fun get(): V = throw ExecutionException(cause)

    override fun toString() = "${super.toString()}[status=SUCCESS, result=[$cause]]"
  }

  internal class ImmediateFailedScheduledFuture<V>(cause: Throwable) : ImmediateFailedFuture<V>(cause), ScheduledFuture<V> {

    override fun getDelay(timeUnit: TimeUnit): Long = 0

    override fun compareTo(other: Delayed): Int = -1
  }

  companion object {
    private val log = Logger.getLogger(ImmediateFuture::class.java.name)

    /**
     * Returns a future that contains a null value.
     *
     * This should be used any time a null value is needed as it uses a static ListenableFuture
     * that contains null, and thus will not allocate.
     */
    @Suppress("UNCHECKED_CAST") // safe since null can be cast to any type
    fun <V> nullFuture(): ListenableFuture<V> = ImmediateSuccessfulFuture.NULL_FUTURE as ListenableFuture<V>
  }
}