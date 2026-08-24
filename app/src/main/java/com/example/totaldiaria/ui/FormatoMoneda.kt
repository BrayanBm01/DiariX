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

    /**
     * Versión compacta para ejes y etiquetas de gráficas:
     * $1,2M / $850k / $15.000.
     */
    fun formatearCompacto(valor: Double): String {

        val absoluto = Math.abs(valor)

        val sufijo = when {

            absoluto >= 1_000_000.0 -> Pair(valor / 1_000_000.0, "M")

            absoluto >= 1_000.0 -> Pair(valor / 1_000.0, "k")

            else -> null
        }

        if (sufijo == null) {
            return formatear(valor)
        }

        var numero = crearFormato().format(sufijo.first)

        if (numero.endsWith(",0")) {
            numero = numero.dropLast(2)
        }

        return "$$numero${sufijo.second}"
    }

    fun paraEdicion(valor: Double): String =
        crearFormato().format(valor.toLong())

    fun parsear(texto: String): Double {
        val limpio = texto.replace(".", "").replace(",", "").trim()
        return if (limpio.isEmpty()) 0.0 else limpio.toDoubleOrNull() ?: 0.0
    }
}
