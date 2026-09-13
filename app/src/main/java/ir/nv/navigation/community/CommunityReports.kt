package ir.nv.navigation.community

import android.content.Context
import ir.nv.navigation.core.Coordinate
import ir.nv.navigation.privacy.PrivacyPolicy
import ir.nv.navigation.privacy.PrivacySettingsState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class CommunityReportType(val titleFa: String) {
    ACCIDENT("تصادف"),
    TRAFFIC("ترافیک"),
    ROAD_CLOSED("مسدودی"),
    DANGER("خطر جاده"),
    FLOOD("آب‌گرفتگی"),
    ROAD_DAMAGE("خرابی راه"),
    STOPPED_VEHICLE("خودروی متوقف")
}

enum class CommunitySyncState { PENDING, SYNCED, FAILED }

data class CommunityReport(
    val id: String,
    val type: CommunityReportType,
    val coordinate: Coordinate,
    val accuracyMeters: Float?,
    val confidence: Double,
    val note: String?,
    val createdAtMillis: Long,
    val syncState: CommunitySyncState = CommunitySyncState.PENDING
)

interface CommunityReportRemote {
    val configured: Boolean
    fun upload(report: CommunityReport): Result<Unit>
}

object UnavailableCommunityReportRemote : CommunityReportRemote {
    override val configured: Boolean = false
    override fun upload(report: CommunityReport): Result<Unit> =
        Result.failure(IllegalStateException("community report endpoint is not configured"))
}

class HttpCommunityReportRemote(
    private val endpoint: String,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) : CommunityReportRemote {
    override val configured: Boolean = endpoint.startsWith("https://")

    override fun upload(report: CommunityReport): Result<Unit> = runCatching {
        require(configured) { "community report endpoint must use HTTPS" }
        val payload = JSONObject()
            .put("id", report.id)
            .put("type", report.type.name.lowercase())
            .put("latitude", report.coordinate.latitude)
            .put("longitude", report.coordinate.longitude)
            .put("accuracy_meters", report.accuracyMeters)
            .put("confidence", report.confidence)
            .put("note", report.note)
            .put("created_at_ms", report.createdAtMillis)
        val request = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("community endpoint returned HTTP ${response.code}")
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

class CommunityReportOutbox(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): List<CommunityReport> = runCatching {
        val array = JSONArray(prefs.getString(KEY_REPORTS, "[]") ?: "[]")
        buildList {
            for (index in 0 until array.length()) add(fromJson(array.getJSONObject(index)))
        }
    }.getOrDefault(emptyList())

    fun replace(report: CommunityReport) {
        val values = (all().filterNot { it.id == report.id } + report)
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_REPORTS)
        persist(values)
    }

    private fun persist(values: List<CommunityReport>) {
        val array = JSONArray()
        values.forEach { array.put(toJson(it)) }
        prefs.edit().putString(KEY_REPORTS, array.toString()).apply()
    }

    private fun toJson(report: CommunityReport) = JSONObject()
        .put("id", report.id)
        .put("type", report.type.name)
        .put("lat", report.coordinate.latitude)
        .put("lon", report.coordinate.longitude)
        .put("accuracy", report.accuracyMeters)
        .put("confidence", report.confidence)
        .put("note", report.note)
        .put("created", report.createdAtMillis)
        .put("state", report.syncState.name)

    private fun fromJson(value: JSONObject) = CommunityReport(
        id = value.getString("id"),
        type = CommunityReportType.valueOf(value.getString("type")),
        coordinate = Coordinate(value.getDouble("lat"), value.getDouble("lon")),
        accuracyMeters = value.optDouble("accuracy", Double.NaN).takeIf { it.isFinite() }?.toFloat(),
        confidence = value.optDouble("confidence", .5).coerceIn(0.0, 1.0),
        note = value.optString("note").takeIf { it.isNotBlank() && it != "null" },
        createdAtMillis = value.getLong("created"),
        syncState = runCatching { CommunitySyncState.valueOf(value.optString("state", CommunitySyncState.PENDING.name)) }
            .getOrDefault(CommunitySyncState.PENDING)
    )

    private companion object {
        const val PREFS = "nv_community_reports"
        const val KEY_REPORTS = "outbox"
        const val MAX_REPORTS = 100
    }
}

class CommunityReportRepository(
    context: Context,
    private val remote: CommunityReportRemote = UnavailableCommunityReportRemote
) {
    private val outbox = CommunityReportOutbox(context)

    fun all(): List<CommunityReport> = outbox.all()

    fun submit(
        type: CommunityReportType,
        coordinate: Coordinate,
        accuracyMeters: Float?,
        note: String? = null
    ): CommunityReport {
        val confidence = when {
            accuracyMeters == null || !accuracyMeters.isFinite() -> .45
            accuracyMeters <= 10f -> .95
            accuracyMeters <= 25f -> .80
            accuracyMeters <= 50f -> .60
            else -> .40
        }
        return CommunityReport(
            id = UUID.randomUUID().toString(),
            type = type,
            coordinate = coordinate,
            accuracyMeters = accuracyMeters,
            confidence = confidence,
            note = note?.trim()?.take(240)?.takeIf(String::isNotBlank),
            createdAtMillis = System.currentTimeMillis()
        ).also(outbox::replace)
    }

    fun syncPending(settings: PrivacySettingsState): List<CommunityReport> {
        val existing = outbox.all()
        if (!PrivacyPolicy.mayUploadCommunityReports(settings) || !remote.configured) return existing
        existing.filter { it.syncState != CommunitySyncState.SYNCED }.forEach { report ->
            val state = if (remote.upload(report).isSuccess) CommunitySyncState.SYNCED else CommunitySyncState.FAILED
            outbox.replace(report.copy(syncState = state))
        }
        return outbox.all()
    }
}
