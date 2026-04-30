package com.scanner.offline.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.scanner.offline.ui.screen.camera.CameraScreen
import com.scanner.offline.ui.screen.crop.CropScreen
import com.scanner.offline.ui.screen.document.DocumentDetailScreen
import com.scanner.offline.ui.screen.export.ExportScreen
import com.scanner.offline.ui.screen.export.FormatConvertScreen
import com.scanner.offline.ui.screen.filter.FilterScreen
import com.scanner.offline.ui.screen.home.HomeScreen
import com.scanner.offline.ui.screen.me.MeScreen
import com.scanner.offline.ui.screen.ocr.OcrScreen
import com.scanner.offline.ui.screen.tools.ToolsScreen

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
                onScanClick = { navController.navigate(AppRoute.Camera.path) },
                onDocumentClick = { docId ->
                    navController.navigate(AppRoute.DocumentDetail.create(docId))
                }
            )
        }
        composable(TopRoute.Tools.path) {
            ToolsScreen(
                onScanClick = { navController.navigate(AppRoute.Camera.path) },
                onPickImageForOcr = { uri ->
                    navController.navigate(AppRoute.Crop.create(uri))
                },
                onFormatConvertClick = { navController.navigate(AppRoute.FormatConvert.path) }
            )
        }
        composable(TopRoute.Me.path) {
            MeScreen()
        }

        composable(AppRoute.Camera.path) {
            CameraScreen(
                onCaptured = { uri ->
                    navController.navigate(AppRoute.Crop.create(uri)) {
                        popUpTo(AppRoute.Camera.path) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.Crop.path,
            arguments = listOf(
                navArgument("imageUri") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("imageUri").orEmpty()
            val uri = java.net.URLDecoder.decode(raw, "UTF-8")
            CropScreen(
                imageUri = uri,
                onCropped = { processedPath ->
                    navController.navigate(AppRoute.Filter.create(processedPath))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = AppRoute.Filter.path,
            arguments = listOf(
                navArgument("imagePath") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("imagePath").orEmpty()
            val path = java.net.URLDecoder.decode(raw, "UTF-8")
            FilterScreen(
                imagePath = path,
                onSaved = { docId ->
                    navController.navigate(AppRoute.DocumentDetail.create(docId)) {
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
                navArgument("docId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("imagePath").orEmpty()
            val path = java.net.URLDecoder.decode(raw, "UTF-8")
            val docId = backStackEntry.arguments?.getLong("docId") ?: -1L
            OcrScreen(
                imagePath = path,
                docId = if (docId == -1L) null else docId,
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
                onOcrClick = { imagePath ->
                    navController.navigate(AppRoute.Ocr.create(imagePath, docId))
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
    }
}
