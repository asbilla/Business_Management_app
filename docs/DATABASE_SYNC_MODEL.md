# Database Synchronization Model

**Document Version:** 1.0.0  
**Schema Version:** `5` (AppDatabase)  
**Database Engine:** Room (Android SQLite) / SQLite 3 (Windows Desktop)  

---

## 1. Schema Design for Offline-First Synchronization

The database schema is engineered specifically for bi-directional peer-to-peer synchronization with:
- **Universal Primary Identifiers (`uuid`)**: Stable RFC 4122 strings.
- **Tombstones (`deletedAt`)**: Explicit deletion tracking.
- **Monotonic Logical Clock (`version`)**: High-water mark versioning per entity.
- **Origin Tracking (`deviceId`)**: Source attribution to prevent echo loops.
- **Audit Timestamps (`createdAt`, `updatedAt`)**: Wall-clock timestamps.
- **Outbox Queue (`sync_queue`)**: Ordered local changes awaiting replication.
- **Inbound Tracking (`lastSyncCursor`)**: Progress bookmark per paired peer.

---

## 2. Table Definitions & SQL DDL

### 2.1 Table: `transactions`
```sql
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
);

CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_uuid ON transactions(uuid);
CREATE INDEX IF NOT EXISTS index_transactions_updatedAt ON transactions(updatedAt);
CREATE INDEX IF NOT EXISTS index_transactions_deletedAt ON transactions(deletedAt);
CREATE INDEX IF NOT EXISTS index_transactions_date ON transactions(date);
```

### 2.2 Table: `appointments`
```sql
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
);

CREATE UNIQUE INDEX IF NOT EXISTS index_appointments_uuid ON appointments(uuid);
CREATE INDEX IF NOT EXISTS index_appointments_updatedAt ON appointments(updatedAt);
CREATE INDEX IF NOT EXISTS index_appointments_deletedAt ON appointments(deletedAt);
CREATE INDEX IF NOT EXISTS index_appointments_appointmentDate ON appointments(appointmentDate);
```

### 2.3 Table: `products`
```sql
CREATE TABLE IF NOT EXISTS products (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    uuid TEXT NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL,
    price REAL NOT NULL,
    type TEXT NOT NULL DEFAULT 'Service',
    active INTEGER NOT NULL DEFAULT 1,
    createdAt INTEGER NOT NULL DEFAULT 0,
    updatedAt INTEGER NOT NULL DEFAULT 0,
    deletedAt INTEGER DEFAULT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    deviceId TEXT NOT NULL DEFAULT ''
);

CREATE UNIQUE INDEX IF NOT EXISTS index_products_uuid ON products(uuid);
CREATE INDEX IF NOT EXISTS index_products_name ON products(name);
CREATE INDEX IF NOT EXISTS index_products_updatedAt ON products(updatedAt);
CREATE INDEX IF NOT EXISTS index_products_deletedAt ON products(deletedAt);
```

### 2.4 Table: `sync_queue` (Outbox Queue)
```sql
CREATE TABLE IF NOT EXISTS sync_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    recordUuid TEXT NOT NULL,
    entityType TEXT NOT NULL,
    operation TEXT NOT NULL,
    payload TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    createdAt INTEGER NOT NULL DEFAULT 0,
    retryCount INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PENDING',
    lastError TEXT DEFAULT NULL,
    lastAttemptAt INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS index_sync_queue_status_createdAt ON sync_queue(status, createdAt);
CREATE INDEX IF NOT EXISTS index_sync_queue_recordUuid ON sync_queue(recordUuid);
```

### 2.5 Table: `paired_devices` (Peer Registration & Cursors)
```sql
CREATE TABLE IF NOT EXISTS paired_devices (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    deviceId TEXT NOT NULL,
    deviceName TEXT NOT NULL,
    ipAddress TEXT NOT NULL,
    port INTEGER NOT NULL DEFAULT 54320,
    pairingSecret TEXT NOT NULL,
    pairedAt INTEGER NOT NULL DEFAULT 0,
    lastSyncAt INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'PAIRED',
    certificateFingerprint TEXT NOT NULL DEFAULT '',
    lastSyncCursor TEXT NOT NULL DEFAULT '',
    protocolVersion INTEGER NOT NULL DEFAULT 1,
    lastSeen INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS index_paired_devices_deviceId ON paired_devices(deviceId);
```

### 2.6 Table: `sync_conflicts` (Conflict Tracking)
```sql
CREATE TABLE IF NOT EXISTS sync_conflicts (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    recordUuid TEXT NOT NULL,
    entityType TEXT NOT NULL,
    localVersion INTEGER NOT NULL,
    remoteVersion INTEGER NOT NULL,
    localData TEXT NOT NULL,
    remoteData TEXT NOT NULL,
    resolved INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL DEFAULT 0,
    resolutionStrategy TEXT NOT NULL DEFAULT 'UNRESOLVED',
    conflictStatus TEXT NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX IF NOT EXISTS index_sync_conflicts_resolved ON sync_conflicts(resolved);
CREATE INDEX IF NOT EXISTS index_sync_conflicts_recordUuid ON sync_conflicts(recordUuid);
```

---

## 3. Cursor Progression Architecture

1. **Stateful Cursor Tokens**:
   - The cursor string is opaque to the client (e.g., `cur_v1_1710000000000_00042` or sequential log sequence number `lsn:42`).
   - Android sends `cursor: pairedDevice.lastSyncCursor` during `POST /api/sync/changes/get`.
   - Windows returns records modified *after* that cursor, plus `nextCursor`.
2. **Two-Phase Commit Guarantee**:
   - Step A: Android receives change records and applies them into Room in a single SQLite transaction.
   - Step B: Android issues `POST /api/sync/ack` with the list of applied UUIDs.
   - Step C: Once ACK succeeds (or in the same batch), Android persists `pairedDevice.lastSyncCursor = nextCursor`.
   - If the network disconnects before Step C, Android will re-request from the previous cursor on the next sync. Duplicate incoming records will be safely detected and ignored via idempotency checks.

---

## 4. End of Database Sync Model Document
