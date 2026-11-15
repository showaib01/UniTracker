package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
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
        // Initially on Home (Today)
        composeTestRule.onNodeWithTag("top_bar_bell_icon").assertIsDisplayed()

        // Tap bell icon -> navigates to Tasks tab
        composeTestRule.onNodeWithTag("top_bar_bell_icon").performClick()
        composeTestRule.waitForIdle()

        // Tap Home bottom bar item -> returns to Home
        composeTestRule.onNodeWithTag("nav_item_today").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("top_bar_bell_icon").assertIsDisplayed()
    }

    @Test
    fun testNavigationHomeToSettingsToSubScreenAndBack() {
        // Tap Settings bottom bar item
        composeTestRule.onNodeWithTag("nav_item_more").performClick()
        composeTestRule.waitForIdle()

        // Tap Account row in Settings -> opens Portal sub-screen
        composeTestRule.onNodeWithTag("settings_account_row").performClick()
        composeTestRule.waitForIdle()

        // Tap back button on Portal sub-screen
        composeTestRule.onNodeWithTag("portal_back_button").performClick()
        composeTestRule.waitForIdle()

        // Tap Home bottom bar item -> returns to Home
        composeTestRule.onNodeWithTag("nav_item_today").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("top_bar_bell_icon").assertIsDisplayed()
    }
}
