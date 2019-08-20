package expo.modules.camera2.camera.configs

import android.support.annotation.RestrictTo
import expo.modules.camera2.camera.UseCase

/**
 * Configuration containing options pertaining to EventListener object.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface UseCaseEventConfig {
  /**
   * Returns the EventListener.
   *
   * @return The stored value, if it exists in this configuration.
   */
  val useCaseEventListener: UseCase.EventListener?

  /**
   * Returns the EventListener.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this
   * configuration.
   * @hide
   */
  fun getUseCaseEventListener(valueIfMissing: UseCase.EventListener?): UseCase.EventListener?

  /**
   * Builder for a [UseCaseEventConfig].
   *
   * @param B The top level builder type for which this builder is composed with.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface Builder<B> {

    /**
     * Sets the EventListener.
     *
     * @param eventListener The EventListener.
     * @return the current Builder.
     */
    fun setUseCaseEventListener(eventListener: UseCase.EventListener): B
  }

  companion object {
    /**
     * Option: expo.camera.useCaseEventListener
     */
    val OPTION_USE_CASE_EVENT_LISTENER = Config.Option("expo.camera.useCaseEventListener", UseCase.EventListener::class.java)
  }
}
