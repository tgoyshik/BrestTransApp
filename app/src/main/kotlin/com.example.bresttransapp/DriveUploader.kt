package com.example.bresttransapp

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

object DriveUploader {

    // Функция перевода кириллицы в латиницу для стандартизации имен файлов
    private fun transliterate(src: String): String {
        val cyr = arrayOf("а","б","в","г","д","е","ё","ж","з","и","й","к","л","м","н","о","п","р","с","т","у","ф","х","ц","ч","ш","щ","ъ","ы","ь","э","ю","я", "А","Б","В","Г","Д","Е","Ё","Ж","З","И","Й","К","Л","М","Н","О","П","Р","С","Т","У","ф","Х","Ц","Ч","Ш","Щ","Ъ","Ы","Ь","Э","Ю","Я")
        val lat = arrayOf("a","b","v","g","d","e","e","zh","z","i","y","k","l","m","n","o","p","r","s","t","u","f","h","ts","ch","sh","shch","","y","","e","yu","ya", "A","B","V","G","D","E","E","Zh","Z","I","Y","K","L","M","N","O","P","R","S","T","U","F","H","Ts","Ch","Sh","Shch","","Y","","E","Yu","Ya")
        var result = src
        for (i in cyr.indices) {
            result = result.replace(cyr[i], lat[i])
        }
        return result.replace(" ", "_") // Заменяем случайные пробелы на подчеркивание
    }

    suspend fun uploadJsonToDrive(
        context: Context,
        records: List<TransportRecord>,
        folderId: String,
        credential: GoogleAccountCredential,
        firstName: String,
        lastName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (records.isEmpty()) return@withContext false

            val driveService = Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("BrestTransApp").build()

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

            val tempFile = java.io.File(context.cacheDir, "data.json")
            tempFile.writeText(jsonString)

            // Форматируем Фамилию
            val cleanLastName = transliterate(lastName.trim()).uppercase()
            val cleanFirstName = transliterate(firstName.trim()).lowercase().replaceFirstChar { it.uppercase() }

            // Форматируем текущую дату и время (ГГГГММДД_ЧЧММ)
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())

            val finalFileName = "${cleanLastName}_${cleanFirstName}_${dateStr}.json"

            val gDriveFile = File().apply {
                name = finalFileName
                parents = listOf(folderId)
            }

            val fileContent = FileContent("application/json", tempFile)
            driveService.files().create(gDriveFile, fileContent).execute()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
