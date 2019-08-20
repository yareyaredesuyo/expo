package expo.modules.camera2.guava.executors

import java.util.ArrayDeque
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Executor ensuring that all Runnables submitted are executed in order, using the provided
 * Executor, and sequentially such that no two will ever be running at the same time.
 *
 * Tasks submitted to [.execute] are executed in FIFO order.
 *
 * The execution of tasks is done by one thread as long as there are tasks left in the queue.
 * When a task is [interrupted][Thread.interrupt], execution of subsequent tasks
 * continues. See [QueueWorker.workOnQueue] for details.
 *
 * `RuntimeException`s thrown by tasks are simply logged and the executor keeps trucking.
 * If an `Error` is thrown, the error will propagate and execution will stop until it is
 * restarted by a call to [.execute].
 *
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/SequentialExecutor.java)
 */
internal class SequentialExecutor(private val executor: Executor) : Executor {
  private val queue = ArrayDeque<Runnable>()

  private var workerRunningState = WorkerRunningState.IDLE

  /**
   * This counter prevents an ABA issue where a thread may successfully schedule the worker, the
   * worker runs and exhausts the queue, another thread enqueues a task and fails to schedule the
   * worker, and then the first thread's call to delegate.execute() returns. Without this counter,
   * it would observe the QUEUING state and set it to QUEUED, and the worker would never be
   * scheduled again for future submissions.
   */
  private var workerRunCount: Long = 0

  private val worker = QueueWorker()

  internal enum class WorkerRunningState {
    /** Runnable is not running and not queued for execution  */
    IDLE,
    /** Runnable is not running, but is being queued for execution  */
    QUEUING,
    /** runnable has been submitted but has not yet begun execution  */
    QUEUED,
    RUNNING
  }

  /**
   * Adds a task to the queue and makes sure a worker thread is running.
   *
   *
   * If this method throws, e.g. a `RejectedExecutionException` from the delegate executor,
   * execution of tasks will stop until a call to this method or to [.resume] is made.
   */
  override fun execute(task: Runnable) {
    checkNotNull(task)
    val submittedTask: Runnable
    val oldRunCount: Long
    synchronized(queue) {
      // If the worker is already running (or execute() on the delegate returned successfully, and
      // the worker has yet to start) then we don't need to start the worker.
      if (workerRunningState == WorkerRunningState.RUNNING || workerRunningState == WorkerRunningState.QUEUED) {
        queue.add(task)
        return
      }

      oldRunCount = workerRunCount

      // If the worker is not yet running, the delegate Executor might reject our attempt to start
      // it. To preserve FIFO order and failure atomicity of rejected execution when the same
      // Runnable is executed more than once, allocate a wrapper that we know is safe to remove by
      // object identity.
      // A data structure that returned a removal handle from add() would allow eliminating this
      // allocation.
      submittedTask = Runnable { task.run() }
      queue.add(submittedTask)
      workerRunningState = WorkerRunningState.QUEUING
    }

    try {
      executor.execute(worker)
    } catch (t: RuntimeException) {
      synchronized(queue) {
        val removed = (workerRunningState == WorkerRunningState.IDLE || workerRunningState == WorkerRunningState.QUEUING) && queue.removeLastOccurrence(submittedTask)
        // If the delegate is directExecutor(), the submitted runnable could have thrown a REE. But
        // that's handled by the log check that catches RuntimeExceptions in the queue worker.
        if (t !is RejectedExecutionException || removed) {
          throw t
        }
      }
      return
    } catch (t: Error) {
      synchronized(queue) {
        val removed = (workerRunningState == WorkerRunningState.IDLE || workerRunningState == WorkerRunningState.QUEUING) && queue.removeLastOccurrence(submittedTask)
        if (t !is RejectedExecutionException || removed) {
          throw t
        }
      }
      return
    }

    /*
     * This is an unsynchronized read! After the read, the function returns immediately or acquires
     * the lock to check again. Since an IDLE state was observed inside the preceding synchronized
     * block, and reference field assignment is atomic, this may save reacquiring the lock when
     * another thread or the worker task has cleared the count and set the state.
     *
     * <p>When {@link #executor} is a directExecutor(), the value written to
     * {@code workerRunningState} will be available synchronously, and behaviour will be
     * deterministic.
     */
    val alreadyMarkedQueued = workerRunningState != WorkerRunningState.QUEUING
    if (alreadyMarkedQueued) {
      return
    }
    synchronized(queue) {
      if (workerRunCount == oldRunCount && workerRunningState == WorkerRunningState.QUEUING) {
        workerRunningState = WorkerRunningState.QUEUED
      }
    }
  }

  /** Worker that runs tasks from [.queue] until it is empty.  */
  private inner class QueueWorker : Runnable {
    override fun run() {
      try {
        workOnQueue()
      } catch (e: Error) {
        synchronized(queue) {
          workerRunningState = WorkerRunningState.IDLE
        }
        throw e
        // The execution of a task has ended abnormally.
        // We could have tasks left in the queue, so should perhaps try to restart a worker,
        // but then the Error will get delayed if we are using a direct (same thread) executor.
      }

    }

    /**
     * Continues executing tasks from [.queue] until it is empty.
     *
     *
     * The thread's interrupt bit is cleared before execution of each task.
     *
     *
     * If the Thread in use is interrupted before or during execution of the tasks in [ ][.queue], the Executor will complete its tasks, and then restore the interruption. This means
     * that once the Thread returns to the Executor that this Executor composes, the interruption
     * will still be present. If the composed Executor is an ExecutorService, it can respond to
     * shutdown() by returning tasks queued on that Thread after [.worker] drains the queue.
     */
    private fun workOnQueue() {
      var interruptedDuringTask = false
      var hasSetRunning = false
      try {
        while (true) {
          val task: Runnable?
          synchronized(queue) {
            // Choose whether this thread will run or not after acquiring the lock on the first
            // iteration
            if (!hasSetRunning) {
              if (workerRunningState == WorkerRunningState.RUNNING) {
                // Don't want to have two workers pulling from the queue.
                return
              } else {
                // Increment the run counter to avoid the ABA problem of a submitter marking the
                // thread as QUEUED after it already ran and exhausted the queue before returning
                // from execute().
                workerRunCount++
                workerRunningState = WorkerRunningState.RUNNING
                hasSetRunning = true
              }
            }
            task = queue.poll()
            if (task == null) {
              workerRunningState = WorkerRunningState.IDLE
              return
            }
          }
          // Remove the interrupt bit before each task. The interrupt is for the "current task" when
          // it is sent, so subsequent tasks in the queue should not be caused to be interrupted
          // by a previous one in the queue being interrupted.
          interruptedDuringTask = interruptedDuringTask or Thread.interrupted()
          try {
            task!!.run()
          } catch (e: RuntimeException) {
            log.log(Level.SEVERE, "Exception while executing runnable " + task!!, e)
          }

        }
      } finally {
        // Ensure that if the thread was interrupted at all while processing the task queue, it
        // is returned to the delegate Executor interrupted so that it may handle the
        // interruption if it likes.
        if (interruptedDuringTask) {
          Thread.currentThread().interrupt()
        }
      }
    }
  }

  companion object {
    private val log = Logger.getLogger(SequentialExecutor::class.java.name)
  }
}