package com.example.totaldiaria.modulos.facturacion.repositorio

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.totaldiaria.modulos.facturacion.modelo.EstadoSincronizacion
import com.example.totaldiaria.modulos.facturacion.modelo.Factura

class FacturaRepository(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun insertarFactura(factura: Factura): Boolean {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("numeroFactura", factura.numeroFactura)
            put("efectivo", factura.efectivo)
            put("transferencia", factura.transferencia)
            put("fecha", factura.fecha)

            // Comprobante de transferencia
            put("comprobanteUri", factura.comprobanteUri)

            // Fase 4: clave de idempotencia. Si el llamador no trae una (los
            // flujos anteriores a la fase la generan aquí) se crea una única
            // vez y se conserva: reenviar la misma fila nunca duplica.
            val clientUid = factura.clientUid ?: java.util.UUID.randomUUID().toString()
            put("clientUid", clientUid)
            put("estadoSincronizacion", factura.estadoSincronizacion)
            put("servidorId", factura.servidorId)
            put("intentos", factura.intentos)
            put("ultimoError", factura.ultimoError)
        }

        val resultado = db.insert(
            "facturas",
            null,
            values
        )

        db.close()

        return resultado != -1L
    }

    fun obtenerFacturas(): MutableList<Factura> =

        consultar(
            """
            SELECT *
            FROM facturas
            WHERE estado='ACTIVA'
            ORDER BY id DESC
            """.trimIndent()
        )

    /**
     * Facturas activas de un día concreto. El día se pasa como
     * 'dd/MM/yyyy' y se compara contra los 10 primeros caracteres de
     * la columna fecha, de modo que el contador del inicio se deriva
     * directamente de los datos de ese día.
     */
    fun obtenerFacturasDelDia(
        dia: String
    ): MutableList<Factura> =

        consultar(
            """
            SELECT *
            FROM facturas
            WHERE substr(fecha,1,10) = ? AND estado='ACTIVA'
            ORDER BY id DESC
            """.trimIndent(),
            arrayOf(dia)
        )

    fun obtenerFacturasPorFecha(
        fecha: String
    ): MutableList<Factura> =

        consultar(
            """
            SELECT *
            FROM facturas
            WHERE substr(fecha,1,10) = ?
            ORDER BY id DESC
            """.trimIndent(),
            arrayOf(fecha)
        )

    fun actualizarFactura(
        id: Int,
        efectivo: Double,
        transferencia: Double
    ): Boolean {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("efectivo", efectivo)
            put("transferencia", transferencia)
        }

        val filas = db.update(
            "facturas",
            values,
            "id = ?",
            arrayOf(id.toString())
        )

        db.close()

        return filas > 0
    }

    fun eliminarFactura(id: Int): Boolean {

        val db = dbHelper.writableDatabase

        val filas = db.delete(
            "facturas",
            "id = ?",
            arrayOf(id.toString())
        )

        db.close()

        return filas > 0
    }

    fun marcarComoArchivadasAnterioresA(fechaHoy: String) {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("estado", "ARCHIVADA")
        }

        db.update(
            "facturas",
            values,
            "substr(fecha,1,10) <> ? AND estado = ?",
            arrayOf(fechaHoy, ESTADO_ACTIVA)
        )

        db.close()
    }

    fun eliminarTodasLasFacturas() {

        val db = dbHelper.writableDatabase

        db.delete(
            "facturas",
            null,
            null
        )

        db.execSQL(
            "DELETE FROM sqlite_sequence WHERE name='facturas'"
        )

        db.close()
    }

    private fun consultar(
        sql: String,
        argumentos: Array<String>? = null
    ): MutableList<Factura> {

        val lista = mutableListOf<Factura>()

        val db = dbHelper.readableDatabase

        val cursor: Cursor = db.rawQuery(sql, argumentos)

        if (cursor.moveToFirst()) {

            do {

                lista.add(mapearFactura(cursor))

            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return lista
    }

    private fun mapearFactura(cursor: Cursor): Factura =

        Factura(
            id = cursor.getInt(
                cursor.getColumnIndexOrThrow("id")
            ),
            numeroFactura = cursor.getString(
                cursor.getColumnIndexOrThrow("numeroFactura")
            ),
            efectivo = cursor.getDouble(
                cursor.getColumnIndexOrThrow("efectivo")
            ),
            transferencia = cursor.getDouble(
                cursor.getColumnIndexOrThrow("transferencia")
            ),
            fecha = cursor.getString(
                cursor.getColumnIndexOrThrow("fecha")
            ),
            comprobanteUri = cursor.getString(
                cursor.getColumnIndexOrThrow("comprobanteUri")
            ),
            clientUid = cursor.getString(
                cursor.getColumnIndexOrThrow("clientUid")
            ),
            estadoSincronizacion = cursor.getString(
                cursor.getColumnIndexOrThrow("estadoSincronizacion")
            ) ?: EstadoSincronizacion.PENDIENTE,
            servidorId = cursor.getString(
                cursor.getColumnIndexOrThrow("servidorId")
            ),
            intentos = cursor.getInt(
                cursor.getColumnIndexOrThrow("intentos")
            ),
            ultimoError = cursor.getString(
                cursor.getColumnIndexOrThrow("ultimoError")
            )
        )

    // =========================================================================
    // Fase 4 · Cola de sincronización
    // =========================================================================

    /**
     * Facturas pendientes de subir, en orden de creación. Solo las que tienen
     * `clientUid` (las generadas a partir de Fase 4). Las RECHAZADA se
     * excluyen de la cola automática: siguen visibles con su error y el usuario
     * decide qué hacer con ellas.
     */
    fun obtenerPendientesDeSincronizacion(limite: Int = 20): MutableList<Factura> =

        consultar(
            """
            SELECT *
            FROM facturas
            WHERE estadoSincronizacion = ? AND clientUid IS NOT NULL
            ORDER BY id ASC
            LIMIT ?
            """.trimIndent(),
            arrayOf(EstadoSincronizacion.PENDIENTE, limite.toString())
        )

    /** Cuántas facturas esperan subida (para el indicador de la app). */
    fun contarPendientesDeSincronizacion(): Int {

        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            """
            SELECT COUNT(*)
            FROM facturas
            WHERE estadoSincronizacion = ? AND clientUid IS NOT NULL
            """.trimIndent(),
            arrayOf(EstadoSincronizacion.PENDIENTE)
        )

        val total = if (cursor.moveToFirst()) cursor.getInt(0) else 0

        cursor.close()
        db.close()

        return total
    }

    /** El servidor confirmó la factura: queda SINCRONIZADA con su id. */
    fun marcarSincronizada(
        idFactura: Int,
        servidorId: String
    ): Boolean {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("estadoSincronizacion", EstadoSincronizacion.SINCRONIZADA)
            put("servidorId", servidorId)
            put("intentos", 0)
            putNull("ultimoError")
        }

        val filas = db.update(
            "facturas",
            values,
            "id = ? AND clientUid IS NOT NULL",
            arrayOf(idFactura.toString())
        )

        db.close()

        return filas > 0
    }

    /** El servidor rechazó la factura: queda local, visible, con su error. */
    fun marcarRechazada(
        idFactura: Int,
        mensaje: String,
        intentos: Int
    ): Boolean {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("estadoSincronizacion", EstadoSincronizacion.RECHAZADA)
            put("intentos", intentos)
            put("ultimoError", mensaje)
        }

        val filas = db.update(
            "facturas",
            values,
            "id = ?",
            arrayOf(idFactura.toString())
        )

        db.close()

        return filas > 0
    }

    companion object {

        const val ESTADO_ACTIVA = "ACTIVA"
    }
}
