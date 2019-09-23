package versioned.host.exp.exponent.modules.universal

import android.content.Context
import expo.modules.errorrecovery.ErrorRecoveryModule
import expo.modules.errorrecovery.RECOVERY_STORE
import host.exp.exponent.ExponentManifest
import host.exp.exponent.kernel.ExperienceId
import org.json.JSONObject
import org.unimodules.core.ModuleRegistry
import org.unimodules.interfaces.constants.ConstantsInterface

class ScopedErrorRecoveryModule(context: Context, val experienceId: ExperienceId) : ErrorRecoveryModule(context) {
  override fun onCreate(moduleRegistry: ModuleRegistry) {
    val manifestString = moduleRegistry.getModule(ConstantsInterface::class.java)?.constants?.get("manifest")
    val currentVersion = if (manifestString != null) {
      val manifest = JSONObject(manifestString as String)
      manifest.getString(ExponentManifest.MANIFEST_SDK_VERSION_KEY)
    } else {
      ""
    }

    mSharedPreferences = context.applicationContext.getSharedPreferences("$RECOVERY_STORE$currentVersion", Context.MODE_PRIVATE)
  }

  override fun setRecoveryProps(props: String) {
    mSharedPreferences.edit().putString(experienceId.get(), props).commit()
  }

  override fun consumeRecoveryProps(): String? {
    return mSharedPreferences.getString(experienceId.get(), null)?.let {
      mSharedPreferences.edit().remove(experienceId.get()).commit()
      it
    }
  }
}
