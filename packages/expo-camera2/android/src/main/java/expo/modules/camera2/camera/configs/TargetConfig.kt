package expo.modules.camera2.camera.configs

import android.support.annotation.RestrictTo

/**
 * Configuration containing options used to identify the target class and object being configured.
 * @param T The type of the object being configured.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface TargetConfig<T> {
  /**
   * Retrieves the class of the object being configured.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalArgumentException::class)
  val targetClass: Class<T>

  /**
   * Retrieves the name of the target object being configured.
   *
   * The name should be a value that can uniquely identify an instance of the object being
   * configured.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalArgumentException::class)
  val targetName: String

  /**
   * Retrieves the class of the object being configured.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getTargetClass(valueIfMissing: Class<T>?): Class<T>?

  /**
   * Retrieves the name of the target object being configured.
   *
   * The name should be a value that can uniquely identify an instance of the object being
   * configured.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getTargetName(valueIfMissing: String?): String?

  /**
   * Builder for a [TargetConfig].
   *
   * A [TargetConfig] contains options used to identify the target class and
   * object being configured.
   *
   * @param T The type of the object being configured.
   * @param B The top level builder type for which this builder is composed with.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface Builder<T, B> {

    /**
     * Sets the class of the object being configured.
     *
     * Setting the target class will automatically generate a unique target name if one does
     * not already exist in this configuration.
     *
     * @param targetClass A class object corresponding to the class of the object being configured.
     * @return the current Builder.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun setTargetClass(targetClass: Class<T>): B

    /**
     * Sets the name of the target object being configured.
     *
     * The name should be a value that can uniquely identify an instance of the object being
     * configured.
     *
     * @param targetName A unique string identifier for the instance of the class being configured.
     * @return the current Builder.
     */
    fun setTargetName(targetName: String): B
  }

  companion object {
    /**
     * Option: expo.camera.target.name
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    val OPTION_TARGET_NAME = Config.Option("expo.camera.target.name", String::class.java)

    /**
     * Option: expo.camera.target.class
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    val OPTION_TARGET_CLASS = Config.Option("expo.camera.target.class", Class::class.java)
  }
}
