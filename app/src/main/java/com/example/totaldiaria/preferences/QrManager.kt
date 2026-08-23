package com.example.totaldiaria.preferences

import android.content.Context
import android.net.Uri
import java.io.File

class QrManager(private val context: Context) {

    private val nombreArchivo = "qr.png"

    fun guardarQr(uri: Uri): Boolean {

        return try {

            val input =
                context.contentResolver.openInputStream(uri)

            val output =
                File(context.filesDir, nombreArchivo)
                    .outputStream()

            input?.copyTo(output)

            input?.close()
            output.close()

            true

        } catch (e: Exception) {

            false
        }
    }

    fun obtenerQr(): File {

        return File(
            context.filesDir,
            nombreArchivo
        )
    }

    fun existeQr(): Boolean {

        return obtenerQr().exists()
    }
}