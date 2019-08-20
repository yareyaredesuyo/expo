package expo.modules.camera2.camera.configs

import android.support.annotation.RestrictTo

/**
 * MutableConfig is a [Config] that can be modified.
 * MutableConfig is the interface used to create immutable Config objects.
 */
interface MutableConfig : Config {

  /**
   * Inserts a Option/Value pair into the configuration.
   *
   * If the option already exists in this configuration, it will be replaced.
   *
   * @param opt      The option to be added or modified
   * @param value    The value to insert for this option.
   * @param <ValueT> The type of the value being inserted.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  fun <ValueT> insertOption(opt: Config.Option<ValueT>, value: ValueT)

  /**
   * Removes an option from the configuration if it exists.
   *
   * @param opt      The option to remove from the configuration.
   * @param <ValueT> The type of the value being removed.
   * @return The value that previously existed for `opt`, or `null` if the
   * option did not exist in this configuration.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  fun <ValueT> removeOption(opt: Config.Option<ValueT>): ValueT?
}
