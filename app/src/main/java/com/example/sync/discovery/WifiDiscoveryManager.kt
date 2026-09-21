package com.example.sync.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.example.sync.model.SyncProtocol
import com.example.sync.security.DeviceIdManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

data class DiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val discoveryMethod: String = "NSD", // "NSD" or "UDP"
    val lastSeen: Long = System.currentTimeMillis()
)

class WifiDiscoveryManager(private val context: Context) {

    private val TAG = "WifiDiscoveryManager"
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var udpJob: Job? = null

    fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true

        acquireMulticastLock()
        startNsdDiscovery()
        startUdpBroadcastDiscovery()
    }

    fun stopDiscovery() {
        _isDiscovering.value = false
        stopNsdDiscovery()
        stopUdpDiscovery()
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("MyBusinessSyncMulticastLock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MulticastLock: ${e.message}")
        }
    }

    private fun startNsdDiscovery() {
        if (nsdManager == null) return
        try {
            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {
                    Log.d(TAG, "NSD discovery started for $regType")
                }

                override fun onServiceFound(service: NsdServiceInfo) {
                    Log.d(TAG, "NSD service found: ${service.serviceName}, type: ${service.serviceType}")
                    if (service.serviceType.contains("mybusiness") || service.serviceName.contains("MyBusiness")) {
                        try {
                            nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                                    Log.e(TAG, "NSD resolve failed: $errorCode")
                                }

                                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                                    val host = serviceInfo.host?.hostAddress ?: return
                                    val port = serviceInfo.port
                                    val name = serviceInfo.serviceName
                                    val deviceId = "WINDOWS-" + name.replace(" ", "-").takeLast(8).uppercase()

                                    addOrUpdateDevice(
                                        DiscoveredDevice(
                                            deviceId = deviceId,
                                            deviceName = name,
                                            host = host,
                                            port = port,
                                            discoveryMethod = "NSD"
                                        )
                                    )
                                }
                            })
                        } catch (e: Exception) {
                            Log.w(TAG, "Exception resolving NSD service: ${e.message}")
                        }
                    }
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    Log.d(TAG, "NSD service lost: ${service.serviceName}")
                }

                override fun onDiscoveryStopped(serviceType: String) {
                    Log.d(TAG, "NSD discovery stopped: $serviceType")
                }

                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "NSD start discovery failed: $errorCode")
                    try {
                        nsdManager.stopServiceDiscovery(this)
                    } catch (_: Exception) {}
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                    Log.e(TAG, "NSD stop discovery failed: $errorCode")
                }
            }

            nsdManager.discoverServices(
                SyncProtocol.SERVICE_TYPE,
                NsdManager.PROTOCOL_DNS_SD,
                discoveryListener
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting NSD discovery: ${e.message}")
        }
    }

    private fun stopNsdDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager?.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping NSD: ${e.message}")
        } finally {
            discoveryListener = null
        }
    }

    private fun startUdpBroadcastDiscovery() {
        udpJob?.cancel()
        udpJob = scope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 2000
                }

                val myDeviceId = DeviceIdManager.getDeviceId(context)
                val discoverMsg = JSONObject().apply {
                    put("type", "DISCOVER_WINDOWS")
                    put("protocol", SyncProtocol.PROTOCOL_VERSION)
                    put("clientDeviceId", myDeviceId)
                    put("clientDeviceName", DeviceIdManager.getDeviceName(context))
                }.toString().toByteArray()

                // Broadcast to standard 255.255.255.255
                val packet = DatagramPacket(
                    discoverMsg,
                    discoverMsg.size,
                    InetAddress.getByName("255.255.255.255"),
                    SyncProtocol.UDP_BROADCAST_PORT
                )

                while (isActive && _isDiscovering.value) {
                    try {
                        socket.send(packet)
                    } catch (e: Exception) {
                        Log.w(TAG, "UDP broadcast send error: ${e.message}")
                    }

                    // Receive responses
                    val receiveBuf = ByteArray(2048)
                    val recvPacket = DatagramPacket(receiveBuf, receiveBuf.size)
                    val startTime = System.currentTimeMillis()

                    while (System.currentTimeMillis() - startTime < 3000 && isActive) {
                        try {
                            socket.receive(recvPacket)
                            val respStr = String(recvPacket.data, 0, recvPacket.length)
                            val json = JSONObject(respStr)
                            val type = json.optString("type")
                            if (type == "DISCOVER_REPLY" || type == "WINDOWS_SYNC_ADVERTISEMENT") {
                                val devId = json.optString("deviceId", "WINDOWS-UNKNOWN")
                                val devName = json.optString("deviceName", "Windows PC")
                                val port = json.optInt("port", SyncProtocol.DEFAULT_PORT)
                                val host = recvPacket.address.hostAddress ?: ""

                                if (host.isNotBlank()) {
                                    addOrUpdateDevice(
                                        DiscoveredDevice(
                                            deviceId = devId,
                                            deviceName = devName,
                                            host = host,
                                            port = port,
                                            discoveryMethod = "UDP"
                                        )
                                    )
                                }
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            break
                        } catch (e: Exception) {
                            Log.w(TAG, "UDP packet parse error: ${e.message}")
                        }
                    }

                    delay(3000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP discovery error: ${e.message}")
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopUdpDiscovery() {
        udpJob?.cancel()
        udpJob = null
    }

    private fun addOrUpdateDevice(device: DiscoveredDevice) {
        val current = _discoveredDevices.value.toMutableList()
        val index = current.indexOfFirst { it.deviceId == device.deviceId || (it.host == device.host && it.port == device.port) }
        if (index >= 0) {
            current[index] = device
        } else {
            current.add(device)
        }
        _discoveredDevices.value = current
    }

    fun clearDiscovered() {
        _discoveredDevices.value = emptyList()
    }
}
