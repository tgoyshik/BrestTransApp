package com.example.bresttransapp

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DriveUploader {

    // Таблица перевода кириллицы в латиницу для имен файлов
    private val TRANSLIT_MAP = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "e",
        'ж' to "zh", 'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m",
        'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u",
        'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "shch",
        'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        'А' to "A", 'Б' to "B", 'В' to "V", 'Г' to "G", 'Д' to "D", 'Е' to "E", 'Ё' to "E",
        'Ж' to "Zh", 'З' to "Z", 'И' to "I", 'Й' to "Y", 'К' to "K", 'Л' to "L", 'М' to "M",
        'Н' to "N", 'О' to "O", 'П' to "P", 'Р' to "R", 'С' to "S", 'Т' to "T", 'У' to "U",
        'Ф' to "F", 'Х' to "H", 'Ц' to "Ts", 'Ч' to "Ch", 'Ш' to "Sh", 'Щ' to "Shch",
        'Ъ' to "", 'Ы' to "Y", 'Ь' to "", 'Э' to "E", 'Ю' to "Yu", 'Я' to "Ya"
    )

    // Функция перевода строки в транслит
    private fun safeTransliterate(src: String): String {
        val sb = StringBuilder()
        for (char in src) {
            sb.append(TRANSLIT_MAP[char] ?: char.toString())
        }
        return sb.toString().replace(" ", "_")
    }

    /**
     * Загружает собранные записи в формате JSON в указанную папку Google Drive.
     */
    suspend fun uploadJsonToDrive(
        context: Context,
        records: List<TransportRecord>,
        folderId: String,
        credential: GoogleAccountCredential,
        firstName: String,
        lastName: String,
        patronymic: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (records.isEmpty()) return@withContext false

            // Создание Drive API клиента
            val driveService = Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("BrestTransApp").build()

            // Формирование упорядоченного JSON-массива
            val orderedRecords = records.map { record ->
                linkedMapOf(
                    "time" to record.time,
                    "currentStop" to record.currentStop,
                    "nextStop" to record.nextStop,
                    "peopleAtStop" to record.peopleAtStop,
                    "entered" to record.entered,
                    "exited" to record.exited,
                    "latitude" to record.latitude,
                    "longitude" to record.longitude,
                    "weather" to record.weather
                )
            }

            val gson = com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()

            val jsonString = gson.toJson(orderedRecords)

            // Создание временного файла в кэше приложения
            val tempFile = java.io.File(context.cacheDir, "data.json")
            tempFile.writeText(jsonString)

            // Подготовка компонентов имени файла
            val cleanLastName = safeTransliterate(lastName.trim()).uppercase()
            val cleanFirstName = safeTransliterate(firstName.trim()).lowercase().replaceFirstChar { it.uppercase() }
            val cleanPatronymic = safeTransliterate(patronymic.trim()).lowercase().replaceFirstChar { it.uppercase() }
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

            val finalFileName = "${cleanLastName}_${cleanFirstName}_${cleanPatronymic}_${dateStr}.json"


            // Создание метаданных файла для Google Drive
            val gDriveFile = File().apply {
                name = finalFileName
                parents = listOf(folderId)
            }

            val fileContent = FileContent("application/json", tempFile)

            // Загрузка на Диск
            driveService.files().create(gDriveFile, fileContent).execute()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
