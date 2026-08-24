package com.example.totaldiaria.ui

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Utilidad única para mostrar la hora de las facturas en formato
 * 12 horas ('hh:mm a'). La base de datos sigue guardando
 * 'dd/MM/yyyy HH:mm'; la conversión ocurre solo al mostrar.
 */
object FormatoHora {

    private const val FORMATO_GUARDADO = "dd/MM/yyyy HH:mm"

    fun hora12(fecha: String): String {

        return try {

            val parseada = SimpleDateFormat(
                FORMATO_GUARDADO,
                Locale.getDefault()
            ).parse(fecha)

            if (parseada == null) {

                horaSinConvertir(fecha)

            } else {

                // Locale.US garantiza 'AM'/'PM' tal como se pide;
                // la hora corresponde a la zona del dispositivo.
                SimpleDateFormat(
                    "hh:mm a",
                    Locale.US
                ).format(parseada)
            }

        } catch (e: Exception) {

            horaSinConvertir(fecha)
        }
    }

    /**
     * Fecha completa para listados: la parte de la fecha no cambia,
     * solo la hora se muestra en 12 horas.
     */
    fun fechaConHora12(fecha: String): String =

        if (fecha.length > 10) {

            "${fecha.take(10)} ${hora12(fecha)}"

        } else {

            fecha
        }

    private fun horaSinConvertir(fecha: String): String =

        fecha.substringAfter(" ", fecha)
}
