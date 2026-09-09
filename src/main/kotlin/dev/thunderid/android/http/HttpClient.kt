// Copyright 2026 The ThunderID Authors
// SPDX-License-Identifier: Apache-2.0

package dev.thunderid.android.http

import dev.thunderid.android.IAMException
import dev.thunderid.android.ThunderIDErrorCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Performs HTTP requests against the ThunderID server. Enforces HTTPS (spec §11.5).
 */
internal class HttpClient(
    private val baseUrl: String,
    private val allowInsecureConnections: Boolean = false,
    private var accessTokenProvider: (suspend () -> String)? = null,
) {
    fun setAccessTokenProvider(provider: suspend () -> String) {
        accessTokenProvider = provider
    }

    suspend inline fun <reified T : Any> get(
        path: String,
        requiresAuth: Boolean = true,
    ): T = request("GET", path, null, requiresAuth)

    suspend inline fun <reified T : Any> post(
        path: String,
        body: Map<String, Any>,
        requiresAuth: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): T = request("POST", path, body, requiresAuth, headers)

    suspend inline fun <reified T : Any> put(
        path: String,
        body: Map<String, Any>,
        requiresAuth: Boolean = true,
        headers: Map<String, String> = emptyMap(),
    ): T = request("PUT", path, body, requiresAuth, headers)

    suspend inline fun <reified T : Any> request(
        method: String,
        path: String,
        body: Map<String, Any>?,
        requiresAuth: Boolean,
        headers: Map<String, String> = emptyMap(),
    ): T =
        withContext(Dispatchers.IO) {
            val urlString = baseUrl + path
            if (!urlString.startsWith("https://")) {
                throw IAMException(ThunderIDErrorCode.INVALID_CONFIGURATION, "baseUrl must use HTTPS")
            }
            val connection =
                (URL(urlString).openConnection() as HttpURLConnection).apply {
                    // The bypass is deliberately limited to loopback. Its only legitimate use is
                    // reaching a development server through the self-signed certificate ThunderID
                    // generates for localhost, and an attacker cannot sit in the middle of a
                    // loopback connection. Honouring the flag for arbitrary hosts would turn a
                    // convenience switch into a full man-in-the-middle hole on the channel
                    // carrying credentials, assertions and refresh tokens, in any build that
                    // happened to ship with it enabled.
                    if (allowInsecureConnections && this is HttpsURLConnection) {
                        if (isLoopbackHost(URL(urlString).host)) {
                            sslSocketFactory = insecureSslSocketFactory()
                            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                        } else {
                            throw IAMException(
                                ThunderIDErrorCode.INVALID_CONFIGURATION,
                                "allowInsecureConnections only applies to loopback hosts " +
                                    "(localhost, 127.0.0.1, ::1, 10.0.2.2); refusing to disable " +
                                    "certificate validation for '${URL(urlString).host}'",
                            )
                        }
                    }
                    requestMethod = method
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    headers.forEach { (name, value) -> setRequestProperty(name, value) }
                    if (requiresAuth) {
                        val token =
                            accessTokenProvider?.invoke()
                                ?: throw IAMException(ThunderIDErrorCode.SDK_NOT_INITIALIZED, "No access token provider")
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                    if (body != null) {
                        doOutput = true
                        OutputStreamWriter(outputStream).use { it.write(JSONObject(body).toString()) }
                    }
                }
            val statusCode = connection.responseCode
            val responseBody =
                runCatching {
                    if (statusCode in 200..299) {
                        connection.inputStream.bufferedReader().readText()
                    } else {
                        connection.errorStream?.bufferedReader()?.readText() ?: ""
                    }
                }.getOrDefault("")

            when (statusCode) {
                in 200..299 -> {
                    parseResponse(responseBody)
                }

                400 -> {
                    val msg = runCatching { JSONObject(responseBody).optString("message", "Bad request") }.getOrDefault("Bad request")
                    throw IAMException(ThunderIDErrorCode.INVALID_INPUT, msg)
                }

                401 -> {
                    throw IAMException(ThunderIDErrorCode.AUTHENTICATION_FAILED, "Unauthorized")
                }

                409 -> {
                    throw IAMException(ThunderIDErrorCode.USER_ALREADY_EXISTS, "Conflict")
                }

                in 500..599 -> {
                    throw IAMException(ThunderIDErrorCode.SERVER_ERROR, "Server error: $statusCode")
                }

                else -> {
                    throw IAMException(ThunderIDErrorCode.UNKNOWN_ERROR, "Unexpected status: $statusCode")
                }
            }
        }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T : Any> parseResponse(body: String): T {
        if (T::class == Unit::class) return Unit as T
        return com.google.gson
            .Gson()
            .fromJson(body, T::class.java)
    }

    /**
     * Whether [host] is a loopback address, and therefore unreachable by a network attacker.
     *
     * `10.0.2.2` is included because that is the Android emulator's alias for the host machine's
     * loopback interface, which is how an emulator reaches a development server.
     */
    private fun isLoopbackHost(host: String?): Boolean = host in LOOPBACK_HOSTS

    private fun insecureSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val trustAll =
            object : X509TrustManager {
                override fun checkClientTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun checkServerTrusted(
                    chain: Array<out X509Certificate>?,
                    authType: String?,
                ) = Unit

                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(null, arrayOf<TrustManager>(trustAll), SecureRandom())
        return ctx.socketFactory
    }

    private companion object {
        /**
         * Hosts a network attacker cannot occupy, and therefore the only ones for which
         * certificate validation may be relaxed.
         *
         * `10.0.2.2` is the Android emulator's alias for the host machine's loopback interface,
         * which is how an emulator reaches a development server.
         */
        val LOOPBACK_HOSTS = setOf("localhost", "127.0.0.1", "::1", "[::1]", "10.0.2.2")
    }
}
