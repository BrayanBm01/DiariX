package com.example.totaldiaria.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.totaldiaria.models.Factura

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
            )
        )

    companion object {

        const val ESTADO_ACTIVA = "ACTIVA"
    }
}
