package expo.modules.camera2.camera.configs

import android.support.annotation.RestrictTo

/**
 * A Configuration is a collection of options and values.
 *
 * Configuration object hold pairs of Options/Values and offer methods for querying whether
 * Options are contained in the configuration along with methods for retrieving the associated
 * values for options.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface Config {
  /**
   * Returns whether this configuration contains the supplied option.
   *
   * @param id The [Option] to search for in this configuration.
   * @return `true` if this configuration contains the supplied option; `false` otherwise.
   */
  fun containsOption(id: Option<*>): Boolean

  /**
   * Retrieves the value for the specified option if it exists in the configuration.
   *
   * If the option does not exist, an exception will be thrown.
   *
   * @param id       The [Option] to search for in this configuration.
   * @param ValueT   The type for the value associated with the supplied [Option].
   * @return The value stored in this configuration, or `null` if it does not exist.
   * @throws IllegalArgumentException if the given option does not exist in this configuration.
   */
  @Throws(IllegalAccessException::class)
  fun <ValueT> retrieveOption(id: Option<ValueT>): ValueT

  /**
   * Retrieves the value for the specified option if it exists in the configuration.
   *
   * If the option does not exist, `valueIfMissing` will be returned.
   *
   * @param id             The [Option] to search for in this configuration.
   * @param valueIfMissing The value to return if the specified [Option] does not exist in this configuration.
   * @param ValueT         The type for the value associated with the supplied [Option].
   * @return The value stored in this configuration, or `null` if it does not exist.
   */
  fun <ValueT> retrieveOption(id: Option<ValueT>, valueIfMissing: ValueT?): ValueT?

  /**
   * Search the configuration for [Option]s whose id match the supplied search string.
   *
   * @param idSearchString The id string to search for. This could be a fully qualified id such as
   * `camerax.core.example.option` or the stem for an option such as `camerax.core.example`.
   * @param matcher        A callback used to receive results of the search. Results will be sent to
   * [OptionMatcher.onOptionMatched] in the order in which they are found inside this configuration.
   * Subsequent results will continue to be sent as long as [OptionMatcher.onOptionMatched] returns `true`.
   */
  fun findOptions(idSearchString: String, matcher: OptionMatcher)

  /**
   * Lists all options contained within this configuration.
   *
   * @return A [Set] of [Option]s contained within this configuration.
   */
  fun listOptions(): Set<Option<*>>

  /**
   * Extendable builders are used to add externally defined options to a configuration.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface ExtendableBuilder {

    /**
     * Returns the underlying [MutableConfig] being modified by this builder.
     *
     * @return The underlying [MutableConfig].
     */
    val mutableConfig: MutableConfig
  }

  /**
   * A callback for retrieving results of a [Config.Option] search.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface OptionMatcher {
    /**
     * Receives results from [Config.findOptions].
     *
     * When searching for a specific option in a [Config], [Option]s will
     * be sent to [onOptionMatched] in the order in which they are found.
     *
     * @param option The matched option.
     * @return `false` if no further results are needed; `true` otherwise.
     */
    fun onOptionMatched(option: Option<*>): Boolean
  }

  /**
   * An [Option] is used to set and retrieve values for settings defined in a [Config].
   *
   * [Option]s can be thought of as the key in a key/value pair that makes up a setting.
   * As the name suggests, [Option]s are optional, and may or may not exist inside a [Config].
   *
   * @param T The type of the value for this option.
   * @param id unique string identifier for this option. This generally follows the scheme `<owner>.optional.subCategories.<optionId>`
   * @param valueClass the class object associated with the value for this option.
   * @param token the optional type-erased context object for this option. Generally this object should have static scope and be immutable.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  data class Option<T> internal constructor(
      val id: String,
      val valueClass: Class<T>,
      val token: Any? = null
  )
}
