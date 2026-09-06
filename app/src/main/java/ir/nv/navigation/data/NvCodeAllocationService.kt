package ir.nv.navigation.data

import ir.nv.navigation.core.Coordinate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NvCodeAllocationService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    data class Allocation(
        val code: String,
        val name: String,
        val coordinate: Coordinate,
        val online: Boolean
    )

    fun isConfigured(): Boolean = NvCodeConfig.REGISTRY_BASE_URL.isNotBlank()

    fun allocateOnline(name: String, coordinate: Coordinate): Result<Allocation> = runCatching {
        val base = NvCodeConfig.REGISTRY_BASE_URL.trimEnd('/')
        require(base.isNotBlank()) { "NV Code Registry هنوز روی سرور تنظیم نشده است" }
        val payload = JSONObject()
            .put("name", name.trim().ifBlank { "NV Place" })
            .put("latitude", coordinate.latitude)
            .put("longitude", coordinate.longitude)
            .toString()
        val request = Request.Builder()
            .url("$base/v1/codes/allocate")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) { "خطای Registry: HTTP ${response.code}" }
            val json = JSONObject(raw)
            val code = json.getLong("code").toString()
            Allocation(
                code = code,
                name = json.optString("name", name).ifBlank { name },
                coordinate = Coordinate(
                    json.optDouble("latitude", coordinate.latitude),
                    json.optDouble("longitude", coordinate.longitude)
                ),
                online = true
            )
        }
    }
}
