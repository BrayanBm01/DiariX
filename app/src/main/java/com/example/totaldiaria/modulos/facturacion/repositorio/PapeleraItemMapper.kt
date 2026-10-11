package com.example.totaldiaria.modulos.facturacion.repositorio

import android.database.Cursor
import com.example.totaldiaria.modulos.facturacion.modelo.PapeleraItem

internal object PapeleraItemMapper {

    fun desdeCursor(cursor: Cursor): PapeleraItem =

        PapeleraItem(
            fecha = cursor.getString(
                cursor.getColumnIndexOrThrow("fecha")
            ),
            cantidadFacturas = cursor.getInt(
                cursor.getColumnIndexOrThrow("cantidad")
            ),
            efectivo = cursor.getDouble(
                cursor.getColumnIndexOrThrow("efectivo")
            ),
            transferencia = cursor.getDouble(
                cursor.getColumnIndexOrThrow("transferencia")
            ),
            total = cursor.getDouble(
                cursor.getColumnIndexOrThrow("total")
            ),
            cantidadEfectivo = cursor.getInt(
                cursor.getColumnIndexOrThrow("cantidadEfectivo")
            ),
            cantidadTransferencia = cursor.getInt(
                cursor.getColumnIndexOrThrow("cantidadTransferencia")
            )
        )
}
