package ninja.mako.discovery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object MakoNotificationChannels {
  const val CONTINUOUS_SCAN_CHANNEL_ID = "continuous-scan"
  const val NEW_DEVICE_ALERT_CHANNEL_ID = "new-device-alert"

  fun ensureCreated(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val manager = context.getSystemService(NotificationManager::class.java)
    val scanChannel = NotificationChannel(
      CONTINUOUS_SCAN_CHANNEL_ID,
      "Continuous scanning",
      NotificationManager.IMPORTANCE_LOW
    ).apply {
      description = "Foreground notification while MAKO keeps scanning the current Wi-Fi network."
    }
    val alertChannel = NotificationChannel(
      NEW_DEVICE_ALERT_CHANNEL_ID,
      "New device alerts",
      NotificationManager.IMPORTANCE_DEFAULT
    ).apply {
      description = "Alerts when MAKO sees new interesting devices during continuous scans."
    }

    manager.createNotificationChannel(scanChannel)
    manager.createNotificationChannel(alertChannel)
  }
}
