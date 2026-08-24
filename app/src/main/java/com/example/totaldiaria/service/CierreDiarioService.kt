package com.example.totaldiaria.service

import android.content.Context
import com.example.totaldiaria.database.DatabaseHelper
import com.example.totaldiaria.database.FacturaRepository
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
class CierreDiarioService(private val context: Context) {

    private val facturaRepository = FacturaRepository(context)
    private val registroRepository = RegistroRepository(context)

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

    /**
     * Ciclo de registros diarios.
     *
     * Un "registro diario" es una fila de la tabla registros creada
     * por el cierre (una por cierre, no por factura). Se conservan los
     * últimos [LIMITE_REGISTROS]; cuando un cierre nuevo hace superar
     * ese límite, todo el ciclo anterior completo pasa a la tabla
     * papelera y el registro más reciente inicia el ciclo siguiente.
     *
     * Todo el movimiento ocurre en una transacción: o se archiva el
     * ciclo completo o no se toca nada.
     */
    fun aplicarCicloRegistros() {

        val db =
            DatabaseHelper(context).writableDatabase

        db.beginTransaction()

        try {

            val cursor = db.rawQuery(
                "SELECT COUNT(*) FROM registros",
                null
            )

            val cantidad =
                if (cursor.moveToFirst()) cursor.getInt(0) else 0

            cursor.close()

            if (cantidad > LIMITE_REGISTROS) {

                db.execSQL(
                    """
                    INSERT INTO papelera(
                        fecha, cantidad, efectivo, transferencia,
                        total, cantidadEfectivo, cantidadTransferencia
                    )
                    SELECT fecha, cantidad, efectivo, transferencia,
                           total, cantidadEfectivo, cantidadTransferencia
                    FROM registros
                    WHERE id <> (SELECT MAX(id) FROM registros)
                    ORDER BY id ASC
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    DELETE FROM registros
                    WHERE id <> (SELECT MAX(id) FROM registros)
                    """.trimIndent()
                )
            }

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()

            db.close()
        }
    }

    companion object {

        private const val FORMATO_FECHA = "dd/MM/yyyy"

        const val LIMITE_REGISTROS = 30
    }
}
