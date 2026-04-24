package com.example.bresttransapp

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Collect : Screen("collect", "Сбор данных", Icons.Filled.DirectionsBus)
    object History : Screen("history", "История", Icons.Filled.History)
    object Profile : Screen("profile", "Профиль", Icons.Filled.Person)
    object Registration : Screen("registration", "Регистрация", Icons.Filled.Person)
}

@Composable
fun MainNavigation(startFromRegistration: Boolean, userLocation: android.location.Location?) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Collect, Screen.History, Screen.Profile)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()

    // Состояния, связанные с профилем пользователя
    var firstName by remember { mutableStateOf(prefs.getString("first_name", "") ?: "") }
    var lastName by remember { mutableStateOf(prefs.getString("last_name", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }

    // Список всех записей транспорта
    val records = remember { mutableStateListOf<TransportRecord>() }

    NavHost(
        navController = navController,
        startDestination = if (startFromRegistration) Screen.Registration.route else Screen.Collect.route
    ) {
        // Экран регистрации
        composable(Screen.Registration.route) {
            RegistrationScreen(
                onRegister = {
                    // После регистрации переходим на экран сбора
                    navController.navigate(Screen.Collect.route) {
                        popUpTo(Screen.Registration.route) { inclusive = true }
                    }
                },
                onDataEntered = { f: String, l: String, e: String ->
                    // Сохраняем введённые данные пользователя
                    firstName = f
                    lastName = l
                    email = e
                }
            )
        }

        // Экран сбора данных
        composable(Screen.Collect.route) {
            ScaffoldWithBottomBar(navController = navController, currentRoute = currentRoute) {
                TransportDataScreen(
                    userLocation = userLocation,
                    onSave = { records.add(it) }
                ) // Добавление записи
            }
        }

        // Экран истории
        composable(Screen.History.route) {
            ScaffoldWithBottomBar(navController = navController, currentRoute = currentRoute) {
                HistoryScreen(
                    records = records,
                    onDelete = { records.remove(it) },
                    onDeleteAll = { records.clear() },
                    onExport = { /* экспорт в JSON реализован в HistoryScreen */ },

                    onUpload = {
                        coroutineScope.launch {
                            val accountName = prefs.getString("accountName", null)
                            val driveLink = prefs.getString("driveLink", "") ?: ""

                            val folderId = driveLink
                                .substringAfter("folders/")
                                .substringBefore("/")
                                .substringBefore("?")
                                .trim()

                            if (accountName == null || folderId.isEmpty()) {
                                Toast.makeText(context, "Проверьте аккаунт и ссылку в профиле", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val credential = GoogleAccountCredential.usingOAuth2(
                                context,
                                listOf("https://www.googleapis.com/auth/drive.file")

                            ).apply {
                                selectedAccountName = accountName
                            }

                            val success = DriveUploader.uploadJsonToDrive(
                                context = context,
                                records = records.toList(),
                                folderId = folderId,
                                credential = credential
                            )

                            if (success) {
                                records.clear()
                                Toast.makeText(context, "Отправлено в вашу папку!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Ошибка. Проверьте ссылку на папку", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }

        // Экран профиля
        composable(Screen.Profile.route) {
            ScaffoldWithBottomBar(navController = navController, currentRoute = currentRoute) {
                var name by remember { mutableStateOf(firstName) }
                var surname by remember { mutableStateOf(lastName) }
                var emailInput by remember { mutableStateOf(email) }
                var driveLink by remember { mutableStateOf(prefs.getString("driveLink", "") ?: "") }

                // Проверка на изменения для активации кнопки
                val hasChanges by remember(name, surname, emailInput, driveLink) {
                    derivedStateOf {
                        name != prefs.getString("first_name", "") ||
                                surname != prefs.getString("last_name", "") ||
                                emailInput != prefs.getString("email", "") ||
                                driveLink != prefs.getString("driveLink", "")
                    }
                }

                // Форма профиля
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Профиль", style = MaterialTheme.typography.headlineSmall)

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Имя") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = surname,
                        onValueChange = { surname = it },
                        label = { Text("Фамилия") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = driveLink,
                        onValueChange = { driveLink = it },
                        label = { Text("Ссылка на Google Drive") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Кнопка сохранения профиля
                    Button(
                        onClick = {
                            prefs.edit()
                                .putString("first_name", name)
                                .putString("last_name", surname)
                                .putString("email", emailInput)
                                .putString("accountName", emailInput)
                                .putString("driveLink", driveLink)
                                .apply()

                            // Обновляем переменные
                            firstName = name
                            lastName = surname
                            email = emailInput

                            Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()
                        },
                        enabled = hasChanges,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Сохранить изменения")
                    }
                }
            }
        }
    }
}

@Composable
fun ScaffoldWithBottomBar(
    navController: NavHostController,
    currentRoute: String?,
    content: @Composable () -> Unit
) {
    val screens = listOf(Screen.Collect, Screen.History, Screen.Profile)

    // Обёртка с нижней панелью навигации
    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                launchSingleTop = true // чтобы не создавать копии экрана в back stack
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Вставка контента с учётом внутренних отступов
        Box(Modifier.padding(innerPadding)) {
            content()
        }
    }
}
