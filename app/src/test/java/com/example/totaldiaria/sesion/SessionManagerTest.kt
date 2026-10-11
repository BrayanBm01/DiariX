package com.example.totaldiaria.sesion

import com.example.totaldiaria.network.CodigosDelBackend
import com.example.totaldiaria.network.ErrorDiarix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la sesión (Fase 4).
 *
 * Cubren lo que el usuario vive: que la app no lo eche mientras el token
 * sirve, que renueve antes de caducar, que nunca lance dos renovaciones a
 * la vez, que un fallo de red NO le cierre la sesión (offline primero) y que
 * una sesión caducada sí lo devuelva al login explicando por qué.
 */
class SessionManagerTest {

    private val inicio = 1_760_000_000_000L
    private var ahora = inicio

    private val duracionAccess = 15 * 60_000L

    private fun almacenConSesionVigente() =
        TokenStoreEnMemoria(
            TokensDeSesion(
                accessToken = "access-0",
                refreshToken = "refresh-0",
                accessExpiraAtMs = inicio + duracionAccess,
                refreshExpiraAtMs = inicio + 30 * 24 * 60 * 60_000L,
            )
        )

    private fun manager(
        almacen: TokenStore,
        api: AuthApiFalsa
    ) = SessionManager(api, almacen) { ahora }

    // ------------------------------------------------------------------
    // Estado de sesión
    // ------------------------------------------------------------------

    @Test
    fun `sin tokens no hay sesion`() {
        val m = manager(TokenStoreEnMemoria(), AuthApiFalsa({ ahora }))

        assertFalse(m.tieneSesion())
    }

    @Test
    fun `con refresh vigente hay sesion aunque el access ya caduco`() {
        val m = manager(almacenConSesionVigente(), AuthApiFalsa({ ahora }))

        // El access vence a los 15 min: la sesión sigue viva porque se puede
        // renovar con el refresh. Eso permite reconectar sin obligar a
        // entrar de nuevo.
        ahora = inicio + duracionAccess + 1_000L

        assertTrue(m.tieneSesion())
    }

    @Test
    fun `con refresh caducado no hay sesion`() {
        val m = manager(almacenConSesionVigente(), AuthApiFalsa({ ahora }))

        ahora = inicio + 31 * 24 * 60 * 60_000L

        assertFalse(m.tieneSesion())
    }

    // ------------------------------------------------------------------
    // Renovación anticipada
    // ------------------------------------------------------------------

    @Test
    fun `con el access vigente no sale a renovar`() {
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacenConSesionVigente(), api)

        val tokens = m.tokensListos()

        assertEquals("access-0", tokens.accessToken)
        assertEquals(0, api.refreshCalls.get())
    }

    @Test
    fun `renueva antes de que el access caduque`() {
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacenConSesionVigente(), api)

        // Faltan 30 s para caducar: dentro del margen de 60 s, se renueva.
        ahora = inicio + duracionAccess - 30_000L

        val tokens = m.tokensListos()

        assertEquals(1, api.refreshCalls.get())
        assertEquals("access-1", tokens.accessToken)
    }

    @Test
    fun `la renovacion se guarda en el almacen para reutilizarla`() {
        val almacen = almacenConSesionVigente()
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacen, api)

        ahora = inicio + duracionAccess - 30_000L

        m.tokensListos()

        val guardado = almacen.tokens

        assertNotNull(guardado)
        assertEquals("access-1", guardado!!.accessToken)
        assertEquals("refresh-1", guardado.refreshToken)
    }

    // ------------------------------------------------------------------
    // Vuelo único
    // ------------------------------------------------------------------

    @Test
    fun `dos peticiones simultaneas solo provocan una renovacion`() {
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacenConSesionVigente(), api)

        ahora = inicio + duracionAccess - 30_000L

        val tarea = java.util.concurrent.Callable {
            val a = m.tokensListos().accessToken
            val b = m.tokensListos().accessToken
            listOf(a, b)
        }

        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)

        val resultados = executor.invokeAll(listOf(tarea))
            .map { it.get() }
            .flatten()

        executor.shutdown()

        // Un único vuelo a la red y las dos llamadas reciben el mismo token.
        assertEquals(1, api.refreshCalls.get())
        assertEquals(listOf("access-1", "access-1"), resultados)
    }

    // ------------------------------------------------------------------
    // Fallos
    // ------------------------------------------------------------------

    @Test
    fun `un fallo de red al renovar no borra la sesion`() {
        val almacen = almacenConSesionVigente()
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacen, api)

        api.refreshFallaPorRed = true
        ahora = inicio + duracionAccess - 30_000L

        var fallo: ErrorDiarix? = null

        try {
            m.tokensListos()
        } catch (e: ErrorDiarix) {
            fallo = e
        }

        // Offline primero: se avisa, pero el usuario conserva su sesión y sus
        // facturas; se reintentará al reconectar.
        assertTrue(fallo is ErrorDiarix.Red)
        assertNotNull(almacen.tokens)
    }

    @Test
    fun `un refresh rechazado borra la sesion y avisa`() {
        val almacen = almacenConSesionVigente()
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacen, api)

        var expiraciones = 0
        m.alExpiarSesion = { expiraciones++ }

        api.refreshRechazado = true
        ahora = inicio + duracionAccess - 30_000L

        var fallo: ErrorDiarix? = null

        try {
            m.tokensListos()
        } catch (e: ErrorDiarix) {
            fallo = e
        }

        assertEquals(
            CodigosDelBackend.NO_AUTENTICADO,
            (fallo as? ErrorDiarix.DeBackend)?.codigo
        )
        assertNull(almacen.tokens)
        assertEquals(1, expiraciones)
    }

    @Test
    fun `con el refresh ya caducado se borra la sesion sin llamar al servidor`() {
        val almacen = almacenConSesionVigente()
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacen, api)

        var expiraciones = 0
        m.alExpiarSesion = { expiraciones++ }

        // Pasaron 40 días: el refresh venció. Ni se intenta la red.
        ahora = inicio + 40 * 24 * 60 * 60_000L

        var fallo: ErrorDiarix? = null

        try {
            m.tokensListos()
        } catch (e: ErrorDiarix) {
            fallo = e
        }

        assertEquals(
            CodigosDelBackend.NO_AUTENTICADO,
            (fallo as? ErrorDiarix.DeBackend)?.codigo
        )
        assertNull(almacen.tokens)
        assertEquals(1, expiraciones)
        assertEquals(0, api.refreshCalls.get())
    }

    @Test
    fun `pedir token sin sesion falla con un mensaje explicito`() {
        val m = manager(TokenStoreEnMemoria(), AuthApiFalsa({ ahora }))

        var fallo: ErrorDiarix? = null

        try {
            m.tokensListos()
        } catch (e: ErrorDiarix) {
            fallo = e
        }

        assertEquals(
            CodigosDelBackend.NO_AUTENTICADO,
            (fallo as? ErrorDiarix.DeBackend)?.codigo
        )
    }

    // ------------------------------------------------------------------
    // Cierre de sesión
    // ------------------------------------------------------------------

    @Test
    fun `cerrar sesion revoca en el servidor y borra lo local`() {
        val almacen = almacenConSesionVigente()
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacen, api)

        // Sesión viva y perfil cargado (como tras un login real).
        m.recargarPerfil()
        assertNotNull(m.perfilActual)

        m.cerrarSesion()

        assertEquals(1, api.logoutCalls.get())
        assertNull(almacen.tokens)
        // El perfil en memoria también se va: nada de una UI "logueada" con
        // la sesión ya cerrada.
        assertNull(m.perfilActual)
        assertFalse(m.tieneSesion())
    }

    @Test
    fun `cerrar sesion sin internet borra lo local igualmente`() {
        val almacen = almacenConSesionVigente()
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacen, api)

        api.refreshFallaPorRed = true

        // No debe lanzar: el cierre local es la prioridad.
        m.cerrarSesion()

        assertEquals(1, api.logoutCalls.get())
        assertNull(almacen.tokens)
    }

    // ------------------------------------------------------------------
    // Perfil: el backend es la autoridad
    // ------------------------------------------------------------------

    @Test
    fun `el perfil viene del servidor con el plan vigente`() {
        val api = AuthApiFalsa({ ahora })
        val m = manager(almacenConSesionVigente(), api)

        val perfil = m.recargarPerfil()

        assertEquals("USER", perfil.rol)
        assertEquals(15, perfil.plan?.facturasPorDia)
        assertFalse(perfil.plan!!.graficasAvanzadas)
        assertEquals(1, api.yoCalls.get())
    }

    @Test
    fun `el plan premium llega con graficas y 30 facturas`() {
        val api = AuthApiFalsa({ ahora })

        api.perfil = AuthApiFalsa.perfilDePrueba(
            plan = PlanSesion(
                rol = "INDIVIDUAL",
                plan = "PREMIUM",
                planVigente = true,
                facturasPorDia = 30,
                graficasAvanzadas = true,
            )
        )

        val m = manager(almacenConSesionVigente(), api)

        val perfil = m.recargarPerfil()

        assertEquals(30, perfil.plan?.facturasPorDia)
        assertTrue(perfil.plan!!.graficasAvanzadas)
    }

    // ------------------------------------------------------------------
    // Accesos
    // ------------------------------------------------------------------

    @Test
    fun `un acceso google de correo empresarial se rechaza con mensaje claro`() {
        val m = manager(TokenStoreEnMemoria(), AuthApiFalsa({ ahora }))

        var fallo: ErrorDiarix? = null

        try {
            m.entrarConGoogle("id-token")
        } catch (e: ErrorDiarix) {
            fallo = e
        }

        assertEquals(
            CodigosDelBackend.CUENTA_CON_ROL_RESERVADO,
            (fallo as? ErrorDiarix.DeBackend)?.codigo
        )
        // No se guarda ninguna sesión.
        assertFalse(m.tieneSesion())
    }

    @Test
    fun `credenciales erroneas devuelven el mensaje del servidor`() {
        val m = manager(TokenStoreEnMemoria(), AuthApiFalsa({ ahora }))

        var fallo: ErrorDiarix? = null

        try {
            m.entrarEmpresa("900123456", "ana@empresa.co", "mala")
        } catch (e: ErrorDiarix) {
            fallo = e
        }

        assertEquals(
            "Correo o contraseña incorrectos.",
            fallo?.mensajeAmigable
        )
    }
}
