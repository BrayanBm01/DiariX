package com.example.totaldiaria.modulos.facturacion.repositorio

import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.modulos.facturacion.modelo.EstadoSincronizacion
import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pruebas de la cola de sincronización sobre SQLite real (Fase 4).
 *
 * Comprueban lo que sostiene la idempotencia y la nunca-pérdida de datos:
 * `clientUid` único y persistente, estados de la cola y transiciones.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ColaDeSincronizacionTest {

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

    private fun factura(numero: String) = Factura(
        id = 0,
        numeroFactura = numero,
        efectivo = 10_000.0,
        transferencia = 0.0,
        fecha = "01/10/2026 10:00",
    )

    @Test
    fun `cada factura nueva recibe un clientUid`() {
        repos.insertarFactura(factura("1"))
        repos.insertarFactura(factura("2"))

        val pendientes = repos.obtenerPendientesDeSincronizacion()

        assertEquals(2, pendientes.size)
        pendientes.forEach {
            assertNotNull("el clientUid nunca es null", it.clientUid)
            assertTrue(it.clientUid!!.isNotBlank())
        }
    }

    @Test
    fun `los clientUid de facturas distintas nunca se repiten`() {
        repos.insertarFactura(factura("1"))
        repos.insertarFactura(factura("2"))
        repos.insertarFactura(factura("3"))

        val uids = repos.obtenerPendientesDeSincronizacion()
            .map { it.clientUid }
            .toSet()

        assertEquals(3, uids.size)
    }

    @Test
    fun `una factura guardada arranca pendiente y sin id de servidor`() {
        repos.insertarFactura(factura("1"))

        val f = repos.obtenerFacturasDelDia("01/10/2026").single()

        assertEquals(EstadoSincronizacion.PENDIENTE, f.estadoSincronizacion)
        assertNull(f.servidorId)
        assertEquals(0, f.intentos)
    }

    @Test
    fun `al marcar sincronizada sale de la cola y guarda el id del servidor`() {
        repos.insertarFactura(factura("1"))

        val pendiente = repos.obtenerPendientesDeSincronizacion().single()

        repos.marcarSincronizada(pendiente.id, "fact_123")

        assertEquals(0, repos.obtenerPendientesDeSincronizacion().size)
        assertEquals(0, repos.contarPendientesDeSincronizacion())

        val f = repos.obtenerFacturasDelDia("01/10/2026").single()

        assertEquals(EstadoSincronizacion.SINCRONIZADA, f.estadoSincronizacion)
        assertEquals("fact_123", f.servidorId)
    }

    @Test
    fun `al marcar rechazada se conserva la factura con su mensaje`() {
        repos.insertarFactura(factura("1"))

        val pendiente = repos.obtenerPendientesDeSincronizacion().single()

        repos.marcarRechazada(
            pendiente.id,
            "Alcanzaste el límite de 15 facturas de este día.",
            1
        )

        // La factura NO se borra: sigue listada para el usuario.
        val f = repos.obtenerFacturasDelDia("01/10/2026").single()

        assertEquals(EstadoSincronizacion.RECHAZADA, f.estadoSincronizacion)
        assertEquals(
            "Alcanzaste el límite de 15 facturas de este día.",
            f.ultimoError
        )
        assertEquals(1, f.intentos)

        // Pero tampoco se reintenta sola.
        assertEquals(0, repos.obtenerPendientesDeSincronizacion().size)
    }

    @Test
    fun `el limite de lote respeta el tamaño pedido`() {
        (1..5).forEach { repos.insertarFactura(factura(it.toString())) }

        assertEquals(2, repos.obtenerPendientesDeSincronizacion(2).size)
        assertEquals(5, repos.contarPendientesDeSincronizacion())
    }

    @Test
    fun `los totales del dia no dependen del estado de sincronizacion`() {
        repos.insertarFactura(factura("1"))

        val pendiente = repos.obtenerPendientesDeSincronizacion().single()

        repos.marcarRechazada(pendiente.id, "error", 1)

        // Rechazada sigue contando como factura del día: el registro local
        // del usuario nunca se deforma por lo que diga el servidor.
        val delDia = repos.obtenerFacturasDelDia("01/10/2026")

        assertEquals(1, delDia.size)
        assertEquals(10_000.0, delDia.sumOf { it.efectivo }, 0.0)
    }

    @Test
    fun `una factura archivada que quedo pendiente sigue en la cola`() {
        repos.insertarFactura(
            Factura(
                id = 0,
                numeroFactura = "1",
                efectivo = 5_000.0,
                transferencia = 0.0,
                fecha = "01/10/2026 09:00",
            )
        )

        // El día cambia: el cierre archiva la factura, pero si no se pudo
        // subir, sigue esperando su turno (día entero sin internet, por
        // ejemplo) para no perderla.
        repos.marcarComoArchivadasAnterioresA("02/10/2026")

        assertEquals(1, repos.obtenerPendientesDeSincronizacion().size)
    }
}
