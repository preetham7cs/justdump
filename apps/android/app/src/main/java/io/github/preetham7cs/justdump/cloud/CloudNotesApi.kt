package io.github.preetham7cs.justdump.cloud

import io.github.preetham7cs.justdump.BuildConfig
import io.github.preetham7cs.justdump.data.Note
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

class CloudNotesApi {
    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private val baseUrl: String
        get() = BuildConfig.API_BASE_URL.trimEnd('/')

    suspend fun upload(note: Note, accessToken: String): CloudNotePayload {
        ensureConfigured()
        val response = client.post("$baseUrl/v1/text-notes") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                TextNoteWritePayload(
                    clientNoteId = note.id,
                    content = note.content,
                    createdAt = Instant.ofEpochMilli(note.createdAtEpochMillis).toString(),
                    updatedAt = Instant.ofEpochMilli(note.updatedAtEpochMillis).toString(),
                ),
            )
        }
        return response.requireCloudNote()
    }

    suspend fun readBack(noteId: String, accessToken: String): CloudNotePayload {
        ensureConfigured()
        val response = client.get("$baseUrl/v1/text-notes/$noteId") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        return response.requireCloudNote()
    }

    private fun ensureConfigured() {
        check(baseUrl.isNotBlank()) { "Cloud API is not configured on this development build." }
    }
}

private suspend fun io.ktor.client.statement.HttpResponse.requireCloudNote(): CloudNotePayload {
    if (!status.isSuccess()) {
        throw CloudRequestException(status.value)
    }
    return body()
}

class CloudRequestException(val statusCode: Int) : Exception("Cloud request failed with HTTP $statusCode")

@Serializable
private data class TextNoteWritePayload(
    @SerialName("client_note_id") val clientNoteId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CloudNotePayload(
    @SerialName("client_note_id") val clientNoteId: String,
    val content: String,
)
