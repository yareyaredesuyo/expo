package expo.modules.camera2

import android.content.Context
import expo.modules.camera2.camera.ExpoCamera

import org.unimodules.core.ExportedModule
import org.unimodules.core.ModuleRegistry

class Camera2ViewManagerModule(context: Context) : ExportedModule(context) {

  companion object {
    private const val TAG = "ExpoCamera2ViewManager"
  }

  private lateinit var mModuleRegistry: ModuleRegistry

  override fun getName(): String {
    return TAG
  }

  override fun onCreate(moduleRegistry: ModuleRegistry) {
    mModuleRegistry = moduleRegistry
  }
}
