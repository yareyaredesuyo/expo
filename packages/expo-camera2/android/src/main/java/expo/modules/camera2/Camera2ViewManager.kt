package expo.modules.camera2

import android.content.Context

import org.unimodules.core.ModuleRegistry
import org.unimodules.core.ViewManager
import org.unimodules.core.interfaces.ExpoProp
import expo.modules.camera2.settings.LensFacing

class Camera2ViewManager : ViewManager<Camera2View>() {

  companion object {
    private const val TAG = "ExpoCamera2View"
  }

  private lateinit var mModuleRegistry: ModuleRegistry

  override fun getName(): String {
    return TAG
  }

  override fun createViewInstance(context: Context): Camera2View {
    return Camera2View(context)
  }

  override fun getViewManagerType(): ViewManagerType {
    return ViewManagerType.SIMPLE
  }

  override fun onCreate(moduleRegistry: ModuleRegistry) {
    mModuleRegistry = moduleRegistry
  }

  // region React View Properties

  @ExpoProp(name = "facing")
  internal fun setFacing(view: Camera2View, facing: String) {
    view.setFacing(facing = LensFacing.valueOf(facing))
  }

  // endregion
}
