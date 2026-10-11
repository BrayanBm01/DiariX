package com.example.totaldiaria.modulos.facturacion.servicio

import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Punto único para determinar el día actual y separar las facturas por fecha.
 *
 * Desde la Fase 4 la zona por defecto es América/Bogotá: el servidor de DiariX
 * es la autoridad para fechas de creación, límites diarios y suscripciones, y
 * calcula su "día contable" en esa zona. Si la app usara la zona del
 * dispositivo, un viajero que cruce la frontera podría ver su límite diario
 * contado contra otro día que el del servidor.
 *
 * Las fechas se almacenan como texto 'dd/MM/yyyy HH:mm'; el día comparable es
 * la parte 'dd/MM/yyyy' (los 10 primeros caracteres).
 */
class DiaService(
    private val reloj: () -> Date = { Date() },
    zonaHoraria: TimeZone = ZONA_BOGOTA
) {

    private val horaLocal: TimeZone = zonaHoraria

    private val formatDia =
        SimpleDateFormat(FORMATO_FECHA, Locale.getDefault()).apply {
            timeZone = horaLocal
        }

    /** Fecha de hoy en formato 'dd/MM/yyyy' en América/Bogotá (o la inyectada). */
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

    /**
     * Fecha y hora local (dd/MM/yyyy HH:mm) de DiariX. Es el campo
     * `fechaLocal` que el servidor conserva tal cual llega.
     */
    fun fechaYHoraLocal(instante: Date? = null): String =
        SimpleDateFormat(FORMATO_FECHA_HORA, Locale.US).apply {
            timeZone = horaLocal
        }.format(instante ?: reloj())

    /**
     * Instante actual en UTC en formato ISO-8601 con milisegundos y 'Z'.
     * Es el campo `ocurrioEn` que el backend trata como autoridad para la
     * fecha contable.
     */
    fun ahoraUtcIso(instante: Date? = null): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(instante ?: reloj())

    /**
     * Instante UTC (ISO-8601 con 'Z') de una fecha local de DiariX
     * ('dd/MM/yyyy HH:mm'). Es la fecha REAL en que se registró la factura,
     * que puede ser anterior al envío si el dispositivo estuvo sin conexión.
     * Devuelve `null` si el texto no tiene el formato esperado, para que quien
     * llame pueda decidir un respaldo en vez de mandar una fecha inválida.
     */
    fun utcIsoDeFechaLocal(fechaLocal: String): String? =
        try {
            val parser = SimpleDateFormat(FORMATO_FECHA_HORA, Locale.US).apply {
                timeZone = horaLocal
                isLenient = false
            }
            val instante = parser.parse(fechaLocal) ?: return null
            ahoraUtcIso(instante)
        } catch (_: Exception) {
            null
        }

    companion object {
        const val FORMATO_FECHA = "dd/MM/yyyy"
        const val FORMATO_FECHA_HORA = "dd/MM/yyyy HH:mm"

        /** Día contable del servidor: América/Bogotá (UTC-5, sin horario de
         * verano). */
        val ZONA_BOGOTA: TimeZone = TimeZone.getTimeZone("America/Bogota")
    }
}