package ninja.mako.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_records")
data class NetworkRecordEntity(
  @PrimaryKey val networkKey: String,
  val keyMaterial: String,
  val stableInputSummary: String,
  val transportLabel: String,
  val interfaceName: String?,
  val localAddress: String?,
  val subnet: String?,
  val gateway: String?,
  val dnsServers: String?,
  val domains: String?,
  val privateDnsServerName: String?,
  val firstSeenAt: Long,
  val lastSeenAt: Long,
  val lastConnectedAt: Long,
  val activationCount: Int
)
