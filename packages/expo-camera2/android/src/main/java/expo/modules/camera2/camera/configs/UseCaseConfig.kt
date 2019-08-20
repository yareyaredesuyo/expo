package expo.modules.camera2.camera.configs

import android.support.annotation.RestrictTo

import expo.modules.camera2.camera.UseCase

/**
 * Configuration containing options for use cases.
 *
 * @param T The use case being configured.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface UseCaseConfig<T : UseCase> :
    TargetConfig<T>,
    Config,
    UseCaseEventConfig
{
  /**
   * Retrieves the default session configuration for this use case.
   *
   * This configuration is used to initialize the use case's session configuration with default
   * values.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalAccessException::class)
  val defaultSessionConfig: SessionConfig

  /**
   * Retrieves the default capture configuration for this use case.
   *
   * This configuration is used to initialize the use case's capture configuration with default
   * values.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalAccessException::class)
  val defaultCaptureConfig: CaptureConfig

  /**
   * Retrieves the [SessionConfig.OptionUnpacker] for this use case.
   *
   * This unpacker is used to initialize the use case's session configuration.
   *
   * TODO(b/120949879): This may be removed when SessionConfig removes all camera2 dependencies.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalAccessException::class)
  val sessionOptionUnpacker: SessionConfig.OptionUnpacker

  /**
   * Retrieves the [CaptureConfig.OptionUnpacker] for this use case.
   *
   * This unpacker is used to initialize the use case's capture configuration.
   *
   * TODO(b/120949879): This may be removed when CaptureConfig removes all camera2 dependencies.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalAccessException::class)
  val captureOptionUnpacker: CaptureConfig.OptionUnpacker

  /**
   * Retrieves the surface occupancy priority of the target intending to use from this
   * configuration.
   *
   * @return The stored value, if it exists in this configuration.
   * @throws IllegalArgumentException if the option does not exist in this configuration.
   */
  @get:Throws(IllegalAccessException::class)
  val surfaceOccupancyPriority: Int


  /**
   * Retrieves the default session configuration for this use case.
   *
   * This configuration is used to initialize the use case's session configuration with default values.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getDefaultSessionConfig(valueIfMissing: SessionConfig?): SessionConfig?

  /**
   * Retrieves the default capture configuration for this use case.
   *
   * This configuration is used to initialize the use case's capture configuration with default values.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getDefaultCaptureConfig(valueIfMissing: CaptureConfig?): CaptureConfig?

  /**
   * Retrieves the [SessionConfig.OptionUnpacker] for this use case.
   *
   * This unpacker is used to initialize the use case's session configuration.
   *
   * TODO(b/120949879): This may be removed when SessionConfig removes all camera2 dependencies.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getSessionOptionUnpacker(valueIfMissing: SessionConfig.OptionUnpacker?): SessionConfig.OptionUnpacker?

  /**
   * Retrieves the [CaptureConfig.OptionUnpacker] for this use case.
   *
   * This unpacker is used to initialize the use case's capture configuration.
   *
   * TODO(b/120949879): This may be removed when CaptureConfig removes all camera2 dependencies.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getCaptureOptionUnpacker(valueIfMissing: CaptureConfig.OptionUnpacker?): CaptureConfig.OptionUnpacker?

  /**
   * Retrieves the surface occupancy priority of the target intending to use from this configuration.
   *
   * @param valueIfMissing The value to return if this configuration option has not been set.
   * @return The stored value or `valueIfMissing` if the value does not exist in this configuration.
   */
  fun getSurfaceOccupancyPriority(valueIfMissing: Int): Int

  /**
   * Builder for a [UseCaseConfig].
   *
   * @param T The type of the object being configured.
   * @param C The top level configuration which will be generated by [.build].
   * @param B The top level builder type for which this builder is composed with.
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  interface Builder<T : UseCase, C : UseCaseConfig<T>, B> :
      TargetConfig.Builder<T, B>,
      Config.ExtendableBuilder,
      UseCaseEventConfig.Builder<B>
  {
    /**
     * Sets the default session configuration for this use case.
     *
     * @param sessionConfig The default session configuration to use for this use case.
     * @return the current Builder.
     */
    fun setDefaultSessionConfig(sessionConfig: SessionConfig): B

    /**
     * Sets the default capture configuration for this use case.
     *
     * @param captureConfig The default capture configuration to use for this use case.
     * @return the current Builder.
     */
    fun setDefaultCaptureConfig(captureConfig: CaptureConfig): B

    /**
     * Sets the Option Unpacker for translating this configuration into a [SessionConfig]
     *
     * TODO(b/120949879): This may be removed when SessionConfig removes all camera2
     * dependencies.
     *
     * @param optionUnpacker The option unpacker for to use for this use case.
     * @return the current Builder.
     * @hide
     */
    fun setSessionOptionUnpacker(optionUnpacker: SessionConfig.OptionUnpacker): B

    /**
     * Sets the Option Unpacker for translating this configuration into a [CaptureConfig]
     *
     * TODO(b/120949879): This may be removed when CaptureConfig removes all camera2
     * dependencies.
     *
     * @param optionUnpacker The option unpacker for to use for this use case.
     * @return the current Builder.
     * @hide
     */
    fun setCaptureOptionUnpacker(optionUnpacker: CaptureConfig.OptionUnpacker): B

    /**
     * Sets the surface occupancy priority of the intended target from this configuration.
     *
     * The stream resource of [android.hardware.camera2.CameraDevice] is limited. When
     * one use case occupies a larger stream resource, it will impact the other use cases to get
     * smaller stream resource. Use this to determine which use case can have higher priority to
     * occupancy stream resource first.
     *
     * @param priority The priority to occupancy the available stream resource. Higher value
     * will have higher priority.
     * @return The current Builder.
     */
    fun setSurfaceOccupancyPriority(priority: Int): B

    /**
     * Builds the configuration for the target use case.
     */
    fun build(): C
  }

  companion object {
    /**
     * Option: expo.camera.useCase.defaultSessionConfig
     */
    val OPTION_DEFAULT_SESSION_CONFIG = Config.Option("expo.camera.useCase.defaultSessionConfig", SessionConfig::class.java)

    /**
     * Option: expo.camera.useCase.defaultCaptureConfig
     */
    val OPTION_DEFAULT_CAPTURE_CONFIG = Config.Option("expo.camera.useCase.defaultCaptureConfig", CaptureConfig::class.java)

    /**
     * Option: expo.camera.useCase.sessionConfigUnpacker
     *
     * TODO(b/120949879): This may be removed when SessionConfig removes all camera2 dependencies.
     */
    val OPTION_SESSION_CONFIG_UNPACKER = Config.Option("expo.camera.useCase.sessionConfigUnpacker",SessionConfig.OptionUnpacker::class.java)

    /**
     * Option: expo.camera.useCase.captureConfigUnpacker
     *
     * TODO(b/120949879): This may be removed when CaptureConfig removes all camera2 dependencies.
     */
    val OPTION_CAPTURE_CONFIG_UNPACKER = Config.Option("expo.camera.useCase.captureConfigUnpacker", CaptureConfig.OptionUnpacker::class.java)

    /**
     * Option: expo.camera.useCase.surfaceOccypyPriority
     */
    val OPTION_SURFACE_OCCUPANCY_PRIORITY = Config.Option("expo.camera.useCase.surfaceOccupancyPriority", Int::class.java)
  }
}
