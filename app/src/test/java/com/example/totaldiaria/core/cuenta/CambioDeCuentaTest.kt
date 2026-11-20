package com.example.totaldiaria.core.cuenta

import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import com.example.totaldiaria.modulos.facturacion.repositorio.FacturaRepository
import com.example.totaldiaria.modulos.facturacion.repositorio.RegistroRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Aislamiento local por cuenta (BD por usuario, sin cambiar el esquema).
 *
 * El requisito de negocio: A registra facturas, B entra con OTRA cuenta y
 * encuentra su espacio vacío; al volver, A conserva exactamente lo suyo. Aquí
 * se comprueba sobre SQLite real que cada dueño lee y escribe SU base, y que
 * sin cuenta confirmada no se ve nada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CambioDeCuentaTest {

    private lateinit var context: android.content.Context

    private val dueñoA = "11111111-1111-1111-1111-111111111111"
    private val dueñoB = "22222222-2222-2222-2222-222222222222"

    private val dia = "19/11/2026"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CuentaLocal.limpiar()
        borrarBases()
    }

    @After
    fun tearDown() {
        borrarBases()
        CuentaLocal.limpiar()
    }

    private fun borrarBases() {
        listOf(dueñoA, dueñoB, "sin-cuenta").forEach { id ->
            context.deleteDatabase(CuentaLocal.nombreBase(id))
        }
    }

    private fun factura(numero: String, uid: String) = Factura(
        id = 0,
        numeroFactura = numero,
        efectivo = 5_000.0,
        transferencia = 0.0,
        fecha = "$dia 10:15",
        clientUid = uid,
    )

    @Test
    fun `el usuario B no ve las facturas del usuario A y cada uno conserva las suyas`() {
        // A registra una factura.
        CuentaLocal.fijar(dueñoA)
        val repoA = FacturaRepository(context)
        assertTrue(repoA.insertarFactura(factura("A-1", "uid-a-1")))
        assertEquals(1, repoA.obtenerFacturasDelDia(dia).size)

        // B entra con otra cuenta: su espacio está VACÍO.
        CuentaLocal.fijar(dueñoB)
        val repoB = FacturaRepository(context)
        assertTrue(repoB.obtenerFacturasDelDia(dia).isEmpty())

        // B registra lo suyo.
        assertTrue(repoB.insertarFactura(factura("B-1", "uid-b-1")))
        assertEquals(1, repoB.obtenerFacturasDelDia(dia).size)

        // A vuelve: solo su factura, nunca la de B.
        CuentaLocal.fijar(dueñoA)
        val repoADeVuelta = FacturaRepository(context)
        val facturasDeA = repoADeVuelta.obtenerFacturasDelDia(dia)
        assertEquals(1, facturasDeA.size)
        assertEquals("A-1", facturasDeA.first().numeroFactura)
    }

    @Test
    fun `los registros diarios también quedan separados por cuenta`() {
        CuentaLocal.fijar(dueñoA)
        val registrosA = RegistroRepository(context)
        registrosA.guardarEnRegistros(dia, 1, 5_000.0, 0.0, 5_000.0, 1, 0)
        assertEquals(1, registrosA.contarRegistros())

        CuentaLocal.fijar(dueñoB)
        val registrosB = RegistroRepository(context)
        assertEquals(0, registrosB.contarRegistros())

        registrosB.guardarEnRegistros(dia, 2, 1_000.0, 1_000.0, 2_000.0, 1, 1)
        assertEquals(1, registrosB.contarRegistros())

        CuentaLocal.fijar(dueñoA)
        assertEquals(1, RegistroRepository(context).contarRegistros())
    }

    @Test
    fun `sin cuenta confirmada no se muestra ningún dato local`() {
        // El caso de arranque antes de validar el perfil: la base "sin-cuenta"
        // es nueva y vacía, así que ninguna pantalla puede enseñar restos de
        // la sesión anterior.
        CuentaLocal.limpiar()

        assertTrue(FacturaRepository(context).obtenerFacturasDelDia(dia).isEmpty())
        assertEquals(0, RegistroRepository(context).contarRegistros())
    }

    @Test
    fun `el nombre de la base es distinto para cada dueño y seguro como archivo`() {
        assertEquals(
            CuentaLocal.nombreBase(dueñoA),
            CuentaLocal.nombreBase(dueñoA),
        )
        assertTrue(CuentaLocal.nombreBase(dueñoA) != CuentaLocal.nombreBase(dueñoB))
        assertTrue(CuentaLocal.nombreBase(dueñoA).endsWith(".db"))
        // Caracteres raros del id no rompen el nombre de archivo.
        assertTrue(!CuentaLocal.nombreBase("a/b:c").contains('/'))
    }
}
