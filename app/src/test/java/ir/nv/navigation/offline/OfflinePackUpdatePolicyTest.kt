package ir.nv.navigation.offline

import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePackUpdatePolicyTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    @Test
    fun newInstallRequiresDownload() {
        val decision = OfflinePackUpdatePolicy.decide(
            installed = null,
            remote = OfflinePackUpdatePolicy.RemoteVersion(1, shaA, 1024, 1)
        )
        assertTrue(decision is OfflinePackUpdatePolicy.Decision.UpdateAvailable)
    }

    @Test
    fun equalVersionAndChecksumIsUpToDate() {
        val decision = OfflinePackUpdatePolicy.decide(
            installed = OfflinePackUpdatePolicy.InstalledVersion(2, shaA, 1),
            remote = OfflinePackUpdatePolicy.RemoteVersion(2, shaA, 1024, 1)
        )
        assertTrue(decision is OfflinePackUpdatePolicy.Decision.UpToDate)
    }

    @Test
    fun changedChecksumAtSameVersionTriggersRepairUpdate() {
        val decision = OfflinePackUpdatePolicy.decide(
            installed = OfflinePackUpdatePolicy.InstalledVersion(2, shaA, 1),
            remote = OfflinePackUpdatePolicy.RemoteVersion(2, shaB, 1024, 1)
        )
        assertTrue(decision is OfflinePackUpdatePolicy.Decision.UpdateAvailable)
    }

    @Test
    fun invalidChecksumIsRejected() {
        val decision = OfflinePackUpdatePolicy.decide(
            installed = null,
            remote = OfflinePackUpdatePolicy.RemoteVersion(3, "bad", 1024, 1)
        )
        assertTrue(decision is OfflinePackUpdatePolicy.Decision.InvalidMetadata)
    }
}
