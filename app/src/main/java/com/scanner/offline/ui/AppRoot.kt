package com.scanner.offline.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.scanner.offline.R
import com.scanner.offline.ui.navigation.AppNavGraph
import com.scanner.offline.ui.navigation.TopRoute

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    val tabs = listOf(
        TabItem(TopRoute.Home, R.string.tab_home, Icons.Outlined.Folder),
        TabItem(TopRoute.Tools, R.string.tab_tools, Icons.Outlined.Build),
        TabItem(TopRoute.Me, R.string.tab_me, Icons.Outlined.Person)
    )

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination
            // 仅在顶级 Tab 时显示底部栏
            val showBar = tabs.any { it.route.path == current?.route }
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = current?.hierarchy?.any { it.route == tab.route.path } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route.path) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        AppNavGraph(
            navController = navController,
            modifier = androidx.compose.ui.Modifier.padding(padding)
        )
    }
}

private data class TabItem(
    val route: TopRoute,
    val label: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
