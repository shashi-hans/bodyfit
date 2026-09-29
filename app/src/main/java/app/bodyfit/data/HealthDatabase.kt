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
    entities = [DailyRecord::class, HourlyRecord::class, WaterEntry::class, ExerciseSession::class],
    version = 5,
    exportSchema = true,
)
abstract class HealthDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        private const val NAME = "bodyfit.db"

        /** Adds `updatedAt`. Rows written before it get 0, which reads as "never changed". */
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

        /**
         * Adds timed exercise sessions. Days recorded before this keep their totals; they
         * simply have no session behind them to explain the figures.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercise_session (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        date TEXT NOT NULL,
                        type TEXT NOT NULL,
                        startedAt INTEGER NOT NULL,
                        seconds INTEGER NOT NULL,
                        kcal REAL NOT NULL,
                        heartPoints INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_session_date ON exercise_session(date)")
            }
        }

        /**
         * Adds the distance a session covered. Sessions logged before this keep a 0, which
         * the list reads as "not measured" and prints nothing for.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_session ADD COLUMN metres REAL NOT NULL DEFAULT 0.0")
            }
        }

        @Volatile
        private var instance: HealthDatabase? = null

        fun get(context: Context): HealthDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                HealthDatabase::class.java,
                NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { instance = it }
        }
    }
}
