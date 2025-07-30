package io.nekohasekai.sagernet.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.matrix.roomigrant.GenerateRoomMigrations
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.fmt.KryoConverters
import io.nekohasekai.sagernet.fmt.gson.GsonConverters
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@Database(
    entities = [ProxyGroup::class, ProxyEntity::class, RuleEntity::class],
    version = 8 // Updated version for hasAttemptedCountryDetection
)
@TypeConverters(value = [KryoConverters::class, GsonConverters::class])
@GenerateRoomMigrations
abstract class SagerDatabase : RoomDatabase() {

    companion object {
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new bandwidth limit columns with default values
                database.execSQL("ALTER TABLE proxy_entities ADD COLUMN bandwidthLimit INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE proxy_entities ADD COLUMN bandwidthLimitUnit TEXT NOT NULL DEFAULT 'MB'")
                database.execSQL("ALTER TABLE proxy_entities ADD COLUMN bandwidthLimitEnabled INTEGER NOT NULL DEFAULT 0") // 0 for false
                database.execSQL("ALTER TABLE proxy_entities ADD COLUMN bandwidthAlertShown INTEGER NOT NULL DEFAULT 0") // 0 for false
            }
        }
        
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add favorite column with default value
                database.execSQL("ALTER TABLE proxy_entities ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0") // 0 for false
            }
        }
        
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add hasAttemptedCountryDetection column with default value
                database.execSQL("ALTER TABLE proxy_entities ADD COLUMN hasAttemptedCountryDetection INTEGER NOT NULL DEFAULT 0") // 0 for false
            }
        }

        @OptIn(DelicateCoroutinesApi::class)
        @Suppress("EXPERIMENTAL_API_USAGE")
        val instance by lazy {
            SagerNet.application.getDatabasePath(Key.DB_PROFILE).parentFile?.mkdirs()
            Room.databaseBuilder(SagerNet.application, SagerDatabase::class.java, Key.DB_PROFILE)
                .addMigrations(MIGRATION_5_6)
                .addMigrations(MIGRATION_6_7)
                .addMigrations(MIGRATION_7_8)
                .allowMainThreadQueries()
                .enableMultiInstanceInvalidation()
                .setQueryExecutor { GlobalScope.launch { it.run() } }
                .build()
        }

        val groupDao get() = instance.groupDao()
        val proxyDao get() = instance.proxyDao()
        val rulesDao get() = instance.rulesDao()

    }

    abstract fun groupDao(): ProxyGroup.Dao
    abstract fun proxyDao(): ProxyEntity.Dao
    abstract fun rulesDao(): RuleEntity.Dao

}
