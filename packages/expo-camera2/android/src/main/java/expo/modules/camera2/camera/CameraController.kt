package expo.modules.camera2.camera

import java.util.concurrent.Executor
import java.util.concurrent.ScheduledExecutorService

class CameraController internal constructor(
    private val scheduler: ScheduledExecutorService,
    private val executor: Executor
) {

}