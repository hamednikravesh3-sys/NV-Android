package ir.nv.navigation.offline

import android.content.Context

/**
 * Resolves the province package selected for offline use.
 * The latest installed/selected province becomes the active package, while the
 * app remains online-first whenever connectivity is available.
 */
class ProvinceOfflineRuntime(context: Context) {
    data class Active(
        val pack: OfflineRegionPack,
        val files: OfflineRegionPackInstaller.InstalledFiles
    )

    private val installer = OfflineRegionPackInstaller(context.applicationContext)
    private val selection = ProvincePackSelectionStore(context.applicationContext)

    fun active(): Active? {
        val selected = selection.activePackId()
            ?.let(OfflinePackCatalog::provinceById)
            ?.let { pack -> installer.installed(pack)?.let { files -> Active(pack, files) } }
        if (selected != null) return selected

        val fallback = OfflinePackCatalog.provinces.firstNotNullOfOrNull { pack ->
            installer.installed(pack)?.let { files -> Active(pack, files) }
        }
        fallback?.let { selection.setActive(it.pack.id) }
        return fallback
    }

    fun hasReadyPack(): Boolean = active() != null

    fun activate(packId: String): Boolean {
        val pack = OfflinePackCatalog.provinceById(packId) ?: return false
        if (installer.installed(pack) == null) return false
        selection.setActive(packId)
        return true
    }
}

class ProvincePackSelectionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun activePackId(): String? = prefs.getString(KEY_ACTIVE_PACK, null)

    fun setActive(packId: String) {
        require(OfflinePackCatalog.provinceById(packId) != null) { "استان ناشناخته است: $packId" }
        prefs.edit().putString(KEY_ACTIVE_PACK, packId).apply()
    }

    fun clearIfActive(packId: String) {
        if (activePackId() == packId) prefs.edit().remove(KEY_ACTIVE_PACK).apply()
    }

    private companion object {
        const val PREFS_NAME = "province_pack_selection"
        const val KEY_ACTIVE_PACK = "active_province_pack"
    }
}
