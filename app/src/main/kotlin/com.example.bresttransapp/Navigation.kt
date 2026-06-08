package com.example.bresttransapp

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.navigation.compose.*
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import kotlinx.coroutines.launch

// Определение экранов для типизированной навигации
sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Collect : Screen("collect", "Сбор данных", Icons.Filled.DirectionsBus)
    object History : Screen("history", "История", Icons.Filled.History)
    object Profile : Screen("profile", "Профиль", Icons.Filled.Person)
    object Registration : Screen("registration", "Регистрация", Icons.Filled.Person)
}

@Composable
fun MainNavigation(
    startFromRegistration: Boolean,
    userLocation: android.location.Location?,
    onRequestLocation: () -> Unit // Команда для ручного вызова окна разрешений GPS
) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Collect, Screen.History, Screen.Profile)

    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()

    // Состояния, связанные с профилем пользователя
    var firstName by remember { mutableStateOf(prefs.getString("first_name", "") ?: "") }
    var lastName by remember { mutableStateOf(prefs.getString("last_name", "") ?: "") }
    var email by remember { mutableStateOf(prefs.getString("email", "") ?: "") }
    var patronymic by remember { mutableStateOf(prefs.getString("patronymic", "") ?: "") }

    // Хранение полей формы при переключении вкладок
    var savedCurrentStop by remember { mutableStateOf("") }
    var savedNextStop by remember { mutableStateOf("") }
    var savedPeople by remember { mutableStateOf("") }
    var savedEntered by remember { mutableStateOf("") }
    var savedExited by remember { mutableStateOf("") }

    // Загрузка локальной базы остановок один раз при старте навигации
    val stops = remember { loadStops(context) }

    // Список всех записей транспорта в оперативной памяти
    val records = remember { mutableStateListOf<TransportRecord>() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute != Screen.Registration.route

    // Общая обертка Scaffold вынесена наружу для плавных переходов
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    screens.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        // Очистка стека до корневого экрана для защиты от дубликатов
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (startFromRegistration) Screen.Registration.route else Screen.Collect.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
            exitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) },
            popEnterTransition = { androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)) },
            popExitTransition = { androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)) }
        ) {
            // Экран регистрации
            composable(Screen.Registration.route) {
                RegistrationScreen(
                    onRequestLocation = onRequestLocation, // Передаем ручной триггер GPS в экран
                    onRegister = {
                        // Переход к экрану сбора с очисткой стека регистрации
                        navController.navigate(Screen.Collect.route) {
                            popUpTo(Screen.Registration.route) { inclusive = true }
                        }
                    },
                    onDataEntered = { f: String, l: String, p: String, e: String ->
                        firstName = f
                        lastName = l
                        patronymic = p
                        email = e
                    }
                )
            }

            // Экран сбора данных
            composable(Screen.Collect.route) {
                TransportDataScreen(
                    userLocation = userLocation,
                    stops = stops,
                    currentStop = savedCurrentStop,
                    nextStop = savedNextStop,
                    peopleAtStop = savedPeople,
                    entered = savedEntered,
                    exited = savedExited,
                    onCurrentStopChange = { savedCurrentStop = it },
                    onNextStopChange = { savedNextStop = it },
                    onPeopleChange = { savedPeople = it },
                    onEnteredChange = { savedEntered = it },
                    onExitedChange = { savedExited = it },
                    onSave = { records.add(it) }
                )
            }

            // Экран истории
            composable(Screen.History.route) {
                HistoryScreen(
                    records = records,
                    onDelete = { records.remove(it) },
                    onDeleteAll = { records.clear() },
                    onUpload = {
                        coroutineScope.launch {
                            val accountName = prefs.getString("accountName", null)
                            val driveLink = prefs.getString("driveLink", "") ?: ""

                            if (accountName == null || !android.util.Patterns.EMAIL_ADDRESS.matcher(accountName).matches()) {
                                Toast.makeText(context, "Проверьте адрес почты", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            // Возвращен проверенный старый текстовый парсер ID папки из старой версии
                            val folderId = driveLink
                                .substringAfter("folders/")
                                .substringBefore("?")
                                .substringBefore("/")
                                .trim()

                            if (folderId.isEmpty() || folderId == driveLink) {
                                Toast.makeText(context, "Проверьте ссылку на папку", Toast.LENGTH_LONG).show()
                                return@launch
                            }

                            val credential = GoogleAccountCredential.usingOAuth2(
                                context,
                                listOf("https://www.googleapis.com/auth/drive.file")
                            ).apply {
                                selectedAccountName = accountName
                            }

                            // Выгрузка данных на Google Диск
                            val success = DriveUploader.uploadJsonToDrive(
                                context = context,
                                records = records.toList(),
                                folderId = folderId,
                                credential = credential,
                                firstName = firstName,
                                lastName = lastName,
                                patronymic = patronymic
                            )

                            if (success) {
                                records.clear()
                                Toast.makeText(context, "Отправлено в вашу папку!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Ошибка отправки! Проверьте адрес почты и ссылку на папку", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }

            // Экран профиля
            composable(Screen.Profile.route) {
                var name by remember { mutableStateOf(firstName) }
                var surname by remember { mutableStateOf(lastName) }
                var patronymicInput by remember { mutableStateOf(patronymic) }
                var emailInput by remember { mutableStateOf(email) }
                var driveLink by remember { mutableStateOf(prefs.getString("driveLink", "") ?: "") }

                val hasChanges by remember(name, surname, patronymicInput, emailInput, driveLink) {
                    derivedStateOf {
                        name != prefs.getString("first_name", "") ||
                                surname != prefs.getString("last_name", "") ||
                                patronymicInput != prefs.getString("patronymic", "") ||
                                emailInput != prefs.getString("email", "") ||
                                driveLink != prefs.getString("driveLink", "")
                    }
                }

                // Форма профиля с поддержкой вертикального скролла для защиты масштаба
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Профиль", style = MaterialTheme.typography.headlineSmall)

                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("Фамилия") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = patronymicInput, onValueChange = { patronymicInput = it }, label = { Text("Отчество") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = emailInput, onValueChange = { emailInput = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = driveLink, onValueChange = { driveLink = it }, label = { Text("Ссылка на Google Drive") }, modifier = Modifier.fillMaxWidth())

                    Button(
                        onClick = {
                            val cleanLink = driveLink.trim()
                            val pattern = java.util.regex.Pattern.compile("^https://drive\\.google\\.com/drive/(mobile/)?folders/[a-zA-Z0-9-_]+(\\?.*)?$")

                            if (cleanLink.isEmpty() || !pattern.matcher(cleanLink).matches()) {
                                Toast.makeText(context, "Введите корректную ссылку на диск", Toast.LENGTH_LONG).show()
                                return@Button
                            }

                            prefs.edit()
                                .putString("first_name", name.trim())
                                .putString("last_name", surname.trim())
                                .putString("patronymic", patronymicInput.trim())
                                .putString("email", emailInput.trim())
                                .putString("accountName", emailInput.trim())
                                .putString("driveLink", driveLink.trim())
                                .apply()

                            firstName = name.trim()
                            lastName = surname.trim()
                            patronymic = patronymicInput.trim()
                            email = emailInput.trim()

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




