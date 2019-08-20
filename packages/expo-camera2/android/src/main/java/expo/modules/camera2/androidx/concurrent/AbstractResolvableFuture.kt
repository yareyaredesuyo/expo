package expo.modules.camera2.androidx.concurrent

import expo.modules.camera2.guava.concurrent.ListenableFuture
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater

import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater
import java.util.concurrent.locks.LockSupport
import java.util.logging.Level
import java.util.logging.Logger

/**
 * An AndroidX version of Guava's `AbstractFuture`.
 *
 *
 * An abstract implementation of [ListenableFuture], intended for advanced users only. A more
 * common ways to create a `ListenableFuture` is to instantiate [ResolvableFuture].
 *
 * This class implements all methods in `ListenableFuture`. Subclasses should provide a way
 * to set the result of the computation through the protected methods [.set], [ ][.setFuture] and [.setException]. Subclasses may also override
 * [.afterDone], which will be invoked automatically when the future completes. Subclasses
 * should rarely override other methods.
 *
 * Copied and adapted from [androidx.concurrent:concurrent-futures]
 */
// TODO(b/119308748): Implement InternalFutureFailureAccess
abstract class AbstractResolvableFuture<V> protected constructor() : ListenableFuture<V> {

  /**
   * This field encodes the current state of the future.
   *
   * The valid values are:
   *
   *  * `null` initial state, nothing has happened.
   *  * [Cancellation] terminal state, `cancel` was called.
   *  * [Failure] terminal state, `setException` was called.
   *  * [SetFuture] intermediate state, `setFuture` was called.
   *  * [.NULL] terminal state, `set(null)` was called.
   *  * Any other non-null value, terminal state, `set` was called with a non-null
   * argument.
   *
   */
  @Volatile internal var value: Any? = null

  /** All listeners.  */
  @Volatile private var listeners: Listener? = null

  /** All waiting threads.  */
  @Volatile private var waiters: Waiter? = null

  /** Waiter links form a Treiber stack, in the [waiters] field.  */
  private class Waiter {
    @Volatile internal var thread: Thread? = null
    @Volatile internal var next: Waiter? = null

    /**
     * Constructor for the TOMBSTONE, avoids use of ATOMIC_HELPER in case this class is loaded
     * before the ATOMIC_HELPER. Apparently this is possible on some android platforms.
     */
    internal constructor(unused: Boolean)

    internal constructor() {
      // avoid volatile write, write is made visible by subsequent CAS on waiters field
      ATOMIC_HELPER.putThread(this, Thread.currentThread())
    }

    // non-volatile write to the next field. Should be made visible by subsequent CAS on waiters
    // field.
    internal fun setNextWaiter(next: Waiter?) {
      ATOMIC_HELPER.putNext(this, next)
    }

    internal fun unpark() {
      // This is racy with removeWaiter. The consequence of the race is that we may
      // spuriously call unpark even though the thread has already removed itself
      // from the list. But even if we did use a CAS, that race would still exist
      // (it would just be ever so slightly smaller).
      val w = thread
      if (w != null) {
        thread = null
        LockSupport.unpark(w)
      }
    }

    companion object {
      internal val TOMBSTONE = Waiter(false /* ignored param */)
    }
  }

  /**
   * Marks the given node as 'deleted' (null waiter) and then scans the list to unlink all deleted
   * nodes. This is an O(n) operation in the common case (and O(n^2) in the worst), but we are
   * saved by two things.
   *
   *
   *  * This is only called when a waiting thread times out or is interrupted. Both of which
   * should be rare.
   *  * The waiters list should be very short.
   *
   */
  private fun removeWaiter(node: Waiter) {
    node.thread = null // mark as 'deleted'
    restart@ while (true) {
      var pred: Waiter? = null
      var curr = waiters
      if (curr == Waiter.TOMBSTONE) {
        return  // give up if someone is calling complete
      }
      var succ: Waiter?
      while (curr != null) {
        succ = curr.next
        if (curr.thread != null) { // we aren't unlinking this node, update pred.
          pred = curr
        } else if (pred != null) { // We are unlinking this node and it has a predecessor.
          pred.next = succ
          if (pred.thread == null) {
            // We raced with another node that unlinked pred. Restart.
            continue@restart
          }
        } else if (!ATOMIC_HELPER.casWaiters(this, curr, succ)) {
          // We are unlinking head
          continue@restart // We raced with an add or complete
        }
        curr = succ
      }
      break
    }
  }

  /** Listeners also form a stack through the [.listeners] field.  */
  private class Listener internal constructor(internal val task: Runnable, internal val executor: Executor) {

    // writes to next are made visible by subsequent CAS's on the listeners field
    internal var next: Listener? = null

    companion object {
      internal val TOMBSTONE = Listener(null!!, null!!)
    }
  }

  /** A special value to represent failure, when [.setException] is called successfully.  */
  private class Failure internal constructor(exception: Throwable?) {
    internal val exception: Throwable = checkNotNull(exception)

    companion object {
      internal val FALLBACK_INSTANCE = Failure(
          object : Throwable("Failure occurred while trying to finish a future.") {
            @Synchronized
            override fun fillInStackTrace(): Throwable {
              return this // no stack trace
            }
          })
    }
  }

  /** A special value to represent cancellation and the 'wasInterrupted' bit.  */
  private class Cancellation internal constructor(
      internal val wasInterrupted: Boolean,
      internal val cause: Throwable?
  ) {
    companion object {
      // constants to use when GENERATE_CANCELLATION_CAUSES = false
      internal val CAUSELESS_INTERRUPTED: Cancellation?
      internal val CAUSELESS_CANCELLED: Cancellation?

      init {
        if (GENERATE_CANCELLATION_CAUSES) {
          CAUSELESS_CANCELLED = null
          CAUSELESS_INTERRUPTED = null
        } else {
          CAUSELESS_CANCELLED = Cancellation(false, null)
          CAUSELESS_INTERRUPTED = Cancellation(true, null)
        }
      }
    }
  }

  /** A special value that encodes the 'setFuture' state.  */
  private class SetFuture<V> internal constructor(internal val owner: AbstractResolvableFuture<V>, internal val future: ListenableFuture<out V>) : Runnable {

    override fun run() {
      if (owner.value !== this) {
        // nothing to do, we must have been cancelled, don't bother inspecting the future.
        return
      }
      val valueToSet = getFutureValue(future)
      if (ATOMIC_HELPER.casValue(owner, this, valueToSet)) {
        complete(owner)
      }
    }
  }

  // Gets and Timed Gets
  //
  // * Be responsive to interruption
  // * Don't create Waiter nodes if you aren't going to park, this helps reduce contention on the
  //   waiters field.
  // * Future completion is defined by when #value becomes non-null/non SetFuture
  // * Future completion can be observed if the waiters field contains a TOMBSTONE

  // Timed Get
  // There are a few design constraints to consider
  // * We want to be responsive to small timeouts, unpark() has non trivial latency overheads (I
  //   have observed 12 micros on 64 bit linux systems to wake up a parked thread). So if the
  //   timeout is small we shouldn't park(). This needs to be traded off with the cpu overhead of
  //   spinning, so we use SPIN_THRESHOLD_NANOS which is what AbstractQueuedSynchronizer uses for
  //   similar purposes.
  // * We want to behave reasonably for timeouts of 0
  // * We are more responsive to completion than timeouts. This is because parkNanos depends on
  //   system scheduling and as such we could either miss our deadline, or unpark() could be
  //   delayed so that it looks like we timed out even though we didn't. For comparison FutureTask
  //   respects completion preferably and AQS is non-deterministic (depends on where in the queue
  //   the waiter is). If we wanted to be strict about it, we could store the unpark() time in
  //   the Waiter node and we could use that to make a decision about whether or not we timed out
  //   prior to being unparked.

  /**
   * {@inheritDoc}
   *
   *
   * The default [AbstractResolvableFuture] implementation throws
   * `InterruptedException` if the current thread is interrupted during the call, even if
   * the value is already available.
   *
   * @throws CancellationException {@inheritDoc}
   */
  @Throws(InterruptedException::class, TimeoutException::class, ExecutionException::class, CancellationException::class)
  override operator fun get(timeout: Long, unit: TimeUnit): V? {
    // NOTE: if timeout < 0, remainingNanos will be < 0 and we will fall into the while(true)
    // loop at the bottom and throw a timeoutexception.
    // we rely on the implicit null check on unit.
    val timeoutNanos = unit.toNanos(timeout)
    var remainingNanos = timeoutNanos
    if (Thread.interrupted()) {
      throw InterruptedException()
    }
    var localValue = value
    if ((localValue != null) and (localValue !is SetFuture<*>)) {
      return getDoneValue(localValue)
    }
    // we delay calling nanoTime until we know we will need to either park or spin
    val endNanos = if (remainingNanos > 0) System.nanoTime() + remainingNanos else 0
    if (remainingNanos >= SPIN_THRESHOLD_NANOS) {
      var skipReturnFlag = false
      var oldHead = waiters
      if (oldHead != Waiter.TOMBSTONE) {
        val node = Waiter()
        long_wait_loop@ do {
          node.setNextWaiter(oldHead)
          if (ATOMIC_HELPER.casWaiters(this, oldHead, node)) {
            while (true) {
              LockSupport.parkNanos(this, remainingNanos)
              // Check interruption first, if we woke up due to interruption we
              // need to honor that.
              if (Thread.interrupted()) {
                removeWaiter(node)
                throw InterruptedException()
              }

              // Otherwise re-read and check doneness. If we loop then it must have
              // been a spurious wakeup
              localValue = value
              if ((localValue != null) and (localValue !is SetFuture<*>)) {
                return getDoneValue(localValue)
              }

              // timed out?
              remainingNanos = endNanos - System.nanoTime()
              if (remainingNanos < SPIN_THRESHOLD_NANOS) {
                // Remove the waiter, one way or another we are done parking this
                // thread.
                removeWaiter(node)
                skipReturnFlag = true
                break@long_wait_loop // jump down to the busy wait loop
              }
            }
          }
          oldHead = waiters // re-read and loop.
        } while (oldHead != Waiter.TOMBSTONE)
      }

      if (!skipReturnFlag) {
        // re-read value, if we get here then we must have observed a TOMBSTONE while trying
        // to add a waiter.
        return getDoneValue(value)
      }
    }
    // If we get here then we have remainingNanos < SPIN_THRESHOLD_NANOS and there is no node
    // on the waiters list
    while (remainingNanos > 0) {
      localValue = value
      if ((localValue != null) and (localValue !is SetFuture<*>)) {
        return getDoneValue(localValue)
      }
      if (Thread.interrupted()) {
        throw InterruptedException()
      }
      remainingNanos = endNanos - System.nanoTime()
    }

    val futureToString = toString()
    val unitString = unit.toString().toLowerCase(Locale.ROOT)
    var message = "Waited " + timeout + " " + unit.toString().toLowerCase(Locale.ROOT)
    // Only report scheduling delay if larger than our spin threshold - otherwise it's just
    // noise
    if (remainingNanos + SPIN_THRESHOLD_NANOS < 0) {
      // We over-waited for our timeout.
      message += " (plus "
      val overWaitNanos = -remainingNanos
      val overWaitUnits = unit.convert(overWaitNanos, TimeUnit.NANOSECONDS)
      val overWaitLeftoverNanos = overWaitNanos - unit.toNanos(overWaitUnits)
      val shouldShowExtraNanos = overWaitUnits == 0L || overWaitLeftoverNanos > SPIN_THRESHOLD_NANOS
      if (overWaitUnits > 0) {
        message += (overWaitUnits).toString() + " " + unitString
        if (shouldShowExtraNanos) {
          message += ","
        }
        message += " "
      }
      if (shouldShowExtraNanos) {
        message += (overWaitLeftoverNanos).toString() + " nanoseconds "
      }

      message += "delay)"
    }
    // It's confusing to see a completed future in a timeout message; if isDone() returns false,
    // then we know it must have given a pending toString value earlier. If not, then the future
    // completed after the timeout expired, and the message might be success.
    if (isDone) {
      throw TimeoutException("$message but future completed as timeout expired")
    }
    throw TimeoutException("$message for $futureToString")
  }

  /**
   * {@inheritDoc}
   *
   *
   * The default [AbstractResolvableFuture] implementation throws
   * `InterruptedException` if the current thread is interrupted during the call, even if
   * the value is already available.
   *
   * @throws CancellationException {@inheritDoc}
   */
  @Throws(InterruptedException::class, ExecutionException::class, CancellationException::class)
  override fun get(): V? {
    if (Thread.interrupted()) {
      throw InterruptedException()
    }
    var localValue = value
    if ((localValue != null) and (localValue !is SetFuture<*>)) {
      return getDoneValue(localValue)
    }
    var oldHead = waiters
    if (oldHead != Waiter.TOMBSTONE) {
      val node = Waiter()
      do {
        node.setNextWaiter(oldHead)
        if (ATOMIC_HELPER.casWaiters(this, oldHead, node)) {
          // we are on the stack, now wait for completion.
          while (true) {
            LockSupport.park(this)
            // Check interruption first, if we woke up due to interruption we need to
            // honor that.
            if (Thread.interrupted()) {
              removeWaiter(node)
              throw InterruptedException()
            }
            // Otherwise re-read and check doneness. If we loop then it must have
            // been a spurious
            // wakeup
            localValue = value
            if ((localValue != null) and (localValue !is SetFuture<*>)) {
              return getDoneValue(localValue)
            }
          }
        }
        oldHead = waiters // re-read and loop.
      } while (oldHead != Waiter.TOMBSTONE)
    }
    // re-read value, if we get here then we must have observed a TOMBSTONE while trying to
    // add a waiter.
    return getDoneValue(value)
  }

  /** Unboxes `obj`. Assumes that obj is not `null` or a [SetFuture].  */
  @Throws(ExecutionException::class)
  private fun getDoneValue(obj: Any?): V? {
    // While this seems like it might be too branch-y, simple benchmarking proves it to be
    // unmeasurable (comparing done AbstractFutures with immediateFuture)
    return when {
      obj is Cancellation -> throw cancellationExceptionWithCause("Task was cancelled.", obj.cause)
      obj is Failure -> throw ExecutionException(obj.exception)
      obj === NULL -> null
      else -> {
        @Suppress("UNCHECKED_CAST")
        obj as V?
      }
    }
  }

  override fun isDone(): Boolean {
    val localValue = value
    return (localValue != null) and (localValue !is SetFuture<*>)
  }

  override fun isCancelled(): Boolean {
    val localValue = value
    return localValue is Cancellation
  }

  /**
   * {@inheritDoc}
   *
   *
   * If a cancellation attempt succeeds on a `Future` that had previously been
   * [set asynchronously][setFuture], then the cancellation will also be propagated
   * to the delegate `Future` that was supplied in the `setFuture` call.
   *
   *
   * Rather than override this method to perform additional cancellation work or cleanup,
   * subclasses should override [.afterDone], consulting [.isCancelled] and [ ][.wasInterrupted] as necessary. This ensures that the work is done even if the future is
   * cancelled without a call to `cancel`, such as by calling `setFuture(cancelledFuture)`.
   */
  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    var localValue = value
    var rValue = false
    if ((localValue == null) or (localValue is SetFuture<*>)) {
      // Try to delay allocating the exception. At this point we may still lose the CAS,
      // but it is certainly less likely.
      val valueToSet = when {
        GENERATE_CANCELLATION_CAUSES -> Cancellation(
            mayInterruptIfRunning,
            CancellationException("Future.cancel() was called."))
        mayInterruptIfRunning -> Cancellation.CAUSELESS_INTERRUPTED
        else -> Cancellation.CAUSELESS_CANCELLED
      }
      var abstractFuture: AbstractResolvableFuture<*> = this
      while (true) {
        if (ATOMIC_HELPER.casValue(abstractFuture, localValue, valueToSet)) {
          rValue = true
          // We call interuptTask before calling complete(), which is consistent with
          // FutureTask
          if (mayInterruptIfRunning) {
            abstractFuture.interruptTask()
          }
          complete(abstractFuture)
          if (localValue is SetFuture<*>) {
            // propagate cancellation to the future set in setfuture, this is racy,
            // and we don't
            // care if we are successful or not.
            val futureToPropagateTo = localValue.future
            if (futureToPropagateTo is AbstractResolvableFuture<*>) {
              // If the future is a trusted then we specifically avoid
              // calling cancel() this has 2 benefits
              // 1. for long chains of futures strung together with setFuture we
              // consume less stack
              // 2. we avoid allocating Cancellation objects at every level of the
              // cancellation chain
              // We can only do this for TrustedFuture, because TrustedFuture
              // .cancel is final and does nothing but delegate to this method.
              localValue = futureToPropagateTo.value
              if ((localValue == null) or (localValue is SetFuture<*>)) {
                abstractFuture = futureToPropagateTo
                continue // loop back up and try to complete the new future
              }
            } else {
              // not a TrustedFuture, call cancel directly.
              futureToPropagateTo.cancel(mayInterruptIfRunning)
            }
          }
          break
        }
        // obj changed, reread
        localValue = abstractFuture.value
        if (localValue !is SetFuture<*>) {
          // obj cannot be null at this point, because value can only change from null
          // to non-null. So if value changed (and it did since we lost the CAS),
          // then it cannot be null and since it isn't a SetFuture, then the future must
          // be done and we should exit the loop
          break
        }
      }
    }
    return rValue
  }

  /**
   * Subclasses can override this method to implement interruption of the future's computation.
   * The method is invoked automatically by a successful call to
   * [cancel(true)][.cancel].
   *
   *
   * The default implementation does nothing.
   *
   *
   * This method is likely to be deprecated. Prefer to override [.afterDone], checking
   * [.wasInterrupted] to decide whether to interrupt your task.
   *
   * @since 10.0
   */
  protected fun interruptTask() {}

  /**
   * Returns true if this future was cancelled with `mayInterruptIfRunning` set to `true`.
   *
   * @since 14.0
   */
  protected fun wasInterrupted(): Boolean {
    val localValue = value
    return (localValue is Cancellation) && localValue.wasInterrupted
  }

  /**
   * {@inheritDoc}
   *
   * @since 10.0
   */
  override fun addListener(listener: Runnable, executor: Executor) {
    checkNotNull(listener)
    checkNotNull(executor)
    var oldHead = listeners
    if (oldHead != Listener.TOMBSTONE) {
      val newNode = Listener(listener, executor)
      do {
        newNode.next = oldHead
        if (ATOMIC_HELPER.casListeners(this, oldHead, newNode)) {
          return
        }
        oldHead = listeners // re-read
      } while (oldHead != Listener.TOMBSTONE)
    }
    // If we get here then the Listener TOMBSTONE was set, which means the future is done, call
    // the listener.
    executeListener(listener, executor)
  }

  /**
   * Sets the result of this `Future` unless this `Future` has already been
   * cancelled or set (including [set asynchronously][.setFuture]).
   * When a call to this method returns, the `Future` is guaranteed to be
   * [done][.isDone] **only if** the call was accepted (in which case it returns
   * `true`). If it returns `false`, the `Future` may have previously been set
   * asynchronously, in which case its result may not be known yet. That result,
   * though not yet known, cannot be overridden by a call to a `set*` method,
   * only by a call to [.cancel].
   *
   * @param value the value to be used as the result
   * @return true if the attempt was accepted, completing the `Future`
   */
  open fun set(value: V?): Boolean {
    val valueToSet = value ?: NULL
    if (ATOMIC_HELPER.casValue(this, null, valueToSet)) {
      complete(this)
      return true
    }
    return false
  }

  /**
   * Sets the failed result of this `Future` unless this `Future` has already been
   * cancelled or set (including [set asynchronously][.setFuture]). When a call to this
   * method returns, the `Future` is guaranteed to be [done][.isDone] **only
   * if**
   * the call was accepted (in which case it returns `true`). If it returns `false`, the
   * `Future` may have previously been set asynchronously, in which case its result may
   * not be
   * known yet. That result, though not yet known, cannot be overridden by a call to a `set*`
   * method, only by a call to [.cancel].
   *
   * @param throwable the exception to be used as the failed result
   * @return true if the attempt was accepted, completing the `Future`
   */
  open fun setException(throwable: Throwable): Boolean {
    val valueToSet = Failure(checkNotNull(throwable))
    if (ATOMIC_HELPER.casValue(this, null, valueToSet)) {
      complete(this)
      return true
    }
    return false
  }

  /**
   * Sets the result of this `Future` to match the supplied input `Future` once the
   * supplied `Future` is done, unless this `Future` has already been cancelled or set
   * (including "set asynchronously," defined below).
   *
   *
   * If the supplied future is [done][.isDone] when this method is called and the
   * call is accepted, then this future is guaranteed to have been completed with the supplied
   * future by the time this method returns. If the supplied future is not done and the call
   * is accepted, then the future will be *set asynchronously*. Note that such a result,
   * though not yet known, cannot be overridden by a call to a `set*` method,
   * only by a call to [.cancel].
   *
   *
   * If the call `setFuture(delegate)` is accepted and this `Future` is later
   * cancelled, cancellation will be propagated to `delegate`. Additionally, any call to
   * `setFuture` after any cancellation will propagate cancellation to the supplied `Future`.
   *
   *
   * Note that, even if the supplied future is cancelled and it causes this future to complete,
   * it will never trigger interruption behavior. In particular, it will not cause this future to
   * invoke the [.interruptTask] method, and the [.wasInterrupted] method will not
   * return `true`.
   *
   * @param future the future to delegate to
   * @return true if the attempt was accepted, indicating that the `Future` was not
   * previously cancelled or set.
   * @since 19.0
   */
  protected open fun setFuture(future: ListenableFuture<out V>): Boolean {
    checkNotNull(future)
    var localValue = value
    if (localValue == null) {
      if (future.isDone) {
        val value = getFutureValue(future)
        if (ATOMIC_HELPER.casValue(this, null, value)) {
          complete(this)
          return true
        }
        return false
      }
      val valueToSet = SetFuture(this, future)
      if (ATOMIC_HELPER.casValue(this, null, valueToSet)) {
        // the listener is responsible for calling completeWithFuture, directExecutor is
        // appropriate since all we are doing is unpacking a completed future
        // which should be fast.
        try {
          future.addListener(valueToSet, DirectExecutor.INSTANCE)
        } catch (t: Throwable) {
          // addListener has thrown an exception! SetFuture.run can't throw any
          // exceptions so this must have been caused by addListener itself.
          // The most likely explanation is a misconfigured mock.
          // Try to switch to Failure.
          val failure: Failure = try {
            Failure(t)
          } catch (oomMostLikely: Throwable) {
            Failure.FALLBACK_INSTANCE
          }

          // Note: The only way this CAS could fail is if cancel() has raced with us.
          // That is ok.
//          val unused = ATOMIC_HELPER.casValue(this, valueToSet, failure)
          ATOMIC_HELPER.casValue(this, valueToSet, failure)
        }

        return true
      }
      localValue = value // we lost the cas, fall through and maybe cancel
    }
    // The future has already been set to something. If it is cancellation we should cancel the
    // incoming future.
    if (localValue is Cancellation) {
      // we don't care if it fails, this is best-effort.
      future.cancel(localValue.wasInterrupted)
    }
    return false
  }

  /**
   * Callback method that is called exactly once after the future is completed.
   *
   *
   * If [.interruptTask] is also run during completion, [.afterDone] runs after it.
   *
   *
   * The default implementation of this method in `AbstractFuture` does nothing. This is
   * intended for very lightweight cleanup work, for example, timing statistics or clearing
   * fields.
   * If your task does anything heavier consider, just using a listener with an executor.
   *
   * @since 20.0
   */
  protected fun afterDone() {}

  /**
   * If this future has been cancelled (and possibly interrupted), cancels (and possibly
   * interrupts) the given future (if available).
   */
  internal fun maybePropagateCancellationTo(related: Future<*>?) {
    if ((related != null) and isCancelled) {
      related!!.cancel(wasInterrupted())
    }
  }

  /** Releases all threads in the [.waiters] list, and clears the list.  */
  private fun releaseWaiters() {
    var head: Waiter?
    do {
      head = waiters
    } while (!ATOMIC_HELPER.casWaiters(this, head, Waiter.TOMBSTONE))
    var currentWaiter = head
    while (currentWaiter != null) {
      currentWaiter.unpark()
      currentWaiter = currentWaiter.next
    }
  }

  /**
   * Clears the [.listeners] list and prepends its contents to `onto`, least recently
   * added first.
   */
  private fun clearListeners(onto: Listener?): Listener? {
    // We need to
    // 1. atomically swap the listeners with TOMBSTONE, this is because addListener uses that to
    //    to synchronize with us
    // 2. reverse the linked list, because despite our rather clear contract, people depend
    //    on us executing listeners in the order they were added
    // 3. push all the items onto 'onto' and return the new head of the stack
    var head: Listener?
    do {
      head = listeners
    } while (!ATOMIC_HELPER.casListeners(this, head, Listener.TOMBSTONE))
    var reversedList = onto
    while (head != null) {
      head = head.next
      head!!.next = reversedList
      reversedList = head
    }
    return reversedList
  }

  // TODO(clm): move parts into a default method on ListenableFuture?
  override fun toString(): String {
    val builder = StringBuilder().append(super.toString()).append("[status=")
    if (isCancelled) {
      builder.append("CANCELLED")
    } else if (isDone) {
      addDoneString(builder)
    } else {
      val pendingDescription: String?
      pendingDescription = try {
        pendingToString()
      } catch (e: RuntimeException) {
        // Don't call getMessage or toString() on the exception, in case the exception
        // thrown by the subclass is implemented with bugs similar to the subclass.
        "Exception thrown from implementation: " + e.javaClass
      }

      // The future may complete during or before the call to getPendingToString, so we use
      // null as a signal that we should try checking if the future is done again.
      if (pendingDescription != null && pendingDescription.isNotEmpty()) {
        builder.append("PENDING, info=[").append(pendingDescription).append("]")
      } else if (isDone) {
        addDoneString(builder)
      } else {
        builder.append("PENDING")
      }
    }
    return builder.append("]").toString()
  }

  /**
   * Provide a human-readable explanation of why this future has not yet completed.
   *
   * @return null if an explanation cannot be provided because the future is done.
   * @since 23.0
   */
  protected open fun pendingToString(): String? {
    val localValue = value
    if (localValue is SetFuture<*>) {
      return "setFuture=[" + userObjectToString((localValue).future) + "]"
    } else if (this is ScheduledFuture<*>) {
      return ("remaining delay=["
          + (this as ScheduledFuture<*>).getDelay(TimeUnit.MILLISECONDS)
          + " ms]")
    }
    return null
  }

  private fun addDoneString(builder: StringBuilder) {
    try {
      val value = getUninterruptibly(this)
      builder.append("SUCCESS, result=[").append(userObjectToString(value as Any)).append("]")
    } catch (e: ExecutionException) {
      builder.append("FAILURE, cause=[").append(e.cause).append("]")
    } catch (e: CancellationException) {
      builder.append("CANCELLED") // shouldn't be reachable
    } catch (e: RuntimeException) {
      builder.append("UNKNOWN, cause=[").append(e.javaClass).append(" thrown from get()]")
    }

  }

  /** Helper for printing user supplied objects into our toString method.  */
  private fun userObjectToString(o: Any): String {
    // This is some basic recursion detection for when people create cycles via set/setFuture
    // This is however only partial protection though since it only detects self loops.  We
    // could detect arbitrary cycles using a thread local or possibly by catching
    // StackOverflowExceptions but this should be a good enough solution
    // (it is also what jdk collections do in these cases)
    return if (o === this) {
      "this future"
    } else (o).toString()
  }

  private abstract class AtomicHelper {
    /** Non volatile write of the thread to the [Waiter.thread] field.  */
    internal abstract fun putThread(waiter: Waiter, newValue: Thread)

    /** Non volatile write of the waiter to the [Waiter.next] field.  */
    internal abstract fun putNext(waiter: Waiter, newValue: Waiter?)

    /** Performs a CAS operation on the [.waiters] field.  */
    internal abstract fun casWaiters(
        future: AbstractResolvableFuture<*>,
        expect: Waiter?,
        update: Waiter?): Boolean

    /** Performs a CAS operation on the [.listeners] field.  */
    internal abstract fun casListeners(
        future: AbstractResolvableFuture<*>,
        expect: Listener?,
        update: Listener): Boolean

    /** Performs a CAS operation on the [.value] field.  */
    internal abstract fun casValue(future: AbstractResolvableFuture<*>, expect: Any?, update: Any?): Boolean
  }

  /** [AtomicHelper] based on [AtomicReferenceFieldUpdater].  */
  private class SafeAtomicHelper internal constructor(
      internal val waiterThreadUpdater: AtomicReferenceFieldUpdater<Waiter, Thread>,
      internal val waiterNextUpdater: AtomicReferenceFieldUpdater<Waiter, Waiter>,
      internal val waitersUpdater: AtomicReferenceFieldUpdater<AbstractResolvableFuture<*>, Waiter>,
      internal val listenersUpdater: AtomicReferenceFieldUpdater<AbstractResolvableFuture<*>, Listener>,
      internal val valueUpdater: AtomicReferenceFieldUpdater<AbstractResolvableFuture<*>, Any>) : AtomicHelper() {

    override fun putThread(waiter: Waiter, newValue: Thread) {
      waiterThreadUpdater.lazySet(waiter, newValue)
    }

    override fun putNext(waiter: Waiter, newValue: Waiter?) {
      waiterNextUpdater.lazySet(waiter, newValue)
    }

    override fun casWaiters(future: AbstractResolvableFuture<*>, expect: Waiter?, update: Waiter?): Boolean {
      return waitersUpdater.compareAndSet(future, expect, update)
    }

    override fun casListeners(future: AbstractResolvableFuture<*>, expect: Listener?, update: Listener): Boolean {
      return listenersUpdater.compareAndSet(future, expect, update)
    }

    override fun casValue(future: AbstractResolvableFuture<*>, expect: Any?, update: Any?): Boolean {
      return valueUpdater.compareAndSet(future, expect, update)
    }
  }

  /**
   * [AtomicHelper] based on `synchronized` and volatile writes.
   *
   * This is an implementation of last resort for when certain basic VM features are broken
   * (like AtomicReferenceFieldUpdater).
   */
  private class SynchronizedHelper internal constructor() : AtomicHelper() {

    override fun putThread(waiter: Waiter, newValue: Thread) {
      waiter.thread = newValue
    }

    override fun putNext(waiter: Waiter, newValue: Waiter?) {
      waiter.next = newValue
    }

    override fun casWaiters(future: AbstractResolvableFuture<*>, expect: Waiter?, update: Waiter?): Boolean {
      synchronized(future) {
        if (future.waiters == expect) {
          future.waiters = update
          return true
        }
        return false
      }
    }

    override fun casListeners(future: AbstractResolvableFuture<*>, expect: Listener?, update: Listener): Boolean {
      synchronized(future) {
        if (future.listeners == expect) {
          future.listeners = update
          return true
        }
        return false
      }
    }

    override fun casValue(future: AbstractResolvableFuture<*>, expect: Any?, update: Any?): Boolean {
      synchronized(future) {
        if (future.value === expect) {
          future.value = update
          return true
        }
        return false
      }
    }
  }

  companion object {

    // NOTE: Whenever both tests are cheap and functional, it's faster to use &, | instead of &&, ||

    internal// Avoiding synthetic accessor.
    val GENERATE_CANCELLATION_CAUSES = java.lang.Boolean.parseBoolean(
        System.getProperty("guava.concurrent.generate_cancellation_cause", "false"))

    // Logger to log exceptions caught when running listeners.
    private val log = Logger.getLogger(AbstractResolvableFuture::class.java.name)

    // A heuristic for timed gets. If the remaining timeout is less than this, spin instead of
    // blocking. This value is what AbstractQueuedSynchronizer uses.
    private const val SPIN_THRESHOLD_NANOS = 1000L

    private val ATOMIC_HELPER: AtomicHelper

    init {
      lateinit var helper: AtomicHelper
      var thrownAtomicReferenceFieldUpdaterFailure: Throwable? = null

      // The access control checks that ARFU does means the caller class has to be
      // AbstractFuture instead of SafeAtomicHelper, so we annoyingly define these here
      try {
        helper = SafeAtomicHelper(
            newUpdater(Waiter::class.java, Thread::class.java, "thread"),
            newUpdater(Waiter::class.java, Waiter::class.java, "next"),
            newUpdater(AbstractResolvableFuture::class.java, Waiter::class.java, "waiters"),
            newUpdater(
                AbstractResolvableFuture::class.java,
                Listener::class.java,
                "listeners"),
            newUpdater(AbstractResolvableFuture::class.java, Any::class.java, "value"))
      } catch (atomicReferenceFieldUpdaterFailure: Throwable) {
        // Some Android 5.0.x Samsung devices have bugs in JDK reflection APIs that cause
        // getDeclaredField to throw a NoSuchFieldException when the field is definitely
        // there. For these users fallback to a suboptimal implementation,
        // based on synchronized. This will be a definite performance hit to those users.
        thrownAtomicReferenceFieldUpdaterFailure = atomicReferenceFieldUpdaterFailure
        helper = SynchronizedHelper()
      }

      ATOMIC_HELPER = helper

      // Prevent rare disastrous classloading in first call to LockSupport.park.
      // See: https://bugs.openjdk.java.net/browse/JDK-8074773
//      val ensureLoaded = LockSupport::class.java

      // Log after all static init is finished; if an installed logger uses any Futures
      // methods, it shouldn't break in cases where reflection is missing/broken.
      if (thrownAtomicReferenceFieldUpdaterFailure != null) {
        log.log(Level.SEVERE, "SafeAtomicHelper is broken!",
            thrownAtomicReferenceFieldUpdaterFailure)
      }
    }

    /** A special value to represent `null`.  */
    private val NULL = Any()

    /**
     * Returns a value that satisfies the contract of the [.value] field based on the state of
     * given future.
     *
     *
     * This is approximately the inverse of [getDoneValue]
     */
    internal fun getFutureValue(future: ListenableFuture<*>): Any? {
      if (future is AbstractResolvableFuture<*>) {
        // Break encapsulation for TrustedFuture instances since we know that subclasses cannot
        // override .get() (since it is final) and therefore this is equivalent to calling
        // .get() and unpacking the exceptions like we do below (just much faster because it is
        // a single field read instead of a read, several branches and possibly
        // creating exceptions).
        var v = future.value
        if (v is Cancellation) {
          // If the other future was interrupted, clear the interrupted bit while
          // preserving the cause this will make it consistent with how non-trustedfutures
          // work which cannot propagate the wasInterrupted bit
          val c = v as Cancellation?
          if (c!!.wasInterrupted) {
            v = if (c.cause != null)
              Cancellation(/* wasInterrupted= */false, c.cause)
            else
              Cancellation.CAUSELESS_CANCELLED
          }
        }
        return v
      }
      val wasCancelled = future.isCancelled
      // Don't allocate a CancellationException if it's not necessary
      if (!GENERATE_CANCELLATION_CAUSES and wasCancelled) {
        return Cancellation.CAUSELESS_CANCELLED
      }
      // Otherwise calculate the value by calling .get()
      try {
        return getUninterruptibly(future) ?: NULL
      } catch (exception: ExecutionException) {
        return Failure(exception.cause)
      } catch (cancellation: CancellationException) {
        return if (!wasCancelled) {
          Failure(
              IllegalArgumentException(
                  ("get() threw CancellationException, despite reporting isCancelled"
                      + "() == false: "
                      + future),
                  cancellation))
        } else Cancellation(false, cancellation)
      } catch (t: Throwable) {
        return Failure(t)
      }

    }

    /**
     * internal dependency on other /util/concurrent classes.
     */
    @Throws(ExecutionException::class)
    private fun <V> getUninterruptibly(future: Future<V>): V? {
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

    /** Unblocks all threads and runs all listeners.  */
    internal fun complete(future: AbstractResolvableFuture<*>?) {
      var future1 = future
      var next: Listener? = null
      outer@ while (true) {
        future1!!.releaseWaiters()
        // We call this before the listeners in order to avoid needing to manage a separate
        // stack data structure for them.  Also, some implementations rely on this running
        // prior to listeners so that the cleanup work is visible to listeners.
        // afterDone() should be generally fast and only used for cleanup work... but in
        // theory can also be recursive and create StackOverflowErrors
        future1.afterDone()
        // push the current set of listeners onto next
        next = future1.clearListeners(next)
        while (next != null) {
          val curr = next
          next = next.next
          val task = curr.task
          if (task is SetFuture<*>) {
            // We unwind setFuture specifically to avoid StackOverflowErrors in the case
            // of long chains of SetFutures
            // Handling this special case is important because there is no way to pass an
            // executor to setFuture, so a user couldn't break the chain by doing this
            // themselves. It is also potentially common if someone writes a recursive
            // Futures.transformAsync transformer.
            future1 = task.owner
            if (future1.value === task) {
              val valueToSet = getFutureValue(task.future)
              if (ATOMIC_HELPER.casValue(future1, task, valueToSet)) {
                continue@outer
              }
            }
            // other wise the future we were trying to set is already done.
          } else {
            executeListener(task, curr.executor)
          }
        }
        break
      }
    }

    /**
     * Submits the given runnable to the given [Executor] catching and logging all [ ] thrown by the executor.
     */
    private fun executeListener(runnable: Runnable, executor: Executor) {
      try {
        executor.execute(runnable)
      } catch (e: RuntimeException) {
        // Log it and keep going -- bad runnable and/or executor. Don't punish the other
        // runnables if we're given a bad one. We only catch RuntimeException
        // because we want Errors to propagate up.
        log.log(
            Level.SEVERE,
            ("RuntimeException while executing runnable " + runnable + " with executor "
                + executor),
            e)
      }
    }

    private fun cancellationExceptionWithCause(message: String, cause: Throwable?): CancellationException {
      return CancellationException(message).apply {
        initCause(cause)
      }
    }

    internal fun <T> checkNotNull(reference: T?): T {
      return reference ?: throw NullPointerException()
    }
  }
}
