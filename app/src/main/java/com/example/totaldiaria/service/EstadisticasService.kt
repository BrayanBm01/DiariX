package com.example.totaldiaria.service

import android.content.Context
import com.example.totaldiaria.database.DatabaseHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Estadísticas de facturas calculadas directamente desde la base de
 * datos. Considera todas las facturas (ACTIVA y ARCHIVADA) porque
 * ambas representan ingresos reales registrados.
 *
 * Las fechas se guardan como texto 'dd/MM/yyyy HH:mm'; para comparar
 * rangos se convierten a día comparable dentro de la propia consulta.
 */
class EstadisticasService(private val context: Context) {

    // ------------------------------------------------------------------
    // Filtros de periodo
    // ------------------------------------------------------------------

    enum class Periodo(val etiqueta: String) {

        HOY("Hoy"),
        ULTIMOS_7("Últimos 7 días"),
        ULTIMOS_30("Últimos 30 días"),
        ESTE_MES("Este mes")
    }

    /** Expresión SQL que convierte 'dd/MM/yyyy HH:mm' en 'yyyy-MM-dd'. */
    private val expresionDia =
        "substr(fecha,7,4) || '-' || substr(fecha,4,2) || '-' || substr(fecha,1,2)"

    private fun condicion(periodo: Periodo): Pair<String, Array<String>> =

        when (periodo) {

            Periodo.HOY -> Pair(
                "substr(fecha,1,10) = ?",
                arrayOf(hoy())
            )

            Periodo.ULTIMOS_7 -> Pair(
                "$expresionDia >= ?",
                arrayOf(diaRelativo(-6))
            )

            Periodo.ULTIMOS_30 -> Pair(
                "$expresionDia >= ?",
                arrayOf(diaRelativo(-29))
            )

            Periodo.ESTE_MES -> Pair(
                // 'MM/yyyy' ocupa las posiciones 4 a 10 de 'dd/MM/yyyy ...'
                "substr(fecha,4,7) = ?",
                arrayOf(hoy().substring(3))
            )
        }

    private fun hoy(): String =

        SimpleDateFormat(
            "dd/MM/yyyy",
            Locale.getDefault()
        ).format(Date())

    /**
     * Día desplazado [dias] desde hoy, en el mismo formato comparable
     * que produce [expresionDia] ('yyyy-MM-dd').
     */
    private fun diaRelativo(dias: Int): String {

        val calendario = Calendar.getInstance()

        calendario.add(Calendar.DAY_OF_YEAR, dias)

        return SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(calendario.time)
    }

    // ------------------------------------------------------------------
    // Modelos de resultado
    // ------------------------------------------------------------------

    data class MetodoPago(

        val nombre: String,
        val monto: Double,
        val cantidadFacturas: Int,
        val porcentaje: Double
    )

    data class ActividadDia(

        val fecha: String,
        val cantidadFacturas: Int,
        val total: Double
    )

    data class Resumen(

        val totalFacturas: Int,
        val ingresosTotales: Double,
        val promedioPorFactura: Double,
        val metodosPago: List<MetodoPago>,
        val actividadPorDia: List<ActividadDia>,
        val mejorDiaFecha: String?,
        val mejorDiaTotal: Double,
        val cantidadSoloEfectivo: Int,
        val cantidadSoloTransferencia: Int,
        val cantidadAmbos: Int
    )

    // ------------------------------------------------------------------
    // Cálculo
    // ------------------------------------------------------------------

    fun obtenerResumen(periodo: Periodo): Resumen {

        val db = DatabaseHelper(context).readableDatabase

        try {

            val (condicion, argumentos) = condicion(periodo)

            val cursor = db.rawQuery(
                """
                SELECT
                    COUNT(*),
                    COALESCE(SUM(efectivo + transferencia), 0),
                    COALESCE(SUM(efectivo), 0),
                    COALESCE(SUM(transferencia), 0),
                    COALESCE(SUM(CASE WHEN efectivo > 0 AND transferencia = 0 THEN 1 ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN transferencia > 0 AND efectivo = 0 THEN 1 ELSE 0 END), 0),
                    COALESCE(SUM(CASE WHEN efectivo > 0 AND transferencia > 0 THEN 1 ELSE 0 END), 0)
                FROM facturas
                WHERE $condicion
                """.trimIndent(),
                argumentos
            )

            cursor.moveToFirst()

            val totalFacturas = cursor.getInt(0)

            val ingresosTotales = cursor.getDouble(1)

            val efectivoTotal = cursor.getDouble(2)

            val transferenciaTotal = cursor.getDouble(3)

            val cantidadSoloEfectivo = cursor.getInt(4)

            val cantidadSoloTransferencia = cursor.getInt(5)

            val cantidadAmbos = cursor.getInt(6)

            cursor.close()

            val promedio =

                if (totalFacturas > 0) ingresosTotales / totalFacturas
                else 0.0

            val metodos = listOf(

                MetodoPago(
                    nombre = "Efectivo",
                    monto = efectivoTotal,
                    cantidadFacturas = cantidadSoloEfectivo + cantidadAmbos,
                    porcentaje = porcentaje(
                        efectivoTotal,
                        ingresosTotales
                    )
                ),

                MetodoPago(
                    nombre = "Transferencia",
                    monto = transferenciaTotal,
                    cantidadFacturas = cantidadSoloTransferencia + cantidadAmbos,
                    porcentaje = porcentaje(
                        transferenciaTotal,
                        ingresosTotales
                    )
                )
            ).filter {
                it.monto > 0 || it.cantidadFacturas > 0
            }

            val dias = actividadPorDia(db, condicion, argumentos)

            val mejorDia = dias.maxByOrNull { it.total }

            return Resumen(
                totalFacturas = totalFacturas,
                ingresosTotales = ingresosTotales,
                promedioPorFactura = promedio,
                metodosPago = metodos,
                actividadPorDia = dias,
                mejorDiaFecha = mejorDia?.fecha,
                mejorDiaTotal = mejorDia?.total ?: 0.0,
                cantidadSoloEfectivo = cantidadSoloEfectivo,
                cantidadSoloTransferencia = cantidadSoloTransferencia,
                cantidadAmbos = cantidadAmbos
            )

        } finally {

            db.close()
        }
    }

    private fun actividadPorDia(
        db: android.database.sqlite.SQLiteDatabase,
        condicion: String,
        argumentos: Array<String>
    ): List<ActividadDia> {

        val lista = mutableListOf<ActividadDia>()

        val cursor = db.rawQuery(
            """
            SELECT
                substr(fecha, 1, 10) AS dia,
                COUNT(*) AS cantidad,
                COALESCE(SUM(efectivo + transferencia), 0) AS total
            FROM facturas
            WHERE $condicion
            GROUP BY dia
            ORDER BY $expresionDia DESC
            """.trimIndent(),
            argumentos
        )

        if (cursor.moveToFirst()) {

            do {

                lista.add(
                    ActividadDia(
                        fecha = cursor.getString(0),
                        cantidadFacturas = cursor.getInt(1),
                        total = cursor.getDouble(2)
                    )
                )

            } while (cursor.moveToNext())
        }

        cursor.close()

        return lista
    }

    private fun porcentaje(parte: Double, total: Double): Double =

        if (total > 0) parte / total * 100.0 else 0.0

    /**
     * Convierte 'dd/MM/yyyy' en una etiqueta corta tipo '23 Ago'.
     */
    fun etiquetaCorta(fecha: String): String {

        return try {

            val fechaParseada = SimpleDateFormat(
                "dd/MM/yyyy",
                Locale.getDefault()
            ).parse(fecha)

            if (fechaParseada == null) {

                fecha

            } else {

                SimpleDateFormat(
                    "dd MMM",
                    Locale.getDefault()
                ).format(fechaParseada)
            }

        } catch (e: Exception) {

            fecha
        }
    }
}
