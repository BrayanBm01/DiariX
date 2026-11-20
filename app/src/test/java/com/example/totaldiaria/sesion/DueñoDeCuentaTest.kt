package com.example.totaldiaria.sesion

import com.example.totaldiaria.core.cuenta.CuentaLocal
import com.example.totaldiaria.network.AuthApi
import com.example.totaldiaria.network.ErrorDiarix
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * El dueño de los datos locales NO lo decide Android: lo da el servidor.
 *
 * Estas pruebas fijan el contrato de [SessionManager] con el aislamiento por
 * cuenta: el login fija el dueño desde `usuario.id`, `/auth/yo` lo puede
 * corregir, cerrar sesión lo limpia, y cualquier cambio avisa por callback
 * para que la cola de sincronización se reconstruya.
 */
class DueñoDeCuentaTest {

    private var ahora = 1_000_000L

    private lateinit var api: AuthApiDeLoginFalsa
    private lateinit var almacen: TokenStoreEnMemoria
    private lateinit var sesion: SessionManager

    private val cambios = mutableListOf<String>()

    @Before
    fun setUp() {
        CuentaLocal.limpiar()
        cambios.clear()

        api = AuthApiDeLoginFalsa()
        almacen = TokenStoreEnMemoria()
        sesion = SessionManager(api, almacen, reloj = { ahora })

        sesion.alCambiarCuenta = { cambios.add(it) }
    }

    @After
    fun tearDown() {
        CuentaLocal.limpiar()
    }

    private fun respuestaLogin(
        usuarioId: String,
        fotoUrl: String? = "https://ejemplo.invalid/foto.png",
    ) = LoginResuelto(
        tokens = TokensDeSesion(
            accessToken = "access",
            refreshToken = "refresh",
            accessExpiraAtMs = ahora + 15 * 60_000L,
            refreshExpiraAtMs = ahora + 30L * 24 * 60 * 60_000L,
            conGoogle = true,
        ),
        rol = "USER",
        email = "persona@correo.com",
        empresa = null,
        cuentaNueva = true,
        usuarioId = usuarioId,
        fotoUrl = fotoUrl,
    )

    @Test
    fun `el login fija el dueño del servidor y avisa para reconstruir la cola`() {
        api.respuesta = respuestaLogin("user-a")
        api.perfil = PerfilActual(
            usuarioId = "user-a",
            email = "persona@correo.com",
            nombre = "Persona",
            rol = "USER",
            emailVerificado = true,
            empresa = null,
            conductorId = null,
            plan = null,
        )

        sesion.entrarConGoogle("id-token", "nonce")

        assertEquals("user-a", CuentaLocal.ownerId)
        assertEquals(listOf("user-a"), cambios)
        assertEquals("user-a", almacen.tokens?.ownerId)
        assertEquals("https://ejemplo.invalid/foto.png", almacen.tokens?.fotoUrl)
    }

    @Test
    fun `si el perfil confirma otro dueño, el local se corrige antes de pintar`() {
        api.respuesta = respuestaLogin("user-a")
        api.perfil = PerfilActual(
            usuarioId = "user-b",
            email = "persona@correo.com",
            nombre = "Persona",
            rol = "USER",
            emailVerificado = true,
            empresa = null,
            conductorId = null,
            plan = null,
        )

        sesion.entrarConGoogle("id-token")

        assertEquals("user-b", CuentaLocal.ownerId)
        assertEquals("user-b", almacen.tokens?.ownerId)
        assertEquals(listOf("user-a", "user-b"), cambios)
    }

    @Test
    fun `cerrar sesión limpia el dueño local`() {
        api.respuesta = respuestaLogin("user-a")
        api.perfil = AuthApiFalsa.perfilDePrueba()

        sesion.entrarConGoogle("id-token")
        CuentaLocal.fijar("user-a")
        cambios.clear()

        sesion.cerrarSesion()

        assertNull(CuentaLocal.ownerId)
        assertNull(almacen.tokens)
        assertEquals(listOf(CuentaLocal.id()), cambios)
    }

    @Test
    fun `la renovación conserva dueño y foto`() {
        api.respuesta = respuestaLogin("user-a")
        api.perfil = PerfilActual(
            usuarioId = "user-a",
            email = "persona@correo.com",
            nombre = "Persona",
            rol = "USER",
            emailVerificado = true,
            empresa = null,
            conductorId = null,
            plan = null,
        )

        sesion.entrarConGoogle("id-token")

        // El access queda por vencer: obliga a renovar en la próxima llamada.
        almacen.guardar(
            almacen.tokens!!.copy(accessExpiraAtMs = ahora - 1)
        )
        ahora += 120_000L

        val tokens = sesion.tokensListos()

        assertEquals("access-1", tokens.accessToken)
        assertEquals("user-a", tokens.ownerId)
        assertEquals("user-a", almacen.tokens?.ownerId)
        assertEquals("https://ejemplo.invalid/foto.png", almacen.tokens?.fotoUrl)
    }

    /** AuthApi falsa que SÍ responde el login de Google con datos preparados. */
    private class AuthApiDeLoginFalsa : AuthApi(
        com.example.totaldiaria.network.ApiCliente("http://localhost:0/api/v1")
    ) {

        var respuesta: LoginResuelto? = null

        var perfil: PerfilActual = AuthApiFalsa.perfilDePrueba()

        val logouts = mutableListOf<Pair<String?, String?>>()

        override fun loginGoogle(idToken: String, nonce: String?): LoginResuelto =
            respuesta ?: throw ErrorDiarix.DeBackend(
                codigo = com.example.totaldiaria.network.CodigosDelBackend.CUENTA_CON_ROL_RESERVADO,
                mensaje = "Este correo pertenece a una cuenta empresarial o de administración.",
            )

        override fun yo(accessToken: String): PerfilActual = perfil

        override fun refrescar(refreshToken: String): LoginResuelto {
            renovaciones += 1

            return LoginResuelto(
                tokens = TokensDeSesion(
                    accessToken = "access-$renovaciones",
                    refreshToken = "refresh-$renovaciones",
                    accessExpiraAtMs = System.currentTimeMillis() + 15 * 60_000L,
                    refreshExpiraAtMs = System.currentTimeMillis() + 30L * 24 * 60 * 60_000L,
                ),
                rol = "USER",
                email = "persona@correo.com",
                empresa = null,
                cuentaNueva = false,
            )
        }

        override fun logout(accessToken: String?, refreshToken: String?) {
            logouts += accessToken to refreshToken
        }

        var renovaciones: Int = 0
    }
}
