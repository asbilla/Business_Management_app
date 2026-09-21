# Android ↔ Windows Compatibility Contract

**Document Version:** 1.0.0  
**Target Systems:**  
- **Android:** API Level 26+ (Android 8.0 to Android 15), Kotlin, Jetpack Compose, Room Database v5  
- **Windows:** Windows 10/11 (64-bit), .NET 8 / C# or Node.js / Electron, SQLite 3  

---

## 1. Architectural Alignment

Both Android and Windows applications share identical data models, deterministic UUID generation rules, and synchronization state machines.

```
+---------------------------+                +---------------------------+
|    Android Application    |                |    Windows Application    |
|                           |                |                           |
|  +---------------------+  |    HTTPS/TLS   |  +---------------------+  |
|  |     Room DB v5      |  | <============> |  |      SQLite 3       |  |
|  | (Transactions,      |  |  (TLS Pinning) |  | (Transactions,      |  |
|  |  Appointments,      |  |  Port: 54320   |  |  Appointments,      |  |
|  |  Products, Sync)    |  |                |  |  Products, Sync)    |  |
|  +---------------------+  |                |  +---------------------+  |
|  |   WifiSyncEngine    |  |                |  |   SyncEngine (C#)   |  |
|  | (Cursor, Tombstone) |  |                |  | (Cursor, Tombstone) |  |
|  +---------------------+  |                |  +---------------------+  |
+---------------------------+                +---------------------------+
```

---

## 2. Shared Data Models & Canonical Types

### 2.1 Transaction Model
| Field | Android Type | SQLite / C# Type | Description / Rules |
|---|---|---|---|
| `uuid` | `String` (TEXT) | `TEXT PRIMARY KEY` | Canonical record UUID |
| `date` | `String` (TEXT) | `TEXT` | Format: `yyyy-MM-dd` |
| `timestamp` | `Long` (INTEGER) | `INTEGER` | Unix epoch millis (UTC) |
| `type` | `String` (TEXT) | `TEXT` | `Daily Income` or `Daily Expense` |
| `category` | `String` (TEXT) | `TEXT` | Expense/Income category name |
| `amount` | `Double` (REAL) | `REAL` | Currency amount (positive decimal) |
| `isSynced` | `Boolean` (INTEGER) | `INTEGER` | `1` if synced, `0` if local only |
| `updatedAt` | `Long` (INTEGER) | `INTEGER` | Unix epoch millis of last modification |
| `deletedAt` | `Long?` (INTEGER) | `INTEGER NULL` | Unix epoch millis if deleted, NULL if active |
| `version` | `Long` (INTEGER) | `INTEGER` | Monotonically increasing version counter |
| `deviceId` | `String` (TEXT) | `TEXT` | Device identifier that created or updated record |

### 2.2 Appointment Model
| Field | Android Type | SQLite / C# Type | Description / Rules |
|---|---|---|---|
| `uuid` | `String` (TEXT) | `TEXT PRIMARY KEY` | Canonical record UUID |
| `customerName` | `String` (TEXT) | `TEXT` | Customer full name |
| `customerPhone` | `String` (TEXT) | `TEXT` | Customer phone number |
| `serviceName` | `String` (TEXT) | `TEXT` | Service description / name |
| `appointmentDate` | `String` (TEXT) | `TEXT` | Format: `yyyy-MM-dd` |
| `appointmentTime` | `String` (TEXT) | `TEXT` | Format: `hh:mm a` (e.g., `10:00 AM`) |
| `durationMinutes` | `Int` (INTEGER) | `INTEGER` | Duration in minutes (default 30) |
| `status` | `String` (TEXT) | `TEXT` | `Scheduled`, `Completed`, `Cancelled` |
| `notes` | `String` (TEXT) | `TEXT` | Optional notes |
| `price` | `Double` (REAL) | `REAL` | Service fee |
| `isSynced` | `Boolean` (INTEGER) | `INTEGER` | `1` if synced, `0` if local only |
| `createdAt` | `Long` (INTEGER) | `INTEGER` | Unix epoch millis of creation |
| `updatedAt` | `Long` (INTEGER) | `INTEGER` | Unix epoch millis of last modification |
| `deletedAt` | `Long?` (INTEGER) | `INTEGER NULL` | Unix epoch millis if deleted, NULL if active |
| `version` | `Long` (INTEGER) | `INTEGER` | Monotonically increasing version counter |
| `deviceId` | `String` (TEXT) | `TEXT` | Device identifier |

### 2.3 Product Model & Deterministic UUIDs
To ensure products created independently on Android and Windows never produce duplicate records:
- **Canonical Deterministic UUID Algorithm (RFC 4122 v5 UUID or SHA-256 Name-Based UUID):**
```csharp
// C# Windows implementation:
public static string GenerateDeterministicProductUuid(string name, string category) {
    string normalized = $"{name.Trim().ToLowerInvariant()}::{category.Trim().ToLowerInvariant()}";
    byte[] hash = SHA256.HashData(Encoding.UTF8.GetBytes(normalized));
    hash[6] = (byte)((hash[6] & 0x0f) | 0x40); // Version 4/5 flag
    hash[8] = (byte)((hash[8] & 0x3f) | 0x80); // Variant 1 flag
    return new Guid(hash.AsSpan(0, 16)).ToString();
}
```
In Kotlin (Android):
```kotlin
DeterministicProductMigration.generateDeterministicProductUuid(name, category)
```
Both implementations produce the exact same UUID for `"Haircut"` and `"Salon"`.

---

## 3. Tombstone Deletion Semantics

Physical row deletion (`DELETE FROM table WHERE ...`) is **strictly prohibited** during standard operations.
1. When a user deletes a record, the application executes:
   ```sql
   UPDATE table SET deletedAt = :now, updatedAt = :now, version = version + 1 WHERE uuid = :uuid;
   ```
2. Queries displaying active records in the UI filter using `WHERE deletedAt IS NULL`.
3. The tombstone is synchronized across the network with `operation = "DELETE"`.
4. The receiving device updates its local record with `deletedAt = :deletedAt` and `version = :version`.
5. Optional tombstone purging: Tombstones older than 90 days may be purged once confirmed synced to all paired devices.

---

## 4. Conflict Resolution Rules

When an incoming record has an identical `uuid` as a local record:
1. If `remoteRecord.version > localRecord.version`:
   - Remote record is newer.
   - **Applied directly** to local database.
2. If `remoteRecord.version < localRecord.version`:
   - Remote record is stale.
   - Ignored; local record preserved; current local record queued for next push.
3. If `remoteRecord.version == localRecord.version`:
   - If payload checksum or contents are identical: Treated as duplicate; no-op (`APPLIED`).
   - If contents differ: **Concurrent modification conflict detected**.
   - Conflict recorded in `sync_conflicts` table with status `PENDING`.
   - Default resolution: **Latest `updatedAt` wins** automatically, or user prompted via UI.

---

## 5. Security Contract
1. **Self-Signed Certificate Generation on Windows**:
   - Windows generates a 2048-bit RSA or ECDSA self-signed X.509 certificate upon first run.
   - Stored in `%APPDATA%\MyBusiness\cert.pfx` or Windows Certificate Store (`CurrentUser\My`).
   - Valid for 10 years.
   - Computes SHA-256 fingerprint: `SHA256(certificate.RawData)` formatted as colon-separated uppercase hex (`8F:3A:4C:...`).
2. **PIN Entropy**:
   - Cryptographically secure 6-digit random number (`RNGCryptoServiceProvider` / `RandomNumberGenerator`).
   - Expires after 5 minutes or 3 failed attempts.
3. **Android Storage**:
   - Auth token stored in Android Keystore with AES-256-GCM encryption.
   - Certificate fingerprint stored in SQLite.

---

## 6. End of Compatibility Document
