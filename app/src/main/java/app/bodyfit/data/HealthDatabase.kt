package app.bodyfit.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * On-device store for all activity and hydration data. Nothing leaves the phone:
 * there is no account, no network client and no analytics in this app.
 */
@Database(
    entities = [DailyRecord::class, HourlyRecord::class, WaterEntry::class],
    version = 3,
    exportSchema = true,
)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        private const val NAME = "bodyfit.db"

        /** Adds `updatedAt` for sync. Existing rows get 0, so the first sync pushes them all. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE daily_record ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Adds the hourly breakdown table. Days recorded before this runs keep their
         * totals and simply have no hourly detail to show.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS hourly_record (
                        date TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        steps INTEGER NOT NULL DEFAULT 0,
                        moveMinutes INTEGER NOT NULL DEFAULT 0,
                        heartPoints INTEGER NOT NULL DEFAULT 0,
                        activeKcal REAL NOT NULL DEFAULT 0.0,
                        PRIMARY KEY(date, hour)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: HealthDatabase? = null

        fun get(context: Context): HealthDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HealthDatabase::class.java,
                NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
