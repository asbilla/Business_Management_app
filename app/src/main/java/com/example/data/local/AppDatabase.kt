package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        AppointmentEntity::class,
        ProductEntity::class,
        SyncQueueEntity::class,
        PairedDeviceEntity::class,
        SyncConflictEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun productDao(): ProductDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun pairedDeviceDao(): PairedDeviceDao
    abstract fun syncConflictDao(): SyncConflictDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS appointments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uuid TEXT NOT NULL,
                        customerName TEXT NOT NULL,
                        customerPhone TEXT NOT NULL,
                        serviceName TEXT NOT NULL,
                        appointmentDate TEXT NOT NULL,
                        appointmentTime TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL DEFAULT 30,
                        status TEXT NOT NULL DEFAULT 'Scheduled',
                        notes TEXT NOT NULL DEFAULT '',
                        price REAL NOT NULL DEFAULT 0.0,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure appointments table schema matches v3
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS appointments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uuid TEXT NOT NULL,
                        customerName TEXT NOT NULL,
                        customerPhone TEXT NOT NULL,
                        serviceName TEXT NOT NULL,
                        appointmentDate TEXT NOT NULL,
                        appointmentTime TEXT NOT NULL,
                        durationMinutes INTEGER NOT NULL DEFAULT 30,
                        status TEXT NOT NULL DEFAULT 'Scheduled',
                        notes TEXT NOT NULL DEFAULT '',
                        price REAL NOT NULL DEFAULT 0.0,
                        isSynced INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Alter transactions table with sync metadata
                db.execSQL("ALTER TABLE transactions ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE transactions ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                // Populate updatedAt from existing timestamp
                db.execSQL("UPDATE transactions SET updatedAt = timestamp WHERE updatedAt = 0")

                // 2. Alter appointments table with sync metadata
                db.execSQL("ALTER TABLE appointments ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE appointments ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE appointments ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE appointments ADD COLUMN deviceId TEXT NOT NULL DEFAULT ''")
                // Populate updatedAt from existing createdAt
                db.execSQL("UPDATE appointments SET updatedAt = createdAt WHERE updatedAt = 0")

                // 3. Create products table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        uuid TEXT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        price REAL NOT NULL,
                        type TEXT NOT NULL,
                        active INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        deletedAt INTEGER DEFAULT NULL,
                        version INTEGER NOT NULL,
                        deviceId TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // 4. Create sync_queue table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_queue (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordUuid TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        retryCount INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        lastError TEXT
                    )
                    """.trimIndent()
                )

                // 5. Create paired_devices table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS paired_devices (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        deviceId TEXT NOT NULL,
                        deviceName TEXT NOT NULL,
                        ipAddress TEXT NOT NULL,
                        port INTEGER NOT NULL,
                        pairingSecret TEXT NOT NULL,
                        pairedAt INTEGER NOT NULL,
                        lastSyncAt INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                // 6. Create sync_conflicts table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sync_conflicts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recordUuid TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        localVersion INTEGER NOT NULL,
                        remoteVersion INTEGER NOT NULL,
                        localData TEXT NOT NULL,
                        remoteData TEXT NOT NULL,
                        resolved INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        resolutionStrategy TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "daily_reporting_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
