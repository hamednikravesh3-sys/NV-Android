from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, got {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new), encoding="utf-8")


vm = "app/src/main/java/ir/nv/navigation/ui/NvViewModel.kt"
home = "app/src/main/java/ir/nv/navigation/ui/RahnamaHome.kt"

replace_once(
    vm,
    "import ir.nv.navigation.map.IranPackManager\n",
    "import ir.nv.navigation.map.IranPackManager\nimport ir.nv.navigation.offline.ProvinceOfflineRuntime\n",
)
replace_once(
    vm,
    "    private val packManager = IranPackManager(application)\n",
    "    private val packManager = IranPackManager(application)\n    private val provinceRuntime = ProvinceOfflineRuntime(application)\n",
)
replace_once(
    vm,
    "    private var router: AStarRouter? = null\n",
    "    private var router: AStarRouter? = null\n    private var activeOfflineMapFile: java.io.File? = null\n",
)
replace_once(
    vm,
    "                if (available) syncCommunityReports()\n",
    "                if (available) {\n                    syncCommunityReports()\n                } else if (!mutableState.value.offlineReady) {\n                    ensureOfflineDataOpen()\n                }\n",
)
replace_once(
    vm,
    "        if (packManager.isReady()) viewModelScope.launch { openDataPack() }\n        else if (packManager.status() !is IranPackManager.Status.NotStarted) monitorDownload()\n",
    "        when {\n            packManager.isReady() -> viewModelScope.launch { openDataPack() }\n            provinceRuntime.hasReadyPack() -> viewModelScope.launch { openProvinceDataPack() }\n            packManager.status() !is IranPackManager.Status.NotStarted -> monitorDownload()\n        }\n",
)
replace_once(
    vm,
    """        mutableState.update {\n            it.copy(\n                packStatus = IranPackManager.Status.NotStarted,\n                offlineReady = false,\n                preferOffline = false,\n                message = \"نقشه آفلاین حذف شد\"\n            )\n        }\n    }\n\n    fun setPreferOffline(value: Boolean) {\n        if (value && !packManager.isReady()) {\n            mutableState.update { it.copy(message = \"ابتدا نقشه آفلاین را دانلود کنید\") }\n        } else {\n            mutableState.update { it.copy(preferOffline = value, message = null) }\n        }\n    }\n""",
    """        val provinceReady = provinceRuntime.hasReadyPack()\n        mutableState.update {\n            it.copy(\n                packStatus = IranPackManager.Status.NotStarted,\n                offlineReady = provinceReady,\n                preferOffline = false,\n                message = if (provinceReady) \"بسته کامل ایران حذف شد؛ بسته استانی همچنان آماده است\" else \"نقشه آفلاین حذف شد\"\n            )\n        }\n        if (provinceReady) viewModelScope.launch { openProvinceDataPack() }\n    }\n\n    fun setPreferOffline(value: Boolean) {\n        if (!value) {\n            mutableState.update { it.copy(preferOffline = false, message = null) }\n            return\n        }\n        viewModelScope.launch {\n            val ready = ensureOfflineDataOpen()\n            if (ready) {\n                mutableState.update { it.copy(preferOffline = true, offlineReady = true, message = null) }\n            } else {\n                mutableState.update { it.copy(preferOffline = false, offlineReady = false, message = \"ابتدا از بخش «دانلود استان‌ها» یک استان را دانلود کنید\") }\n            }\n        }\n    }\n""",
)
replace_once(
    vm,
    """    private suspend fun openDataPack() = withContext(Dispatchers.IO) {\n        runCatching {\n            places?.close(); graph?.close()\n            places = PlaceRepository(packManager.placesFile)\n            graph = SqliteRoutingGraph(packManager.routingFile)\n            router = AStarRouter(requireNotNull(graph))\n        }.onSuccess {\n            mutableState.update { it.copy(packStatus = IranPackManager.Status.Ready, offlineReady = true) }\n        }.onFailure { error ->\n            mutableState.update { it.copy(packStatus = IranPackManager.Status.Failed(error.message ?: \"داده نامعتبر\"), offlineReady = false) }\n        }\n    }\n\n    fun mapFile() = packManager.mapFile\n""",
    """    private suspend fun openDataPack() = withContext(Dispatchers.IO) {\n        runCatching {\n            places?.close(); graph?.close()\n            places = PlaceRepository(packManager.placesFile)\n            graph = SqliteRoutingGraph(packManager.routingFile)\n            router = AStarRouter(requireNotNull(graph))\n            activeOfflineMapFile = packManager.mapFile\n        }.onSuccess {\n            mutableState.update { it.copy(packStatus = IranPackManager.Status.Ready, offlineReady = true) }\n        }.onFailure { error ->\n            activeOfflineMapFile = null\n            mutableState.update { it.copy(packStatus = IranPackManager.Status.Failed(error.message ?: \"داده نامعتبر\"), offlineReady = false) }\n        }\n    }\n\n    private suspend fun openProvinceDataPack(): Boolean = withContext(Dispatchers.IO) {\n        val active = provinceRuntime.active() ?: return@withContext false\n        runCatching {\n            places?.close(); graph?.close()\n            places = PlaceRepository(active.files.placesFile)\n            graph = SqliteRoutingGraph(active.files.routingFile)\n            router = AStarRouter(requireNotNull(graph))\n            activeOfflineMapFile = active.files.mapFile\n        }.onSuccess {\n            mutableState.update { state ->\n                state.copy(\n                    offlineReady = true,\n                    message = if (!state.onlineAvailable) \"اینترنت قطع است؛ بسته آفلاین ${active.pack.title} فعال شد\" else state.message\n                )\n            }\n        }.onFailure { error ->\n            activeOfflineMapFile = null\n            router = null\n            mutableState.update { it.copy(offlineReady = false, message = error.message ?: \"بسته استانی قابل استفاده نیست\") }\n        }.isSuccess\n    }\n\n    private suspend fun ensureOfflineDataOpen(): Boolean = when {\n        packManager.isReady() -> {\n            openDataPack()\n            router != null && activeOfflineMapFile?.isFile == true\n        }\n        provinceRuntime.hasReadyPack() -> openProvinceDataPack()\n        else -> false\n    }\n\n    fun mapFile() = activeOfflineMapFile ?: packManager.mapFile\n""",
)

replace_once(
    home,
    """                    Column {\n                        Text(\"نقشه آفلاین ایران\")\n                        Text(if (state.offlineReady) \"آماده استفاده\" else \"هنوز دانلود نشده\", color = NvColors.TextSecondaryDark, style = MaterialTheme.typography.labelSmall)\n                    }\n                    Switch(\n                        checked = state.preferOffline,\n                        onCheckedChange = { enabled ->\n                            if (enabled && !state.offlineReady) viewModel.startMapDownload() else viewModel.setPreferOffline(enabled)\n                        }\n                    )\n                }\n                Text(\"نقشه‌های آفلاین منطقه‌ای\", color = NvColors.TextSecondaryDark)\n""",
    """                    Column {\n                        Text(\"حالت آفلاین\")\n                        Text(\n                            if (state.offlineReady) \"بسته آفلاین آماده است؛ حالت اصلی همچنان آنلاین است\" else \"برای استفاده بدون اینترنت، استان موردنیاز را دانلود کنید\",\n                            color = NvColors.TextSecondaryDark,\n                            style = MaterialTheme.typography.labelSmall\n                        )\n                    }\n                    Switch(\n                        checked = state.preferOffline,\n                        onCheckedChange = viewModel::setPreferOffline\n                    )\n                }\n                Text(\"دانلود استان‌ها\", color = NvColors.TextSecondaryDark)\n""",
)

print("v0.18.4 online-first/province runtime patch applied")
