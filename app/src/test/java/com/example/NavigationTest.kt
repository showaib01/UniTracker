package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.util.NotificationNav
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setupWorkManager() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            val config = Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
            WorkManager.initialize(context, config)
        } catch (e: Exception) {
            // Already initialized
        }
    }

    @Test
    fun testNavigationHomeToBellToTasksAndReturnHome() {
        composeTestRule.onNodeWithTag("top_bar_bell_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_bar_bell_icon").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("nav_item_today").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("top_bar_bell_icon").assertIsDisplayed()
    }

    @Test
    fun testSettingsToPortalToHomeToSettingsShowsSettingsRoot() {
        // Tap Settings bottom bar item
        composeTestRule.onNodeWithTag("nav_item_more").performClick()
        composeTestRule.waitForIdle()

        // Tap Account row in Settings -> opens Portal sub-screen
        composeTestRule.onNodeWithTag("settings_account_row").performClick()
        composeTestRule.waitForIdle()

        // Tap Home bottom bar item -> goes to Today
        composeTestRule.onNodeWithTag("nav_item_today").performClick()
        composeTestRule.waitForIdle()

        // Tap Settings bottom bar item -> should show Settings root, not Portal
        composeTestRule.onNodeWithTag("nav_item_more").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_account_row").assertIsDisplayed()
    }

    @Test
    fun testSettingsToPortalToSettingsShowsSettingsRootFirstTap() {
        // Tap Settings bottom bar item
        composeTestRule.onNodeWithTag("nav_item_more").performClick()
        composeTestRule.waitForIdle()

        // Tap Account row in Settings -> opens Portal sub-screen
        composeTestRule.onNodeWithTag("settings_account_row").performClick()
        composeTestRule.waitForIdle()

        // Tap Settings bottom bar item -> should show Settings root on first tap
        composeTestRule.onNodeWithTag("nav_item_more").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("settings_account_row").assertIsDisplayed()
    }

    @Test
    fun testNotificationNavBuildIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pendingIntent = NotificationNav.buildContentIntent(context, 123, NotificationNav.TARGET_COURSE, "Algorithms_B", 456L)
        assertNotNull(pendingIntent)
    }
}
