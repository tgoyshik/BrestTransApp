package com.example.bresttransapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.bresttransapp.ui.theme.BrestTransAppTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


class MainActivity : ComponentActivity() {

    // Регистрируем обработчик результата входа в аккаунт Google
    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data

            // Обрабатываем результат входа
            DriveAuthHelper.handleSignInResult(data) { account ->
                account?.let {
                    // Сохраняем имя аккаунта в SharedPreferences
                    val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("accountName", it.account?.name).apply()
                }
            }
        }
    }

    private lateinit var fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient
    private var userLocation by mutableStateOf<android.location.Location?>(null)

    // Запрос разрешений на GPS
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startLocationUpdates()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(this)
        locationPermissionRequest.launch(arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ))

        // Получаем SharedPreferences для хранения состояния регистрации и аккаунта
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Проверяем, был ли пользователь уже зарегистрирован ранее
        val isRegistered = prefs.getBoolean("is_registered", false)

        // Проверяем, уже ли выполнен вход в Google аккаунт
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null) {
            // Если нет — запускаем интент входа
            val signInIntent = DriveAuthHelper.getSignInIntent(this)
            signInLauncher.launch(signInIntent)
        } else {
            // Если аккаунт найден — сохраняем имя в prefs
            prefs.edit().putString("accountName", account.account?.name).apply()
        }

        setContent {
            BrestTransAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainNavigation(
                        startFromRegistration = !isRegistered,
                        userLocation = userLocation
                    )
                }
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 5000
        ).build()

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                userLocation = result.lastLocation
            }
        }

        if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, android.os.Looper.getMainLooper())
        }
    }

}
