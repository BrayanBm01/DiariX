package com.example.totaldiaria.modulos.facturacion.servicio

import android.content.Context
import android.net.Uri
import com.example.totaldiaria.core.cuenta.CuentaLocal
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
/**
 * Lo que la sincronización necesita del almacén local: leer el comprobante y
 * saber su tipo. Se abstrae para poder probar la subida sin Android.
 */
interface AlmacenDeComprobantes {
    fun leerParaSubir(uriTexto: String): ComprobanteParaSubir?
}

class ComprobanteStore(private val context: Context) : AlmacenDeComprobantes {

    /** Cada cuenta tiene su propia carpeta: un URI ajeno nunca se abre. */
    private val directorio
        get() = File(context.filesDir, CARPETA_COMPROBANTES).resolve(CuentaLocal.id())

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

    /**
     * Lee el comprobante para subirlo al servidor y detecta su tipo REAL.
     *
     * El nombre del archivo interno siempre termina en `.jpg`, pero el
     * contenido puede ser PNG/PDF. El backend valida el MIME que declara el
     * multipart, así que se mira la firma del archivo en vez de confiar en la
     * extensión.
     */
    override fun leerParaSubir(uriTexto: String): ComprobanteParaSubir? {

        val bytes = abrir(uriTexto)?.use { it.readBytes() } ?: return null

        if (bytes.isEmpty()) return null

        val mime = detectarMime(bytes)

        val extension = when (mime) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            "application/pdf" -> "pdf"
            else -> "jpg"
        }

        return ComprobanteParaSubir(
            bytes = bytes,
            mime = mime,
            nombre = "comprobante.$extension",
        )
    }

    private fun detectarMime(bytes: ByteArray): String {

        if (bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "image/jpeg"
        }

        if (bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return "image/png"
        }

        if (bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) {
            return "image/webp"
        }

        if (bytes.size >= 4 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "%PDF"
        ) {
            return "application/pdf"
        }

        // HEIC/HEIF: caja `ftyp` en el byte 4 con alguna de sus marcas.
        if (bytes.size >= 12 &&
            String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp"
        ) {
            val marca = String(bytes, 8, 4, Charsets.US_ASCII)

            return when (marca) {
                "heic", "heix", "hevc", "hevx" -> "image/heic"
                "mif1", "msf1" -> "image/heif"
                else -> "image/jpeg"
            }
        }

        return "image/jpeg"
    }

    companion object {

        private const val CARPETA_COMPROBANTES = "comprobantes"
    }
}

/** Archivo listo para el multipart del backend. */
data class ComprobanteParaSubir(
    val bytes: ByteArray,
    val mime: String,
    val nombre: String,
)
