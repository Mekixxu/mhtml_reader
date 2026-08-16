package com.html_reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @Test
    fun appContextIsPresent() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(appContext)
        assertEquals("com.html_reader", appContext.packageName)
    }

    @Test
    fun mainActivityIsLaunchable() {
        val intent = android.content.Intent().setClassName(
            InstrumentationRegistry.getInstrumentation().targetContext,
            MainActivity::class.java.name
        )
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = InstrumentationRegistry.getInstrumentation()
            .startActivitySync(intent)
        assertNotNull(activity)
        assertTrue(activity.isFinishing.not())
    }
}