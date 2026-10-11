package com.example.totaldiaria.sesion

import com.example.totaldiaria.network.ApiCliente
import com.example.totaldiaria.network.AuthApi
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.network.UsuariosApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DIARIX · Fase 0.1 · El perfil dentro de la sesión.
 *
 * Aquí se comprueban tres cosas que una pantalla de perfil necesita y que son
 * fáciles de hacer mal sin querer:
 *
 *  1. **Que la marca de "cuenta de Google" sobreviva a renovar la sesión.**
 *     El refresh rota los dos tokens y no dice nada de cómo se entró. Sin
 *     acordarse, la etiqueta de Google desaparecería de Configuración a los
 *     cincuenta minutos de entrar, y el usuario pensaría que su cuenta se ha
 *     desligado.
 *  2. **Que la marca no sobreviva a cerrar sesión.** Es un dato de ESTA sesión,
 *     no de la cuenta: si se quedara, alguien que cerrara sesión y entrara con
 *     contraseña vería "Cuenta de Google" sin serlo.
 *  3. **Que guardar el nombre actualice el perfil de sesión**, para que el
 *     resto de la app no siga enseñando el nombre viejo.
 */
class PerfilEnSesionTest {

    // ----------------------------------------------------------------
    // La marca de Google
    // ----------------------------------------------------------------

    /**
     * Entrar con Google marca la cuenta como de Google.
     *
     * Es el único sitio del que se puede saber: ni `GET /usuarios/yo` ni el
     * token lo dicen. El botón de Google sabe con certeza que la cuenta es de
     * Google; suponerlo por el dominio del correo sería inventar.
     */
    @Test
    fun `entrar con Google deja marcada la cuenta`() {

        val manager = SessionManager(AuthApiConGoogle(), TokenStoreEnMemoria())

        manager.entrarConGoogle("id-token")

        assertTrue(manager.esCuentaDeGoogle())
    }

    /**
     * Entrar con NIT y contraseña NO marca la cuenta como de Google.
     *
     * Y cerrar sesión borra la marca, porque es un dato de ESTA sesión y no de
     * la cuenta: si se quedara, quien cerrara sesión y entrara después con
     * contraseña vería una etiqueta de Google que no le corresponde.
     */
    @Test
    fun `cerrar sesion borra la marca de Google`() {

        val manager = SessionManager(AuthApiConGoogle(), TokenStoreEnMemoria())

        manager.entrarConGoogle("id-token")

        assertTrue(manager.esCuentaDeGoogle())

        manager.cerrarSesion()

        assertFalse(
            "La marca de Google es de esta sesión: cerrarla debe borrarla",
            manager.esCuentaDeGoogle()
        )
    }

    /**
     * La marca sobrevive a renovar los tokens.
     *
     * El `access` dura 15 minutos, así que se renueva muchas veces en una
     * sesión normal. Si el refresh no se acordara de la marca, la etiqueta
     * desaparecería a mitad de tarde sin que nadie tocara nada.
     */
    @Test
    fun `la marca de Google sobrevive a renovar la sesion`() {

        val almacen = TokenStoreEnMemoria()

        val manager = SessionManager(AuthApiConGoogle(), almacen)

        manager.entrarConGoogle("id-token")

        assertTrue(manager.esCuentaDeGoogle())

        // Se fuerza el refresh con el reloj ya pasado: el access "caduca".
        manager.tokensListos()

        assertTrue(
            "Renovar la sesión no puede borrar que la cuenta es de Google",
            manager.esCuentaDeGoogle()
        )

        // Y otra vez, para comprobar que no es un caso que se arregla solo.
        manager.tokensListos()

        assertTrue(manager.esCuentaDeGoogle())
    }

    /**
     * Un token guardado sin la marca (una versión anterior de la app) se lee
     * como "no es de Google", no como un error.
     *
     * Perder la etiqueta es una molestia menor. Perder la SESIÓN entera porque
     * al blob le falta un campo que solo servía para una etiqueta sería un
     * desastre por un detalle.
     */
    @Test
    fun `un token sin la marca no rompe la sesion`() {

        val manager = SessionManager(
            AuthApiConGoogle(),
            TokenStoreEnMemoria(tokensVigentes())
        )

        assertTrue(
            "Un token sin la marca de Google debe seguir siendo una sesión válida",
            manager.tieneSesion()
        )

        assertFalse(manager.esCuentaDeGoogle())
    }

    @Test
    fun `sin sesion no hay cuenta de Google`() {

        assertFalse(SessionManager(AuthApiConGoogle(), TokenStoreEnMemoria()).esCuentaDeGoogle())
    }

    // ----------------------------------------------------------------
    // Editar el perfil
    // ----------------------------------------------------------------

    /**
     * Guardar el nombre actualiza también el perfil de sesión.
     *
     * El perfil de sesión es lo que pinta el resto de la app. Si al cambiar el
     * nombre en Configuración siguiera enseñando el viejo en Inicio, quien acaba
     * de corregir su nombre creería que el guardado no funcionó, y volvería a
     * corregirlo indefinidamente.
     */
    @Test
    fun `guardar el nombre actualiza el perfil de sesion`() {

        val manager = SessionManager(
            AuthApiConGoogle(),
            TokenStoreEnMemoria(),
            usuariosApi = UsuariosApiFalso(nombre = "Ana Vieja"),
        )

        // Se entra primero: editar el perfil exige sesión, y el perfil de
        // sesión es precisamente lo que hay que comprobar que queda al día.
        manager.entrarConGoogle("id-token")

        val antes = manager.miPerfil()
        assertEquals("Ana Vieja", antes.nombreCompleto)

        val despues = manager.actualizarMiPerfil(
            nombreCompleto = "Ana Torres",
            telefono = null,
        )

        assertEquals("Ana Torres", despues.nombreCompleto)

        assertEquals(
            "El perfil de sesión debe quedar al día con lo guardado",
            "Ana Torres",
            manager.perfilActual?.nombre
        )
    }

    /**
     * Editar el perfil necesita la API de usuarios conectada.
     *
     * Sin ella, el fallo tiene que ser un [ErrorDiarix] CLARO y no un
     * `NullPointerException`: quien llama muestra el mensaje, y un NPE aparecería
     * como un fallo de la app entera.
     */
    @Test
    fun `sin la API de usuarios el fallo es claro y no una excepcion nula`() {

        val manager = SessionManager(
            AuthApiConGoogle(),
            TokenStoreEnMemoria(tokensVigentes()),
        )

        listOf(
            { manager.miPerfil() },
            { manager.actualizarMiPerfil("Ana", null) },
            { manager.cerrarSesionEnTodosLosDispositivos() },
        ).forEach { accion ->

            val fallo = try {
                accion()
                null
            } catch (e: ErrorDiarix) {
                e
            }

            assertTrue(
                "Sin la API de usuarios debe fallar con ErrorDiarix, no con NPE",
                fallo is ErrorDiarix
            )
        }
    }

    // ----------------------------------------------------------------
    // Dobles
    // ----------------------------------------------------------------

    private fun tokensVigentes(
        ahora: Long = System.currentTimeMillis()
    ) = TokensDeSesion(
        accessToken = "access",
        refreshToken = "refresh",
        accessExpiraAtMs = ahora + 60 * 60_000L,
        refreshExpiraAtMs = ahora + 30 * 24 * 60 * 60_000L,
    )

    /** AuthApi que sabe entrar por Google y responde a `/auth/yo`. */
    private class AuthApiConGoogle : AuthApi(ApiCliente("http://localhost:0/api/v1")) {

        override fun loginGoogle(idToken: String): LoginResuelto =
            LoginResuelto(
                tokens = tokens(idToken),
                rol = "USER",
                email = "ana@ejemplo.co",
                empresa = null,
                cuentaNueva = true,
            )

        override fun loginEmpresarial(
            nitEmpresa: String?,
            email: String,
            password: String
        ): LoginResuelto =
            LoginResuelto(
                tokens = tokens("contrasena"),
                rol = "ACCOUNTANT",
                email = email,
                empresa = null,
                cuentaNueva = false,
            )

        override fun refrescar(refreshToken: String): LoginResuelto =
            LoginResuelto(
                // El backend devuelve tokens NUEVOS y no dice nada de cómo se
                // entró: por eso `SessionManager` tiene que acordarse.
                tokens = tokens("renovado-$refreshToken"),
                rol = "USER",
                email = "ana@ejemplo.co",
                empresa = null,
                cuentaNueva = false,
            )

        override fun yo(accessToken: String): PerfilActual =
            PerfilActual(
                usuarioId = "usr_1",
                email = "ana@ejemplo.co",
                nombre = "Ana Vieja",
                rol = "USER",
                emailVerificado = true,
                empresa = null,
                conductorId = null,
                plan = null,
            )

        override fun logout(accessToken: String?, refreshToken: String?) = Unit

        private fun tokens(sufijo: String) = TokensDeSesion(
            accessToken = "access-$sufijo",
            refreshToken = "refresh-$sufijo",
            // Access YA VENCIDO a propósito: obliga al refresh en el primer
            // `tokensListos()`, que es justo donde la marca se podría perder.
            accessExpiraAtMs = System.currentTimeMillis() - 1,
            refreshExpiraAtMs = System.currentTimeMillis() + 30 * 24 * 60 * 60_000L,
        )
    }

    /** UsuariosApi en memoria, sin red. */
    private class UsuariosApiFalso(
        private var nombre: String,
        private var telefono: String? = null,
    ) : UsuariosApi(ApiCliente("http://localhost:0/api/v1")) {

        override fun miPerfil(accessToken: String): PerfilEditable =
            PerfilEditable(
                id = "usr_1",
                email = "ana@ejemplo.co",
                nombreCompleto = nombre,
                telefono = telefono,
                rol = "USER",
                estado = "ACTIVE",
                correoVerificado = true,
                empresaNombre = null,
                plan = null,
            )

        override fun actualizarMiPerfil(
            accessToken: String,
            nombreCompleto: String?,
            telefono: String?,
        ): PerfilEditable {

            nombreCompleto?.let { nombre = it }
            this.telefono = telefono

            return miPerfil(accessToken)
        }

        override fun cerrarSesionEnTodosLosDispositivos(accessToken: String): Int = 2
    }
}