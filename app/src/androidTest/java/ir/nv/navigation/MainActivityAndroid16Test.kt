package ir.nv.navigation

import android.os.Build
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAndroid16Test {
    @Test
    fun launchesRtlWithAndroid16Target() {
        assertTrue("instrumentation must run on API 36+", Build.VERSION.SDK_INT >= 36)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(36, activity.applicationInfo.targetSdkVersion)
                assertEquals(View.LAYOUT_DIRECTION_RTL, activity.window.decorView.layoutDirection)
            }
            onView(isRoot()).check(matches(isDisplayed()))
        }
    }
}
