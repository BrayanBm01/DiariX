package com.example.totaldiaria.service

import com.example.totaldiaria.models.Factura
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pruebas del día de trabajo y de la separación de facturas por
 * fecha. Usa un reloj inyectado y una zona horaria fija (UTC) para
 * que los casos de cambio de mes/año sean deterministas.
 */
class DiaServiceTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun fecha(dia: String): Date =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply {
            timeZone = utc
        }.parse("$dia 12:00")!!

    private fun servicio(diaActual: String): DiaService =
        DiaService(
            reloj = { fecha(diaActual) },
            zonaHoraria = utc
        )

    private fun factura(
        id: Int,
        dia: String,
        efectivo: Double = 10.0
    ): Factura =
        Factura(
            id = id,
            numeroFactura = id.toString(),
            efectivo = efectivo,
            transferencia = 0.0,
            fecha = "$dia 10:30"
        )

    // TEST 1: Las facturas del día actual se cuentan correctamente.
    @Test
    fun `hoy devuelve el dia actual en dd MM yyyy`() {
        assertEquals("15/09/2026", servicio("15/09/2026").hoy())
    }

    // TEST 2: Las facturas del día anterior no se contabilizan hoy.
    @Test
    fun `las facturas de otros dias se excluyen del dia actual`() {
        val s = servicio("15/09/2026")

        val lista = listOf(
            factura(1, "15/09/2026"),
            factura(2, "14/09/2026"),
            factura(3, "15/09/2026")
        )

        val delDia = s.filtrarDelDia(lista, s.hoy())

        assertEquals(listOf(1, 3), delDia.map { it.id })
    }

    @Test
    fun `esDelDia distingue dias consecutivos`() {
        val s = servicio("15/09/2026")

        assertTrue(s.esDelDia("15/09/2026 23:59", "15/09/2026"))
        assertFalse(s.esDelDia("14/09/2026 23:59", "15/09/2026"))
        assertTrue(s.esDelDia("15/09/2026 00:00", "15/09/2026"))
    }

    // TEST 3: Al cambiar de día, el contador del nuevo día arranca en 0.
    @Test
    fun `dia nuevo sin facturas queda en cero`() {
        val lista = listOf(
            factura(1, "14/09/2026"),
            factura(2, "14/09/2026")
        )

        val diaNuevo = servicio("15/09/2026")

        assertEquals(
            0,
            diaNuevo.filtrarDelDia(lista, diaNuevo.hoy()).size
        )
    }

    // TEST 4: Tras crear una factura en el día nuevo, el contador es 1.
    @Test
    fun `una factura nueva en el dia nuevo cuenta exactamente una`() {
        val lista = listOf(
            factura(1, "14/09/2026"),
            factura(2, "15/09/2026")
        )

        val diaNuevo = servicio("15/09/2026")

        assertEquals(
            listOf(2),
            diaNuevo.filtrarDelDia(lista, diaNuevo.hoy()).map { it.id }
        )
    }

    // TEST 5: Varias facturas en el día nuevo coinciden exactamente.
    @Test
    fun `varias facturas del dia coinciden con el contador`() {
        val lista = (1..7).map {
            factura(it, "15/09/2026")
        } + factura(8, "13/09/2026")

        val s = servicio("15/09/2026")

        assertEquals(7, s.filtrarDelDia(lista, s.hoy()).size)

        val total = s.filtrarDelDia(lista, s.hoy()).sumOf {
            it.efectivo + it.transferencia
        }

        assertEquals(70.0, total, 0.0)
    }

    // TEST 9: Cambio de mes (31/01 → 01/02).
    @Test
    fun `cambio de mes se detecta correctamente`() {
        val lista = listOf(
            factura(1, "31/01/2026"),
            factura(2, "01/02/2026")
        )

        val primeroFebrero = servicio("01/02/2026")

        assertFalse(primeroFebrero.esDelDia("31/01/2026", "01/02/2026"))
        assertEquals(
            listOf(2),
            primeroFebrero.filtrarDelDia(lista, primeroFebrero.hoy())
                .map { it.id }
        )
    }

    @Test
    fun `cambio de mes sin facturas nuevas deja el contador en cero`() {
        val lista = listOf(
            factura(1, "31/01/2026"),
            factura(2, "30/01/2026")
        )

        val primeroFebrero = servicio("01/02/2026")

        assertEquals(
            0,
            primeroFebrero.filtrarDelDia(lista, primeroFebrero.hoy()).size
        )
    }

    // TEST 10: Cambio de año (31/12 → 01/01).
    @Test
    fun `cambio de anio se detecta correctamente`() {
        val lista = listOf(
            factura(1, "31/12/2025"),
            factura(2, "01/01/2026")
        )

        val primeroEnero = servicio("01/01/2026")

        assertFalse(primeroEnero.esDelDia("31/12/2025", "01/01/2026"))
        assertEquals(
            listOf(2),
            primeroEnero.filtrarDelDia(lista, primeroEnero.hoy())
                .map { it.id }
        )
    }

    // TEST 12: Las facturas históricas no interfieren con el contador.
    @Test
    fun `facturas historicas no interfieren con el conteo del dia`() {
        val historicas = listOf(
            factura(100, "10/09/2026", 500.0),
            factura(101, "11/09/2026", 300.0),
            factura(102, "12/09/2026", 200.0)
        )

        val s = servicio("15/09/2026")

        assertEquals(0, s.filtrarDelDia(historicas, s.hoy()).size)
    }
}