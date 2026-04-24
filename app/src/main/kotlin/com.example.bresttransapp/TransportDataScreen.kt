    package com.example.bresttransapp

    import android.os.SystemClock
    import android.content.Context
    import android.widget.Toast
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.filled.ArrowDropDown
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

    // Данные остановок из JSON
    data class StopEntry(val name: String, val moveto: String, val x: String, val y: String)

    // Запись о транспорте
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

    // Ответ от OpenWeatherMap
    data class WeatherResponse(val weather: List<Weather>, val main: Main)
    data class Weather(val main: String, val description: String)
    data class Main(val temp: Float)

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

    suspend fun fetchWeather(lat: String, lon: String, context: Context): String {
        return try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.openweathermap.org/data/2.5/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            val api = retrofit.create(WeatherApi::class.java)
            val response = api.getCurrentWeather(lat, lon, BuildConfig.OPEN_WEATHER_MAP_API_KEY)
            val description = response.weather.firstOrNull()?.description?.replaceFirstChar { it.uppercase() } ?: "Неизвестно"
            val temperature = response.main.temp
            "$description, ${temperature}°C"
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка загрузки погоды", Toast.LENGTH_SHORT).show()
            "Ошибка"
        }
    }


    @Composable
    fun TransportDataScreen(modifier: Modifier = Modifier, userLocation: android.location.Location?, onSave: (TransportRecord) -> Unit) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val stops = remember { loadStops(context) }

        var currentStop by remember { mutableStateOf("") }
        var nextStop by remember { mutableStateOf("") }
        var peopleAtStop by remember { mutableStateOf("") }
        var entered by remember { mutableStateOf("") }
        var exited by remember { mutableStateOf("") }

        var timeLeft by remember { mutableIntStateOf(0) }
        var isTimerRunning by remember { mutableStateOf(false) }

        val prefs = remember { context.getSharedPreferences("timer_prefs", Context.MODE_PRIVATE) }

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

        // Логика отсчета
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

        val matchingStops = stops.filter { it.name == currentStop }
        val isAtStop = if (matchingStops.isNotEmpty() && userLocation != null) {
            matchingStops.any { stop ->
                val dist = FloatArray(1)
                android.location.Location.distanceBetween(
                    userLocation.latitude, userLocation.longitude,
                    stop.y.toDoubleOrNull() ?: 0.0,
                    stop.x.toDoubleOrNull() ?: 0.0,
                    dist
                )
                dist[0] <= 50

            }
        } else false

        val allFieldsFilled = currentStop.isNotBlank() && nextStop.isNotBlank() &&
                listOf(
                    peopleAtStop,
                    entered,
                    exited
                ).all { it.isNotBlank() && it.matches(Regex("\\d+")) }


        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            AutoCompleteTextField("Текущая остановка", currentStop, { currentStop = it }, stops.map { it.name }.distinct(), !isTimerRunning)

            AutoCompleteTextField(
                "Следующая остановка",
                nextStop,
                { nextStop = it },
                stops.map { it.name }.distinct(),
                !isTimerRunning
            )


            OutlinedTextField(
                value = peopleAtStop,
                onValueChange = { if (it.all(Char::isDigit)) peopleAtStop = it },
                label = { Text("Заполненность остановки") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTimerRunning,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = entered,
                onValueChange = { if (it.all(Char::isDigit)) entered = it },
                label = { Text("Вошло") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTimerRunning,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )

            OutlinedTextField(
                value = exited,
                onValueChange = { if (it.all(Char::isDigit)) exited = it },
                label = { Text("Вышло") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTimerRunning,
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (!allFieldsFilled) {
                        Toast.makeText(context, "Пожалуйста, заполните все поля корректно", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch {

                        val exactTime = System.currentTimeMillis()

                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("GMT+3")
                        }
                        val time = sdf.format(Date(exactTime))

                        val matchingStops = stops.filter { it.name == currentStop }
                        val stopEntry = matchingStops
                            .minByOrNull { stop ->
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
                        prefs.edit().putLong("last_save_time", SystemClock.elapsedRealtime()).apply()

                        Toast.makeText(context, "Сохранено", Toast.LENGTH_SHORT).show()

                        timeLeft = 300
                        isTimerRunning = true
                        peopleAtStop = ""
                        entered = ""
                        exited = ""

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
                enabled = allFieldsFilled && !isTimerRunning && isAtStop
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

        Column {
            OutlinedTextField(
                value = value,
                onValueChange = {
                    onValueChange(it)
                    debounceJob?.cancel()
                    debounceJob = coroutineScope.launch {
                        delay(500)
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

    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    }

