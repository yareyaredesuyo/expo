package expo.modules.camera2.androidx.concurrent

import java.util.concurrent.Executor

/**
 * An [Executor] that runs each task in the thread that invokes [ execute][Executor.execute].
 *
 * Copied and adapted from [androidx.concurrent:concurrent-futures]
 */
internal enum class DirectExecutor : Executor {
  INSTANCE;

  override fun execute(command: Runnable) {
    command.run()
  }

  override fun toString(): String {
    return "DirectExecutor"
  }
}