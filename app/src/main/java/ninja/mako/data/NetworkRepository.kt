package ninja.mako.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import ninja.mako.network.NetworkIdentity
import ninja.mako.network.NetworkSnapshot

class NetworkRepository(context: Context) {
  private val dao = MakoDatabase.getInstance(context).networkRecordDao()

  suspend fun persistSnapshot(
    identity: NetworkIdentity,
    snapshot: NetworkSnapshot,
    isActivation: Boolean
  ): NetworkRecordEntity {
    val now = System.currentTimeMillis()
    val existing = dao.getByKey(identity.networkKey)

    val entity = if (existing == null) {
      NetworkRecordEntity(
        networkKey = identity.networkKey,
        keyMaterial = identity.keyMaterial,
        stableInputSummary = identity.stableInputSummary,
        transportLabel = snapshot.transportLabel,
        interfaceName = snapshot.interfaceName,
        localAddress = snapshot.localAddress,
        subnet = snapshot.subnet,
        gateway = snapshot.gateway,
        dnsServers = snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", "),
        domains = snapshot.domains,
        privateDnsServerName = snapshot.privateDnsServerName,
        firstSeenAt = now,
        lastSeenAt = now,
        lastConnectedAt = now,
        activationCount = 1
      )
    } else {
      existing.copy(
        keyMaterial = identity.keyMaterial,
        stableInputSummary = identity.stableInputSummary,
        transportLabel = snapshot.transportLabel,
        interfaceName = snapshot.interfaceName,
        localAddress = snapshot.localAddress,
        subnet = snapshot.subnet,
        gateway = snapshot.gateway,
        dnsServers = snapshot.dnsServers.takeIf { it.isNotEmpty() }?.joinToString(", "),
        domains = snapshot.domains,
        privateDnsServerName = snapshot.privateDnsServerName,
        lastSeenAt = now,
        lastConnectedAt = if (isActivation) now else existing.lastConnectedAt,
        activationCount = if (isActivation) existing.activationCount + 1 else existing.activationCount
      )
    }

    dao.upsert(entity)
    return entity
  }

  fun observeRecord(networkKey: String): Flow<NetworkRecordEntity?> = dao.observeByKey(networkKey)

  fun observeCount(): Flow<Int> = dao.observeCount()

  fun observeAllRecords(): Flow<List<NetworkRecordEntity>> = dao.observeAll()
}
