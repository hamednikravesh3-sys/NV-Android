package ir.nv.navigation.cloud

import ir.nv.navigation.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal authenticated cloud transport for NV account/sync data.
 *
 * The client is intentionally disabled until NV_CLOUD_API_URL is configured. That keeps the
 * offline-first application deterministic while giving Batch 30 a real production transport
 * contract instead of a local stub.
 */
class NvCloudSyncClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    data class CloudPlace(
        val code: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val updatedAtEpochSeconds: Long
    )

    data class SyncSnapshot(
        val revision: Long,
        val places: List<CloudPlace>
    )

    fun isConfigured(): Boolean = BuildConfig.CLOUD_API_URL.isNotBlank()

    fun pull(accessToken: String, sinceRevision: Long = 0L): Result<SyncSnapshot> = runCatching {
        requireConfigured()
        require(accessToken.isNotBlank()) { "توکن حساب NV موجود نیست" }
        val url = BuildConfig.CLOUD_API_URL.trimEnd('/') + "/v1/sync?since=$sinceRevision"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Cloud HTTP ${response.code}" }
            parseSnapshot(requireNotNull(response.body).string())
        }
    }

    fun push(accessToken: String, revision: Long, places: List<CloudPlace>): Result<SyncSnapshot> = runCatching {
        requireConfigured()
        require(accessToken.isNotBlank()) { "توکن حساب NV موجود نیست" }
        val payload = JSONObject().apply {
            put("revision", revision)
            put("places", JSONArray().apply {
                places.forEach { place ->
                    put(JSONObject().apply {
                        put("code", place.code)
                        put("name", place.name)
                        put("latitude", place.latitude)
                        put("longitude", place.longitude)
                        put("updatedAt", place.updatedAtEpochSeconds)
                    })
                }
            })
        }
        val request = Request.Builder()
            .url(BuildConfig.CLOUD_API_URL.trimEnd('/') + "/v1/sync")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Cloud HTTP ${response.code}" }
            parseSnapshot(requireNotNull(response.body).string())
        }
    }

    internal fun parseSnapshot(raw: String): SyncSnapshot {
        val document = JSONObject(raw)
        val revision = document.optLong("revision", -1L)
        require(revision >= 0L) { "نسخه همگام‌سازی نامعتبر است" }
        val items = document.optJSONArray("places") ?: JSONArray()
        val places = buildList {
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                val code = item.getString("code").trim()
                val latitude = item.getDouble("latitude")
                val longitude = item.getDouble("longitude")
                require(code.matches(Regex("^[0-9]+$"))) { "کد NV ابری نامعتبر است" }
                require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "مختصات ابری نامعتبر است" }
                add(
                    CloudPlace(
                        code = code,
                        name = item.optString("name").trim().ifBlank { "مکان NV $code" },
                        latitude = latitude,
                        longitude = longitude,
                        updatedAtEpochSeconds = item.optLong("updatedAt", 0L)
                    )
                )
            }
        }
        return SyncSnapshot(revision, places.distinctBy { it.code })
    }

    private fun requireConfigured() {
        check(isConfigured()) { "سامانه ابری NV هنوز پیکربندی نشده است" }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
