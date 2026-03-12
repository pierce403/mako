package ninja.mako.discovery

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import ninja.mako.R
import ninja.mako.network.NetworkIdentityFactory
import ninja.mako.network.NetworkMonitor
import ninja.mako.network.NetworkSnapshot
import ninja.mako.ui.MainActivity

class ContinuousScanService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val networkMonitor by lazy { NetworkMonitor(applicationContext) }
  private val hostSweepRunner = TcpHostSweepRunner()
  private var latestSnapshot = NetworkSnapshot()
  private var activeNetworkKey: String? = null
  private var scanCycle = 0
  private var baselineComplete = false
  private val knownInterestingHosts = linkedSetOf<String>()
  private var scanLoopStarted = false

  override fun onCreate() {
    super.onCreate()
    MakoNotificationChannels.ensureCreated(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (intent?.action == ACTION_STOP) {
      ContinuousScanPreferences.setEnabled(this, false)
      ScanPreferences.setEnabled(this, false)
      stopSelf()
      return START_NOT_STICKY
    }

    startInForeground(buildStatusNotification(getString(R.string.continuous_scan_waiting)))
    if (!scanLoopStarted) {
      scanLoopStarted = true
      serviceScope.launch {
        networkMonitor.snapshots().collect { snapshot ->
          latestSnapshot = snapshot
          val identity = NetworkIdentityFactory.fromSnapshot(snapshot)
          val networkKey = identity?.networkKey
          if (networkKey != activeNetworkKey) {
            activeNetworkKey = networkKey
            scanCycle = 0
            baselineComplete = false
            knownInterestingHosts.clear()
          }
        }
      }
      serviceScope.launch {
        runScanLoop()
      }
    }

    return START_STICKY
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private suspend fun runScanLoop() {
    while (serviceScope.isActive) {
      if (!ContinuousScanPreferences.isEnabled(this) || !ScanPreferences.isEnabled(this)) {
        stopSelf()
        return
      }

      val snapshot = latestSnapshot
      val identity = NetworkIdentityFactory.fromSnapshot(snapshot)
      if (identity == null || !snapshot.connected || !snapshot.isWifi) {
        updateStatusNotification(getString(R.string.continuous_scan_waiting))
        delay(SCAN_INTERVAL_MS)
        continue
      }

      val plan = HostCandidatePlanner.buildPlan(snapshot, scanCycle = scanCycle)
      if (plan == null) {
        updateStatusNotification(getString(R.string.continuous_scan_waiting))
        delay(SCAN_INTERVAL_MS)
        continue
      }

      updateStatusNotification(
        getString(
          R.string.continuous_scan_active,
          plan.candidateHosts.size,
          plan.subnetCidr
        )
      )

      val session = hostSweepRunner.run(identity.networkKey, plan)
      val interestingHosts = session.results
        .asSequence()
        .filter(::isResponsive)
        .map { result -> result.host }
        .filter { host -> isInterestingHost(snapshot, host) }
        .toList()

      if (baselineComplete) {
        val newHosts = interestingHosts.filter { host -> knownInterestingHosts.add(host) }
        if (newHosts.isNotEmpty()) {
          notifyAboutNewHosts(newHosts, snapshot)
        }
      } else {
        knownInterestingHosts += interestingHosts
        baselineComplete = true
      }

      updateStatusNotification(
        getString(
          R.string.continuous_scan_last_result,
          session.reachableHosts,
          session.subnetCidr
        )
      )

      scanCycle += 1
      delay(SCAN_INTERVAL_MS)
    }
  }

  private fun notifyAboutNewHosts(hosts: List<String>, snapshot: NetworkSnapshot) {
    val title = if (hosts.size == 1) {
      getString(R.string.new_device_alert_title_single)
    } else {
      getString(R.string.new_device_alert_title_multiple, hosts.size)
    }
    val body = if (hosts.size == 1) {
      getString(R.string.new_device_alert_body_single, hosts.first(), snapshot.subnet ?: "current subnet")
    } else {
      getString(R.string.new_device_alert_body_multiple, hosts.joinToString(", "))
    }

    runCatching {
      NotificationManagerCompat.from(this).notify(
        ALERT_NOTIFICATION_ID,
        NotificationCompat.Builder(this, MakoNotificationChannels.NEW_DEVICE_ALERT_CHANNEL_ID)
          .setSmallIcon(R.drawable.ic_launcher_foreground)
          .setContentTitle(title)
          .setContentText(body)
          .setStyle(NotificationCompat.BigTextStyle().bigText(body))
          .setAutoCancel(true)
          .setContentIntent(contentIntent())
          .build()
      )
    }
  }

  private fun startInForeground(notification: android.app.Notification) {
    val serviceType =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
      } else {
        0
      }

    ServiceCompat.startForeground(this, ONGOING_NOTIFICATION_ID, notification, serviceType)
  }

  private fun updateStatusNotification(text: String) {
    NotificationManagerCompat.from(this).notify(
      ONGOING_NOTIFICATION_ID,
      buildStatusNotification(text)
    )
  }

  private fun buildStatusNotification(text: String): android.app.Notification {
    return NotificationCompat.Builder(this, MakoNotificationChannels.CONTINUOUS_SCAN_CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_launcher_foreground)
      .setContentTitle(getString(R.string.continuous_scan_notification_title))
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setContentIntent(contentIntent())
      .addAction(
        R.drawable.ic_filter_list,
        getString(R.string.notification_action_stop_scanning),
        stopIntent()
      )
      .build()
  }

  private fun contentIntent(): PendingIntent {
    return PendingIntent.getActivity(
      this,
      0,
      Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun stopIntent(): PendingIntent {
    return PendingIntent.getService(
      this,
      1,
      intent(this, ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
  }

  private fun isResponsive(result: HostProbeResult): Boolean {
    return result.outcome == HostProbeOutcome.CONNECTED || result.outcome == HostProbeOutcome.REFUSED
  }

  private fun isInterestingHost(snapshot: NetworkSnapshot, host: String): Boolean {
    return host != snapshot.localAddress &&
      host != snapshot.gateway &&
      !snapshot.dnsServers.contains(host)
  }

  companion object {
    private const val ACTION_START = "ninja.mako.action.START_CONTINUOUS_SCAN"
    private const val ACTION_STOP = "ninja.mako.action.STOP_CONTINUOUS_SCAN"
    private const val ONGOING_NOTIFICATION_ID = 7001
    private const val ALERT_NOTIFICATION_ID = 7002
    private const val SCAN_INTERVAL_MS = 30_000L

    fun intent(context: Context, action: String = ACTION_START): Intent {
      return Intent(context, ContinuousScanService::class.java).setAction(action)
    }

    fun stopIntent(context: Context): Intent = intent(context, ACTION_STOP)

    fun start(context: Context) {
      ContextCompat.startForegroundService(context, intent(context))
    }

    fun stop(context: Context) {
      context.stopService(Intent(context, ContinuousScanService::class.java))
    }
  }
}
