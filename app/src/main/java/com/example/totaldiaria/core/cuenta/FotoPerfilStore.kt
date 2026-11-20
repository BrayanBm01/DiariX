package com.example.totaldiaria.core.cuenta

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Foto de perfil guardada SOLO en este dispositivo y dentro de la carpeta de
 * la cuenta activa.
 *
 * El backend no tiene columna de foto (no se tocó el esquema de Prisma), así
 * que la imagen NO puede acabar en una ruta pública: vive en `filesDir`, que
 * es privado de la app. Hay dos archivos por cuenta:
 *
 *  - `google.jpg`: copia de la imagen que entregó Google al entrar;
 *  - `personalizada.jpg`: la que la persona eligió en Configuración.
 *
 * La personalizada tiene prioridad y NO se sobrescribe en cada login. Para
 * volver a la de Google basta con dejar de usar la personalizada; no hay que
 * borrar la copia de Google.
 */
class FotoPerfilStore(private val context: Context) {

    private val directorio: File
        get() = File(context.filesDir, CARPETA).resolve(CuentaLocal.id())

    private val archivoGoogle: File
        get() = File(directorio, NOMBRE_GOOGLE)

    private val archivoPersonalizada: File
        get() = File(directorio, NOMBRE_PERSONALIZADA)

    private val preferencias
        get() = context.getSharedPreferences(
            "${PREFIJO_PREFERENCIAS}_${CuentaLocal.id()}",
            Context.MODE_PRIVATE,
        )

    /** Foto que debe pintarse ahora, o null si la cuenta no tiene ninguna. */
    fun fotoActual(): File? = when {
        usandoPersonalizada() && archivoPersonalizada.exists() -> archivoPersonalizada
        archivoGoogle.exists() -> archivoGoogle
        archivoPersonalizada.exists() -> archivoPersonalizada
        else -> null
    }

    fun usandoPersonalizada(): Boolean =
        preferencias.getBoolean(CLAVE_PERSONALIZADA, false)

    fun tieneFotoGoogle(): Boolean = archivoGoogle.exists()

    fun urlGoogle(): String? =
        preferencias.getString(CLAVE_URL_GOOGLE, null)?.takeIf { it.isNotBlank() }

    fun guardarUrlGoogle(url: String?) {
        preferencias.edit().putString(CLAVE_URL_GOOGLE, url?.takeIf { it.isNotBlank() }).apply()
    }

    /** Guarda la copia local de la foto de Google para esta cuenta. */
    fun guardarGoogle(bytes: ByteArray): Boolean =
        if (esImagen(bytes)) escribir(archivoGoogle, bytes) else false

    /**
     * Guarda la foto elegida y le da prioridad sobre la de Google. No se
     * escribe nunca el archivo de Google: esa copia se conserva por si el
     * usuario quiere volver a ella.
     */
    fun guardarPersonalizada(bytes: ByteArray): Boolean {
        if (!esImagen(bytes) || !escribir(archivoPersonalizada, bytes)) return false

        preferencias.edit().putBoolean(CLAVE_PERSONALIZADA, true).apply()
        return true
    }

    /** Vuelve a la foto original de Google sin borrar la personalizada. */
    fun usarFotoDeGoogle() {
        preferencias.edit().putBoolean(CLAVE_PERSONALIZADA, false).apply()
    }

    /**
     * Descarga la foto de Google. Se llama desde un hilo de fondo: es
     * best-effort, si falla la red se sigue con el avatar de iniciales.
     */
    fun descargarGoogle(url: String): Boolean {
        val bytes = runCatching {
            val conexion = URL(url).openConnection() as HttpURLConnection
            conexion.connectTimeout = 8_000
            conexion.readTimeout = 8_000
            conexion.instanceFollowRedirects = true

            conexion.inputStream.use { entrada ->
                val salida = ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0

                while (true) {
                    val leidos = entrada.read(buffer)
                    if (leidos < 0) break

                    total += leidos
                    if (total > LIMITE_BYTES) break

                    salida.write(buffer, 0, leidos)
                }

                salida.toByteArray()
            }
        }.getOrNull() ?: return false

        guardarUrlGoogle(url)
        return guardarGoogle(bytes)
    }

    private fun escribir(destino: File, bytes: ByteArray): Boolean =
        try {
            directorio.mkdirs()
            destino.writeBytes(bytes)
            true
        } catch (_: Exception) {
            false
        }

    /** Evita guardar HTML de un error como si fuera una imagen. */
    private fun esImagen(bytes: ByteArray): Boolean {
        if (bytes.size < 4) return false

        val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()
        val png = bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte()
        val webp = bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"

        return jpeg || png || webp
    }

    private companion object {
        const val CARPETA = "fotos_perfil"
        const val NOMBRE_GOOGLE = "google.jpg"
        const val NOMBRE_PERSONALIZADA = "personalizada.jpg"
        const val PREFIJO_PREFERENCIAS = "foto_perfil"
        const val CLAVE_PERSONALIZADA = "personalizada"
        const val CLAVE_URL_GOOGLE = "url_google"
        const val LIMITE_BYTES = 4 * 1024 * 1024
    }
}
