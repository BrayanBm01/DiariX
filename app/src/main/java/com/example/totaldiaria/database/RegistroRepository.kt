package com.example.totaldiaria.database

import android.content.ContentValues
import android.content.Context
import com.example.totaldiaria.database.PapeleraItemMapper.desdeCursor
import com.example.totaldiaria.models.PapeleraItem

class RegistroRepository(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun guardarEnRegistros(
        fecha: String,
        cantidad: Int,
        efectivo: Double,
        transferencia: Double,
        total: Double,
        cantidadEfectivo: Int,
        cantidadTransferencia: Int
    ) {

        insertarEn("registros", valoresDe(fecha, cantidad, efectivo, transferencia, total, cantidadEfectivo, cantidadTransferencia))
    }

    fun obtenerRegistros(): MutableList<PapeleraItem> =

        consultar(
            "SELECT * FROM registros ORDER BY id DESC"
        )

    fun obtenerRegistrosPorFecha(
        fecha: String
    ): MutableList<PapeleraItem> =

        consultar(
            """
            SELECT *
            FROM registros
            WHERE fecha = ?
            ORDER BY id DESC
            """.trimIndent(),
            arrayOf(fecha)
        )

    /**
     * Conserva solo los [limite] registros más recientes.
     */
    fun eliminarRegistrosExcedentes(limite: Int) {

        val db = dbHelper.writableDatabase

        db.execSQL(
            """
            DELETE FROM registros
            WHERE id NOT IN (
                SELECT id
                FROM registros
                ORDER BY id DESC
                LIMIT ?
            )
            """.trimIndent(),
            arrayOf(limite)
        )

        db.close()
    }

    private fun insertarEn(tabla: String, values: ContentValues) {

        val db = dbHelper.writableDatabase

        db.insert(tabla, null, values)

        db.close()
    }

    private fun consultar(
        sql: String,
        argumentos: Array<String>? = null
    ): MutableList<PapeleraItem> {

        val lista = mutableListOf<PapeleraItem>()

        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(sql, argumentos)

        if (cursor.moveToFirst()) {

            do {

                lista.add(desdeCursor(cursor))

            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return lista
    }

    private fun valoresDe(
        fecha: String,
        cantidad: Int,
        efectivo: Double,
        transferencia: Double,
        total: Double,
        cantidadEfectivo: Int,
        cantidadTransferencia: Int
    ) = ContentValues().apply {
        put("fecha", fecha)
        put("cantidad", cantidad)
        put("efectivo", efectivo)
        put("transferencia", transferencia)
        put("total", total)
        put("cantidadEfectivo", cantidadEfectivo)
        put("cantidadTransferencia", cantidadTransferencia)
    }
}
