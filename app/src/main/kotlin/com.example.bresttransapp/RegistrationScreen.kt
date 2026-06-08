package com.example.bresttransapp

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.PermissionStatus
import java.util.regex.Pattern

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RegistrationScreen(
    onRequestLocation: () -> Unit,                   // Команда для запуска системного окна GPS из MainActivity
    onRegister: () -> Unit,                          // Колбэк, вызывается после успешной регистрации
    onDataEntered: (String, String, String, String) -> Unit  // Колбэк для передачи введённых данных пользователя
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

    // Состояния для текстовых полей ввода
    var name by remember { mutableStateOf("") }
    var surname by remember { mutableStateOf("") }
    var patronymic by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var driveLink by remember { mutableStateOf("") }

    // Отслеживание состояния системного разрешения на геоданные
    val permissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val locationGranted = permissionState.status is PermissionStatus.Granted

    // Функция валидации электронной почты стандартным паттерном Android
    fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // Возвращен оригинальный жесткий паттерн проверки ссылки из старой версии
    fun isValidDriveLink(link: String): Boolean {
        val pattern = Pattern.compile("^https://drive\\.google\\.com/drive/(mobile/)?folders/[a-zA-Z0-9-_]+(\\?.*)?$")
        return pattern.matcher(link).matches()
    }

    // Проверка заполнения обязательных текстовых полей
    val allFieldsFilled = name.isNotBlank() && email.isNotBlank() && driveLink.isNotBlank()


    // Интерфейс экрана регистрации с поддержкой прокрутки для защиты масштаба
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState()), // Защита от уплывания кнопок под экран
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Регистрация", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))

            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя*") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = surname, onValueChange = { surname = it }, label = { Text("Фамилия*") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = patronymic, onValueChange = { patronymic = it }, label = { Text("Отчество*") }, modifier = Modifier.fillMaxWidth())

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Электронная почта*") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )

            OutlinedTextField(
                value = driveLink,
                onValueChange = { driveLink = it },
                label = { Text("Ссылка на папку Google Drive*") },
                modifier = Modifier.fillMaxWidth()
            )

            // Кнопка запроса геолокации по требованию пользователя
            Button(
                onClick = { onRequestLocation() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (locationGranted)
                        "Геолокация разрешена ✅"
                    else
                        "Разрешить доступ к геолокации"
                )
            }

            // Кнопка подтверждения регистрации
            Button(
                onClick = {
                    val cleanEmail = email.trim()
                    val cleanDriveLink = driveLink.trim()

                    if (name.isBlank() || surname.isBlank() || patronymic.isBlank()){
                        Toast.makeText(context, "Введите ФИО полностью", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (cleanEmail.isEmpty() || !isValidEmail(cleanEmail)){
                        Toast.makeText(context, "Введите корректный адрес почты", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (cleanDriveLink.isEmpty() || !isValidDriveLink(cleanDriveLink)) {
                        Toast.makeText(context, "Введите корректную ссылку на диск", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    if (!locationGranted) {
                        Toast.makeText(context, "Необходимо разрешить доступ к геолокации", Toast.LENGTH_LONG).show()
                        return@Button
                    }


                    // Сохранение настроек в SharedPreferences устройства
                    prefs.edit()
                        .putBoolean("is_registered", true)
                        .putString("first_name", name.trim())
                        .putString("last_name", surname.trim())
                        .putString("patronymic", patronymic.trim())
                        .putString("email", cleanEmail)
                        .putString("accountName", cleanEmail)
                        .putString("driveLink", cleanDriveLink)
                        .apply()

                    Toast.makeText(context, "Профиль сохранен", Toast.LENGTH_SHORT).show()

                    onDataEntered(name.trim(), surname.trim(), patronymic.trim(), cleanEmail)
                    onRegister()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = true
            ) {
                Text("Зарегистрироваться")
            }
        }
    }
}
