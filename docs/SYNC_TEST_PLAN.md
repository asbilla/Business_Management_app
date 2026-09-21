# Wi-Fi Synchronization Test Plan & Verification Matrix

**Document Version:** 1.0.0  
**Test Suite:** `com.example.sync.WifiSyncIntegrationTest` & `com.example.data.local.MigrationTest`  
**Automated Tests:** 24 Total Unit & Integration Tests  

---

## 1. Test Verification Matrix

| Test ID | Test Name | Description | Status |
|---|---|---|---|
| M-01 | `testMigration1To2CreatesAppointmentsTable` | Validates Room migration 1 -> 2 creates appointments table | PASS |
| M-02 | `testMigration3To4AddsSyncMetadataAndTables` | Validates migration 3 -> 4 adds sync columns & tables, preserving data | PASS |
| M-03 | `testMigration4To5IndexesAndColumns` | Validates migration 4 -> 5 adds UUID indices, cursor, fingerprint, status | PASS |
| M-04 | `testDeterministicProductUuidConsistency` | Ensures product name & category hashing yields stable, case-insensitive UUIDs | PASS |
| T-01 | `test01_PairingHandshakeSuccess` | Validates full pairing flow, token storage in Keystore, and fingerprint capture | PASS |
| T-02 | `test02_PairingRejectionOnInvalidPin` | Rejects pairing when PIN does not match | PASS |
| T-03 | `test03_TlsCertificatePinningMismatch` | Blocks connection if server TLS certificate fingerprint does not match pinned value | PASS |
| T-04 | `test04_HealthCheckReachability` | Performs `/api/sync/health` pre-flight check before attempting sync | PASS |
| T-05 | `test05_CreateTransactionSync` | Ingests new transaction record via sync with proper versioning | PASS |
| T-06 | `test06_UpdateTransactionSync` | Updates existing transaction record when incoming version is higher | PASS |
| T-07 | `test07_SoftDeleteTombstoneSync` | Applies tombstone deletion; verifies active queries exclude deleted item | PASS |
| T-08 | `test08_ConflictDetectionOnConcurrentEdit` | Detects concurrent edits at same version; records conflict in database | PASS |
| T-09 | `test09_ConflictResolutionLocalWins` | Resolves conflict by retaining local copy and marking conflict resolved | PASS |
| T-10 | `test10_ConflictResolutionRemoteWins` | Resolves conflict by applying remote copy and updating local database | PASS |
| T-11 | `test11_PerRecordAckPush` | Verifies per-record ACK processing: APPLIED dequeued, RETRY kept | PASS |
| T-12 | `test12_ProductSyncDeterministicUuid` | Replicates product entity using canonical deterministic UUID | PASS |
| T-13 | `test13_AppointmentSync` | Replicates appointment record with customer & timing information | PASS |
| T-14 | `test14_BusinessProfileSync` | Replicates singleton business profile configuration | PASS |
| T-15 | `test15_UnpairDeviceFlow` | Notifies peer of unpairing, removes paired record, wipes keystore secrets | PASS |
| T-16 | `test16_IncrementalSyncWithPagination` | Paginates through cursor-based changes across multiple batches | PASS |
| T-17 | `test17_BackupAndRestoreProductDeterministicMapping` | Verifies backup restore maps products to deterministic UUIDs without collision | PASS |
| T-18 | `test18_LocalDeleteGeneratesTombstoneAndSyncQueueItem` | Deleting item locally sets `deletedAt` and creates sync queue tombstone | PASS |
| T-19 | `test19_StaleRemoteVersionIgnored` | Stale incoming records (lower version) are acknowledged without overwriting | PASS |
| T-20 | `test20_EmptySyncNoOp` | Clean sync with no changes updates timestamp and cursor cleanly without errors | PASS |

---

## 2. Manual Verification Checklist (Android ↔ Windows)

1. **Local Wi-Fi Network Setup:** Connect Android device and Windows PC to the same Wi-Fi router.
2. **Launch Windows Application:** Start Windows My Business app, open "Sync" tab, verify status displays "Listening on port 54320".
3. **Trigger Discovery on Android:** Open "Wi-Fi Synchronization" screen on Android. Verify Windows PC appears in "Discovered Windows PCs" list.
4. **Initiate Pairing:** Click "Pair" next to Windows PC. Look at 6-digit PIN on Windows screen, enter PIN on Android, click "Confirm Pairing".
5. **Verify Secure Pairing:** Verify green "Paired" badge appears on Android with "🔒 TLS SHA-256" fingerprint visible.
6. **Perform Bi-Directional Sync:**
   - Add a Transaction on Android. Click "Sync Now". Verify transaction appears on Windows.
   - Add an Appointment on Windows. Click "Sync Now" on Android. Verify appointment appears on Android.
7. **Verify Deletion (Tombstone):** Delete an item on Android. Sync. Verify item is removed from active list on Windows.
8. **Verify Disconnection Behavior:** Turn off Wi-Fi on Android. Click "Sync Now". Verify UI gracefully displays "Offline / No Wi-Fi" badge without crashing or hanging.
