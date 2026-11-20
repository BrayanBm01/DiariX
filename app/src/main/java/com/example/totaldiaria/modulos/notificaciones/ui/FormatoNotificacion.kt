package com.example.totaldiaria.modulos.notificaciones.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Fechas de las notificaciones.
 *
 * El backend manda `creadaEn` en ISO-8601 UTC (`2026-11-14T12:51:20.468Z`).
 * Se muestra en la zona del teléfono, que es la que entiende quien lo lee.
 * Si el texto no se puede interpretar, se devuelve un recorte legible en vez
 * de una excepción: una fecha rara no debe tumbar la bandeja.
 */
object FormatoNotificacion {

    private val SALIDA: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a", Locale.getDefault())

    fun fechaHora(iso: String): String =
        try {
            Instant.parse(iso)
                .atZone(ZoneId.systemDefault())
                .format(SALIDA)
        } catch (_: Exception) {
            iso.take(16).replace('T', ' ')
        }
}
