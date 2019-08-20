package expo.modules.camera2.camera.configs

import android.support.annotation.RestrictTo
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * A value set implementation that store multiple values in type [C].
 *
 * @param C The type of the parameter.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
abstract class MultiValueSet<C> {

  private val mSet = HashSet<C>()

  /**
   * Returns the list of [C] which containing all the elements were added to this value set.
   */
  val allItems: List<C>
    get() = Collections.unmodifiableList(ArrayList(mSet))

  /**
   * Adds all of the elements in the specified collection to this value set if they're not already
   * present (optional operation).
   *
   * @param  value collection containing elements to be added to this value.
   */
  fun addAll(value: List<C>) {
    mSet.addAll(value)
  }

  /**
   * Need to implement the clone method for object copy.
   * @return the cloned instance.
   */
  abstract fun clone(): MultiValueSet<C>
}

