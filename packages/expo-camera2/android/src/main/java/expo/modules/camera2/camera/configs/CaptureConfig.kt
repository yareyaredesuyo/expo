package expo.modules.camera2.camera.configs

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.support.annotation.RestrictTo
import android.view.Surface

import expo.modules.camera2.camera.CameraCaptureCallback
import expo.modules.camera2.camera.DeferrableSurface
import expo.modules.camera2.camera.MutableOptionsBundle
import expo.modules.camera2.camera.OptionsBundle

import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * Configurations needed for a capture request.
 * The CaptureConfig contains all the [android.hardware.camera2] parameters that are required to issue a [CaptureRequest].
 *
 * In practice, the [CaptureConfig.Builder] will be used to construct a CaptureConfig.
 *
 * @param surfaces               The set of [Surface] that data from the camera will be put into.
 * @param implementationOptions  The generic parameters to be passed to the [BaseCamera] class.
 * @param templateType           The template for parameters of the [CaptureRequest]. This must match the constants defined by [CameraDevice].
 * @param cameraCaptureCallbacks All camera capture callbacks.
 * @param isUseRepeatingSurface  True if this capture request needs a repeating surface
 * @param tag                    The tag for associating capture result with capture request.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class CaptureConfig private constructor(
    surfaces: List<DeferrableSurface>,
    val implementationOptions: Config,
    val templateType: Int,
    cameraCaptureCallbacks: List<CameraCaptureCallback>,
    val isUseRepeatingSurface: Boolean,
    val tag: Any
) {
  /**
   * The camera capture callback for a [CameraCaptureSession].
   * Obtains all registered [CameraCaptureCallback] callbacks.
   */
  val cameraCaptureCallbacks: List<CameraCaptureCallback> = Collections.unmodifiableList(cameraCaptureCallbacks)

  /** Get all the surfaces that the request will write data to.  */
  val surfaces: List<DeferrableSurface> = Collections.unmodifiableList(surfaces)

  /**
   * Interface for unpacking a configuration into a CaptureConfig.Builder
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface OptionUnpacker {
    /**
     * Apply the options from the config onto the builder
     * @param config  the set of options to apply
     * @param builder the builder on which to apply the options
     */
    fun unpack(config: UseCaseConfig<*>, builder: Builder)
  }

  /**
   * Builder for easy modification/rebuilding of a [CaptureConfig].
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  class Builder {

    /**
     * Gets the surfaces attached to the request.
     */
    val surfaces: MutableSet<DeferrableSurface> = HashSet()

    /**
     * The templates used for configuring a [CaptureRequest].
     * This must match the constants defined by [CameraDevice]
     */
    var templateType = -1

    var isUseRepeatingSurface = false

    private val cameraCaptureCallbacks = ArrayList<CameraCaptureCallback>()

    lateinit var tag: Any

    var implementationOptions: MutableConfig = MutableOptionsBundle.create()

    constructor()

    private constructor(base: CaptureConfig) {
      surfaces.addAll(base.surfaces)
      cameraCaptureCallbacks.addAll(base.cameraCaptureCallbacks)
      implementationOptions = MutableOptionsBundle.from(base.implementationOptions)
      isUseRepeatingSurface = base.isUseRepeatingSurface
      templateType = base.templateType
      tag = base.tag
    }

    fun setImplementationOptions(config: Config) {
      implementationOptions = MutableOptionsBundle.from(config)
    }

    /**
     * Adds a [CameraCaptureSession.StateCallback] callback.
     *
     * @throws IllegalArgumentException if the callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addCameraCaptureCallback(cameraCaptureCallback: CameraCaptureCallback) {
      if (cameraCaptureCallbacks.contains(cameraCaptureCallback)) {
        throw IllegalArgumentException("duplicate camera capture callback")
      }
      cameraCaptureCallbacks.add(cameraCaptureCallback)
    }

    /**
     * Adds all [CameraCaptureSession.StateCallback] callbacks.
     *
     * @throws IllegalArgumentException if any callback already exists in the configuration.
     */
    @Throws(IllegalArgumentException::class)
    fun addAllCameraCaptureCallbacks(cameraCaptureCallbacks: Collection<CameraCaptureCallback>) {
      for (c in cameraCaptureCallbacks) {
        addCameraCaptureCallback(c)
      }
    }

    /**
     * Add a surface that the request will write data to.
     */
    fun addSurface(surface: DeferrableSurface) {
      surfaces.add(surface)
    }

    /**
     * Remove a surface that the request will write data to.
     */
    fun removeSurface(surface: DeferrableSurface) {
      surfaces.remove(surface)
    }

    /**
     * Remove all the surfaces that the request will write data to.
     */
    fun clearSurfaces() {
      surfaces.clear()
    }

    /**
     * Add a set of implementation specific options to the request.
     */
    fun addImplementationOptions(config: Config) {
      for (option: Config.Option<*> in config.listOptions()) {
        val existValue = implementationOptions.retrieveOption(option, null)
        var newValue = config.retrieveOption(option)
        if (existValue is MultiValueSet<*>) {
          @Suppress("UNCHECKED_CAST")
          existValue.addAll((newValue as MultiValueSet<*>).allItems as List<Nothing>)
        } else {
          if (newValue is MultiValueSet<*>) {
            newValue = newValue.clone()
          }
          @Suppress("UNCHECKED_CAST")
          implementationOptions.insertOption(option as Config.Option<Any?>, newValue)
        }
      }
    }

    /**
     * Builds an instance of a CaptureConfig that has all the combined parameters of the
     * CaptureConfig that have been added to the Builder.
     */
    fun build() = CaptureConfig(
        ArrayList(this.surfaces),
        OptionsBundle.from(implementationOptions),
        templateType,
        cameraCaptureCallbacks,
        isUseRepeatingSurface,
        tag)


    companion object {
      /**
       * Creates a [Builder] from a [UseCaseConfig].
       * Populates the builder with all the properties defined in the base configuration.
       */
      fun createFrom(config: UseCaseConfig<*>): Builder {
        val unpacker = config.getCaptureOptionUnpacker(null)
            ?: throw IllegalStateException("Implementation is missing option unpacker for " + config.getTargetName(config.toString()))

        // Unpack the configuration into this builder
        return Builder().also { unpacker.unpack(config, it) }
      }

      /**
       * Create a [Builder] from a [CaptureConfig]
       */
      fun from(base: CaptureConfig) = Builder(base)
    }
  }

  companion object {

    /**
     * Returns an instance of a capture configuration with minimal configurations.
     */
    fun defaultEmptyCaptureConfig(): CaptureConfig {
      return Builder().build()
    }
  }
}

