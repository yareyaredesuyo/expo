package expo.modules.camera2.guava.executors

import java.util.concurrent.Executor

/**
 * An [Executor] that runs each task in the thread that invokes [Executor.execute].
 *
 * Copied and adapted from [Guava](https://github.com/google/guava/blob/master/guava/src/com/google/common/util/concurrent/DirectExecutor.java)
 */
object DirectExecutor : Executor {
  override fun execute(command: Runnable) {
    command.run()
  }
}