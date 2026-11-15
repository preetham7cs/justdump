package io.github.preetham7cs.justdump.cloud

import io.github.preetham7cs.justdump.BuildConfig
import io.github.preetham7cs.justdump.data.VaultItem
import io.github.preetham7cs.justdump.data.VaultItemKind
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class CloudVaultApi {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }
    private val baseUrl get() = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun uploadAndReadBack(item: VaultItem, token: String): CloudVaultItemPayload {
        check(baseUrl.isNotBlank()) { "Cloud API is not configured on this development build." }
        when (item.kind) {
            VaultItemKind.LINK -> uploadLink(item, token)
            VaultItemKind.IMAGE -> uploadImage(item, token)
            VaultItemKind.TEXT -> error("Text notes use the text-note cloud contract.")
        }
        val response = client.get("$baseUrl/v1/vault-items/${item.id}") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        return response.requireCloudVaultItem()
    }

    private suspend fun uploadLink(item: VaultItem, token: String) {
        val url = requireNotNull(item.sourceUrl)
        val response = client.post("$baseUrl/v1/vault-items") {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                VaultItemWritePayload(
                    clientItemId = item.id,
                    kind = "LINK",
                    sourceUrl = url,
                    originalObjectPath = null,
                    mimeType = "text/uri-list",
                    contentSha256 = sha256(url.toByteArray()),
                    createdAt = Instant.ofEpochMilli(item.createdAtEpochMillis).toString(),
                    updatedAt = Instant.ofEpochMilli(item.updatedAtEpochMillis).toString(),
                ),
            )
        }
        response.requireCloudVaultItem()
    }

    private suspend fun uploadImage(item: VaultItem, token: String) {
        val image = File(requireNotNull(item.localFilePath))
        if (!image.isFile) throw IllegalStateException("The original image is no longer available on this device.")
        val mime = requireNotNull(item.mimeType)
        val response = client.post(
            "$baseUrl/v1/vault-items/images/${item.id}" +
                "?created_at=${Instant.ofEpochMilli(item.createdAtEpochMillis)}" +
                "&updated_at=${Instant.ofEpochMilli(item.updatedAtEpochMillis)}",
        ) {
            header(HttpHeaders.Authorization, "Bearer $token")
            header(HttpHeaders.ContentType, mime)
            setBody(image.readBytes())
        }
        response.requireCloudVaultItem()
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.requireCloudVaultItem(): CloudVaultItemPayload {
    if (!status.isSuccess()) {
        val detail = runCatching {
            Json.parseToJsonElement(bodyAsText()).jsonObject["detail"]?.jsonPrimitive?.content
                ?.takeIf { it.length <= 180 && it.all { char -> !char.isISOControl() } }
        }.getOrNull()
        throw CloudRequestException(status.value, detail)
    }
    return body()
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

@Serializable
private data class VaultItemWritePayload(
    @SerialName("client_item_id") val clientItemId: String,
    val kind: String,
    @SerialName("source_url") val sourceUrl: String?,
    @SerialName("original_object_path") val originalObjectPath: String?,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("content_sha256") val contentSha256: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CloudVaultItemPayload(
    @SerialName("client_item_id") val clientItemId: String,
    val kind: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("original_object_path") val originalObjectPath: String? = null,
    @SerialName("processing_status") val processingStatus: String,
)
