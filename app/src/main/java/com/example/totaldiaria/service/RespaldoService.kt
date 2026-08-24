package com.example.totaldiaria.service

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.totaldiaria.database.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Respaldos y restauración completa de la aplicación.
 *
 * El respaldo es un archivo ZIP que contiene:
 *  - data.json: todas las tablas con una versión de formato.
 *  - comprobantes/<idFactura>.jpg: imagen asociada a cada factura,
 *    nombrada por el id para poder reconstruir la relación.
 *
 * La importación usa el id como identidad de cada fila: si existe la
 * restaura encima y si no la agrega, por lo que nunca genera
 * duplicados aunque se importe el mismo respaldo varias veces.
 */
class RespaldoService(private val context: Context) {

    private val almacenComprobantes = ComprobanteStore(context)

    // ------------------------------------------------------------------
    // Exportación
    // ------------------------------------------------------------------

    /**
     * Escribe el respaldo completo en [destino]. Devuelve un resumen
     * del contenido o lanza [RespaldoException] si algo falla.
     */
    fun exportar(destino: OutputStream): ResumenBackup {

        val db = DatabaseHelper(context).readableDatabase

        try {

            val facturas = consultarFilas(db, TABLA_FACTURAS)

            val resumen = ResumenBackup(
                facturas = facturas.size,
                registros = contar(db, TABLA_REGISTROS),
                papelera = contar(db, TABLA_PAPELERA),
                comprobantes = 0,
                fechaExportacion = SimpleDateFormat(
                    FORMATO_FECHA_HORA,
                    Locale.getDefault()
                ).format(Date()),
                qrIncluido = false
            )

            val json = JSONObject()

            json.put(CLAVE_VERSION, VERSION_FORMATO)
            json.put(CLAVE_APP, NOMBRE_APP)
            json.put(CLAVE_FECHA_EXPORTACION, resumen.fechaExportacion)

            var comprobantesExportados = 0

            ZipOutputStream(destino).use { zip ->

                val arrayFacturas = JSONArray()

                for (fila in facturas) {

                    val objeto = JSONObject()

                    objeto.put("id", fila.id)
                    objeto.put(
                        "numeroFactura",
                        fila.texto("numeroFactura")
                    )
                    objeto.put("efectivo", fila.numero("efectivo"))
                    objeto.put(
                        "transferencia",
                        fila.numero("transferencia")
                    )
                    objeto.put("fecha", fila.texto("fecha"))
                    objeto.put("estado", fila.texto("estado"))

                    val nombre =
                        copiarComprobanteAlZip(zip, fila)

                    objeto.put(
                        "comprobante",
                        nombre ?: JSONObject.NULL
                    )

                    if (nombre != null) comprobantesExportados++

                    arrayFacturas.put(objeto)
                }

                json.put(CLAVE_FACTURAS, arrayFacturas)
                json.put(
                    CLAVE_REGISTROS,
                    tablaAJson(db, TABLA_REGISTROS)
                )
                json.put(
                    CLAVE_PAPELERA,
                    tablaAJson(db, TABLA_PAPELERA)
                )

                resumen.qrIncluido = copiarQrAlZip(zip)

                zip.putNextEntry(ZipEntry(NOMBRE_JSON))

                zip.write(
                    json.toString(2).toByteArray(Charsets.UTF_8)
                )

                zip.closeEntry()
            }

            resumen.comprobantes = comprobantesExportados

            return resumen

        } catch (e: RespaldoException) {

            throw e

        } catch (e: Exception) {

            throw RespaldoException(MENSAJE_ERROR_EXPORTAR, e)

        } finally {

            db.close()
        }
    }

    /**
     * Copia el comprobante de la factura al ZIP que se está
     * escribiendo. Devuelve el nombre asignado o null si la imagen no
     * está disponible (por ejemplo un URI antiguo sin permiso).
     */
    private fun copiarComprobanteAlZip(
        zip: ZipOutputStream,
        fila: Fila
    ): String? {

        val uriTexto = fila.texto("comprobanteUri")

        if (uriTexto.isEmpty()) return null

        val entrada = almacenComprobantes.abrir(uriTexto)

            ?: return null

        val nombre = "$CARPETA_COMPROBANTES/${fila.id}.jpg"

        try {

            zip.putNextEntry(ZipEntry(nombre))

            entrada.use { it.copyTo(zip) }

            zip.closeEntry()

        } catch (e: Exception) {

            return null
        }

        return nombre
    }

    private fun copiarQrAlZip(zip: ZipOutputStream): Boolean {

        val qr = File(context.filesDir, NOMBRE_QR)

        if (!qr.exists()) return false

        return try {

            zip.putNextEntry(ZipEntry(NOMBRE_QR))

            qr.inputStream().use { it.copyTo(zip) }

            zip.closeEntry()

            true

        } catch (e: Exception) {

            false
        }
    }

    // ------------------------------------------------------------------
    // Validación previa (no modifica nada)
    // ------------------------------------------------------------------

    sealed class Validacion {

        abstract val mensaje: String

        data class Ok(val resumen: ResumenBackup) : Validacion() {

            override val mensaje = ""
        }

        object ArchivoInvalido : Validacion() {

            override val mensaje = MENSAJE_ARCHIVO_INVALIDO
        }

        object VersionIncompatible : Validacion() {

            override val mensaje = MENSAJE_VERSION_INCOMPATIBLE
        }
    }

    /**
     * Lee y valida el respaldo sin tocar la base de datos.
     */
    fun validar(abrirEntrada: () -> InputStream?): Validacion {

        return try {

            val entrada = abrirEntrada() ?: return Validacion.ArchivoInvalido

            entrada.use { leerValidando(it) }

        } catch (e: Exception) {

            Validacion.ArchivoInvalido
        }
    }

    private fun leerValidando(entrada: InputStream): Validacion {

        var json: JSONObject? = null
        var qrPresente = false

        ZipInputStream(entrada).use { zip ->

            var entry: ZipEntry? = zip.getNextEntry()

            while (entry != null) {

                when {

                    entry.name == NOMBRE_JSON -> {

                        json = JSONObject(
                            zip.readBytes().toString(Charsets.UTF_8)
                        )
                    }

                    entry.name == NOMBRE_QR -> qrPresente = true

                    else -> {
                        // Comprobantes u otras entradas: solo presencia.
                    }
                }

                zip.closeEntry()

                entry = zip.getNextEntry()
            }
        }

        val datos = json ?: return Validacion.ArchivoInvalido

        val version = datos.optInt(CLAVE_VERSION, -1)

        if (version > VERSION_FORMATO) {
            return Validacion.VersionIncompatible
        }

        if (version != VERSION_FORMATO) {
            return Validacion.ArchivoInvalido
        }

        if (!datos.has(CLAVE_FACTURAS)) return Validacion.ArchivoInvalido
        if (!datos.has(CLAVE_REGISTROS)) return Validacion.ArchivoInvalido
        if (!datos.has(CLAVE_PAPELERA)) return Validacion.ArchivoInvalido

        val facturas = datos.getJSONArray(CLAVE_FACTURAS)

        for (i in 0 until facturas.length()) {

            val factura = facturas.getJSONObject(i)

            if (!factura.has("id")) return Validacion.ArchivoInvalido
            if (factura.optString("fecha").isEmpty()) {
                return Validacion.ArchivoInvalido
            }
        }

        var comprobantes = 0

        for (i in 0 until facturas.length()) {

            val factura = facturas.getJSONObject(i)

            val tieneImagen =
                !factura.isNull("comprobante") &&
                        factura.optString("comprobante").isNotEmpty()

            if (tieneImagen) comprobantes++
        }

        for (tabla in listOf(CLAVE_REGISTROS, CLAVE_PAPELERA)) {

            val filas = datos.getJSONArray(tabla)

            for (i in 0 until filas.length()) {

                val fila = filas.getJSONObject(i)

                if (!fila.has("id")) return Validacion.ArchivoInvalido
                if (fila.optString("fecha").isEmpty()) {
                    return Validacion.ArchivoInvalido
                }
            }
        }

        return Validacion.Ok(
            ResumenBackup(
                facturas = facturas.length(),
                registros = datos.getJSONArray(CLAVE_REGISTROS).length(),
                papelera = datos.getJSONArray(CLAVE_PAPELERA).length(),
                comprobantes = comprobantes,
                fechaExportacion =
                    datos.optString(CLAVE_FECHA_EXPORTACION),
                qrIncluido = qrPresente
            )
        )
    }

    // ------------------------------------------------------------------
    // Importación
    // ------------------------------------------------------------------

    /**
     * Importa un respaldo previamente validado dentro de una
     * transacción: si algo falla la base de datos queda intacta.
     */
    fun importar(abrirEntrada: () -> InputStream?): ResultadoImportacion {

        val db = DatabaseHelper(context).writableDatabase

        var carpetaTemporal: File? = null

        try {

            carpetaTemporal = extraerArchivosTemporales(abrirEntrada)

            db.beginTransaction()

            try {

                val datos =
                    leerJsonDeZip(abrirEntrada())

                var insertadas = 0
                var actualizadas = 0
                var restauradas = 0

                val facturas =
                    datos.getJSONArray(CLAVE_FACTURAS)

                for (i in 0 until facturas.length()) {

                    val factura = facturas.getJSONObject(i)

                    val idFactura = factura.getInt("id")

                    val uriComprobante = restaurarComprobante(
                        carpetaTemporal,
                        idFactura
                    )

                    if (uriComprobante != null) restauradas++

                    val valores = ContentValues().apply {

                        put("id", idFactura)
                        put(
                            "numeroFactura",
                            factura.optString("numeroFactura")
                        )
                        put(
                            "efectivo",
                            factura.optDouble("efectivo", 0.0)
                        )
                        put(
                            "transferencia",
                            factura.optDouble("transferencia", 0.0)
                        )
                        put("fecha", factura.getString("fecha"))
                        put(
                            "estado",
                            normalizarEstado(
                                factura.optString("estado")
                            )
                        )
                        put("comprobanteUri", uriComprobante)
                    }

                    val eraNueva = insertarOActualizar(
                        db,
                        TABLA_FACTURAS,
                        valores
                    )

                    if (eraNueva) insertadas++ else actualizadas++
                }

                for (clave in listOf(
                    CLAVE_REGISTROS to TABLA_REGISTROS,
                    CLAVE_PAPELERA to TABLA_PAPELERA
                )) {

                    val (nuevas, existentes) = importarTablaSimple(
                        db,
                        datos.getJSONArray(clave.first),
                        clave.second
                    )

                    insertadas += nuevas
                    actualizadas += existentes
                }

                db.setTransactionSuccessful()

                val qrRestaurado =
                    restaurarQr(carpetaTemporal)

                return ResultadoImportacion(
                    insertadas = insertadas,
                    actualizadas = actualizadas,
                    comprobantesRestaurados = restauradas,
                    qrRestaurado = qrRestaurado
                )

            } finally {

                db.endTransaction()
            }

        } catch (e: RespaldoException) {

            throw e

        } catch (e: Exception) {

            throw RespaldoException(MENSAJE_ERROR_IMPORTAR, e)

        } finally {

            db.close()

            carpetaTemporal?.deleteRecursively()
        }
    }

    /**
     * Extrae los comprobantes y el QR del ZIP a un directorio temporal
     * bajo cacheDir. Los nombres se validan contra un patrón estricto
     * y se verifica la ruta canónica para impedir escrituras fuera
     * del directorio controlado.
     */
    private fun extraerArchivosTemporales(
        abrirEntrada: () -> InputStream?
    ): File {

        val carpetaTemporal =
            File(context.cacheDir, CARPETA_TEMPORAL)

        carpetaTemporal.deleteRecursively()

        if (!carpetaTemporal.mkdirs()) {

            throw RespaldoException(MENSAJE_ERROR_IMPORTAR)
        }

        val rutaPermitida =
            carpetaTemporal.canonicalPath + File.separator

        val entrada = abrirEntrada()
            ?: throw RespaldoException(MENSAJE_ERROR_IMPORTAR)

        entrada.use { stream ->

            ZipInputStream(stream).use { zip ->

                var entry: ZipEntry? = zip.getNextEntry()

                while (entry != null) {

                    val nombre = entry.name

                    val destino: File? = when {

                        PATRON_COMPROBANTE.matches(nombre) ->
                            File(
                                carpetaTemporal,
                                extraerIdComprobante(nombre) + ".img"
                            )

                        nombre == NOMBRE_QR ->
                            File(carpetaTemporal, NOMBRE_QR)

                        else -> null
                    }

                    if (destino != null &&
                        destino.canonicalPath.startsWith(rutaPermitida)
                    ) {

                        destino.outputStream().use { salida ->
                            zip.copyTo(salida)
                        }
                    }

                    zip.closeEntry()

                    entry = zip.getNextEntry()
                }
            }
        }

        return carpetaTemporal
    }

    private fun leerJsonDeZip(entrada: InputStream?): JSONObject {

        entrada ?: throw RespaldoException(MENSAJE_ERROR_IMPORTAR)

        entrada.use { stream ->

            ZipInputStream(stream).use { zip ->

                var entry: ZipEntry? = zip.getNextEntry()

                while (entry != null) {

                    if (entry.name == NOMBRE_JSON) {

                        return JSONObject(
                            zip.readBytes().toString(Charsets.UTF_8)
                        )
                    }

                    zip.closeEntry()

                    entry = zip.getNextEntry()
                }
            }
        }

        throw RespaldoException(MENSAJE_ARCHIVO_INVALIDO)
    }

    /**
     * Guarda la imagen extraída como copia interna estable mediante
     * ComprobanteStore y devuelve su URI, o null si no hay imagen.
     */
    private fun restaurarComprobante(
        carpetaTemporal: File,
        idFactura: Int
    ): String? {

        val archivo = File(carpetaTemporal, "$idFactura.img")

        if (!archivo.exists()) return null

        return archivo.inputStream().use {

            almacenComprobantes.guardarStream(it)
        }?.toString()
    }

    private fun restaurarQr(carpetaTemporal: File): Boolean {

        val origen = File(carpetaTemporal, NOMBRE_QR)

        if (!origen.exists()) return false

        return try {

            origen.copyTo(
                File(context.filesDir, NOMBRE_QR),
                overwrite = true
            )

            true

        } catch (e: Exception) {

            false
        }
    }

    /**
     * Inserta o actualiza las filas de registros/papelera usando el id
     * como identidad. Devuelve cuántas eran nuevas y cuántas existían.
     */
    private fun importarTablaSimple(
        db: SQLiteDatabase,
        array: JSONArray,
        tabla: String
    ): Pair<Int, Int> {

        var nuevas = 0
        var existentes = 0

        for (i in 0 until array.length()) {

            val objeto = array.getJSONObject(i)

            if (!objeto.has("id")) continue
            if (objeto.optString("fecha").isEmpty()) continue

            val valores = ContentValues().apply {

                put("id", objeto.getInt("id"))
                put("fecha", objeto.getString("fecha"))
                put("cantidad", objeto.optInt("cantidad", 0))
                put("efectivo", objeto.optDouble("efectivo", 0.0))
                put(
                    "transferencia",
                    objeto.optDouble("transferencia", 0.0)
                )
                put("total", objeto.optDouble("total", 0.0))
                put(
                    "cantidadEfectivo",
                    objeto.optInt("cantidadEfectivo", 0)
                )
                put(
                    "cantidadTransferencia",
                    objeto.optInt("cantidadTransferencia", 0)
                )
            }

            val eraNueva =
                insertarOActualizar(db, tabla, valores)

            if (eraNueva) nuevas++ else existentes++
        }

        return Pair(nuevas, existentes)
    }

    /**
     * true si el id no existía (inserción), false si ya estaba
     * (actualización). Nunca duplica porque el id es la identidad.
     */
    private fun insertarOActualizar(
        db: SQLiteDatabase,
        tabla: String,
        valores: ContentValues
    ): Boolean {

        val id = valores.getAsInteger("id") ?: return false

        val cursor = db.rawQuery(
            "SELECT id FROM $tabla WHERE id = ?",
            arrayOf(id.toString())
        )

        val existia = cursor.moveToFirst()

        cursor.close()

        db.insertWithOnConflict(
            tabla,
            null,
            valores,
            SQLiteDatabase.CONFLICT_REPLACE
        )

        return !existia
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private class Fila(
        val id: Int,
        private val valores: Map<String, Any?>
    ) {

        fun texto(columna: String): String =

            (valores[columna] as? String) ?: ""

        fun numero(columna: String): Double =

            (valores[columna] as? Number)?.toDouble() ?: 0.0
    }

    private fun consultarFilas(
        db: SQLiteDatabase,
        tabla: String
    ): MutableList<Fila> {

        val lista = mutableListOf<Fila>()

        val cursor: Cursor = db.rawQuery(
            "SELECT * FROM $tabla ORDER BY id ASC",
            null
        )

        if (cursor.moveToFirst()) {

            do {

                val indiceId =
                    cursor.getColumnIndexOrThrow("id")

                val valores = mutableMapOf<String, Any?>()

                for (columna in cursor.columnNames) {

                    val indice =
                        cursor.getColumnIndexOrThrow(columna)

                    valores[columna] = when (
                        cursor.getType(indice)
                    ) {

                        Cursor.FIELD_TYPE_STRING ->
                            cursor.getString(indice)

                        Cursor.FIELD_TYPE_FLOAT ->
                            cursor.getDouble(indice)

                        Cursor.FIELD_TYPE_INTEGER ->
                            cursor.getLong(indice)

                        else -> null
                    }
                }

                lista.add(Fila(cursor.getInt(indiceId), valores))

            } while (cursor.moveToNext())
        }

        cursor.close()

        return lista
    }

    private fun tablaAJson(
        db: SQLiteDatabase,
        tabla: String
    ): JSONArray {

        val array = JSONArray()

        for (fila in consultarFilas(db, tabla)) {

            val objeto = JSONObject()

            objeto.put("id", fila.id)
            objeto.put("fecha", fila.texto("fecha"))
            objeto.put("cantidad", fila.numero("cantidad").toInt())
            objeto.put("efectivo", fila.numero("efectivo"))
            objeto.put("transferencia", fila.numero("transferencia"))
            objeto.put("total", fila.numero("total"))
            objeto.put(
                "cantidadEfectivo",
                fila.numero("cantidadEfectivo").toInt()
            )
            objeto.put(
                "cantidadTransferencia",
                fila.numero("cantidadTransferencia").toInt()
            )

            array.put(objeto)
        }

        return array
    }

    private fun contar(db: SQLiteDatabase, tabla: String): Int {

        val cursor = db.rawQuery(
            "SELECT COUNT(*) FROM $tabla",
            null
        )

        val total = if (cursor.moveToFirst()) cursor.getInt(0) else 0

        cursor.close()

        return total
    }

    private fun normalizarEstado(estado: String): String =

        if (estado == ESTADO_ARCHIVADA) ESTADO_ARCHIVADA
        else ESTADO_ACTIVA

    data class ResumenBackup(

        val facturas: Int,
        val registros: Int,
        val papelera: Int,
        var comprobantes: Int,
        val fechaExportacion: String,
        var qrIncluido: Boolean
    )

    data class ResultadoImportacion(

        val insertadas: Int,
        val actualizadas: Int,
        val comprobantesRestaurados: Int,
        val qrRestaurado: Boolean
    )

    class RespaldoException(
        val mensajeUsuario: String,
        causa: Throwable? = null
    ) : Exception(causa)

    companion object {

        const val VERSION_FORMATO = 1

        private const val CLAVE_VERSION = "backupVersion"
        private const val CLAVE_APP = "app"
        private const val CLAVE_FECHA_EXPORTACION = "fechaExportacion"
        private const val CLAVE_FACTURAS = "facturas"
        private const val CLAVE_REGISTROS = "registros"
        private const val CLAVE_PAPELERA = "papelera"

        private const val TABLA_FACTURAS = "facturas"
        private const val TABLA_REGISTROS = "registros"
        private const val TABLA_PAPELERA = "papelera"

        private const val NOMBRE_JSON = "data.json"
        private const val NOMBRE_QR = "qr.png"
        private const val CARPETA_COMPROBANTES = "comprobantes"
        private const val CARPETA_TEMPORAL = "respaldo_import"

        private const val NOMBRE_APP = "TotalDiaria"

        private const val FORMATO_FECHA_HORA = "dd/MM/yyyy HH:mm"

        private const val ESTADO_ACTIVA = "ACTIVA"
        private const val ESTADO_ARCHIVADA = "ARCHIVADA"

        private val PATRON_COMPROBANTE =
            Regex("^comprobantes/[0-9]+\\.(jpg|jpeg|png|webp)$")

        const val MENSAJE_ARCHIVO_INVALIDO =
            "El archivo seleccionado no es un respaldo válido de TotalDiaria"

        const val MENSAJE_VERSION_INCOMPATIBLE =
            "La versión del respaldo no es compatible con esta versión de la aplicación"

        const val MENSAJE_ERROR_EXPORTAR =
            "No fue posible generar el archivo de respaldo"

        const val MENSAJE_ERROR_IMPORTAR =
            "Ocurrió un error durante la importación. La base de datos no fue modificada"

        private fun extraerIdComprobante(nombre: String): String =

            nombre.substringAfterLast('/')
                .substringBeforeLast('.')
    }
}
