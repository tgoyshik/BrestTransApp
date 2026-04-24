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

    suspend fun uploadJsonToDrive(
        context: Context,
        records: List<TransportRecord>,
        folderId: String,
        credential: GoogleAccountCredential
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (records.isEmpty()) return@withContext false

            val driveService = Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("BrestTransApp").build()

            val jsonString = Gson().toJson(records)

            val tempFile = java.io.File(context.cacheDir, "data.json")
            tempFile.writeText(jsonString)

            val gDriveFile = File().apply {
                name = "История_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"
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

