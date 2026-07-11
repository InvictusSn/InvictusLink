package com.invictus.link

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

data class LinkHttpResponse(val code: Int, val body: String)

object LinkHttp {
    private const val DEFAULT_CONNECT_MS = 15_000L
    private const val DEFAULT_READ_WRITE_MS = 120_000L

    private val defaultClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DEFAULT_CONNECT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(DEFAULT_READ_WRITE_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(DEFAULT_READ_WRITE_MS, TimeUnit.MILLISECONDS)
        .build()

    private fun client(connectTimeoutMs: Long? = null, readTimeoutMs: Long? = null): OkHttpClient {
        if (connectTimeoutMs == null && readTimeoutMs == null) return defaultClient
        val builder = defaultClient.newBuilder()
        connectTimeoutMs?.let { builder.connectTimeout(it, TimeUnit.MILLISECONDS) }
        readTimeoutMs?.let {
            builder.readTimeout(it, TimeUnit.MILLISECONDS)
            builder.writeTimeout(it, TimeUnit.MILLISECONDS)
        }
        return builder.build()
    }

    private fun buildRequest(
        url: String,
        method: String,
        token: String? = null,
        headers: Map<String, String> = emptyMap(),
        body: okhttp3.RequestBody? = null,
    ): Request {
        val builder = Request.Builder().url(url).method(method, body)
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun execute(
        request: Request,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val httpClient = client(connectTimeoutMs, readTimeoutMs)
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            return LinkHttpResponse(response.code, body)
        }
    }

    fun get(
        url: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val request = buildRequest(url, "GET", token)
        return execute(request, connectTimeoutMs, readTimeoutMs)
    }

    fun postJson(
        url: String,
        json: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = buildRequest(url, "POST", token, body = body)
        return execute(request, connectTimeoutMs, readTimeoutMs)
    }

    fun postEmptyJson(
        url: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        return postJson(url, "{}", token, connectTimeoutMs, readTimeoutMs)
    }

    fun postBytes(
        url: String,
        bytes: ByteArray,
        contentType: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val body = bytes.toRequestBody(contentType.toMediaType())
        val request = buildRequest(url, "POST", token, body = body)
        return execute(request, connectTimeoutMs, readTimeoutMs)
    }

    fun postText(
        url: String,
        text: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val body = text.toRequestBody("text/plain; charset=utf-8".toMediaType())
        val request = buildRequest(url, "POST", token, body = body)
        return execute(request, connectTimeoutMs, readTimeoutMs)
    }

    fun patchJson(
        url: String,
        json: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = buildRequest(url, "PATCH", token, body = body)
        return execute(request, connectTimeoutMs, readTimeoutMs)
    }

    fun delete(
        url: String,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val request = buildRequest(url, "DELETE", token)
        return execute(request, connectTimeoutMs, readTimeoutMs)
    }

    fun downloadToFile(
        url: String,
        dest: File,
        token: String? = null,
        connectTimeoutMs: Long? = null,
        readTimeoutMs: Long? = null,
    ): LinkHttpResponse {
        val request = buildRequest(url, "GET", token)
        val httpClient = client(connectTimeoutMs, readTimeoutMs)
        httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                response.body?.byteStream()?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return LinkHttpResponse(response.code, "")
        }
    }
}
