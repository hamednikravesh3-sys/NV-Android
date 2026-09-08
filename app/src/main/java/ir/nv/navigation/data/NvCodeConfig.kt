package ir.nv.navigation.data

import ir.nv.navigation.BuildConfig

object NvCodeConfig {
    /**
     * Production NV Code registry endpoint supplied at build time through
     * NV_CODE_REGISTRY_URL. Empty keeps allocation disabled/fail-closed.
     */
    val REGISTRY_BASE_URL: String
        get() = BuildConfig.NV_CODE_REGISTRY_URL
}
