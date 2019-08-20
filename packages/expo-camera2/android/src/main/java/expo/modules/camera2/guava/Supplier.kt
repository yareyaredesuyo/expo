package expo.modules.camera2.guava

/**
 * Compat version of [java.util.function.Supplier]
 *
 * Represents a supplier of results.
 *
 *
 * There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 *
 * This is a [functional interface](package-summary.html)
 * whose functional method is [.get].
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
interface Supplier<T> {

  /**
   * Gets a result.
   *
   * @return a result
   */
  fun get(): T
}
