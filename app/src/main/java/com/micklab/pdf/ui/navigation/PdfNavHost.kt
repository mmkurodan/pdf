package com.micklab.pdf.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.micklab.pdf.ui.docs.DocumentScreen
import com.micklab.pdf.ui.edit.EditScreen
import com.micklab.pdf.ui.home.HomeScreen
import com.micklab.pdf.ui.merge.MergeScreen
import com.micklab.pdf.ui.ocr.OcrScreen
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
        composable(PdfDestination.HOME.route) {
            HomeScreen(onOpenTool = { navController.navigate(it.route) })
        }
        composable(PdfDestination.SPLIT.route) {
            SplitScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.MERGE.route) {
            MergeScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.REORDER.route) {
            ReorderScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.PDF_TO_IMAGE.route) {
            PdfToImageScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.IMAGE_TO_PDF.route) {
            ImageToPdfScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.EDIT.route) {
            EditScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.OCR.route) {
            OcrScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.SUMMARY.route) {
            SummaryScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.OCR_SETTINGS.route) {
            OcrSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(PdfDestination.LANGUAGE.route) {
            LanguageScreen(onBack = { navController.popBackStack() })
        }
        listOf(PdfDestination.MANUAL, PdfDestination.PRIVACY, PdfDestination.LICENSES).forEach { doc ->
            composable(doc.route) {
                DocumentScreen(destination = doc, onBack = { navController.popBackStack() })
            }
        }
    }
}
