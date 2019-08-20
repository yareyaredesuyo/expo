package expo.modules.camera2.camera

import android.support.annotation.RestrictTo
import expo.modules.camera2.camera.configs.Config
import expo.modules.camera2.camera.configs.MutableConfig

import java.util.Comparator
import java.util.TreeMap

/**
 * A MutableOptionsBundle is an [OptionsBundle] which allows for insertion/removal.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class MutableOptionsBundle private constructor(persistentOptions: TreeMap<Config.Option<*>, Any>) :
    OptionsBundle(persistentOptions),
    MutableConfig
{
  override fun <ValueT> removeOption(opt: Config.Option<ValueT>): ValueT? {
    @Suppress("UNCHECKED_CAST")
    return options.remove(opt) as ValueT
  }

  override fun <ValueT> insertOption(opt: Config.Option<ValueT>, value: ValueT) {
    options[opt] = value as Any
  }

  companion object {

    private val ID_COMPARE = Comparator<Config.Option<*>> { o1, o2 -> o1.id.compareTo(o2.id) }

    /**
     * Creates an empty MutableOptionsBundle.
     *
     * @return an empty MutableOptionsBundle containing no options.
     */
    fun create(): MutableOptionsBundle {
      return MutableOptionsBundle(TreeMap(ID_COMPARE))
    }

    /**
     * Creates a MutableOptionsBundle from an existing immutable Config.
     *
     * @param otherConfig configuration options to insert.
     * @return a MutableOptionsBundle prepopulated with configuration options.
     */
    fun from(otherConfig: Config): MutableOptionsBundle {
      val persistentOptions = TreeMap<Config.Option<*>, Any>(ID_COMPARE)
      for (opt in otherConfig.listOptions()) {
        persistentOptions[opt] = otherConfig.retrieveOption(opt) as Any
      }

      return MutableOptionsBundle(persistentOptions)
    }
  }
}
