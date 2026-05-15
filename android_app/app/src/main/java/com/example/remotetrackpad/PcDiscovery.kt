package com.example.remotetrackpad

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

object PcDiscovery {

    private const val DISCOVERY_PORT = 8766
    private val MAGIC = "RTRACKPAD_DISCOVER".toByteArray(Charsets.UTF_8)

    private val http = OkHttpClient.Builder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(800, TimeUnit.MILLISECONDS)
        .callTimeout(1200, TimeUnit.MILLISECONDS)
        .build()

    data class Result(val host: String, val port: Int)

    /** UDP broadcast + probe common USB/Wi‑Fi addresses. */
    fun find(port: Int = 8765): Result? {
        lastSavedHost()?.let { saved ->
            if (probeHttp(saved, port)) return Result(saved, port)
        }
        udpDiscover(port)?.let { return it }
        for (host in probeCandidates()) {
            if (probeHttp(host, port)) return Result(host, port)
        }
        return null
    }

    private fun udpDiscover(port: Int): Result? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true
            socket.soTimeout = 2500

            val targets = mutableSetOf<InetAddress>()
            targets.add(InetAddress.getByName("255.255.255.255"))
            for (ip in phoneIpv4()) {
                val parts = ip.split('.')
                if (parts.size == 4) {
                    targets.add(InetAddress.getByName("${parts[0]}.${parts[1]}.${parts[2]}.255"))
                }
            }
            for (addr in targets) {
                socket.send(DatagramPacket(MAGIC, MAGIC.size, addr, DISCOVERY_PORT))
            }

            val buf = ByteArray(512)
            val deadline = System.currentTimeMillis() + 2800
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buf, buf.size)
                try {
                    socket.receive(packet)
                } catch (_: Exception) {
                    break
                }
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                parseDiscoveryJson(text, port)?.let { return it }
            }
        } catch (_: Exception) {
        } finally {
            socket?.close()
        }
        return null
    }

    private fun parseDiscoveryJson(text: String, defaultPort: Int): Result? {
        return try {
            val json = JSONObject(text)
            if (json.optString("service") != "remote_trackpad") return null
            val port = json.optInt("port", defaultPort)
            val ips = mutableListOf<String>()
            val arr = json.optJSONArray("ips")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i)?.takeIf { it.isNotBlank() }?.let { ips.add(it) }
                }
            }
            json.optString("ip").takeIf { it.isNotBlank() }?.let { ips.add(0, it) }
            val host = pickBestHost(ips) ?: ips.firstOrNull() ?: return null
            Result(host, port)
        } catch (_: Exception) {
            null
        }
    }

    private fun pickBestHost(pcIps: List<String>): String? {
        if (pcIps.isEmpty()) return null
        val phone = phoneIpv4()
        for (pip in phone) {
            val prefix = pip.substringBeforeLast('.')
            pcIps.firstOrNull { it.startsWith("$prefix.") }?.let { return it }
        }
        return pcIps.first()
    }

    private fun probeCandidates(): List<String> {
        val out = linkedSetOf<String>()
        val commonLast = listOf(1, 2, 15, 100, 129, 200, 254)
        for (ip in phoneIpv4()) {
            val prefix = ip.substringBeforeLast('.')
            if (prefix.isNotEmpty()) {
                for (last in commonLast) out.add("$prefix.$last")
            }
        }
        for (subnet in listOf("192.168.42", "192.168.43", "192.168.137")) {
            for (last in commonLast) out.add("$subnet.$last")
        }
        return out.toList()
    }

    private fun probeHttp(host: String, port: Int): Boolean {
        return try {
            val req = Request.Builder()
                .url("http://$host:$port/api/info")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                resp.isSuccessful && resp.body?.string()?.contains("remote_trackpad") == true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun phoneIpv4(): List<String> {
        val out = mutableListOf<String>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return out
            for (ni in interfaces) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        out.add(addr.hostAddress ?: continue)
                    }
                }
            }
        } catch (_: Exception) {}
        return out.distinct()
    }

    fun lastSavedHost(): String? = ConnectionPrefs.peekHost()

    fun saveHost(host: String, port: Int) = ConnectionPrefs.save(host, port)
}
