package expo.modules.camera2.utils

import android.support.annotation.IntDef
import android.view.Surface

/**
 * Valid integer rotation values.
 *
 * @hide
 */
@IntDef(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)
@kotlin.annotation.Retention(AnnotationRetention.SOURCE)
annotation class RotationValue