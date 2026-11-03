package com.scanner.offline.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.scanner.offline.domain.model.ScanSessionViewModel
import com.scanner.offline.ui.screen.camera.BurstProcessScreen
import com.scanner.offline.ui.screen.camera.CameraScreen
import com.scanner.offline.ui.screen.crop.CropScreen
import com.scanner.offline.ui.screen.document.DocumentDetailScreen
import com.scanner.offline.ui.screen.edit.ImageEditMode
import com.scanner.offline.ui.screen.edit.ImageEditScreen
import com.scanner.offline.ui.screen.export.ExportScreen
import com.scanner.offline.ui.screen.export.FormatConvertScreen
import com.scanner.offline.ui.screen.filter.FilterScreen
import com.scanner.offline.ui.screen.home.HomeScreen
import com.scanner.offline.ui.screen.home.TrashScreen
import com.scanner.offline.ui.screen.me.MeScreen
import com.scanner.offline.ui.screen.ocr.OcrScreen
import com.scanner.offline.ui.screen.settings.SettingsScreen
import com.scanner.offline.ui.screen.tools.BarcodeScreen
import com.scanner.offline.ui.screen.tools.BatchEditScreen
import com.scanner.offline.ui.screen.tools.IdCardScreen
import com.scanner.offline.ui.screen.tools.PdfToolScreen
import com.scanner.offline.ui.screen.tools.StitchScreen
import com.scanner.offline.ui.screen.tools.ToolsScreen
import java.net.URLDecoder

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopRoute.Home.path,
        modifier = modifier
    ) {
        composable(TopRoute.Home.path) {
            HomeScreen(
                onScanClick = { navController.navigate(AppRoute.Camera.create()) },
                onDocumentClick = { docId ->
                    navController.navigate(AppRoute.DocumentDetail.create(docId))
                },
                onTrashClick = { navController.navigate(AppRoute.Trash.path) }
            )
        }
        composable(TopRoute.Tools.path) {
            ToolsScreen(
                onScanClick = { navController.navigate(AppRoute.Camera.create()) },
                onPickImageForOcr = { uri ->
                    navController.navigate(AppRoute.Crop.create(uri))
                },
                onFormatConvertClick = { navController.navigate(AppRoute.FormatConvert.path) },
                onPickImageForEdit = { uri, mode ->
                    navController.navigate(AppRoute.ImageEdit.create(uri, mode))
                },
                onBatchClick = { navController.navigate(AppRoute.Batch.path) },
                onStitchClick = { navController.navigate(AppRoute.Stitch.path) },
                onPdfToolsClick = { navController.navigate(AppRoute.PdfTools.path) },
                onBarcodeClick = { navController.navigate(AppRoute.Barcode.path) },
                onIdCardClick = { navController.navigate(AppRoute.IdCard.path) }
            )
        }
        composable(TopRoute.Me.path) {
            MeScreen(onSettingsClick = { navController.navigate(AppRoute.Settings.path) })
        }

        composable(
            route = AppRoute.Camera.path,
            arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            val sessionVm: ScanSessionViewModel = hiltViewModel()
            CameraScreen(
                onCaptured = { uri ->
                    navController.navigate(AppRoute.Crop.create(uri, docId.takeIf { it >= 0 })) {
                        popUpTo(AppRoute.Camera.path) { inclusive = true }
                    }
                },
                onBurstDone = { uris ->
                    sessionVm.session.setBurst(uris)
                    navController.navigate(AppRoute.Burst.path) {
                        popUpTo(AppRoute.Camera.path) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.Crop.path,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; defaultValue = "" },
                navArgument("docId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("imageUri").orEmpty()
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            CropScreen(
                imageUri = uri,
                onCropped = { processedPath ->
                    navController.navigate(AppRoute.Filter.create(processedPath, docId.takeIf { it >= 0 }))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.Filter.path,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("docId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("imagePath").orEmpty()
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            FilterScreen(
                imagePath = path,
                docId = docId.takeIf { it >= 0 },
                onSaved = { savedDocId ->
                    navController.navigate(AppRoute.DocumentDetail.create(savedDocId)) {
                        popUpTo(TopRoute.Home.path)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.Ocr.path,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("docId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("pageId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("imagePath").orEmpty()
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            val pageId = backStackEntry.arguments?.getLong("pageId") ?: -1L
            OcrScreen(
                imagePath = path,
                docId = if (docId == -1L) null else docId,
                pageId = if (pageId == -1L) null else pageId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.DocumentDetail.path,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            DocumentDetailScreen(
                docId = docId,
                onExportClick = { navController.navigate(AppRoute.Export.create(docId)) },
                onOcrClick = { imagePath, pageId ->
                    navController.navigate(AppRoute.Ocr.create(imagePath, docId, pageId))
                },
                onEditClick = { imagePath, pageId ->
                    navController.navigate(AppRoute.ImageEdit.create(imagePath, "crop", pageId))
                },
                onAddPageCamera = {
                    navController.navigate(AppRoute.Camera.create(docId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.Export.path,
            arguments = listOf(navArgument("docId") { type = NavType.LongType })
        ) { backStackEntry ->
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            ExportScreen(
                docId = docId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.FormatConvert.path) {
            FormatConvertScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = AppRoute.ImageEdit.path,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; defaultValue = "" },
                navArgument("mode") { type = NavType.StringType; defaultValue = "crop" },
                navArgument("pageId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("imageUri").orEmpty()
            val modeArg = backStackEntry.arguments?.getString("mode").orEmpty()
            val pageId = backStackEntry.arguments?.getLong("pageId") ?: -1L
            val mode = when (modeArg.lowercase()) {
                "flip" -> ImageEditMode.FLIP
                "mark" -> ImageEditMode.MARK
                "redact" -> ImageEditMode.REDACT
                else -> ImageEditMode.CROP
            }
            ImageEditScreen(
                imageUri = uri,
                mode = mode,
                pageId = if (pageId == -1L) null else pageId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(AppRoute.Burst.path) {
            BurstProcessScreen(
                onDone = { docId ->
                    navController.navigate(AppRoute.DocumentDetail.create(docId)) {
                        popUpTo(TopRoute.Home.path)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoute.Batch.path) {
            BatchEditScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Stitch.path) {
            StitchScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.PdfTools.path) {
            PdfToolScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Barcode.path) {
            BarcodeScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.IdCard.path) {
            IdCardScreen(
                onBack = { navController.popBackStack() },
                onOpenDocument = { docId ->
                    navController.navigate(AppRoute.DocumentDetail.create(docId))
                }
            )
        }
        composable(AppRoute.Trash.path) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(AppRoute.Settings.path) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
