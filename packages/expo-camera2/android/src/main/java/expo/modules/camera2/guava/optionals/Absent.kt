package expo.modules.camera2.guava.optionals

import expo.modules.camera2.guava.Supplier
import expo.modules.camera2.guava.Preconditions.checkNotNull

/** Implementation of an [Optional] not containing a reference.
 *
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/base/Absent.java)
 */
internal class Absent<T> private constructor() : Optional<T>() {

  override val isPresent: Boolean
    get() = false

  override fun get(): T {
    throw IllegalStateException("Optional.get() cannot be called on an absent value")
  }

  override fun or(defaultValue: T): T {
    return checkNotNull(defaultValue, "use Optional.orNull() instead of Optional.or(null)")
  }

  override fun or(secondChoice: Optional<out T>): Optional<T> {
    return checkNotNull(secondChoice) as Optional<T>
  }

  override fun or(supplier: Supplier<out T>): T {
    return checkNotNull(supplier.get(), "use Optional.orNull() instead of a Supplier that returns null")
  }

  override fun orNull(): T? {
    return null
  }

  override fun equals(other: Any?): Boolean {
    return other === this
  }

  override fun hashCode(): Int {
    return 0x79a31aac
  }

  override fun toString(): String {
    return "Optional.absent()"
  }

  private fun readResolve(): Any {
    return sInstance
  }

  companion object {
    val sInstance = Absent<Any>()

    fun <T> withType(): Optional<T> {
      return sInstance as Optional<T>
    }

    private const val serialVersionUID: Long = 0
  }
}