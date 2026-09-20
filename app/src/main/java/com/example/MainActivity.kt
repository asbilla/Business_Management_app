package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.repository.TransactionRepository
import com.example.notification.NotificationHelper
import com.example.ui.appointments.AppointmentsScreen
import com.example.ui.balancesheet.BalanceSheetScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.entry.EntryScreen
import com.example.ui.offers.SpecialOffersScreen
import com.example.ui.setup.MenuManagementScreen
import com.example.ui.setup.SetupScreen
import com.example.ui.theme.MyApplicationTheme

data class CallerBookingInfo(
    val callerName: String,
    val callerPhone: String
)

class MainActivity : ComponentActivity() {

    private val pendingCallerBooking = mutableStateOf<CallerBookingInfo?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ensure Notification Channel is created for booking alerts and reminders
        NotificationHelper.createNotificationChannel(applicationContext)

        handleCallerIntent(intent)

        val repository = TransactionRepository.getInstance(applicationContext)
        val navigateTo = intent?.getStringExtra("navigate_to")
        val isAutoBook = intent?.getBooleanExtra("auto_book", false) == true
        val startDestination = if (navigateTo == "appointments" || isAutoBook) Screen.Appointments.route else Screen.Dashboard.route
        
        setContent {
            val themeMode by repository.themeMode.collectAsState(initial = repository.getThemeMode())
            
            MyApplicationTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        repository = repository,
                        startDestination = startDestination,
                        pendingCallerBooking = pendingCallerBooking.value,
                        onClearCallerBooking = {
                            pendingCallerBooking.value = null
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleCallerIntent(intent)
    }

    private fun handleCallerIntent(intent: Intent?) {
        if (intent == null) return
        val isAutoBook = intent.getBooleanExtra("auto_book", false)
        val callerName = intent.getStringExtra("caller_name").orEmpty()
        val callerPhone = intent.getStringExtra("caller_phone").orEmpty()

        if (isAutoBook || (callerName.isNotBlank() && callerPhone.isNotBlank())) {
            pendingCallerBooking.value = CallerBookingInfo(
                callerName = callerName,
                callerPhone = callerPhone
            )
        }
    }
}

sealed class Screen(val route: String) {
    data object Setup : Screen("setup")
    data object Dashboard : Screen("dashboard")
    data object BalanceSheet : Screen("balancesheet")
    data object Appointments : Screen("appointments")
    data object MenuManagement : Screen("menu_management")
    data object SpecialOffers : Screen("special_offers")
    data object StatementSelection : Screen("statement_selection")
    data object Entry : Screen("entry/{entryType}") {
        fun createRoute(entryType: String): String = "entry/${Uri.encode(entryType)}"
    }
}

@Composable
fun AppNavigation(
    repository: TransactionRepository,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Dashboard.route,
    pendingCallerBooking: CallerBookingInfo? = null,
    onClearCallerBooking: () -> Unit = {}
) {
    val navController = rememberNavController()

    // Automatically navigate to Appointments when an incoming call book action is triggered
    LaunchedEffect(pendingCallerBooking) {
        if (pendingCallerBooking != null) {
            if (navController.currentDestination?.route != Screen.Appointments.route) {
                navController.navigate(Screen.Appointments.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        // Setup Screen
        composable(Screen.Setup.route) {
            SetupScreen(
                repository = repository,
                onConfigured = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                },
                onNavigateToMenuManagement = {
                    navController.navigate(Screen.MenuManagement.route)
                },
                onNavigateBack = if (navController.previousBackStackEntry != null) {
                    { navController.popBackStack() }
                } else null
            )
        }

        // Main Dashboard Screen
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                repository = repository,
                onNavigateToEntry = { entryType ->
                    navController.navigate(Screen.Entry.createRoute(entryType))
                },
                onNavigateToBalanceSheet = {
                    navController.navigate(Screen.BalanceSheet.route)
                },
                onNavigateToAppointments = {
                    navController.navigate(Screen.Appointments.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Setup.route)
                },
                onNavigateToMenuManagement = {
                    navController.navigate(Screen.MenuManagement.route)
                },
                onNavigateToSpecialOffers = {
                    navController.navigate(Screen.SpecialOffers.route)
                }
            )
        }

        // Special Offers & Promo Broadcast Screen
        composable(Screen.SpecialOffers.route) {
            SpecialOffersScreen(
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Menu & Services Management Screen
        composable(Screen.MenuManagement.route) {
            MenuManagementScreen(
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Appointments & Phone Bookings Screen
        composable(Screen.Appointments.route) {
            AppointmentsScreen(
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Setup.route)
                },
                initialCallerName = pendingCallerBooking?.callerName,
                initialCallerPhone = pendingCallerBooking?.callerPhone,
                autoOpenBooking = pendingCallerBooking != null,
                onClearAutoBooking = onClearCallerBooking
            )
        }

        // Transaction Entry Input Screen
        composable(
            route = Screen.Entry.route,
            arguments = listOf(
                navArgument("entryType") {
                    type = NavType.StringType
                    defaultValue = "Daily Income"
                }
            )
        ) { backStackEntry ->
            val rawType = backStackEntry.arguments?.getString("entryType") ?: "Daily Income"
            val decodedType = Uri.decode(rawType)
            EntryScreen(
                entryType = decodedType,
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Day-by-Day Balance Sheet Screen
        composable(Screen.BalanceSheet.route) {
            BalanceSheetScreen(
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToStatementSelection = {
                    navController.navigate(Screen.StatementSelection.route)
                }
            )
        }

        // Statement Selection Screen
        composable(Screen.StatementSelection.route) {
            com.example.ui.balancesheet.StatementSelectionScreen(
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
