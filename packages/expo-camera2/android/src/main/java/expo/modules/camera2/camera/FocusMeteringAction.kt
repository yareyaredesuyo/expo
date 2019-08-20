package expo.modules.camera2.camera

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A configuration used to trigger a focus and/or metering action.
 *
 * To construct a [FocusMeteringAction], apps have to create a [Builder] by
 * [Builder.from] or [Builder.from].
 * [MeteringPoint] is a point used to specify the focus/metering areas. Apps can use various
 * [MeteringPointFactory] to create the points. When the [FocusMeteringAction] is built,
 * pass it to [CameraControl.startFocusAndMetering] to initiate the focus
 * and metering action.
 *
 * The default [MeteringMode] is [MeteringMode.AF_AE_AWB] which means the point is
 * used for all AF/AE/AWB regions. Apps can set the proper [MeteringMode] to optionally
 * exclude some 3A regions. Multiple regions for specific 3A type are also supported via
 * [Builder.addPoint] or
 * [Builder.addPoint]. App can also this API to enable
 * different region for AF and AE respectively.
 *
 * If any AF points are specified, it will trigger AF to start a manual AF scan and cancel AF
 * trigger when [CameraControl.cancelFocusAndMetering] is called. When triggering AF is
 * done, it will call the [OnAutoFocusListener.onFocusCompleted] which is set via
 * [Builder.setAutoFocusCallback].  If AF point is not specified or
 * the action is cancelled before AF is locked, CameraX will call the
 * [OnAutoFocusListener.onFocusCompleted] with isFocusLocked set to false.
 *
 * App can set a auto-cancel duration to let CameraX call
 * [CameraControl.cancelFocusAndMetering] automatically in the specified duration. By
 * default the auto-cancel duration is 5 seconds. Apps can call [Builder.disableAutoCancel]
 * to disable auto-cancel.
 */
class FocusMeteringAction internal constructor(builder: Builder) {
  /**
   * Returns all [MeteringPoint]s used for AF regions.
   */
  val meteringPointsAF: List<MeteringPoint>

  /**
   * Returns all [MeteringPoint]s used for AE regions.
   */
  val meteringPointsAE: List<MeteringPoint>

  /**
   * Returns all [MeteringPoint]s used for AWB regions.
   */
  val meteringPointsAWB: List<MeteringPoint>

  internal val listenerExecutor: Executor
  /**
   * Returns current [OnAutoFocusListener].
   */
  val onAutoFocusListener: OnAutoFocusListener?

  /**
   * Returns auto-cancel duration.  Returns 0 if auto-cancel is disabled.
   */
  val autoCancelDurationInMs: Long

  private val mHasNotifiedListener = AtomicBoolean(false)

  /**
   * Returns if auto-cancel is enabled or not.
   */
  val isAutoCancelEnabled: Boolean
    get() = autoCancelDurationInMs != 0L

  init {
    meteringPointsAF = builder.mMeteringPointsAF
    meteringPointsAE = builder.mMeteringPointsAE
    meteringPointsAWB = builder.mMeteringPointsAWB
    listenerExecutor = builder.mListenerExecutor
    onAutoFocusListener = builder.mOnAutoFocusListener
    autoCancelDurationInMs = builder.mAutoCancelDurationInMs
  }

  /**
   * Notifies current [OnAutoFocusListener] and ensures it is called once.
   *
   * @hide
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
  fun notifyAutoFocusCompleted(isFocused: Boolean) {
    if (!mHasNotifiedListener.getAndSet(true)) {
      if (onAutoFocusListener != null) {
        listenerExecutor.execute { onAutoFocusListener!!.onFocusCompleted(isFocused) }
      }
    }
  }

  /**
   * Listener for receiving auto-focus completion event.
   */
  interface OnAutoFocusListener {
    /**
     * Called when camera auto focus completes or when the action is cancelled before
     * auto-focus completes.
     *
     * @param isFocusLocked true if focus is locked successfully, false otherwise.
     */
    fun onFocusCompleted(isFocusLocked: Boolean)
  }

  /**
   * Focus/Metering mode used to specify which 3A regions is activated for corresponding
   * [MeteringPoint].
   */
  enum class MeteringMode {
    AF_AE_AWB,
    AF_AE,
    AE_AWB,
    AF_AWB,
    AF_ONLY,
    AE_ONLY,
    AWB_ONLY
  }

  /**
   * The builder used to create the [FocusMeteringAction]. App must use
   * [Builder.from]
   * or [Builder.from] to create the [Builder].
   */
  class Builder private constructor(@NonNull point: MeteringPoint, @NonNull mode: MeteringMode = DEFAULT_METERINGMODE) {
    internal/* synthetic accessor */ val mMeteringPointsAF: MutableList<MeteringPoint> = ArrayList<MeteringPoint>()
    internal/* synthetic accessor */ val mMeteringPointsAE: MutableList<MeteringPoint> = ArrayList<MeteringPoint>()
    internal/* synthetic accessor */ val mMeteringPointsAWB: MutableList<MeteringPoint> = ArrayList<MeteringPoint>()
    internal /* synthetic accessor */ var mOnAutoFocusListener: OnAutoFocusListener? = null
    internal /* synthetic accessor */ var mListenerExecutor = CameraXExecutors.mainThreadExecutor()
    internal /* synthetic accessor */ var mAutoCancelDurationInMs = DEFAULT_AUTOCANCEL_DURATION

    init {
      addPoint(point, mode)
    }

    /**
     * Adds another [MeteringPoint] with specified [MeteringMode].
     */
    @NonNull
    @JvmOverloads
    fun addPoint(@NonNull point: MeteringPoint, @NonNull mode: MeteringMode = DEFAULT_METERINGMODE): Builder {
      if (mode == MeteringMode.AF_AE_AWB
          || mode == MeteringMode.AF_AE
          || mode == MeteringMode.AF_AWB
          || mode == MeteringMode.AF_ONLY) {
        mMeteringPointsAF.add(point)
      }

      if (mode == MeteringMode.AF_AE_AWB
          || mode == MeteringMode.AF_AE
          || mode == MeteringMode.AE_AWB
          || mode == MeteringMode.AE_ONLY) {
        mMeteringPointsAE.add(point)
      }

      if (mode == MeteringMode.AF_AE_AWB
          || mode == MeteringMode.AE_AWB
          || mode == MeteringMode.AF_AWB
          || mode == MeteringMode.AWB_ONLY) {
        mMeteringPointsAWB.add(point)
      }
      return this
    }

    /**
     * Sets the [OnAutoFocusListener] to be notified when auto-focus completes. The
     * listener is called on main thread.
     */
    @NonNull
    fun setAutoFocusCallback(@NonNull listener: OnAutoFocusListener): Builder {
      mOnAutoFocusListener = listener
      return this
    }

    /**
     * Sets the [OnAutoFocusListener] to be notified when auto-focus completes. The
     * listener is called on specified [Executor].
     */
    @NonNull
    fun setAutoFocusCallback(@NonNull executor: Executor,
                             @NonNull listener: OnAutoFocusListener): Builder {
      mListenerExecutor = executor
      mOnAutoFocusListener = listener
      return this
    }

    /**
     * Sets the auto-cancel duration. After set, [CameraControl.cancelFocusAndMetering]
     * will be called in specified duration. By default, auto-cancel is enabled with 5
     * seconds duration.
     */
    @NonNull
    fun setAutoCancelDuration(duration: Long, @NonNull timeUnit: TimeUnit): Builder {
      mAutoCancelDurationInMs = timeUnit.toMillis(duration)
      return this
    }

    /**
     * Disables the auto-cancel.
     */
    @NonNull
    fun disableAutoCancel(): Builder {
      mAutoCancelDurationInMs = 0
      return this
    }

    /**
     * Builds the [FocusMeteringAction] instance.
     */
    @NonNull
    fun build(): FocusMeteringAction {
      return FocusMeteringAction(this)
    }

    companion object {

      /**
       * Creates the Builder from a [MeteringPoint] with default [MeteringMode].
       */
      @NonNull
      fun from(@NonNull meteringPoint: MeteringPoint): Builder {
        return Builder(meteringPoint)
      }

      /**
       * Creates the Builder from a [MeteringPoint] and [MeteringMode]
       */
      @NonNull
      fun from(@NonNull meteringPoint: MeteringPoint,
               @NonNull mode: MeteringMode): Builder {
        return Builder(meteringPoint, mode)
      }
    }

  }

  /**
   * Adds another [MeteringPoint] with default [MeteringMode].
   */

  companion object {
    internal val DEFAULT_METERINGMODE = MeteringMode.AF_AE_AWB
    internal val DEFAULT_AUTOCANCEL_DURATION: Long = 5000
  }
}
