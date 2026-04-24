package com.example.bresttransapp

import android.Manifest
import android.content.Context
import android.location.Location
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.regex.Pattern
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationScreen(
    onRegister: () -> Unit,                          // Колбэк, вызывается после регистрации
    onDataEntered: (String, String, String) -> Unit  // Колбэк для передачи введённых данных (имя, фамилия, email)
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    val coroutineScope = rememberCoroutineScope()

    // Состояния для ввода
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var patronymic by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var driveLink by remember { mutableStateOf("") }
    var locationGranted by remember { mutableStateOf(false) }

    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Проверка наличия доступа к геолокации
    suspend fun checkLocationPermission(context: Context): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        continuation.resume(location != null)
                    }
                    .addOnFailureListener {
                        continuation.resume(false)
                    }
            } catch (e: SecurityException) {
                continuation.resume(false)
            }
        }
    }

    // Проверка email
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // Проверка ссылки на Google Drive
    fun isValidDriveLink(link: String): Boolean {
        val pattern = Pattern.compile("^https://drive\\.google\\.com/drive/folders/[^\\s]+$")
        return pattern.matcher(link).matches()
    }

    // Все поля должны быть валидны
    val allFieldsValid = name.isNotBlank() &&
            isValidEmail(email) &&
            driveLink.isNotBlank() &&
            isValidDriveLink(driveLink)

    // Эффект запуска при изменении статуса разрешения
    LaunchedEffect(permissionState.status) {
        locationGranted = permissionState.status is PermissionStatus.Granted
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя*") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("Фамилия") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = patronymic, onValueChange = { patronymic = it }, label = { Text("Отчество (необязательно)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Электронная почта*") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(value = driveLink, onValueChange = { driveLink = it }, label = { Text("Ссылка на папку Google Drive*") }, modifier = Modifier.fillMaxWidth())

            // Кнопка разрешения геолокации
            Button(
                onClick = {
                    coroutineScope.launch {
                        permissionState.launchPermissionRequest()
                        kotlinx.coroutines.delay(300) // Ждём, пока статус обновится
                        locationGranted = permissionState.status is PermissionStatus.Granted
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (locationGranted)
                        "Геолокация разрешена ✅"
                    else
                        "Разрешить доступ к геолокации"
                )
            }

            // Кнопка регистрации
            Button(
                onClick = {
                    coroutineScope.launch {
                        if (!allFieldsValid) {
                            Toast.makeText(context, "Введите корректную почту и ссылку на папку Google Drive", Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        if (!locationGranted) {
                            Toast.makeText(context, "Необходимо разрешить доступ к геолокации", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        // Сохраняем данные в SharedPreferences
                        prefs.edit()
                            .putBoolean("is_registered", true)
                            .putString("first_name", name)
                            .putString("last_name", surname)
                            .putString("email", email)
                            .putString("driveLink", driveLink)
                            .apply()

                        // Передаём данные в верхний уровень и переходим к следующему экрану
                        onDataEntered(name, surname, email)
                        onRegister()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = allFieldsValid && locationGranted
            ) {
                Text("Зарегистрироваться")
            }
        }
    }
}
