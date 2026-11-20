package com.example.totaldiaria.modulos.facturacion.preferencias

import android.content.Context
import android.net.Uri
import com.example.totaldiaria.core.cuenta.CuentaLocal
import java.io.File

class QrManager(private val context: Context) {

    /** El QR también es privado de la cuenta que lo eligió. */
    private val nombreArchivo: String
        get() = "qr_${CuentaLocal.id()}.png"

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