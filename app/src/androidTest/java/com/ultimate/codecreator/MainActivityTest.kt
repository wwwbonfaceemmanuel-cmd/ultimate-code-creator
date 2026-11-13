package com.ultimate.codecreator

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun generateRecipeShowsResult() {
        composeRule.onNodeWithText("Generate Python code").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Binary search").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Binary search").assertExists()
    }
}
