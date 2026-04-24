package com.example.bresttransapp

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential

// Вспомогательный объект для работы с авторизацией Google Drive
object DriveAuthHelper {

    // Разрешения (scopes) — только доступ к собственным файлам на диске
    private val SCOPES = listOf(Scope("https://www.googleapis.com/auth/drive.file"))

    /**
     * Возвращает Intent для входа в аккаунт Google, чтобы получить доступ к Google Drive.
     */
    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()                      // Запрашиваем доступ к email
            .requestScopes(SCOPES.first(), *SCOPES.drop(1).toTypedArray()) // Запрашиваем доступ к Google Drive
            .build()

        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent // Возвращаем Intent для запуска активности входа
    }

    /**
     * Обрабатывает результат входа в аккаунт Google и передаёт аккаунт в колбэк.
     */
    fun handleSignInResult(data: Intent?, onResult: (GoogleSignInAccount?) -> Unit) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            onResult(task.result) // Успешный результат — передаём аккаунт
        } catch (e: Exception) {
            onResult(null) // Ошибка — возвращаем null
        }
    }

    /**
     * Возвращает объект учетных данных GoogleAccountCredential,
     * который можно использовать для Drive API.
     */
    fun getCredential(context: Context, account: GoogleSignInAccount): GoogleAccountCredential {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, SCOPES.map { it.scopeUri } // Преобразуем список Scope в список URI
        )
        credential.selectedAccount = account.account // Устанавливаем выбранный аккаунт
        return credential
    }
}
