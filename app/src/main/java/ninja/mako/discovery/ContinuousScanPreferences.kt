package ninja.mako.discovery

import android.content.Context

object ContinuousScanPreferences {
  private const val PREFS_NAME = "mako.discovery"
  private const val KEY_CONTINUOUS_SCANNING = "continuous_scanning"

  fun isEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_CONTINUOUS_SCANNING, false)
  }

  fun setEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_CONTINUOUS_SCANNING, enabled).apply()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
