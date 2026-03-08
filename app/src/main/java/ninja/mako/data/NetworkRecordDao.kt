package ninja.mako.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkRecordDao {
  @Query("SELECT * FROM network_records WHERE networkKey = :networkKey")
  suspend fun getByKey(networkKey: String): NetworkRecordEntity?

  @Query("SELECT * FROM network_records WHERE networkKey = :networkKey")
  fun observeByKey(networkKey: String): Flow<NetworkRecordEntity?>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: NetworkRecordEntity)

  @Query("SELECT COUNT(*) FROM network_records")
  fun observeCount(): Flow<Int>
}
