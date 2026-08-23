package com.example.totaldiaria.ui

import java.text.NumberFormat
import java.util.Locale

object FormatoMoneda {

    private fun crearFormato(): NumberFormat =
        NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
            maximumFractionDigits = 0
        }

    fun formatear(valor: Double): String =
        "$" + crearFormato().format(valor)

    fun paraEdicion(valor: Double): String =
        crearFormato().format(valor.toLong())

    fun parsear(texto: String): Double {
        val limpio = texto.replace(".", "").replace(",", "").trim()
        return if (limpio.isEmpty()) 0.0 else limpio.toDoubleOrNull() ?: 0.0
    }
}
