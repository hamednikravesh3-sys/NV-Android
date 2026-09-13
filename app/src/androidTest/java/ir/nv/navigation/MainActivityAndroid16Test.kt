package ir.nv.navigation

import android.Manifest
import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAndroid16Test {
    @Test
    fun launchesRtlWithAndroid16Target() {
        assertTrue("instrumentation must run on API 36+", Build.VERSION.SDK_INT >= 36)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        grantPermission(instrumentation, packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        grantPermission(instrumentation, packageName, Manifest.permission.ACCESS_FINE_LOCATION)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertEquals(36, activity.applicationInfo.targetSdkVersion)
                assertEquals(View.LAYOUT_DIRECTION_RTL, activity.window.decorView.layoutDirection)
            }
        }
    }

    private fun grantPermission(
        instrumentation: android.app.Instrumentation,
        packageName: String,
        permission: String
    ) {
        instrumentation.uiAutomation
            .executeShellCommand("pm grant $packageName $permission")
            .use { it.readBytes() }
        instrumentation.waitForIdleSync()
    }
}
