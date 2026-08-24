package com.example.totaldiaria.service

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Guarda copias de los comprobantes en almacenamiento interno.
 *
 * Los URI que entrega el selector de fotos pueden perder su permiso
 * de lectura (comportamiento observado en algunos dispositivos), por
 * lo que conservar el archivo dentro de la app garantiza que el
 * comprobante siga disponible al cerrar y reabrir la aplicación.
 *
 * Es la única fuente de verdad para leer y escribir comprobantes:
 * la exportación e importación de respaldos también pasan por aquí.
 */
class ComprobanteStore(private val context: Context) {

    private val directorio =
        File(context.filesDir, CARPETA_COMPROBANTES)

    fun guardarCopia(uri: Uri): Uri? {

        return try {

            val entrada =
                context.contentResolver.openInputStream(uri)

            if (entrada == null) {

                null

            } else {

                entrada.use { guardarStream(it) }
            }

        } catch (e: Exception) {

            null
        }
    }

    /**
     * Guarda los datos recibidos como una nueva copia interna y
     * devuelve su URI estable, o null si falla la escritura.
     */
    fun guardarStream(datos: InputStream): Uri? {

        return try {

            directorio.mkdirs()

            val destino = File(directorio, nombreDisponible())

            datos.use { origen ->
                destino.outputStream().use { salida ->
                    origen.copyTo(salida)
                }
            }

            Uri.fromFile(destino)

        } catch (e: Exception) {

            null
        }
    }

    /**
     * Abre un comprobante guardado en la base de datos. Acepta las
     * tres formas históricas del campo comprobanteUri: file://,
     * content:// y rutas absolutas.
     */
    fun abrir(uriTexto: String): InputStream? {

        if (uriTexto.isEmpty()) return null

        val uri = Uri.parse(uriTexto)

        return try {

            when (uri.scheme) {

                "file" -> File(uri.path ?: "").inputStream()

                null -> File(uriTexto).inputStream()

                else -> context.contentResolver.openInputStream(uri)
            }

        } catch (e: Exception) {

            null
        }
    }

    private fun nombreDisponible(): String =

        "Comprobante_${System.currentTimeMillis()}.jpg"

    companion object {

        private const val CARPETA_COMPROBANTES = "comprobantes"
    }
}
