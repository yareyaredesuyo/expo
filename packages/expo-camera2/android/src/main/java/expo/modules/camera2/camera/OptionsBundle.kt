package expo.modules.camera2.camera

import android.support.annotation.RestrictTo
import expo.modules.camera2.camera.configs.Config

import java.util.Collections
import java.util.Comparator
import java.util.TreeMap

/**
 * An immutable implementation of [Config].
 *
 * OptionsBundle is a collection of [Config.Option]s and their values which can be
 * queried based on exact [Config.Option] objects or based on Option ids.
 *
 * TODO: Make these options parcelable
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
open class OptionsBundle internal constructor(
    protected val options: TreeMap<Config.Option<*>, Any>
) : Config
{
  override fun listOptions(): Set<Config.Option<*>> {
    return Collections.unmodifiableSet(options.keys)
  }

  override fun containsOption(id: Config.Option<*>): Boolean {
    return options.containsKey(id)
  }

  override fun <ValueT> retrieveOption(id: Config.Option<ValueT>): ValueT {
    return retrieveOption(id, null) ?: throw IllegalArgumentException("Option does not exist: $id")
  }

  override fun <ValueT> retrieveOption(id: Config.Option<ValueT>, valueIfMissing: ValueT?): ValueT? {
    // Options should have only been inserted via insertOption()
    @Suppress("UNCHECKED_CAST")
    return options[id] as ValueT ?: valueIfMissing
  }

  override fun findOptions(idSearchString: String, matcher: Config.OptionMatcher) {
    val query = Config.Option(idSearchString, Void::class.java)
    for ((option) in options.tailMap(query)) {
      if (!option.id.startsWith(idSearchString)) {
        // We've reached the end of the range that contains our search stem.
        break
      }

      if (!matcher.onOptionMatched(option)) {
        // Caller does not need further results
        break
      }
    }
  }

  companion object {

    private val EMPTY_BUNDLE = OptionsBundle(TreeMap(Comparator { o1, o2 -> o1.id.compareTo(o2.id) }))

    /**
     * Create an OptionsBundle from another configuration.
     *
     * This will copy the options/values from the provided configuration.
     *
     * @param otherConfig Configuration containing options/values to be copied.
     * @return A new OptionsBundle pre-populated with options/values.
     */
    fun from(otherConfig: Config): OptionsBundle {
      // No need to create another instance since OptionsBundle is immutable
      if (otherConfig is OptionsBundle) {
        return otherConfig
      }

      val persistentOptions: TreeMap<Config.Option<*>, Any> = TreeMap(Comparator { o1, o2 -> o1.id.compareTo(o2.id) })
      for (opt: Config.Option<*> in otherConfig.listOptions()) {
        persistentOptions[opt] = otherConfig.retrieveOption(opt) as Any
      }

      return OptionsBundle(persistentOptions)
    }

    /**
     * Create an empty OptionsBundle.
     *
     * This options bundle will have no option/value pairs.
     *
     * @return An OptionsBundle pre-populated with no options/values.
     */
    fun emptyBundle(): OptionsBundle {
      return EMPTY_BUNDLE
    }
  }
}
