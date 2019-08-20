package expo.modules.camera2.guava.optionals

import expo.modules.camera2.guava.Supplier
import expo.modules.camera2.guava.Preconditions.checkNotNull

/** Implementation of an [Optional] containing a reference.
 *
 * Copied and adopted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/base/Present.java)
 */
internal class Present<T>(private val reference: T) : Optional<T>() {

  override val isPresent: Boolean
    get() = true

  override fun get(): T {
    return reference
  }

  override fun or(defaultValue: T): T {
    checkNotNull(defaultValue, "use Optional.orNull() instead of Optional.or(null)")
    return reference
  }

  override fun or(secondChoice: Optional<out T>): Optional<T> {
    checkNotNull(secondChoice)
    return this
  }

  override fun or(supplier: Supplier<out T>): T {
    checkNotNull(supplier)
    return reference
  }

  override fun orNull(): T? {
    return reference
  }

  override fun equals(other: Any?): Boolean {
    return if (other is Present<*>) {
      return reference == other.reference
    } else false
  }

  override fun hashCode(): Int {
    return 0x598df91c + reference.hashCode()
  }

  override fun toString(): String {
    return "Optional.of($reference)"
  }

  companion object {
    private const val serialVersionUID: Long = 0
  }
}