package expo.modules.camera2.guava.optionals

import java.io.Serializable

import expo.modules.camera2.guava.Supplier

/**
 * **Comparison to `java.util.Optional` (JDK 8 and higher):** A new `Optional`
 * class was added for Java 8. The two classes are extremely similar, but incompatible (they cannot
 * share a common supertype). *All* known differences are listed either here or with the
 * relevant methods below.
 *
 *  * This class is serializable; `java.util.Optional` is not.
 *  * `java.util.Optional` has the additional methods `ifPresent`, `filter`,
 * `flatMap`, and `orElseThrow`.
 *  * `java.util` offers the primitive-specialized versions `OptionalInt`, `OptionalLong` and `OptionalDouble`, the use of which is recommended; Guava does not
 * have these.
 *
 * @param <T> the type of instance that can be contained. `Optional` is naturally covariant on
 * this type, so it is safe to cast an `Optional<T>` to `Optional<S>` for any
 * supertype `S` of `T`.
 *
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/base/Optional.java)
 */
abstract class Optional<T> internal constructor() : Serializable {

  /**
   * Returns `true` if this holder contains a (non-null) instance.
   *
   * **Comparison to `java.util.Optional`:** no differences.
   */
  abstract val isPresent: Boolean

  /**
   * Returns the contained instance, which must be present. If the instance might be absent, use
   * [.or] or [.orNull] instead.
   *
   * **Comparison to `java.util.Optional`:** when the value is absent, this method
   * throws [IllegalStateException], whereas the Java 8 counterpart throws [ ].
   *
   * @throws IllegalStateException if the instance is absent ([.isPresent] returns `false`); depending on this *specific* exception type (over the more general [     ]) is discouraged
   */
  @Throws(IllegalStateException::class)
  abstract fun get(): T

  /**
   * Returns the contained instance if it is present; `defaultValue` otherwise. If no default
   * value should be required because the instance is known to be present, use [.get]
   * instead. For a default value of `null`, use [.orNull].
   *
   * Note about generics: The signature `public T or(T defaultValue)` is overly
   * restrictive. However, the ideal signature, `public <S super T> S or(S)`, is not legal
   * Java. As a result, some sensible operations involving subtypes are compile errors:
   *
   * <pre>`Optional<Integer> optionalInt = getSomeOptionalInt();
   * Number value = optionalInt.or(0.5); // error
   *
   * FluentIterable<? extends Number> numbers = getSomeNumbers();
   * Optional<? extends Number> first = numbers.first();
   * Number value = first.or(0.5); // error
  `</pre> *
   *
   *
   * As a workaround, it is always safe to cast an `Optional<? extends T>` to `Optional<T>`. Casting either of the above example `Optional` instances to `Optional<Number>` (where `Number` is the desired output type) solves the problem:
   *
   * <pre>`Optional<Number> optionalInt = (Optional) getSomeOptionalInt();
   * Number value = optionalInt.or(0.5); // fine
   *
   * FluentIterable<? extends Number> numbers = getSomeNumbers();
   * Optional<Number> first = (Optional) numbers.first();
   * Number value = first.or(0.5); // fine
  `</pre> *
   *
   * **Comparison to `java.util.Optional`:** this method is similar to Java 8's `Optional.orElse`, but will not accept `null` as a `defaultValue` ([.orNull]
   * must be used instead). As a result, the value returned by this method is guaranteed non-null,
   * which is not the case for the `java.util` equivalent.
   */
  abstract fun or(defaultValue: T): T

  /**
   * Returns this `Optional` if it has a value present; `secondChoice` otherwise.
   *
   * **Comparison to `java.util.Optional`:** this method has no equivalent in Java 8's
   * `Optional` class; write `thisOptional.isPresent() ? thisOptional : secondChoice`
   * instead.
   */
  abstract fun or(secondChoice: Optional<out T>): Optional<T>

  /**
   * Returns the contained instance if it is present; `supplier.get()` otherwise.
   *
   * **Comparison to `java.util.Optional`:** this method is similar to Java 8's `Optional.orElseGet`, except when `supplier` returns `null`. In this case this
   * method throws an exception, whereas the Java 8 method returns the `null` to the caller.
   *
   * @throws NullPointerException if this optional's value is absent and the supplier returns `null`
   */
  @Throws(NullPointerException::class)
  abstract fun or(supplier: Supplier<out T>): T

  /**
   * Returns the contained instance if it is present; `null` otherwise. If the instance is
   * known to be present, use [.get] instead.
   *
   * **Comparison to `java.util.Optional`:** this method is equivalent to Java 8's
   * `Optional.orElse(null)`.
   */
  abstract fun orNull(): T?

  /**
   * Returns `true` if `object` is an `Optional` instance, and either the
   * contained references are [equal][Object.equals] to each other or both are absent.
   * Note that `Optional` instances of differing parameterized types can be equal.
   *
   * **Comparison to `java.util.Optional`:** no differences.
   */
  abstract override fun equals(other: Any?): Boolean

  /**
   * Returns a hash code for this instance.
   *
   * **Comparison to `java.util.Optional`:** this class leaves the specific choice of
   * hash code unspecified, unlike the Java 8 equivalent.
   */
  abstract override fun hashCode(): Int

  /**
   * Returns a string representation for this instance.
   *
   * **Comparison to `java.util.Optional`:** this class leaves the specific string
   * representation unspecified, unlike the Java 8 equivalent.
   */
  abstract override fun toString(): String

  companion object {
    /**
     * Returns an `Optional` instance with no contained reference.
     *
     * **Comparison to `java.util.Optional`:** no differences
     */
    fun <T> absent(): Optional<T> {
      return Absent.withType()
    }

    /**
     * Returns an `Optional` instance containing the given non-null reference. To have `null` treated as [.absent], use [.fromNullable] instead.
     *
     * **Comparison to `java.util.Optional`:** no differences.
     *
     * @throws NullPointerException if `reference` is null
     */
    @Throws(NullPointerException::class)
    fun <T> of(reference: T): Optional<T> {
      return Present(reference)
    }

    /**
     * If `nullableReference` is non-null, returns an `Optional` instance containing that
     * reference; otherwise returns [Optional.absent].
     *
     * **Comparison to `java.util.Optional`:** no differences
     */
    fun <T> fromNullable(nullableReference: T?): Optional<T> {
      return nullableReference?.let { Present(it) } ?: absent()
    }

    private const val serialVersionUID: Long = 0
  }
}