package com.example.totaldiaria.service

import android.content.Context
import com.example.totaldiaria.database.FacturaRepository
import com.example.totaldiaria.database.PapeleraRepository
import com.example.totaldiaria.database.RegistroRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Consolida el cierre del día:
 *  1. Las facturas de días anteriores se resumen en un registro diario
 *     y se marcan como ARCHIVADA.
 *  2. Los registros que exceden el límite de días se mueven a la papelera.
 */
class CierreDiarioService(context: Context) {

    private val facturaRepository = FacturaRepository(context)
    private val registroRepository = RegistroRepository(context)
    private val papeleraRepository = PapeleraRepository(context)

    fun archivarFacturasDeDiasAnteriores() {

        val hoy = SimpleDateFormat(
            FORMATO_FECHA,
            Locale.getDefault()
        ).format(Date())

        val facturas = facturaRepository.obtenerFacturas()

        if (facturas.isEmpty()) return

        val antiguas = facturas.filter {
            it.fecha.take(10) != hoy
        }

        if (antiguas.isEmpty()) return

        val cantidad = antiguas.size

        val efectivo = antiguas.sumOf {
            it.efectivo
        }

        val transferencia = antiguas.sumOf {
            it.transferencia
        }

        val total = efectivo + transferencia

        val cantidadEfectivo =
            antiguas.count { it.efectivo > 0 }

        val cantidadTransferencia =
            antiguas.count { it.transferencia > 0 }

        val fechaRegistro =
            antiguas.first().fecha.take(10)

        registroRepository.guardarEnRegistros(
            fechaRegistro,
            cantidad,
            efectivo,
            transferencia,
            total,
            cantidadEfectivo,
            cantidadTransferencia
        )

        facturaRepository.marcarComoArchivadasAnterioresA(hoy)
    }

    fun moverRegistrosExcedentesAPapelera() {

        val lista =
            registroRepository.obtenerRegistros()

        if (lista.size <= LIMITE_REGISTROS)
            return

        val sobrantes =
            lista.dropLast(LIMITE_REGISTROS)

        for (item in sobrantes) {

            papeleraRepository.guardarEnPapelera(
                item.fecha,
                item.cantidadFacturas,
                item.efectivo,
                item.transferencia,
                item.total,
                item.cantidadEfectivo,
                item.cantidadTransferencia
            )
        }

        registroRepository.eliminarRegistrosExcedentes(LIMITE_REGISTROS)
    }

    companion object {

        private const val FORMATO_FECHA = "dd/MM/yyyy"

        const val LIMITE_REGISTROS = 30
    }
}
