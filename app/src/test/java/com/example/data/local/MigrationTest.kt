package com.example.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.DeterministicProductMigration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun createInMemoryDb(version: Int, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(callback)
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        return helper.writableDatabase
    }

    @Test
    fun testMigration1To2CreatesAppointmentsTable() {
        var db: SupportSQLiteDatabase? = null
        try {
            val callback = object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            uuid TEXT NOT NULL,
                            date TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            category TEXT NOT NULL,
                            amount REAL NOT NULL,
                            isSynced INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }

            db = createInMemoryDb(1, callback)
            // Apply MIGRATION_1_2
            AppDatabase.MIGRATION_1_2.migrate(db)

            // Verify appointments table exists
            val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='appointments'")
            assertTrue(cursor.moveToFirst())
            assertEquals("appointments", cursor.getString(0))
            cursor.close()
        } finally {
            db?.close()
        }
    }

    @Test
    fun testMigration3To4AddsSyncMetadataAndTables() {
        var db: SupportSQLiteDatabase? = null
        try {
            val callback = object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            uuid TEXT NOT NULL,
                            date TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            category TEXT NOT NULL,
                            amount REAL NOT NULL,
                            isSynced INTEGER NOT NULL DEFAULT 0
                        )
                        """.trimIndent()
                    )
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

                    // Insert test legacy data
                    db.execSQL("INSERT INTO transactions (uuid, date, timestamp, type, category, amount, isSynced) VALUES ('tx-123', '2026-09-20', 1710000000000, 'Daily Income', 'Spa', 50.0, 0)")
                    db.execSQL("INSERT INTO appointments (uuid, customerName, customerPhone, serviceName, appointmentDate, appointmentTime, createdAt) VALUES ('apt-123', 'John Doe', '5551234', 'Massage', '2026-09-21', '10:00 AM', 1710000000000)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }

            db = createInMemoryDb(3, callback)
            // Apply MIGRATION_3_4
            AppDatabase.MIGRATION_3_4.migrate(db)

            // Verify tables exist
            val tables = listOf("products", "sync_queue", "paired_devices", "sync_conflicts")
            for (tableName in tables) {
                val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$tableName'")
                assertTrue("Table $tableName should exist after migration 3->4", cursor.moveToFirst())
                cursor.close()
            }

            // Verify data preservation and default columns
            val txCursor = db.query("SELECT uuid, updatedAt, version, deviceId, amount FROM transactions WHERE uuid='tx-123'")
            assertTrue(txCursor.moveToFirst())
            assertEquals("tx-123", txCursor.getString(0))
            assertEquals(1710000000000L, txCursor.getLong(1)) // UpdatedAt initialized to timestamp
            assertEquals(1L, txCursor.getLong(2)) // default version 1
            assertEquals(50.0, txCursor.getDouble(4), 0.001)
            txCursor.close()

            val aptCursor = db.query("SELECT uuid, updatedAt, version, customerName FROM appointments WHERE uuid='apt-123'")
            assertTrue(aptCursor.moveToFirst())
            assertEquals("apt-123", aptCursor.getString(0))
            assertEquals(1710000000000L, aptCursor.getLong(1)) // UpdatedAt initialized to createdAt
            assertEquals(1L, aptCursor.getLong(2))
            assertEquals("John Doe", aptCursor.getString(3))
            aptCursor.close()
        } finally {
            db?.close()
        }
    }

    @Test
    fun testMigration4To5IndexesAndColumns() {
        var db: SupportSQLiteDatabase? = null
        try {
            val callback = object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v4 schema
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS transactions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            uuid TEXT NOT NULL,
                            date TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            type TEXT NOT NULL,
                            category TEXT NOT NULL,
                            amount REAL NOT NULL,
                            isSynced INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0,
                            deletedAt INTEGER DEFAULT NULL,
                            version INTEGER NOT NULL DEFAULT 1,
                            deviceId TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
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
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0,
                            deletedAt INTEGER DEFAULT NULL,
                            version INTEGER NOT NULL DEFAULT 1,
                            deviceId TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
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

                    // Seed test v4 data
                    db.execSQL("INSERT INTO transactions (uuid, date, timestamp, type, category, amount, version) VALUES ('tx-v4', '2026-09-20', 1710000000000, 'Daily Income', 'Facial', 65.0, 2)")
                    db.execSQL("INSERT INTO paired_devices (deviceId, deviceName, ipAddress, port, pairingSecret, pairedAt, lastSyncAt, status) VALUES ('WIN-001', 'Desktop PC', '192.168.1.50', 54320, 'sec_123', 1710000000000, 0, 'PAIRED')")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            }

            db = createInMemoryDb(4, callback)
            // Apply MIGRATION_4_5
            AppDatabase.MIGRATION_4_5.migrate(db)

            // Verify unique index on transactions uuid
            val indexCursor = db.query("PRAGMA index_list('transactions')")
            var hasUniqueUuidIndex = false
            while (indexCursor.moveToNext()) {
                val name = indexCursor.getString(indexCursor.getColumnIndexOrThrow("name"))
                val unique = indexCursor.getInt(indexCursor.getColumnIndexOrThrow("unique"))
                if (name == "index_transactions_uuid" && unique == 1) {
                    hasUniqueUuidIndex = true
                }
            }
            indexCursor.close()
            assertTrue("index_transactions_uuid unique index must exist", hasUniqueUuidIndex)

            // Verify new columns in paired_devices
            val pdCursor = db.query("SELECT deviceId, certificateFingerprint, lastSyncCursor, protocolVersion FROM paired_devices WHERE deviceId='WIN-001'")
            assertTrue(pdCursor.moveToFirst())
            assertEquals("WIN-001", pdCursor.getString(0))
            assertEquals("", pdCursor.getString(1)) // default empty
            assertEquals("", pdCursor.getString(2)) // default empty
            assertEquals(1, pdCursor.getInt(3)) // default protocolVersion 1
            pdCursor.close()

            // Verify new columns in sync_queue
            db.execSQL("INSERT INTO sync_queue (recordUuid, entityType, operation, payload, createdAt, retryCount, status) VALUES ('q-1', 'TRANSACTION', 'CREATE', '{}', 1710000000000, 0, 'PENDING')")
            val sqCursor = db.query("SELECT recordUuid, version, lastAttemptAt FROM sync_queue WHERE recordUuid='q-1'")
            assertTrue(sqCursor.moveToFirst())
            assertEquals(1L, sqCursor.getLong(1))
            assertEquals(0L, sqCursor.getLong(2))
            sqCursor.close()

            // Verify new column in sync_conflicts
            db.execSQL("INSERT INTO sync_conflicts (recordUuid, entityType, localVersion, remoteVersion, localData, remoteData, resolved, createdAt, resolutionStrategy) VALUES ('c-1', 'PRODUCT', 1, 2, '{}', '{}', 0, 1710000000000, 'UNRESOLVED')")
            val scCursor = db.query("SELECT recordUuid, conflictStatus FROM sync_conflicts WHERE recordUuid='c-1'")
            assertTrue(scCursor.moveToFirst())
            assertEquals("PENDING", scCursor.getString(1))
            scCursor.close()
        } finally {
            db?.close()
        }
    }

    @Test
    fun testDeterministicProductUuidConsistency() {
        val uuid1 = DeterministicProductMigration.generateDeterministicProductUuid("Haircut Deluxe", "Salon")
        val uuid2 = DeterministicProductMigration.generateDeterministicProductUuid("Haircut Deluxe", "Salon")
        val uuid3 = DeterministicProductMigration.generateDeterministicProductUuid("haircut deluxe ", " SALON ")

        assertEquals("Same product name and category must generate identical UUIDs", uuid1, uuid2)
        assertEquals("Case and whitespace normalization must produce identical UUIDs", uuid1, uuid3)

        val differentUuid = DeterministicProductMigration.generateDeterministicProductUuid("Shampoo", "Retail")
        assertNotEquals(uuid1, differentUuid)
    }
}
