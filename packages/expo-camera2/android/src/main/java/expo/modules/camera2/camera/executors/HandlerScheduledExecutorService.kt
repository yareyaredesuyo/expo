package expo.modules.camera2.camera.executors

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import expo.modules.camera2.androidx.concurrent.CallbackToFutureAdapter

import expo.modules.camera2.guava.concurrent.Futures
import expo.modules.camera2.guava.concurrent.ListenableFuture

import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

/**
 * An implementation of [ScheduledExecutorService] which delegates all scheduled task to the given [Handler].
 */
open class HandlerScheduledExecutorService(private val handler: Handler) : AbstractExecutorService(), ScheduledExecutorService {

  companion object {
    /**
     * Retrieves a cached executor derived from the current thread's looper.
     */
    val currentThreadExecutor: ScheduledExecutorService
      get() {
        return threadLocalInstance.get() ?: let {
          val executor = HandlerScheduledExecutorService(Handler(Looper.myLooper() ?: throw IllegalStateException("Current thread has no looper!")))
          threadLocalInstance.set(executor)
          executor
        }
      }

    private val threadLocalInstance = object : ThreadLocal<ScheduledExecutorService>() {
      public override fun initialValue(): ScheduledExecutorService? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
          return CameraExecutors.mainThreadExecutor()
        } else if (Looper.myLooper() != null) {
          val handler = Handler(Looper.myLooper())
          return HandlerScheduledExecutorService(handler)
        }
        return null
      }
    }
  }

  override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> {
    return schedule(Callable<Void> {
      command.run()
      null
    }, delay, unit)
  }

  override fun <V> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> {
    val runAtMillis = SystemClock.uptimeMillis() + TimeUnit.MILLISECONDS.convert(delay, unit)
    val future = HandlerScheduledFuture(handler, runAtMillis, callable)
    return if (handler.postAtTime(future, runAtMillis)) {
      future
    } else Futures.immediateFailedScheduledFuture(createPostFailedException())
  }

  override fun scheduleAtFixedRate(
      command: Runnable,
      initialDelay: Long,
      period: Long,
      unit: TimeUnit
  ): ScheduledFuture<*> {
    throw UnsupportedOperationException(
        HandlerScheduledExecutorService::class.java.simpleName + " does not yet support fixed-rate scheduling.")
  }

  override fun scheduleWithFixedDelay(
      command: Runnable,
      initialDelay: Long,
      delay: Long,
      unit: TimeUnit
  ): ScheduledFuture<*> {
    throw UnsupportedOperationException(
        HandlerScheduledExecutorService::class.java.simpleName + " does not yet support fixed-delay scheduling.")
  }

  override fun shutdown() {
    throw UnsupportedOperationException(
        HandlerScheduledExecutorService::class.java.simpleName + " cannot be shut down. Use Looper.quitSafely().")
  }

  override fun shutdownNow(): List<Runnable> {
    throw UnsupportedOperationException(
        HandlerScheduledExecutorService::class.java.simpleName + " cannot be shut down. Use Looper.quitSafely().")
  }

  override fun isShutdown(): Boolean {
    return false
  }

  override fun isTerminated(): Boolean {
    return false
  }

  override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean {
    throw UnsupportedOperationException(
        HandlerScheduledExecutorService::class.java.simpleName + " cannot be shut down. Use Looper.quitSafely().")
  }

  override fun execute(command: Runnable) {
    if (!handler.post(command)) {
      throw createPostFailedException()
    }
  }

  private fun createPostFailedException(): RejectedExecutionException {
    return RejectedExecutionException("$handler is shutting down")
  }

  private class HandlerScheduledFuture<V> internal constructor(
      handler: Handler,
      private val runAtMillis: Long,
      private val task: Callable<V>
  ) : RunnableScheduledFuture<V> {

    internal val completer: AtomicReference<CallbackToFutureAdapter.Completer<V>> = AtomicReference(null)
    private val delegate: ListenableFuture<V> = CallbackToFutureAdapter.getFuture(
        object : CallbackToFutureAdapter.Resolver<V> {

          @Throws(RejectedExecutionException::class)
          override fun attachCompleter(completer: CallbackToFutureAdapter.Completer<V>): Any {
            completer.addCancellationListener(Runnable {
              // Remove the completer if we're cancelled so the task won't
              // run.
              if (this@HandlerScheduledFuture.completer.getAndSet(null) != null) {
                handler.removeCallbacks(this@HandlerScheduledFuture)
              }
            }, CameraExecutors.directExecutor())

            this@HandlerScheduledFuture.completer.set(completer)
            return "HandlerScheduledFuture-$task"
          }
        })

    override fun isPeriodic(): Boolean {
      return false
    }

    override fun getDelay(unit: TimeUnit): Long {
      return unit.convert(runAtMillis - System.currentTimeMillis(),
          TimeUnit.MILLISECONDS)
    }

    override fun compareTo(other: Delayed): Int {
      return getDelay(TimeUnit.MILLISECONDS).compareTo(other.getDelay(TimeUnit.MILLISECONDS))
    }

    override fun run() {
      // If completer is null, it has already run or is cancelled.
      completer.getAndSet(null)?.apply {
        try {
          set(task.call())
        } catch (e: Exception) {
          setException(e)
        }
      }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      return delegate.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
      return delegate.isCancelled
    }

    override fun isDone(): Boolean {
      return delegate.isDone
    }

    @Throws(
        ExecutionException::class,
        InterruptedException::class
    )
    override fun get(): V {
      return delegate.get()
    }

    @Throws(
        ExecutionException::class,
        InterruptedException::class,
        TimeoutException::class
    )
    override fun get(timeout: Long, unit: TimeUnit): V {
      return delegate.get(timeout, unit)
    }
  }

}