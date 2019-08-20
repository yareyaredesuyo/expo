package expo.modules.camera2.camera.executors

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executor

/**
 * Helper class for retrieving an [Executor] which will post to the main thread.
 */
object MainThreadExecutor: HandlerScheduledExecutorService(Handler(Looper.getMainLooper()))