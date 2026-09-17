package com.trae.expensetracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.InsertChartOutlined
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface as M3Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trae.expensetracker.ExpenseTrackerApp
import com.trae.expensetracker.ui.screens.CardsScreen
import com.trae.expensetracker.ui.screens.DashboardScreen
import com.trae.expensetracker.ui.screens.ManualExpenseScreen
import com.trae.expensetracker.ui.screens.ReportsScreen
import com.trae.expensetracker.ui.screens.ReviewScreen
import com.trae.expensetracker.ui.screens.SettingsScreen
import com.trae.expensetracker.ui.screens.TransactionsScreen
import com.trae.expensetracker.ui.theme.Primary
import com.trae.expensetracker.ui.theme.PrimaryContainer
import com.trae.expensetracker.ui.theme.SecondaryContainer
import com.trae.expensetracker.ui.theme.Surface
import com.trae.expensetracker.ui.theme.Surface2
import com.trae.expensetracker.ui.theme.TextSecondary
import com.trae.expensetracker.ui.theme.Border

sealed class Dest(val route: String, val label: String, val icon: ImageVector? = null) {
    data object Dashboard : Dest("dashboard", "Home", Icons.Outlined.GridView)
    data object Transactions : Dest("transactions", "Txns", Icons.AutoMirrored.Outlined.ListAlt)
    data object Reports : Dest("reports", "Reports", Icons.Outlined.InsertChartOutlined)
    data object Cards : Dest("cards", "Cards", Icons.Outlined.CreditCard)
    data object Review : Dest("review", "Review", Icons.Outlined.Notifications)
    data object Settings : Dest("settings", "Settings", Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val container = (ctx.applicationContext as ExpenseTrackerApp).container
    var showManualSheet by remember { mutableStateOf(false) }

    val items = listOf(
        Dest.Dashboard,
        Dest.Transactions,
        Dest.Reports,
        Dest.Cards,
        Dest.Review,
        Dest.Settings,
    )
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val manualSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = Surface2,
        floatingActionButton = {
            val showFab = currentRoute != Dest.Review.route &&
                currentRoute != Dest.Settings.route &&
                currentRoute != Dest.Transactions.route &&
                currentRoute != Dest.Reports.route
            if (showFab) {
                FloatingActionButton(
                    containerColor = Primary,
                    contentColor = Surface,
                    shape = RoundedCornerShape(24.dp),
                    onClick = { showManualSheet = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add manual expense")
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                M3Surface(
                    color = Surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 2.dp,
                    shadowElevation = 4.dp,
                    border = BorderStroke(1.dp, Border)
                ) {
                    NavigationBar(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ) {
                        items.forEach { d ->
                            val selected = currentDestination?.hierarchy?.any { it.route == d.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(d.route) {
                                        launchSingleTop = true
                                        restoreState = true
                                        popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    }
                                },
                                icon = { d.icon?.let { Icon(it, contentDescription = d.label) } },
                                label = {
                                    Text(
                                        d.label,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Primary,
                                    selectedTextColor = Primary,
                                    indicatorColor = SecondaryContainer,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary,
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Dashboard.route) { DashboardScreen(container) }
            composable(Dest.Transactions.route) { TransactionsScreen(container) }
            composable(Dest.Reports.route) { ReportsScreen(container) }
            composable(Dest.Cards.route) { CardsScreen(container) }
            composable(Dest.Review.route) { ReviewScreen(container) }
            composable(Dest.Settings.route) { SettingsScreen(container) }
        }

        if (showManualSheet) {
            ModalBottomSheet(
                onDismissRequest = { showManualSheet = false },
                sheetState = manualSheetState,
                scrimColor = Color.Black.copy(alpha = 0.42f),
                containerColor = Surface,
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                dragHandle = null,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                        .navigationBarsPadding()
                ) {
                    ManualExpenseScreen(
                        container = container,
                        onDone = { showManualSheet = false }
                    )
                }
            }
        }
    }
}
