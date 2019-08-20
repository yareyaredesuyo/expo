package expo.modules.camera2.guava.concurrent

import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException

/**
 * A [Future] that accepts completion listeners. Each listener has an associated executor, and
 * it is invoked using this executor once the future's computation is [ complete][Future.isDone]. If the computation has already completed when the listener is added, the listener will
 * execute immediately.
 *
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/ListenableFuture.java)
 */
interface ListenableFuture<V> : Future<V> {
  /**
   * Registers a listener to be [run][Executor.execute] on the given executor.
   * The listener will run when the `Future`'s computation is [Future.isDone] or, if the computation is already complete, immediately.
   *
   * @param listener the listener to run when the computation is complete
   * @param executor the executor to run the listener in
   * @throws RejectedExecutionException if we tried to execute the listener immediately but the executor rejected it.
   */
  @Throws(RejectedExecutionException::class)
  fun addListener(listener: Runnable, executor: Executor)
}