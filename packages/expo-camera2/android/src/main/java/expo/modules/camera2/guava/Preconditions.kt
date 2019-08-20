package expo.modules.camera2.guava

/**
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/base/Preconditions.java)
 */
object Preconditions {

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @throws IllegalArgumentException if `expression` is false
   */
  @Throws(IllegalStateException::class)
  fun checkArgument(expression: Boolean) {
    if (!expression) {
      throw IllegalArgumentException()
    }
  }

  /**
   * Ensures the truth of an expression involving one or more parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using [String.valueOf]
   * @throws IllegalArgumentException if `expression` is false
   */
  @Throws(IllegalArgumentException::class)
  fun checkArgument(expression: Boolean, errorMessage: Any?) {
    if (!expression) {
      throw IllegalArgumentException(errorMessage.toString())
    }
  }

  /**
   * Ensures the truth of an expression involving the state of the calling
   * instance, but not involving any parameters to the calling method.
   *
   * @param expression a boolean expression
   * @param message exception message
   * @throws IllegalStateException if `expression` is false
   */
  @JvmOverloads
  @Throws(IllegalStateException::class)
  fun checkState(expression: Boolean, message: String? = null) {
    if (!expression) {
      throw IllegalStateException(message)
    }
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @return the non-null reference that was validated
   * @throws NullPointerException if `reference` is null
   */
  @Throws(NullPointerException::class)
  fun <T> checkNotNull(reference: T?): T {
    if (reference == null) {
      throw NullPointerException()
    }
    return reference
  }

  /**
   * Ensures that an object reference passed as a parameter to the calling method is not null.
   *
   * @param reference an object reference
   * @param errorMessage the exception message to use if the check fails; will be converted to a
   * string using [String.valueOf]
   * @return the non-null reference that was validated
   * @throws NullPointerException if `reference` is null
   */
  @Throws(NullPointerException::class)
  fun <T> checkNotNull(reference: T?, errorMessage: Any?): T {
    if (reference == null) {
      throw NullPointerException(errorMessage.toString())
    }
    return reference
  }
}
