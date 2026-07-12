package com.micklab.pdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.micklab.pdf.ui.home.HomeScreen
import com.micklab.pdf.ui.navigation.PdfDestination
import com.micklab.pdf.ui.theme.PdfToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * HomeScreen takes a plain callback (no ViewModel), so it can be rendered
 * without Hilt. Verifies the tool list renders and routes clicks.
 */
class HomeScreenComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersToolsAndRoutesClicks() {
        var opened: PdfDestination? = null
        composeRule.setContent {
            PdfToolsTheme {
                HomeScreen(onOpenTool = { opened = it })
            }
        }

        composeRule.onNodeWithText(PdfDestination.MERGE.title).assertIsDisplayed()
        composeRule.onNodeWithText(PdfDestination.OCR.title).performClick()

        assertEquals(PdfDestination.OCR, opened)
    }
}
