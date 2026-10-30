package com.carlogbook.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.carlogbook.app.data.LastCar

@Composable
fun AppNav(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val context = LocalContext.current

    // On a fresh start, jump straight to the car you used last (Back still goes to the garage).
    var openedLastCar by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!openedLastCar) {
            openedLastCar = true
            val lastId = LastCar.get(context)
            if (lastId > 0L && vm.getCar(lastId) != null) {
                nav.navigate("history/$lastId")
            }
        }
    }

    NavHost(navController = nav, startDestination = "cars") {

        composable("cars") {
            CarsScreen(
                vm = vm,
                onOpenCar = { nav.navigate("history/$it") },
                onAddCar = { nav.navigate("car_edit/0") },
                onEditCar = { nav.navigate("car_edit/$it") },
                onBackup = { nav.navigate("backup") }
            )
        }

        composable(
            route = "history/{carId}",
            arguments = listOf(navArgument("carId") { type = NavType.LongType })
        ) { entry ->
            val carId = entry.arguments?.getLong("carId") ?: 0L
            HistoryScreen(
                vm = vm,
                carId = carId,
                onBack = { nav.popBackStack() },
                onAddService = { nav.navigate("service_edit/$carId/0") },
                onEditService = { serviceId -> nav.navigate("service_edit/$carId/$serviceId") },
                onUpcoming = { nav.navigate("upcoming/$carId") },
                onEditCar = { nav.navigate("car_edit/$carId") }
            )
        }

        composable(
            route = "service_edit/{carId}/{serviceId}",
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("serviceId") { type = NavType.LongType }
            )
        ) { entry ->
            ServiceEditScreen(
                vm = vm,
                carId = entry.arguments?.getLong("carId") ?: 0L,
                serviceId = entry.arguments?.getLong("serviceId") ?: 0L,
                onDone = { nav.popBackStack() }
            )
        }

        composable(
            route = "car_edit/{carId}",
            arguments = listOf(navArgument("carId") { type = NavType.LongType })
        ) { entry ->
            CarEditScreen(
                vm = vm,
                carId = entry.arguments?.getLong("carId") ?: 0L,
                onDone = { nav.popBackStack() },
                onDeleted = { nav.popBackStack("cars", inclusive = false) }
            )
        }

        composable("backup") {
            BackupScreen(vm = vm, onBack = { nav.popBackStack() })
        }

        composable(
            route = "upcoming/{carId}",
            arguments = listOf(navArgument("carId") { type = NavType.LongType })
        ) { entry ->
            UpcomingScreen(
                vm = vm,
                carId = entry.arguments?.getLong("carId") ?: 0L,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
