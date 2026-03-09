package com.trueroute.app.vpn

import com.trueroute.app.model.ProxyConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.text.Charsets

data class Socks5PreflightResult(
    val success: Boolean,
    val message: String,
    val authentication: String? = null,
    val udpAssociateAddress: String? = null,
)

object Socks5Preflight {
    suspend fun run(config: ProxyConfig): Socks5PreflightResult = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.soTimeout = SOCKET_TIMEOUT_MS
                socket.connect(InetSocketAddress(config.proxyHost, config.proxyPort), SOCKET_TIMEOUT_MS)

                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())

                val method = negotiateAuthentication(config, input, output)
                val udpAssociateAddress = performUdpAssociate(input, output)

                Socks5PreflightResult(
                    success = true,
                    message = "SOCKS5 preflight passed",
                    authentication = method,
                    udpAssociateAddress = udpAssociateAddress,
                )
            }
        }.getOrElse { error ->
            Socks5PreflightResult(
                success = false,
                message = error.message ?: "SOCKS5 preflight failed",
            )
        }
    }

    private fun negotiateAuthentication(
        config: ProxyConfig,
        input: BufferedInputStream,
        output: BufferedOutputStream,
    ): String {
        val requiresAuth = config.username.isNotEmpty() || config.password.isNotEmpty()
        val methods = if (requiresAuth) byteArrayOf(0x00, 0x02) else byteArrayOf(0x00)
        output.write(byteArrayOf(0x05, methods.size.toByte()))
        output.write(methods)
        output.flush()

        val response = input.readFully(2)
        require(response[0].toInt() == 0x05) { "Proxy replied with an invalid SOCKS version" }

        return when (val method = response[1].toInt() and 0xFF) {
            0x00 -> "No authentication"
            0x02 -> {
                val username = config.username.toByteArray(Charsets.UTF_8)
                val password = config.password.toByteArray(Charsets.UTF_8)
                output.write(byteArrayOf(0x01, username.size.toByte()))
                output.write(username)
                output.write(byteArrayOf(password.size.toByte()))
                output.write(password)
                output.flush()

                val authResponse = input.readFully(2)
                require(authResponse[0].toInt() == 0x01) { "Proxy returned an invalid auth reply" }
                require(authResponse[1].toInt() == 0x00) {
                    "Proxy rejected username/password authentication"
                }
                "Username / password"
            }
            0xFF -> throw IllegalStateException("Proxy rejected all authentication methods")
            else -> throw IllegalStateException("Proxy selected unsupported auth method $method")
        }
    }

    private fun performUdpAssociate(
        input: BufferedInputStream,
        output: BufferedOutputStream,
    ): String {
        output.write(byteArrayOf(0x05, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        output.flush()

        val responseHead = input.readFully(4)
        require(responseHead[0].toInt() == 0x05) { "Proxy returned an invalid UDP Associate reply" }

        val replyCode = responseHead[1].toInt() and 0xFF
        require(replyCode == 0x00) {
            "UDP Associate failed: ${replyName(replyCode)}"
        }

        return readAddress(responseHead[3].toInt() and 0xFF, input)
    }

    private fun readAddress(addressType: Int, input: BufferedInputStream): String = when (addressType) {
        0x01 -> {
            val address = input.readFully(4).joinToString(".") { (it.toInt() and 0xFF).toString() }
            val port = input.readPort()
            "$address:$port"
        }
        0x03 -> {
            val length = input.readFully(1)[0].toInt() and 0xFF
            val host = String(input.readFully(length), Charsets.UTF_8)
            val port = input.readPort()
            "$host:$port"
        }
        0x04 -> {
            val addressBytes = input.readFully(16)
            val groups = addressBytes.asList().chunked(2).joinToString(":") { group ->
                "%02x%02x".format(group[0].toInt() and 0xFF, group[1].toInt() and 0xFF)
            }
            val port = input.readPort()
            "[$groups]:$port"
        }
        else -> throw IllegalStateException("Proxy returned unsupported address type $addressType")
    }

    private fun BufferedInputStream.readPort(): Int {
        val bytes = readFully(2)
        return ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
    }

    private fun BufferedInputStream.readFully(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(buffer, offset, length - offset)
            if (read < 0) {
                throw EOFException("Unexpected end of stream from proxy")
            }
            offset += read
        }
        return buffer
    }

    private fun replyName(code: Int): String = when (code) {
        0x01 -> "general SOCKS server failure"
        0x02 -> "connection not allowed by ruleset"
        0x03 -> "network unreachable"
        0x04 -> "host unreachable"
        0x05 -> "connection refused"
        0x06 -> "TTL expired"
        0x07 -> "command not supported"
        0x08 -> "address type not supported"
        else -> "error code $code"
    }

    private const val SOCKET_TIMEOUT_MS = 10_000
}