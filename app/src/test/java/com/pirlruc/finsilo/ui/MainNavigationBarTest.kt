package com.pirlruc.finsilo.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class MainNavigationBarTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun fourTabsSwitchSelection() {
        compose.setContent {
            val tab = remember { mutableStateOf(HomeTab.OVERVIEW) }
            MainNavigationBar(tab.value) { tab.value = it }
        }
        compose.onNodeWithText("Overview").assertIsSelected()
        compose.onNodeWithText("Holdings").performClick()
        compose.onNodeWithText("Holdings").assertIsSelected()
        compose.onNodeWithText("Activity").assertExists()
        compose.onNodeWithText("Insights").assertExists()
    }
}
