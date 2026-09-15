package app.bodyfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import app.bodyfit.data.Backup
import app.bodyfit.ui.screens.GoalsScreen
import app.bodyfit.ui.screens.HealthScreen
import app.bodyfit.ui.screens.TodayScreen
import app.bodyfit.ui.screens.TrendsScreen
import app.bodyfit.ui.screens.WaterScreen
import kotlinx.coroutines.launch

private enum class Tab(val route: String, val emoji: String, val label: String) {
    TODAY("today", "🏠", "Today"),
    WATER("water", "💧", "Water"),
    TRENDS("trends", "📈", "Trends"),
    HEALTH("health", "🩺", "Health"),
    GOALS("goals", "🎯", "Goals"),
}

/**
 * The four tabs, and the banner that appears while the activity-recognition permission
 * is missing. Without that permission the phone will not report steps at all, so the
 * banner sits above the content until it is granted rather than hiding in settings.
 */
@Composable
fun BodyFitAppScreen(
    activityPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    viewModel: HealthViewModel = viewModel(),
) {
    // Re-anchors the screens on the current day when the app comes back to the front. The
    // view model also wakes itself at midnight; this covers the case of the app being
    // backgrounded across the boundary and brought back.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Tab.TODAY.route

    val settings by viewModel.settings.collectAsState()
    val today by viewModel.today.collectAsState()
    val week by viewModel.week.collectAsState()
    val allDays by viewModel.allDays.collectAsState()
    val activeDate by viewModel.activeDate.collectAsState()
    val waterEntries by viewModel.waterEntries.collectAsState()
    val hourly by viewModel.hourly.collectAsState()
    val hourlyWater by viewModel.hourlyWater.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    // The system picker owns the destination, so no storage permission is involved and the
    // app only ever writes to a location the user chose in that moment.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME_TYPE)
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = runCatching {
                val bytes = Backup.write(context, uri, viewModel.backupJson())
                "Backup saved, ${bytes / 1024} KB"
            }.getOrElse { "Could not write the backup: ${it.message}" }
            snackbar.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            if (currentRoute != tab.route) {
                                navController.navigate(tab.route) {
                                    popUpTo(Tab.TODAY.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Text(tab.emoji, style = MaterialTheme.typography.titleMedium) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val contentPadding = PaddingValues(
            start = scaffoldPadding.calculateStartPadding(layoutDirection) + 16.dp,
            end = scaffoldPadding.calculateEndPadding(layoutDirection) + 16.dp,
            top = scaffoldPadding.calculateTopPadding() + 12.dp,
            bottom = scaffoldPadding.calculateBottomPadding() + 16.dp,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            if (!activityPermissionGranted) {
                PermissionBanner(
                    onRequestPermissions = onRequestPermissions,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = scaffoldPadding.calculateTopPadding() + 12.dp,
                    ),
                )
            }

            NavHost(
                navController = navController,
                startDestination = Tab.TODAY.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Tab.TODAY.route) {
                    TodayScreen(
                        record = today,
                        week = week,
                        allDays = allDays,
                        settings = settings,
                        activeDate = activeDate,
                        onLogWater = viewModel::logWater,
                        contentPadding = contentPadding,
                    )
                }
                composable(Tab.WATER.route) {
                    WaterScreen(
                        record = today,
                        entries = waterEntries,
                        settings = settings,
                        onLogWater = viewModel::logWater,
                        onUndoWater = viewModel::undoWater,
                        contentPadding = contentPadding,
                    )
                }
                composable(Tab.TRENDS.route) {
                    TrendsScreen(
                        allDays = allDays,
                        settings = settings,
                        activeDate = activeDate,
                        hours = hourly,
                        hourlyWater = hourlyWater,
                        onSelectDay = viewModel::showHoursFor,
                        contentPadding = contentPadding,
                    )
                }
                composable(Tab.HEALTH.route) {
                    HealthScreen(
                        allDays = allDays,
                        settings = settings,
                        activeDate = activeDate,
                        contentPadding = contentPadding,
                    )
                }
                composable(Tab.GOALS.route) {
                    GoalsScreen(
                        settings = settings,
                        onStepGoal = viewModel::setStepGoal,
                        onWaterGoal = viewModel::setWaterGoal,
                        onCalorieGoal = viewModel::setCalorieGoal,
                        onHeartPointGoal = viewModel::setHeartPointGoal,
                        onMoveMinuteGoal = viewModel::setMoveMinuteGoal,
                        onWeeklyStepGoal = viewModel::setWeeklyStepGoal,
                        onWeeklyHeartPointGoal = viewModel::setWeeklyHeartPointGoal,
                        onHeight = viewModel::setHeight,
                        onWeight = viewModel::setWeight,
                        onDefaultCup = viewModel::setDefaultCup,
                        onTrackerEnabled = viewModel::setTrackerEnabled,
                        onAge = viewModel::setAge,
                        onSmoker = viewModel::setSmoker,
                        onSex = viewModel::setSex,
                        onExport = { exportLauncher.launch(Backup.suggestedFileName()) },
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionBanner(onRequestPermissions: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🚶 Step counting is off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "BodyFit needs physical activity access to read the step sensor, and " +
                    "notification access to show the lock-screen card.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onRequestPermissions) { Text("Allow") }
            }
        }
    }
}
