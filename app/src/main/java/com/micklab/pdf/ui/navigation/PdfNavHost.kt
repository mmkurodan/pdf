package com.micklab.pdf.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.micklab.pdf.ui.docs.DocumentScreen
import com.micklab.pdf.ui.edit.EditScreen
import com.micklab.pdf.ui.home.HomeScreen
import com.micklab.pdf.ui.merge.MergeScreen
import com.micklab.pdf.ui.ocr.OcrScreen
import com.micklab.pdf.ui.prompt.PromptScreen
import com.micklab.pdf.ui.reorder.ReorderScreen
import com.micklab.pdf.ui.settings.LanguageScreen
import com.micklab.pdf.ui.settings.OcrSettingsScreen
import com.micklab.pdf.ui.split.SplitScreen
import com.micklab.pdf.ui.summary.SummaryScreen
import com.micklab.pdf.ui.toimage.PdfToImageScreen
import com.micklab.pdf.ui.topdf.ImageToPdfScreen

/** App root: a single-activity Compose navigation graph. */
@Composable
fun PdfApp() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = PdfDestination.HOME.route) {
        composable(PdfDestination.HOME.route) { entry ->
            HomeScreen(onOpenTool = { dest ->
                // Only navigate while Home is the settled destination; ignoring taps
                // that arrive mid-transition avoids pushing a destination twice.
                entry.ifResumed { navController.navigate(dest.route) { launchSingleTop = true } }
            })
        }
        composable(PdfDestination.SPLIT.route) { entry ->
            SplitScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.MERGE.route) { entry ->
            MergeScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.REORDER.route) { entry ->
            ReorderScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.PDF_TO_IMAGE.route) { entry ->
            PdfToImageScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.IMAGE_TO_PDF.route) { entry ->
            ImageToPdfScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.EDIT.route) { entry ->
            EditScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.OCR.route) { entry ->
            OcrScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.SUMMARY.route) { entry ->
            SummaryScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.PROMPT.route) { entry ->
            PromptScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.OCR_SETTINGS.route) { entry ->
            OcrSettingsScreen(onBack = navController.backFrom(entry))
        }
        composable(PdfDestination.LANGUAGE.route) { entry ->
            LanguageScreen(onBack = navController.backFrom(entry))
        }
        listOf(PdfDestination.MANUAL, PdfDestination.PRIVACY, PdfDestination.LICENSES).forEach { doc ->
            composable(doc.route) { entry ->
                DocumentScreen(destination = doc, onBack = navController.backFrom(entry))
            }
        }
    }
}

/** Runs [block] only when this entry is the settled, on-screen destination. */
private inline fun NavBackStackEntry.ifResumed(block: () -> Unit) {
    if (lifecycle.currentState == Lifecycle.State.RESUMED) block()
}

/**
 * Back callback that pops only while [entry] is RESUMED. Repeat taps that land
 * mid-transition are ignored, so we never pop past the start destination and
 * end up showing a blank screen.
 */
private fun NavController.backFrom(entry: NavBackStackEntry): () -> Unit =
    { entry.ifResumed { popBackStack() } }
