package com.example.totaldiaria.service

import com.example.totaldiaria.models.Factura
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Punto único para determinar el día actual y separar las facturas
 * por fecha. Usa la zona horaria local del dispositivo.
 *
 * Las fechas se almacenan como texto 'dd/MM/yyyy HH:mm'; el día
 * comparable es la parte 'dd/MM/yyyy' (los 10 primeros caracteres).
 */
class DiaService(
    private val reloj: () -> Date = { Date() },
    zonaHoraria: TimeZone = TimeZone.getDefault()
) {

    private val formatDia =
        SimpleDateFormat(FORMATO_FECHA, Locale.getDefault()).apply {
            timeZone = zonaHoraria
        }

    /** Fecha de hoy en formato 'dd/MM/yyyy' según el dispositivo. */
    fun hoy(): String =
        formatDia.format(reloj())

    /** true si la fecha almacenada corresponde al día [dia]. */
    fun esDelDia(fechaAlmacenada: String, dia: String): Boolean =
        fechaAlmacenada.take(FORMATO_FECHA.length) == dia

    /** Facturas cuyo día coincide exactamente con [dia]. */
    fun filtrarDelDia(
        facturas: List<Factura>,
        dia: String
    ): List<Factura> =
        facturas.filter { esDelDia(it.fecha, dia) }

    companion object {
        const val FORMATO_FECHA = "dd/MM/yyyy"
    }
}