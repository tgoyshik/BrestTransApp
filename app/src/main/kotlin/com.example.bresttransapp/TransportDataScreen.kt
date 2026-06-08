package com.example.bresttransapp

import android.os.SystemClock
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import androidx.compose.ui.platform.LocalSoftwareKeyboardController


// Описание структуры остановки транспорта из локального JSON справочника
data class StopEntry(val name: String, val moveto: String, val x: String, val y: String)

// Модель собираемой записи для новой стационарной задачи на остановке
data class TransportRecord(
    val time: String,
    val currentStop: String,
    val nextStop: String,
    val peopleAtStop: String,
    val entered: String,
    val exited: String,
    val latitude: String,
    val longitude: String,
    val weather: String
)

// Модели ответов сервера OpenWeatherMap API
data class WeatherResponse(val weather: List<Weather>, val main: Main)
data class Weather(val main: String, val description: String)
data class Main(val temp: Float)

// Интерфейс сетевых запросов к OpenWeatherMap API
interface WeatherApi {
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("lat") latitude: String,
        @Query("lon") longitude: String,
        @Query("appid") apiKey: String,
        @Query("lang") lang: String = "ru",
        @Query("units") units: String = "metric"
    ): WeatherResponse
}

/**
 * Suspend-функция для безопасного получения текущей погоды по координатам
 */
private val retrofit = Retrofit.Builder()
    .baseUrl("https://api.openweathermap.org/data/2.5/")
    .addConverterFactory(GsonConverterFactory.create())
    .build()

private val weatherApi = retrofit.create(WeatherApi::class.java)
suspend fun fetchWeather(lat: String, lon: String, context: Context): String {
    return try {

        val response = weatherApi.getCurrentWeather(lat, lon, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
        val description = response.weather.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: "Неизвестно"
        val temperature = response.main.temp
        "$description, ${temperature}°C"
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка загрузки погоды", Toast.LENGTH_SHORT).show()
        "Ошибка"
    }
}

@Composable
fun TransportDataScreen(
    modifier: Modifier = Modifier,
    userLocation: android.location.Location?,
    stops: List<StopEntry>,
    currentStop: String,
    nextStop: String,
    peopleAtStop: String,
    entered: String,
    exited: String,
    onCurrentStopChange: (String) -> Unit,
    onNextStopChange: (String) -> Unit,
    onPeopleChange: (String) -> Unit,
    onEnteredChange: (String) -> Unit,
    onExitedChange: (String) -> Unit,
    onSave: (TransportRecord) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()


    // Состояния для работы пятиминутного таймера блокировки
    var timeLeft by remember { mutableIntStateOf(0) }
    var isTimerRunning by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE) }

    // Эффект восстановления таймера при перезапуске приложения
    LaunchedEffect(Unit) {
        val lastSaveTime = prefs.getLong("last_save_time", 0L)
        val currentTime = SystemClock.elapsedRealtime()
        val elapsedSeconds = (currentTime - lastSaveTime) / 1000

        if (lastSaveTime > 0 && elapsedSeconds < 300) {
            timeLeft = (300 - elapsedSeconds).toInt()
            isTimerRunning = true
        } else {
            timeLeft = 0
            isTimerRunning = false
        }
    }

    // Фоновый цикл отсчета времени работы таймера
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning && timeLeft > 0) {
            val startTime = SystemClock.elapsedRealtime()
            val totalSecondsAtStart = timeLeft

            while (isTimerRunning && timeLeft > 0) {
                val elapsedSeconds = (SystemClock.elapsedRealtime() - startTime) / 1000
                val newTimeLeft = totalSecondsAtStart - elapsedSeconds.toInt()

                if (newTimeLeft != timeLeft) {
                    timeLeft = if (newTimeLeft > 0) newTimeLeft else 0
                }

                if (timeLeft <= 0) {
                    isTimerRunning = false
                }
                delay(500L)
            }
        }
    }

    // Расчет расстояния по GPS
    val matchingStops = stops.filter { it.name == currentStop }

    val maxDistance = 50000

    val isAtStop = if (matchingStops.isNotEmpty() && userLocation != null) {
        matchingStops.any { stop ->
            val dist = FloatArray(1)
            android.location.Location.distanceBetween(
                userLocation.latitude, userLocation.longitude,
                stop.y.toDoubleOrNull() ?: 0.0,
                stop.x.toDoubleOrNull() ?: 0.0,
                dist
            )
            dist[0] <= maxDistance
        }
    } else false

    // Проверка заполнения всех обязательных полей цифрами
    val allFieldsFilled = currentStop.isNotBlank() && nextStop.isNotBlank() &&
            listOf(peopleAtStop, entered, exited).all { it.isNotBlank() && it.matches(Regex("\\d+")) }

    // Контейнер формы с вертикальной прокруткой для полной защиты масштаба
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AutoCompleteTextField(
            label = "Текущая остановка",
            value = currentStop,
            onValueChange = { onCurrentStopChange(it) },
            options = stops.map { it.name }.distinct(),
            enabled = !isTimerRunning
        )

        AutoCompleteTextField(
            label = "Следующая остановка",
            value = nextStop,
            onValueChange = { onNextStopChange(it) },
            options = stops.map { it.name }.distinct(),
            enabled = !isTimerRunning
        )

        OutlinedTextField(
            value = peopleAtStop,
            onValueChange = { if (it.all(Char::isDigit)) onPeopleChange(it) },
            label = { Text("Заполненность остановки") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTimerRunning,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = entered,
            onValueChange = { if (it.all(Char::isDigit)) onEnteredChange(it) },
            label = { Text("Вошло") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTimerRunning,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )

        OutlinedTextField(
            value = exited,
            onValueChange = { if (it.all(Char::isDigit)) onExitedChange(it) },
            label = { Text("Вышло") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTimerRunning,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(32.dp))

        // Кнопка сохранения собранных данных
        Button(
            onClick = {
                if (!allFieldsFilled) {
                    Toast.makeText(context, "Заполните все поля", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // Проверка включенных геоданных на устройстве
                if (userLocation == null) {
                    Toast.makeText(context, "Включите геолокацию на устройстве", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                // Проверка нахождения пользователя в радиусе остановки
                if (!isAtStop) {
                    Toast.makeText(context, "Вы находитесь слишком далеко от остановки", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                coroutineScope.launch {
                    val exactTime = System.currentTimeMillis()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("GMT+3")
                    }
                    val time = sdf.format(Date(exactTime))

                    // Поиск ближайших координат выбранной остановки
                    val matchingStopsForSave = stops.filter { it.name == currentStop }
                    val stopEntry = matchingStopsForSave.minByOrNull { stop ->
                        val dist = FloatArray(1)
                        android.location.Location.distanceBetween(
                            userLocation?.latitude ?: 0.0,
                            userLocation?.longitude ?: 0.0,
                            stop.y.toDoubleOrNull() ?: 0.0,
                            stop.x.toDoubleOrNull() ?: 0.0,
                            dist
                        )
                        dist[0]
                    }

                    val latitude = stopEntry?.y ?: "0.0"
                    val longitude = stopEntry?.x ?: "0.0"
                    val weather = fetchWeather(latitude, longitude, context)

                    // Передача собранной записи в общий список истории
                    onSave(
                        TransportRecord(
                            time = time,
                            currentStop = currentStop,
                            nextStop = nextStop,
                            peopleAtStop = peopleAtStop,
                            entered = entered,
                            exited = exited,
                            latitude = latitude,
                            longitude = longitude,
                            weather = weather
                        )
                    )

                    // Фиксация времени сохранения для защиты от перезапуска
                    prefs.edit().putLong("last_save_time", SystemClock.elapsedRealtime()).apply()
                    Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()

                    // Запуск таймера блокировки и очистка числовых полей
                    timeLeft = 300
                    isTimerRunning = true
                    onPeopleChange("")
                    onEnteredChange("")
                    onExitedChange("")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .padding(bottom = 24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = !isTimerRunning
        ) {
            Text(
                text = if (isTimerRunning) {
                    val minutes = timeLeft / 60
                    val seconds = timeLeft % 60
                    "Ждите ${String.format("%d:%02d", minutes, seconds)}"
                } else {
                    "Сохранить"
                },
                maxLines = 1
            )
        }
    }
}

@Composable
fun AutoCompleteTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var filteredOptions by remember { mutableStateOf(emptyList<String>()) }
    val coroutineScope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Контроллер для принудительного закрытия клавиатуры
    val keyboardController = LocalSoftwareKeyboardController.current

    // Автоматический сброс тяжелых процессов поиска при уходе с экрана
    DisposableEffect(Unit) {
        onDispose {
            debounceJob?.cancel()
            expanded = false
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                debounceJob?.cancel()
                debounceJob = coroutineScope.launch {
                    delay(500) // Задержка для предотвращения частой фильтрации
                    filteredOptions = options.filter { option ->
                        option.contains(it, ignoreCase = true) && it.isNotBlank()
                    }
                    expanded = filteredOptions.isNotEmpty()
                }
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        )
        DropdownMenu(
            expanded = expanded && filteredOptions.isNotEmpty(),
            onDismissRequest = { expanded = false }
        ) {
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Загружает и парсит базу данных остановок из локального JSON файла в assets
 */
fun loadStops(context: Context): List<StopEntry> {
    return try {
        val inputStream = context.assets.open("astops_with_next.json")
        val reader = InputStreamReader(inputStream)
        val type = object : TypeToken<List<StopEntry>>() {}.type
        Gson().fromJson(reader, type)
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Вычисляет расстояние в метрах между двумя точками на карте
 */
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
    return results[0]
}
