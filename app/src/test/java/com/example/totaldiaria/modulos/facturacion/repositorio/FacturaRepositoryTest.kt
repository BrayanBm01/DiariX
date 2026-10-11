package com.example.totaldiaria.modulos.facturacion.repositorio

import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import com.example.totaldiaria.modulos.facturacion.servicio.CierreDiarioService
import com.example.totaldiaria.modulos.facturacion.servicio.DiaService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pruebas sobre SQLite real (Robolectric) del contador por día y del
 * cierre diario. Cubren el ciclo completo: creación de facturas en
 * varios días, cambio de día, archivo y conservación de históricos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FacturaRepositoryTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private val diaDia1 = "14/09/2026"
    private val diaDia2 = "15/09/2026"
    private val diaDia3 = "16/09/2026"

    private lateinit var context: android.content.Context
    private lateinit var repos: FacturaRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("facturas.db")
        repos = FacturaRepository(context)
    }

    @After
    fun tearDown() {
        context.deleteDatabase("facturas.db")
    }

    private fun factura(
        id: Int,
        dia: String,
        efectivo: Double = 1000.0
    ): Factura =
        Factura(
            id = id,
            numeroFactura = id.toString(),
            efectivo = efectivo,
            transferencia = 0.0,
            fecha = "$dia 10:15"
        )

    private fun cierre(diaActual: String): CierreDiarioService =
        CierreDiarioService(
            context,
            DiaService(
                reloj = {
                    SimpleDateFormat(
                        "dd/MM/yyyy HH:mm",
                        Locale.US
                    ).apply { timeZone = utc }
                        .parse("$diaActual 12:00")!!
                },
                zonaHoraria = utc
            )
        )

    // TEST 1: Crear facturas del día actual: el contador muestra el
    // número correcto.
    @Test
    fun `contador del dia actual refleja sus facturas y totales`() {
        repos.insertarFactura(factura(1, diaDia2, 1000.0))
        repos.insertarFactura(factura(2, diaDia2, 2000.0))

        val delDia = repos.obtenerFacturasDelDia(diaDia2)

        assertEquals(2, delDia.size)

        val total = delDia.sumOf { it.efectivo + it.transferencia }

        assertEquals(3000.0, total, 0.0)
    }

    // TEST 2: Las facturas del día anterior no se contabilizan como
    // del día actual.
    @Test
    fun `facturas del dia anterior no se cuentan como hoy`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia1))

        val hoy = repos.obtenerFacturasDelDia(diaDia2)

        assertEquals(0, hoy.size)
    }

    // TEST 3 + 4 + 5: Cambio de día: contador en 0, luego 1 con la
    // primera factura y exacto con varias.
    @Test
    fun `al cambiar de dia el contador arranca en cero y crece con las facturas`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia1))

        assertEquals(0, repos.obtenerFacturasDelDia(diaDia2).size)

        repos.insertarFactura(factura(3, diaDia2))

        assertEquals(1, repos.obtenerFacturasDelDia(diaDia2).size)

        repos.insertarFactura(factura(4, diaDia2))
        repos.insertarFactura(factura(5, diaDia2))

        assertEquals(3, repos.obtenerFacturasDelDia(diaDia2).size)
    }

    // TEST 6/7/8: El conteo no depende de estados guardados: aunque
    // las facturas del día anterior sigan ACTIVAS, el día actual solo
    // cuenta las suyas.
    @Test
    fun `el contador se deriva de las facturas del dia aunque no se haya archivado`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia1))
        repos.insertarFactura(factura(3, diaDia2))

        val delDia = repos.obtenerFacturasDelDia(diaDia2)

        assertEquals(1, delDia.size)
        assertEquals(diaDia2, delDia[0].fecha.take(10))
    }

    // TEST de archivo: al aplicar el cierre, las facturas anteriores
    // pasan a ARCHIVADA y se genera un registro por cada día real.
    @Test
    fun `el cierre archiva dias anteriores y crea un registro por dia`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia1))
        repos.insertarFactura(factura(3, diaDia2))
        repos.insertarFactura(factura(4, diaDia3))

        val servicio = cierre(diaDia3)

        servicio.archivarFacturasDeDiasAnteriores()

        val activas = repos.obtenerFacturas()
        val registros = RegistroRepository(context).obtenerRegistros()

        // Solo queda activa la del día actual.
        assertEquals(listOf(4), activas.map { it.id })

        // Un registro por día anterior (2 días), no uno consolidado.
        assertEquals(2, registros.size)

        val fechas = registros.map { it.fecha }.sorted()

        assertEquals(listOf(diaDia1, diaDia2), fechas)
    }

    // TEST 9/10: El cierre distingue cambios de mes y de año.
    @Test
    fun `el cierre respeta el cambio de mes`() {
        repos.insertarFactura(factura(1, "31/01/2026"))
        repos.insertarFactura(factura(2, "01/02/2026"))

        val servicio = cierre("02/02/2026")

        servicio.archivarFacturasDeDiasAnteriores()

        val registros = RegistroRepository(context).obtenerRegistros()

        assertEquals(2, registros.size)
    }

    @Test
    fun `el cierre respeta el cambio de anio`() {
        repos.insertarFactura(factura(1, "31/12/2025"))
        repos.insertarFactura(factura(2, "01/01/2026"))

        val servicio = cierre("02/01/2026")

        servicio.archivarFacturasDeDiasAnteriores()

        val registros = RegistroRepository(context).obtenerRegistros()

        assertEquals(2, registros.size)
    }

    // TEST 11: Las facturas históricas se conservan tras el cierre.
    @Test
    fun `las facturas historicas se conservan despues del cierre`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia2))

        cierre(diaDia3).archivarFacturasDeDiasAnteriores()

        val historicas = repos.obtenerFacturasPorFecha(diaDia1)

        assertEquals(1, historicas.size)
        assertEquals(diaDia1, historicas[0].fecha.take(10))

        val dia2 = repos.obtenerFacturasPorFecha(diaDia2)

        assertEquals(1, dia2.size)
    }

    // TEST 12: Las facturas históricas archivadas no interfieren con
    // el contador del día actual.
    @Test
    fun `las historicas archivadas no modifican el contador del dia`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia1))
        repos.insertarFactura(factura(3, diaDia3))

        cierre(diaDia3).archivarFacturasDeDiasAnteriores()

        assertEquals(1, repos.obtenerFacturasDelDia(diaDia3).size)
    }

    // Seguridad: aplicar el cierre varias veces es idempotente; no
    // se generan registros duplicados.
    @Test
    fun `aplicar el cierre varias veces no duplica registros`() {
        repos.insertarFactura(factura(1, diaDia1))
        repos.insertarFactura(factura(2, diaDia2))

        val servicio = cierre(diaDia3)

        servicio.archivarFacturasDeDiasAnteriores()
        servicio.archivarFacturasDeDiasAnteriores()
        servicio.archivarFacturasDeDiasAnteriores()

        val registros = RegistroRepository(context).obtenerRegistros()

        assertEquals(2, registros.size)
    }

    @Test
    fun `los totales del dia incluyen efectivo y transferencia`() {
        repos.insertarFactura(
            Factura(
                id = 1,
                numeroFactura = "1",
                efectivo = 1500.0,
                transferencia = 500.0,
                fecha = "$diaDia2 09:00"
            )
        )

        repos.insertarFactura(
            Factura(
                id = 2,
                numeroFactura = "2",
                efectivo = 0.0,
                transferencia = 2000.0,
                fecha = "$diaDia2 09:05"
            )
        )

        val delDia = repos.obtenerFacturasDelDia(diaDia2)

        val total = delDia.sumOf { it.efectivo + it.transferencia }

        assertEquals(4000.0, total, 0.0)
        assertTrue(delDia.all { it.fecha.take(10) == diaDia2 })
    }
}