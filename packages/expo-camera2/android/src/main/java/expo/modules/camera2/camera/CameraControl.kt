package expo.modules.camera2.camera

/**
 * An interface for controlling camera's zoom, focus and metering across all use cases.
 * Applications can retrieve the interface via CameraX.getCameraControl.
 */
interface CameraControl {

  /**
   * Starts a focus and metering action by the [FocusMeteringAction].
   *
   * The [FocusMeteringAction] contains the configuration of multiple 3A
   * [MeteringPoint]s, auto-cancel duration and[OnAutoFocusListener] to receive the
   * auto-focus result. Check [FocusMeteringAction] for more details.
   *
   * @param action the [FocusMeteringAction] to be executed.
   */
  fun startFocusAndMetering(action: FocusMeteringAction)

  /**
   * Cancels current [FocusMeteringAction].
   *
   * It clears the 3A regions and update current AF mode to CONTINOUS AF (if supported).
   * If auto-focus does not completes, it will notify the [OnAutoFocusListener] with
   * isFocusLocked set to false.
   */
  fun cancelFocusAndMetering()
}
