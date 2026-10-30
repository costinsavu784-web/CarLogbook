package com.carlogbook.app.ui

import android.net.Uri
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
            CarScreen(
                vm = vm,
                carId = carId,
                onBack = { nav.popBackStack() },
                onAddService = { nav.navigate("service_edit/$carId/0") },
                onEditService = { serviceId -> nav.navigate("service_edit/$carId/$serviceId") },
                onEditCar = { nav.navigate("car_edit/$carId") },
                onOpenCategory = { categoryId -> nav.navigate("category/$carId/$categoryId") }
            )
        }

        composable(
            route = "category/{carId}/{categoryId}",
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("categoryId") { type = NavType.StringType }
            )
        ) { entry ->
            val carId = entry.arguments?.getLong("carId") ?: 0L
            CategoryScreen(
                vm = vm,
                carId = carId,
                categoryId = entry.arguments?.getString("categoryId") ?: "",
                onBack = { nav.popBackStack() },
                onOpenComponent = { name ->
                    nav.navigate("component/$carId/${Uri.encode(name)}")
                }
            )
        }

        composable(
            route = "component/{carId}/{name}",
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { entry ->
            val carId = entry.arguments?.getLong("carId") ?: 0L
            val name = entry.arguments?.getString("name") ?: ""
            ComponentScreen(
                vm = vm,
                carId = carId,
                componentName = name,
                onBack = { nav.popBackStack() },
                onAddService = { nav.navigate("service_edit/$carId/0?job=${Uri.encode(name)}") },
                onEditService = { serviceId -> nav.navigate("service_edit/$carId/$serviceId") }
            )
        }

        composable(
            route = "service_edit/{carId}/{serviceId}?job={job}",
            arguments = listOf(
                navArgument("carId") { type = NavType.LongType },
                navArgument("serviceId") { type = NavType.LongType },
                navArgument("job") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            ServiceEditScreen(
                vm = vm,
                carId = entry.arguments?.getLong("carId") ?: 0L,
                serviceId = entry.arguments?.getLong("serviceId") ?: 0L,
                initialJob = entry.arguments?.getString("job"),
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
    }
}
