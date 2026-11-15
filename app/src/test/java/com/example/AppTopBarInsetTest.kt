package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.ui.components.AppTopBar
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppTopBarInsetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testAppTopBarRendersTitleAndActionsWithoutOverlap() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                AppTopBar(
                    title = "UniTrack",
                    subtitle = "Academic Routine",
                    badgeCount = 2
                )
            }
        }

        composeTestRule.onNodeWithTag("app_top_bar_title_column").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_top_bar_actions").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_clock_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("top_bar_bell_icon").assertIsDisplayed()
        composeTestRule.onNodeWithTag("theme_toggle_button").assertIsDisplayed()
    }
}
