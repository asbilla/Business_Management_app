# Wi-Fi Synchronization Protocol Specification

**Document Version:** 1.0.0  
**Target Applications:** My Business Android App & My Business Windows Desktop Application  
**Protocol Version:** `1`  
**Default Port:** `54320` (TCP/HTTPS)  
**Discovery Port:** `54321` (UDP Broadcast)  
**mDNS / NSD Service Type:** `_mybusiness._tcp`  

---

## 1. Overview & Architecture

The **My Business Wi-Fi Synchronization Protocol** defines the secure, peer-to-peer, local-network communication and replication mechanism between the Android mobile application and the Windows desktop application.

### Key Tenets
1. **Offline-First**: Both Android and Windows operate autonomously against their respective local databases (Room on Android, SQLite on Windows). Local operations never block on network connectivity.
2. **Deterministic & Symmetrical**: Entity identifiers (UUIDs), versioning, tombstone deletes, and conflict resolution semantics are symmetric and platform-agnostic.
3. **Zero Public Cloud Dependency**: Synchronizes exclusively over local Wi-Fi / LAN. No external SaaS, cloud servers, or Firebase instances are required.
4. **Transport Security (TLS Pinning)**: HTTPS over TLS v1.2/1.3 with SHA-256 certificate fingerprint pinning prevents man-in-the-middle (MITM) attacks and unauthorized eavesdropping.
5. **Robust Change Tracking (Cursors)**: Synchronization uses persistent pagination cursors instead of unreliable wall-clock timestamps.
6. **Per-Record Acknowledgment (ACK)**: Queued local mutations are dequeued only upon explicit per-record server confirmation (`APPLIED` or `DUPLICATE`).

---

## 2. Discovery & Addressing

### 2.1 mDNS / NSD Service Discovery
Windows desktop instances advertise themselves over local multicast DNS:
- **Service Name:** `MyBusinessSync`
- **Service Type:** `_mybusiness._tcp`
- **Port:** `54320`
- **TXT Records:**
  - `protocolVersion=1`
  - `deviceId=WINDOWS-<MACHINE-GUID>`
  - `deviceName=Front Counter PC`
  - `fingerprint=3A:8F:7C:12:...`

Android searches for `_mybusiness._tcp` using Android `NsdManager`.

### 2.2 UDP Broadcast Fallback
For routers that filter or throttle multicast DNS:
- **Port:** `54321`
- Broadcast JSON payload:
```json
{
  "protocolVersion": 1,
  "deviceId": "WINDOWS-A1B2C3D4",
  "deviceName": "Counter Desktop",
  "port": 54320,
  "serviceType": "_mybusiness._tcp"
}
```

### 2.3 Manual IP Pairing
Users can explicitly input the host IP address (e.g., `192.168.1.150`) and port (default `54320`) into the application UI.

---

## 3. Security & Pairing Flow

### 3.1 Trust Establishment & PIN Pairing
1. The Windows PC generates a 6-digit numeric pairing PIN (valid for 5 minutes).
2. The user enters this PIN on Android.
3. Android connects over HTTPS to `POST /api/sync/pair`:
```json
{
  "deviceId": "ANDROID-D4E5F6A7",
  "deviceName": "Pixel 8 Pro",
  "pairingPin": "849201",
  "clientIp": "192.168.1.120",
  "clientPort": 54320,
  "clientTimestamp": 1710000000000,
  "nonce": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```
4. If valid, Windows returns HTTP 200 with an authentication token and its SHA-256 certificate fingerprint:
```json
{
  "success": true,
  "deviceId": "WINDOWS-A1B2C3D4",
  "deviceName": "Front Counter PC",
  "authToken": "TOKEN_SEC_948fbc2a34de1109a",
  "serverCertificateFingerprint": "8F:3A:4C:91:20:FE:44:81:92:03:CB:49:12:34:56:78:90:AB:CD:EF:12:34:56:78:90:AB:CD:EF:12:34:56:78",
  "message": "Paired successfully"
}
```
5. Android stores `authToken` inside **Android Keystore (hardware-backed AES-256-GCM)** and stores the `serverCertificateFingerprint` in Room.
6. All future HTTPS requests pin this certificate fingerprint. If a different certificate is ever presented, the connection is instantly aborted with `CERTIFICATE_MISMATCH`.

---

## 4. REST API Specification

All payloads are UTF-8 encoded JSON.

### 4.1 Health Check (Pre-Flight)
- **Method:** `POST`
- **Path:** `/api/sync/health`
- **Headers:** None required
- **Request Body:**
```json
{
  "protocolVersion": 1,
  "deviceId": "ANDROID-D4E5F6A7",
  "clientTimestamp": 1710000000000,
  "nonce": "random_string"
}
```
- **Response Body (200 OK):**
```json
{
  "protocolVersion": 1,
  "deviceId": "WINDOWS-A1B2C3D4",
  "deviceName": "Front Counter PC",
  "serverTime": 1710000000100,
  "status": "READY"
}
```

### 4.2 Pull Changes (PULL)
- **Method:** `POST`
- **Path:** `/api/sync/changes/get`
- **Headers:**
  - `Authorization: Bearer <authToken>`
  - `X-Device-Id: <deviceId>`
- **Request Body:**
```json
{
  "deviceId": "ANDROID-D4E5F6A7",
  "authToken": "TOKEN_SEC_948fbc2a34de1109a",
  "cursor": "cursor_marker_v1_00492",
  "limit": 50,
  "sinceTimestamp": 1710000000000,
  "clientTimestamp": 1710000000500
}
```
- **Response Body (200 OK):**
```json
{
  "records": [
    {
      "uuid": "550e8400-e29b-41d4-a716-446655440000",
      "entityType": "TRANSACTION",
      "operation": "CREATE",
      "version": 1,
      "updatedAt": 1710000000000,
      "deletedAt": null,
      "deviceId": "WINDOWS-A1B2C3D4",
      "dataJson": "{\"id\":0,\"uuid\":\"550e8400-e29b-41d4-a716-446655440000\",\"date\":\"2026-09-21\",\"timestamp\":1710000000000,\"type\":\"Daily Income\",\"category\":\"Hair Styling\",\"amount\":45.0,\"isSynced\":true,\"updatedAt\":1710000000000,\"deletedAt\":null,\"version\":1,\"deviceId\":\"WINDOWS-A1B2C3D4\"}"
    }
  ],
  "nextCursor": "cursor_marker_v1_00493",
  "hasMore": false,
  "serverTimestamp": 1710000000550
}
```

### 4.3 Acknowledge Applied Records (ACK)
- **Method:** `POST`
- **Path:** `/api/sync/ack`
- **Headers:**
  - `Authorization: Bearer <authToken>`
  - `X-Device-Id: <deviceId>`
- **Request Body:**
```json
{
  "deviceId": "ANDROID-D4E5F6A7",
  "authToken": "TOKEN_SEC_948fbc2a34de1109a",
  "acknowledgedUuids": [
    "550e8400-e29b-41d4-a716-446655440000"
  ],
  "clientTimestamp": 1710000000600
}
```
- **Response Body (200 OK):**
```json
{
  "success": true,
  "message": "1 record(s) acknowledged"
}
```

### 4.4 Push Changes (PUSH)
- **Method:** `POST`
- **Path:** `/api/sync/changes/push`
- **Headers:**
  - `Authorization: Bearer <authToken>`
  - `X-Device-Id: <deviceId>`
- **Request Body:**
```json
{
  "deviceId": "ANDROID-D4E5F6A7",
  "authToken": "TOKEN_SEC_948fbc2a34de1109a",
  "records": [
    {
      "uuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "entityType": "PRODUCT",
      "operation": "CREATE",
      "version": 1,
      "updatedAt": 1710000000000,
      "deletedAt": null,
      "deviceId": "ANDROID-D4E5F6A7",
      "dataJson": "{\"id\":0,\"uuid\":\"6ba7b810-9dad-11d1-80b4-00c04fd430c8\",\"name\":\"Deluxe Spa\",\"category\":\"Spa\",\"price\":60.0,\"type\":\"Service\",\"active\":true,\"createdAt\":1710000000000,\"updatedAt\":1710000000000,\"deletedAt\":null,\"version\":1,\"deviceId\":\"ANDROID-D4E5F6A7\"}"
    }
  ],
  "clientTimestamp": 1710000000700
}
```
- **Response Body (200 OK):**
```json
{
  "success": true,
  "appliedCount": 1,
  "results": [
    {
      "recordUuid": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
      "status": "APPLIED",
      "error": null,
      "resolvedVersion": 1
    }
  ],
  "conflicts": [],
  "serverTimestamp": 1710000000800,
  "error": null
}
```

### 4.5 Unpair Device
- **Method:** `POST`
- **Path:** `/api/sync/unpair`
- **Headers:**
  - `Authorization: Bearer <authToken>`
  - `X-Device-Id: <deviceId>`
- **Request Body:**
```json
{
  "deviceId": "ANDROID-D4E5F6A7",
  "authToken": "TOKEN_SEC_948fbc2a34de1109a",
  "reason": "USER_REQUESTED",
  "timestamp": 1710000000900
}
```
- **Response Body (200 OK):**
```json
{
  "success": true,
  "message": "Device successfully unpaired and token revoked"
}
```

---

## 5. Standard Error Codes

When an error occurs, the server responds with an appropriate HTTP error status (400, 401, 403, 409, 500) and body:
```json
{
  "errorCode": "AUTHENTICATION_FAILED",
  "message": "Supplied token is invalid or expired"
}
```

| Error Code | HTTP Code | Meaning |
|---|---|---|
| `AUTHENTICATION_FAILED` | 401 | Invalid, expired, or revoked authToken |
| `PAIRING_REQUIRED` | 401 | Device has never paired with this host |
| `PAIRING_EXPIRED` | 403 | Pairing PIN expired (exceeded 5 minutes) |
| `INVALID_PIN` | 401 | Provided PIN does not match |
| `PIN_RETRY_LIMIT_EXCEEDED` | 429 | Too many incorrect PIN attempts (rate limit) |
| `INVALID_CURSOR` | 400 | Supplied cursor is invalid or truncated |
| `CONFLICT` | 409 | Version conflict occurred during mutation |
| `INVALID_VERSION` | 400 | Version number decreased or is invalid |
| `DUPLICATE_RECORD` | 200/409 | Record with this UUID and version already applied |
| `SERVER_UNAVAILABLE` | 503 | Server is shutting down or in maintenance |
| `CERTIFICATE_MISMATCH` | Client-Side | Certificate SHA-256 fingerprint mismatch |

---

## 6. End of Protocol Document
