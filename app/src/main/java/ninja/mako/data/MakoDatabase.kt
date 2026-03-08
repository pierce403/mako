package ninja.mako.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [NetworkRecordEntity::class],
  version = 1,
  exportSchema = false
)
abstract class MakoDatabase : RoomDatabase() {
  abstract fun networkRecordDao(): NetworkRecordDao

  companion object {
    @Volatile
    private var instance: MakoDatabase? = null

    fun getInstance(context: Context): MakoDatabase {
      return instance ?: synchronized(this) {
        instance ?: Room.databaseBuilder(
          context.applicationContext,
          MakoDatabase::class.java,
          "mako.db"
        ).build().also { created ->
          instance = created
        }
      }
    }
  }
}
