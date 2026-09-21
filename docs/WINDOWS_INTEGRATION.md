# Windows Integration Guide

**Document Version:** 1.0.0  
**Audience:** Windows Desktop Application Developers (.NET / C# / WPF / WinUI / Electron)  
**Target:** Building the Windows counterpart for the My Business Sync Engine  

---

## 1. Overview

This document guides Windows developers in implementing the sync server and client endpoints on Windows to communicate seamlessly with the My Business Android application.

Windows acts as:
1. **HTTPS Server**: Listening on port `54320` to accept pairing, pull, push, ack, and health requests from Android phones.
2. **mDNS Advertiser**: Advertising `_mybusiness._tcp` on local network.
3. **Local SQLite Database**: Housing `transactions`, `appointments`, `products`, `sync_queue`, and `paired_devices`.

---

## 2. Windows HTTPS Server Setup (C# / .NET 8)

### 2.1 Kestrel Configuration with Self-Signed Certificate
```csharp
using System.Security.Cryptography.X509Certificates;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.Extensions.Hosting;

var builder = WebApplication.CreateBuilder(args);

// Load or generate self-signed certificate
X509Certificate2 serverCert = CertificateManager.GetOrCreateServerCertificate();
string certFingerprint = CertificateManager.GetSha256Fingerprint(serverCert);

builder.WebHost.ConfigureKestrel(options => {
    options.ListenAnyIP(54320, listenOptions => {
        listenOptions.UseHttps(serverCert);
    });
});

var app = builder.Build();

// Endpoints:
app.MapPost("/api/sync/health", SyncEndpoints.HandleHealth);
app.MapPost("/api/sync/pair", SyncEndpoints.HandlePair);
app.MapPost("/api/sync/changes/get", SyncEndpoints.HandleGetChanges);
app.MapPost("/api/sync/changes/push", SyncEndpoints.HandlePushChanges);
app.MapPost("/api/sync/ack", SyncEndpoints.HandleAck);
app.MapPost("/api/sync/unpair", SyncEndpoints.HandleUnpair);

app.Run();
```

### 2.2 Certificate Generation & Fingerprint
```csharp
public static class CertificateManager {
    public static X509Certificate2 GetOrCreateServerCertificate() {
        string certPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MyBusiness", "server.pfx");
        if (File.Exists(certPath)) {
            return new X509Certificate2(certPath, "LocalSyncSecret123!", X509KeyStorageFlags.Exportable);
        }

        using var rsa = RSA.Create(2048);
        var certRequest = new CertificateRequest("CN=MyBusinessSyncServer", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        certRequest.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        certRequest.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
        certRequest.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") }, false));

        var cert = certRequest.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(10));
        Directory.CreateDirectory(Path.GetDirectoryName(certPath)!);
        File.WriteAllBytes(certPath, cert.Export(X509ContentType.Pfx, "LocalSyncSecret123!"));
        return cert;
    }

    public static string GetSha256Fingerprint(X509Certificate2 cert) {
        byte[] hash = SHA256.HashData(cert.RawData);
        return string.Join(":", hash.Select(b => b.ToString("X2")));
    }
}
```

---

## 3. Pairing Controller Implementation
```csharp
public static IResult HandlePair(SyncPairRequest request, HttpContext context) {
    // 1. Verify pairing PIN against active PIN in memory
    if (!PairingManager.ValidatePin(request.PairingPin)) {
        return Results.Json(new { errorCode = "INVALID_PIN", message = "Invalid PIN" }, statusCode: 401);
    }

    // 2. Generate secure random auth token
    string authToken = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));

    // 3. Save paired device in Windows database
    DeviceDao.SavePairedDevice(new PairedDevice {
        DeviceId = request.DeviceId,
        DeviceName = request.DeviceName,
        IpAddress = request.ClientIp ?? context.Connection.RemoteIpAddress?.ToString(),
        AuthToken = authToken,
        PairedAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
    });

    // 4. Return token & Windows certificate fingerprint
    return Results.Ok(new SyncPairResponse {
        Success = true,
        DeviceId = "WINDOWS-" + Environment.MachineName,
        DeviceName = Environment.MachineName,
        AuthToken = authToken,
        ServerCertificateFingerprint = CertificateManager.GetSha256Fingerprint(serverCert),
        Message = "Paired successfully"
    });
}
```

---

## 4. End of Windows Integration Guide
