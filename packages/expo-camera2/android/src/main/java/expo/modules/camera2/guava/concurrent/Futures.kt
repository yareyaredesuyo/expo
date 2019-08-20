package expo.modules.camera2.guava.concurrent

import java.lang.IllegalStateException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture

/**
 * Utility class for generating specific implementations of [ListenableFuture].
 */
object Futures {

  /**
   * Returns an implementation of [ListenableFuture] which immediately contains a result.
   *
   * @param value The result that is immediately set on the future.
   * @param <V>   The type of the result.
   * @return A future which immediately contains the result.
  </V> */
  fun <V> immediateFuture(value: V?): ListenableFuture<V> {
    return if (value == null) {
      ImmediateFuture.nullFuture()
    } else ImmediateFuture.ImmediateSuccessfulFuture(value)

  }

  /**
   * Returns an implementation of [ListenableFuture] which immediately contains an
   * exception that will be thrown by [Future.get].
   *
   * @param cause The cause of the [ExecutionException] that will be thrown by
   * [Future.get].
   * @param <V>   The type of the result.
   * @return A future which immediately contains an exception.
  </V> */
  fun <V> immediateFailedFuture(cause: Throwable): ListenableFuture<V> {
    return ImmediateFuture.ImmediateFailedFuture(cause)
  }

  /**
   * Returns an implementation of [ScheduledFuture] which immediately contains an
   * exception that will be thrown by [Future.get].
   *
   * @param cause The cause of the [ExecutionException] that will be thrown by
   * [Future.get].
   * @param <V>   The type of the result.
   * @return A future which immediately contains an exception.
  </V> */
  fun <V> immediateFailedScheduledFuture(cause: Throwable): ScheduledFuture<V> {
    return ImmediateFuture.ImmediateFailedScheduledFuture(cause)
  }

  /**
   * Returns a new `Future` whose result is asynchronously derived from the result
   * of the given `Future`. If the given `Future` fails, the returned `Future`
   * fails with the same exception (and the function is not invoked).
   *
   * @param input The future to transform
   * @param function A function to transform the result of the input future to the result of the
   * output future
   * @param executor Executor to run the function in.
   * @return A future that holds result of the function (if the input succeeded) or the original
   * input's failure (if not)
   */
  fun <I, O> transformAsync(
      input: ListenableFuture<I>,
      function: AsyncFunction<in I, out O>,
      executor: Executor): ListenableFuture<O> {
    return AbstractTransformFuture.create(input, function, executor)
  }

  /**
   * Returns a new `Future` whose result is derived from the result of the given `Future`. If `input` fails, the returned `Future` fails with the same
   * exception (and the function is not invoked)
   *
   * @param input The future to transform
   * @param function A Function to transform the results of the provided future to the results of
   * the returned future.
   * @param executor Executor to run the function in.
   * @return A future that holds result of the transformation.
   */
  fun <I, O> transform(
      input: ListenableFuture<I>,
      function: Function<in I, out O>,
      executor: Executor
  ): ListenableFuture<O> {
    return AbstractTransformFuture.create(input, function, executor)
  }

  /**
   * Creates a new `ListenableFuture` whose value is a list containing the values of all its
   * successful input futures. The list of results is in the same order as the input list, and if
   * any of the provided futures fails or is canceled, its corresponding position will contain
   * `null` (which is indistinguishable from the future having a successful value of `null`).
   *
   *
   * Canceling this future will attempt to cancel all the component futures.
   *
   * @param futures futures to combine
   * @return a future that provides a list of the results of the component futures
   */
  fun <V> successfulAsList(futures: Collection<ListenableFuture<out V>>): ListenableFuture<List<V>> {
    return CollectionFuture.ListFuture(futures, false)
  }

  /**
   * Creates a new `ListenableFuture` whose value is a list containing the values of all its
   * input futures, if all succeed.
   *
   *
   * The list of results is in the same order as the input list.
   *
   *
   * Canceling this future will attempt to cancel all the component futures, and if any of the
   * provided futures fails or is canceled, this one is, too.
   *
   * @param futures futures to combine
   * @return a future that provides a list of the results of the component futures
   */
  fun <V> allAsList(futures: Collection<ListenableFuture<out V>>): ListenableFuture<List<V>> {
    return CollectionFuture.ListFuture(futures, true)
  }

  /**
   * Registers separate success and failure callbacks to be run when the `Future`'s
   * computation is [complete][java.util.concurrent.Future.isDone] or, if the
   * computation is already complete, immediately.
   *
   * @param future The future attach the callback to.
   * @param callback The callback to invoke when `future` is completed.
   * @param executor The executor to run `callback` when the future completes.
   */
  fun <V> addCallback(
      future: ListenableFuture<V>,
      callback: FutureCallback<in V>,
      executor: Executor
  ) {
    future.addListener(CallbackListener<V>(future, callback), executor)
  }

  /**
   * See [.addCallback] for behavioral notes.
   */
  private class CallbackListener<V> internal constructor(internal val mFuture: Future<V>, internal val mCallback: FutureCallback<in V>) : Runnable {

    override fun run() {
      val value: V
      try {
        value = getDone(mFuture)
      } catch (e: ExecutionException) {
        mCallback.onFailure(e.cause)
        return
      } catch (e: RuntimeException) {
        mCallback.onFailure(e)
        return
      } catch (e: Error) {
        mCallback.onFailure(e)
        return
      }

      mCallback.onSuccess(value)
    }

    override fun toString(): String {
      return javaClass.simpleName + "," + mCallback
    }
  }

  /**
   * Returns the result of the input `Future`, which must have already completed.
   *
   *
   * The benefits of this method are twofold. First, the name "getDone" suggests to readers
   * that the `Future` is already done. Second, if buggy code calls `getDone` on a
   * `Future` that is still pending, the program will throw instead of block.
   *
   * @throws ExecutionException if the `Future` failed with an exception
   * @throws CancellationException if the `Future` was cancelled
   * @throws IllegalStateException if the `Future` is not done
   */
  @Throws(ExecutionException::class, CancellationException::class, IllegalStateException::class)
  fun <V> getDone(future: Future<V>): V {
    /*
     * We throw IllegalStateException, since the call could succeed later. Perhaps we
     * "should" throw IllegalArgumentException, since the call could succeed with a different
     * argument. Those exceptions' docs suggest that either is acceptable. Google's Java
     * Practices page recommends IllegalArgumentException here, in part to keep its
     * recommendation simple: Static methods should throw IllegalStateException only when
     * they use static state.
     *
     * Why do we deviate here? The answer: We want for fluentFuture.getDone() to throw the same
     * exception as Futures.getDone(fluentFuture).
     */
    return getUninterruptibly(future)
  }

  /**
   * Invokes `Future.`[get()][Future.get] uninterruptibly.
   *
   * @throws ExecutionException if the computation threw an exception
   * @throws CancellationException if the computation was cancelled
   */
  @Throws(ExecutionException::class, CancellationException::class)
  fun <V> getUninterruptibly(future: Future<V>): V {
    var interrupted = false
    try {
      while (true) {
        try {
          return future.get()
        } catch (e: InterruptedException) {
          interrupted = true
        }

      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt()
      }
    }
  }
}
