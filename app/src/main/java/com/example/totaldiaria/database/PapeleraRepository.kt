package com.example.totaldiaria.database

import android.content.ContentValues
import android.content.Context
import com.example.totaldiaria.database.PapeleraItemMapper.desdeCursor
import com.example.totaldiaria.models.PapeleraItem

class PapeleraRepository(context: Context) {

    private val dbHelper = DatabaseHelper(context)

    fun guardarEnPapelera(
        fecha: String,
        cantidad: Int,
        efectivo: Double,
        transferencia: Double,
        total: Double,
        cantidadEfectivo: Int,
        cantidadTransferencia: Int
    ) {

        val db = dbHelper.writableDatabase

        val values = ContentValues().apply {
            put("fecha", fecha)
            put("cantidad", cantidad)
            put("efectivo", efectivo)
            put("transferencia", transferencia)
            put("total", total)
            put("cantidadEfectivo", cantidadEfectivo)
            put("cantidadTransferencia", cantidadTransferencia)
        }

        db.insert(
            "papelera",
            null,
            values
        )

        db.close()
    }

    fun obtenerPapelera(): MutableList<PapeleraItem> {

        val lista = mutableListOf<PapeleraItem>()

        val db = dbHelper.readableDatabase

        val cursor = db.rawQuery(
            "SELECT * FROM papelera ORDER BY id DESC",
            null
        )

        if (cursor.moveToFirst()) {

            do {

                lista.add(desdeCursor(cursor))

            } while (cursor.moveToNext())
        }

        cursor.close()
        db.close()

        return lista
    }
}
