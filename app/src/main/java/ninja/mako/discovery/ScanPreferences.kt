package ninja.mako.discovery

import android.content.Context

object ScanPreferences {
  private const val PREFS_NAME = "mako.discovery"
  private const val KEY_SCAN_ENABLED = "scan_enabled"

  fun isEnabled(context: Context): Boolean {
    return prefs(context).getBoolean(KEY_SCAN_ENABLED, true)
  }

  fun setEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_SCAN_ENABLED, enabled).apply()
  }

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
