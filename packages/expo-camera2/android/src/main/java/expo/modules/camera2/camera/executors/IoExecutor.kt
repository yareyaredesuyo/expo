package expo.modules.camera2.camera.executors

import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Executor which should be used for I/O tasks.
 */
object IoExecutor : Executor {
  private val ioService = Executors.newFixedThreadPool(
      2,
      object: ThreadFactory {
        private val THREAD_NAME_STEM = "Camera-io_thread_%d"

        private val threadId = AtomicInteger(0)

        override fun newThread(r: Runnable): Thread {
          return Thread(r).apply {
            name = String.format(THREAD_NAME_STEM, threadId.getAndIncrement())
          }
        }
      }
  )

  override fun execute(command: Runnable) {
    ioService.execute(command)
  }
}