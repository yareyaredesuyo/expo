package expo.modules.camera2.camera.executors

import android.os.Handler
import android.os.Looper
import expo.modules.camera2.guava.executors.DirectExecutor
import expo.modules.camera2.guava.executors.SequentialExecutor
import java.lang.IllegalStateException

import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

/**
 * Utility class for generating specific implementations of [Executor].
 */
object CameraExecutors {

  /** Returns a cached [Executor] which posts to the main thread.  */
  fun mainThreadExecutor(): ScheduledExecutorService {
    return MainThreadExecutor
  }

  /** Returns a cached [Executor] suitable for disk I/O.  */
  fun ioExecutor(): Executor {
    return IoExecutor
  }

  /** Returns a cached executor that runs tasks directly from the calling thread.  */
  fun directExecutor(): Executor {
    return DirectExecutor
  }

  /**
   * Returns a new executor which will perform all tasks sequentially.
   *
   * The returned executor delegates all tasks to the provided delegate Executor, but will
   * ensure all tasks are run in order and without overlapping. Note this can only be
   * guaranteed for tasks that are submitted via the same sequential executor. Tasks submitted
   * directly to the delegate or to different instances of the sequential executor do not have
   * any ordering guarantees.
   */
  fun getNewSequentialExecutor(delegate: Executor): Executor {
    return SequentialExecutor(delegate)
  }

  /**
   * Returns whether the executor is a sequential executor as returned by
   * [.newSequentialExecutor].
   */
  fun isSequentialExecutor(executor: Executor): Boolean {
    return executor is SequentialExecutor
  }

  /**
   * Returns an executor which posts to the thread's current [Looper].
   *
   * @return An executor which posts to the thread's current looper.
   * @throws IllegalStateException if the current thread does not have a looper.
   */
  @Throws(IllegalStateException::class)
  fun getMyLooperExecutor(): Executor {
    return HandlerScheduledExecutorService.currentThreadExecutor
  }

  /**
   * Returns an executor which posts to the given [Handler].
   *
   * @return An executor which posts to the given handler.
   */
  fun getNewHandlerExecutor(handler: Handler): ScheduledExecutorService {
    return HandlerScheduledExecutorService(handler)
  }
}
