package ir.nv.navigation.offline

import ir.nv.navigation.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Reads release metadata once and exposes which province packages are actually published. */
class ProvincePackAvailabilityService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
) {
    data class Availability(
        val publishedPackIds: Set<String>,
        val releaseTag: String?
    ) {
        fun isPublished(pack: OfflineRegionPack): Boolean = pack.id in publishedPackIds
    }

    fun fetch(): Result<Availability> = runCatching {
        val apiUrl = BuildConfig.PROVINCE_PACK_RELEASE_API_URL.trim()
        require(apiUrl.startsWith("https://")) { "آدرس بررسی بسته‌های استانی نامعتبر است" }
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "NV-Android")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Province metadata HTTP ${response.code}" }
            parseRelease(requireNotNull(response.body).string())
        }
    }

    internal fun parseRelease(raw: String): Availability {
        val document = JSONObject(raw)
        val assets = document.optJSONArray("assets")
        val published = buildSet {
            if (assets != null) {
                for (index in 0 until assets.length()) {
                    val name = assets.optJSONObject(index)?.optString("name").orEmpty()
                    PACK_NAME.matchEntire(name)?.groupValues?.getOrNull(1)?.let { id ->
                        if (OfflinePackCatalog.provinceById(id) != null) add(id)
                    }
                }
            }
        }
        return Availability(
            publishedPackIds = published,
            releaseTag = document.optString("tag_name").takeIf { it.isNotBlank() }
        )
    }

    private companion object {
        val PACK_NAME = Regex("^province-([a-z0-9-]+)\\.nvpack$")
    }
}
