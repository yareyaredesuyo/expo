package expo.modules.camera2.camera

import android.support.annotation.RestrictTo

/**
 * This file defines the enumeration constants used for querying the camera capture mode and results.
 */

/**
 * Auto focus (AF) mode.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class AfMode {

  /**
   * AF mode is currently unknown.
   */
  UNKNOWN,

  /**
   * The AF routine does not control the lens.
   */
  OFF,

  /**
   * AF is triggered on demand.
   * In this mode, the lens does not move unless the auto focus trigger action is called.
   */
  ON_MANUAL_AUTO,

  /**
   * AF is continually scanning.
   *
   *
   * In this mode, the AF algorithm modifies the lens position continually to attempt to
   * provide a constantly-in-focus stream.
   */
  ON_CONTINUOUS_AUTO
}

/**
 * Auto focus (AF) state.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class AfState {

  /**
   * AF state is currently unknown.
   */
  UNKNOWN,

  /**
   * AF is off or not yet has been triggered.
   */
  INACTIVE,

  /**
   * AF is performing an AF scan.
   */
  SCANNING,

  /**
   * AF currently believes it is in focus.
   */
  FOCUSED,

  /**
   * AF believes it is focused correctly and has locked focus.
   */
  LOCKED_FOCUSED,

  /**
   * AF has failed to focus and has locked focus.
   */
  LOCKED_NOT_FOCUSED
}

/**
 * Auto exposure (AE) state.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class AeState {

  /**
   * AE state is currently unknown.
   */
  UNKNOWN,

  /**
   * AE is off or has not yet been triggered.
   */
  INACTIVE,

  /**
   * AE is performing an AE search.
   */
  SEARCHING,

  /**
   * AE has a good set of control values, but flash needs to be fired for good quality still capture.
   */
  FLASH_REQUIRED,

  /**
   * AE has a good set of control values for the current scene.
   */
  CONVERGED,

  /**
   * AE has been locked.
   */
  LOCKED
}

/**
 * Auto white balance (AWB) state.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class AwbState {

  /**
   * AWB state is currently unknown.
   */
  UNKNOWN,

  /**
   * AWB is not in auto mode, or has not yet started metering.
   */
  INACTIVE,

  /**
   * AWB is performing AWB metering.
   */
  METERING,

  /**
   * AWB has a good set of control values for the current scene.
   */
  CONVERGED,

  /**
   * AWB has been locked.
   */
  LOCKED
}

/**
 * Flash state.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
enum class FlashState {

  /**
   * Flash state is unknown.
   */
  UNKNOWN,

  /**
   * Flash is unavailable or not ready to fire.
   */
  NONE,

  /**
   * Flash is ready to fire.
   */
  READY,

  /**
   * Flash has been fired.
   */
  FIRED
}
