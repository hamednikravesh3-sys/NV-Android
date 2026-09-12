package ir.nv.navigation.data

import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.core.Place
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class NvCodeAllocationService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    data class Allocation(
        val code: String,
        val name: String,
        val coordinate: Coordinate,
        val online: Boolean = true
    )

    fun isConfigured(): Boolean = NvCodeConfig.REGISTRY_BASE_URL.isNotBlank()

    /**
     * NV codes are authoritative server-side identifiers. This method never
     * generates a local/offline fallback code.
     */
    fun allocateOnline(name: String, coordinate: Coordinate): Result<Allocation> = runCatching {
        val base = NvCodeConfig.REGISTRY_BASE_URL.trimEnd('/')
        require(base.isNotBlank()) { "سامانه آنلاین کد NV هنوز تنظیم نشده است" }

        val payload = JSONObject()
            .put("name", name.trim().ifBlank { "مکان NV" })
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
            check(response.isSuccessful) {
                if (response.code in 500..599) "سرور کد NV در دسترس نیست؛ دوباره تلاش کنید"
                else "خطای سامانه کد NV: ${response.code}"
            }

            val json = JSONObject(raw)
            val code = json.getLong("code").toString()
            require(code.all(Char::isDigit) && code.toLong() > 0L) { "کد دریافتی از سرور نامعتبر است" }

            Allocation(
                code = code,
                name = json.optString("name", name).ifBlank { name },
                coordinate = Coordinate(
                    json.optDouble("latitude", coordinate.latitude),
                    json.optDouble("longitude", coordinate.longitude)
                )
            )
        }
    }

    /** Resolves an NV code against the central registry so the same code works on every device. */
    fun resolveOnline(rawCode: String): Result<Place?> = runCatching {
        val base = NvCodeConfig.REGISTRY_BASE_URL.trimEnd('/')
        require(base.isNotBlank()) { "سامانه آنلاین کد NV هنوز تنظیم نشده است" }
        val code = PlaceCodes.publicCode(rawCode) ?: return@runCatching null
        require(code > 0L) { "کد NV نامعتبر است" }

        val request = Request.Builder()
            .url("$base/v1/codes/$code")
            .get()
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@use null
            val raw = response.body?.string().orEmpty()
            check(response.isSuccessful) {
                if (response.code in 500..599) "سرور کد NV در دسترس نیست؛ دوباره تلاش کنید"
                else "خطای سامانه کد NV: ${response.code}"
            }
            val json = JSONObject(raw)
            val latitude = json.getDouble("latitude")
            val longitude = json.getDouble("longitude")
            Place(
                code = json.optLong("code", code),
                name = json.optString("name", "مکان NV $code").ifBlank { "مکان NV $code" },
                coordinate = Coordinate(latitude, longitude),
                category = "nv:registry",
                personalCode = code.toString()
            )
        }
    }
}
