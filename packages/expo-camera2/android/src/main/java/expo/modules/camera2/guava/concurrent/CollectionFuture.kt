package expo.modules.camera2.guava.concurrent


import java.util.ArrayList

/**
 * Aggregate future that collects (stores) results of each future.
 *
 *
 * Copied and adapted from Guava.
 */
internal abstract class CollectionFuture<V, C> : AggregateFuture<V, C>() {

  internal abstract inner class CollectionFutureRunningState(
      futures: Collection<ListenableFuture<out V>>,
      allMustSucceed: Boolean) : RunningState(futures, allMustSucceed, true) {
    private var mValues: MutableList<Optional<V>>? = null

    init {

      this.mValues = if (futures.isEmpty())
        ArrayList<Optional<V>>()
      else
        ArrayList<Optional<V>>(futures.size)

      // Populate the results list with null initially.
      for (i in futures.indices) {
        mValues!!.add(null)
      }
    }

    fun collectOneValue(allMustSucceed: Boolean, index: Int, @Nullable returnValue: V) {
      val localValues = mValues

      if (localValues != null) {
        localValues[index] = Optional.fromNullable(returnValue)
      } else {
        // Some other future failed or has been cancelled, causing this one to also be
        // cancelled or have an exception set. This should only happen if allMustSucceed
        // is true or if the output itself has been cancelled.
        Preconditions.checkState(
            allMustSucceed || isCancelled(),
            "Future was done before all dependencies completed")
      }
    }

    fun handleAllCompleted() {
      val localValues = mValues
      if (localValues != null) {
        set(combine(localValues))
      } else {
        Preconditions.checkState(isDone())
      }
    }

    fun releaseResourcesAfterFailure() {
      super.releaseResourcesAfterFailure()
      this.mValues = null
    }

    internal abstract fun combine(values: List<Optional<V>>): C
  }

  /** Used for [Futures.successfulAsList].  */
  internal class ListFuture<V>(
      futures: Collection<ListenableFuture<out V>>,
      allMustSucceed: Boolean) : CollectionFuture<V, List<V>>() {
    init {
      init(ListFutureRunningState(futures, allMustSucceed))
    }

    private inner class ListFutureRunningState internal constructor(
        futures: Collection<ListenableFuture<out V>>,
        allMustSucceed: Boolean) : CollectionFutureRunningState(futures, allMustSucceed) {

      override fun combine(values: List<Optional<V>>): List<V> {
        val result = ArrayList<V>(values.size)
        for (element in values) {
          result.add(if (element != null) element!!.orNull() else null)
        }
        return result
      }
    }
  }
}