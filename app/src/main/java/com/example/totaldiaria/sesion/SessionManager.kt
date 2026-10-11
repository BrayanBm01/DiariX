package com.example.totaldiaria.sesion

import com.example.totaldiaria.network.AuthApi
import com.example.totaldiaria.network.CodigosDelBackend
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.network.UsuariosApi

/**
 * Orquesta sesión, refresh y perfil (Fase 4).
 *
 * Reglas que cumple:
 *  - El refresh se dispara ANTES de que el access expire (margen de 60 s) y
 *    es de vuelo único (`single-flight`): si dos hilos necesitan a la vez un
 *    token fresco, solo uno sale a la red y el otro espera.
 *  - Si el refresh FALLA por "sesión inválida/caducada", se borra la sesión
 *    local y se notifica para volver al login explicando la expiración.
 *  - Si falla por RED, NO se borra nada: la app sigue funcionando offline y
 *    reintenta cuando vuelva la conexión.
 *  - `cerrarSesion()` llama a `/auth/logout` (revoca el refresh en el
 *    servidor) y, pase lo que pase con la red, borra los tokens locales.
 *  - El backend es la autoridad: `recargarPerfil()` vuelve a leer
 *    `/auth/yo` para rol, empresa, conductor y plan.
 *
 * Todas las llamadas son SÍNCRONAS y pensadas para ejecutarse desde un hilo
 * de fondo; la UI hace su propio hilo + `runOnUiThread`.
 */
class SessionManager(
    private val authApi: AuthApi,
    private val almacen: TokenStore,
    private val usuariosApi: UsuariosApi? = null,
    private val reloj: () -> Long = { System.currentTimeMillis() },
) {

    /** Perfil de /auth/yo en memoria (para pintar la UI sin repetir llamadas). */
    @Volatile
    var perfilActual: PerfilActual? = null
        private set

    /** Se invoca (en el hilo que detecta el problema) cuando la sesión debe
     *  cerrarse por expiración confirmada por el servidor. */
    @Volatile
    var alExpiarSesion: (() -> Unit)? = null

    /**
     * DIARIX · Fase 0.1 · ¿Esta cuenta se abrió con Google?
     *
     * Sale de [TokensDeSesion.conGoogle], que es donde se guarda al iniciar
     * sesión, y por eso sobrevive a que el sistema mate el proceso. Se usa para
     * poner la etiqueta "Cuenta de Google" en Configuración.
     *
     * Se lee del almacén y no de una variable en memoria a propósito: si
     * viviera solo en memoria, la etiqueta desaparecería tras un reinicio y el
     * usuario pensaría que su cuenta se había desligado de Google.
     */
    fun esCuentaDeGoogle(): Boolean =
        almacen.leer()?.conGoogle ?: false

    /** Anticipación del refresh: se renueva cuando quedan menos de 60 s. */
    private val margenRefreshMs = 60_000L

    /** ¿Hay una sesión que PUEDA renovarse? (el access puede estar vencido.) */
    fun tieneSesion(): Boolean {
        val tokens = almacen.leer() ?: return false
        return tokens.refreshExpiraAtMs > reloj()
    }

    fun entrarEmpresa(
        nitEmpresa: String?,
        email: String,
        password: String,
    ): PerfilActual = completarLogin(
        authApi.loginEmpresarial(nitEmpresa, email, password),
        conGoogle = false,
    )

    fun entrarConGoogle(idToken: String): PerfilActual =
        completarLogin(authApi.loginGoogle(idToken), conGoogle = true)

    /**
     * Guarda los tokens y levanta el perfil desde /auth/yo.
     *
     * `conGoogle` lo fija el MÉTODO con el que se entró, no la respuesta del
     * servidor, porque el servidor no lo dice en ningún sitio (ver
     * [TokensDeSesion.conGoogle]).
     */
    private fun completarLogin(
        resuelto: LoginResuelto,
        conGoogle: Boolean,
    ): PerfilActual {

        almacen.guardar(
            resuelto.tokens.copy(conGoogle = conGoogle)
        )

        val perfil = try {
            authApi.yo(resuelto.tokens.accessToken)
        } catch (_: ErrorDiarix) {
            // El login ya fue 200; si /auth/yo falla por red no se bloquea la
            // entrada: se usa lo que trajo la respuesta del login.
            perfilDesdeLogin(resuelto)
        }

        perfilActual = perfil
        return perfil
    }

    /**
     * Access token listo para usar, renovándolo si faltan menos de 60 s o ya
     * venció. Es el único punto por el que pasa una petición autenticada.
     */
    fun tokensListos(): TokensDeSesion {
        val actuales = almacen.leer()
            ?: throw ErrorDiarix.DeBackend(
                CodigosDelBackend.NO_AUTENTICADO,
                "Tu sesión no está activa. Inicia sesión.",
            )

        if (!accesoPorVencer(actuales)) return actuales

        return renovarEnVueloUnico(actuales)
    }

    /** Recarga el perfil desde /auth/yo (backend = autoridad). */
    fun recargarPerfil(): PerfilActual {
        val tokens = tokensListos()

        val perfil = try {
            authApi.yo(tokens.accessToken)
        } catch (e: ErrorDiarix) {
            if (esExpiracionConfirmada(e)) {
                expulsarPorExpiracion()
            }
            throw e
        }

        perfilActual = perfil
        return perfil
    }

    /** Cierra sesión: revoca en el servidor y borra lo local SIEMPRE. */
    fun cerrarSesion() {
        val tokens = almacen.leer()

        if (tokens != null) {
            try {
                // El endpoint solo necesita el refresh para revocar.
                authApi.logout(accessToken = null, refreshToken = tokens.refreshToken)
            } catch (_: ErrorDiarix) {
                // Sin internet: se borra local de todos modos. La sesión del
                // servidor caduca sola con el refresh de 30 días.
            }
        }

        almacen.borrar()
        perfilActual = null
    }

    // =========================================================================
    // Fase 0.1 · Perfil y seguridad
    // =========================================================================

    /**
     * La ficha de "Mi perfil" tal como la tiene el servidor.
     *
     * `GET /usuarios/yo` y no `GET /auth/yo`: el de autenticación no trae el
     * teléfono, que es justo uno de los dos campos editables.
     *
     * @throws ErrorDiarix si no hay [usuariosApi] (todavía no wired en una app
     *   antigua) o si la red falla. Quien llama debe poder trabajar sin red.
     */
    fun miPerfil(): PerfilEditable {
        val api = usuariosApi
            ?: throw ErrorDiarix.Inesperado("El perfil detallado no está disponible.")

        return api.miPerfil(tokensListos().accessToken)
    }

    /**
     * Guarda los datos editables del perfil.
     *
     * `null` = "este campo no se toca"; `""` = "vacíalo". La diferencia no es
     * un detalle: es lo que permite que Configuración envíe solo lo que la
     * persona cambió de verdad.
     */
    fun actualizarMiPerfil(
        nombreCompleto: String?,
        telefono: String?,
    ): PerfilEditable {
        val api = usuariosApi
            ?: throw ErrorDiarix.Inesperado("El perfil detallado no está disponible.")

        val actualizado = api.actualizarMiPerfil(
            accessToken = tokensListos().accessToken,
            nombreCompleto = nombreCompleto,
            telefono = telefono,
        )

        // El perfil de sesión se queda también al día, para que "Tu cuenta" en
        // el resto de la app no siga enseñando el nombre viejo después de
        // haberlo cambiado aquí.
        perfilActual = perfilActual?.copy(nombre = actualizado.nombreCompleto)

        return actualizado
    }

    /**
     * Cierra la sesión en TODOS los dispositivos; devuelve cuántas revocó.
     *
     * No borra la sesión local: el usuario sigue dentro de este dispositivo
     * hasta que cierre sesión o caduque el token. Es lo que el endpoint hace.
     */
    fun cerrarSesionEnTodosLosDispositivos(): Int {
        val api = usuariosApi
            ?: throw ErrorDiarix.Inesperado("La sesión remota no está disponible.")

        return api.cerrarSesionEnTodosLosDispositivos(tokensListos().accessToken)
    }

    // =========================================================================
    // Interno
    // =========================================================================

    private fun accesoPorVencer(tokens: TokensDeSesion): Boolean =
        tokens.accessExpiraAtMs - margenRefreshMs <= reloj()

    /**
     * Renovación de vuelo único. `@Synchronized` hace que un solo hilo entre
     * a la red; los demás esperan y reaprovechan el resultado.
     */
    @Synchronized
    private fun renovarEnVueloUnico(previos: TokensDeSesion): TokensDeSesion {
        // Mientras esperábamos el candado pudo renovar otro hilo: se relee.
        val actuales = almacen.leer()
            ?: throw ErrorDiarix.DeBackend(
                CodigosDelBackend.NO_AUTENTICADO,
                "Tu sesión no está activa. Inicia sesión.",
            )

        if (!accesoPorVencer(actuales)) return actuales

        // El refresh ya caducó: la sesión está muerta y hay que re-loguear.
        if (actuales.refreshExpiraAtMs <= reloj()) {
            expulsarPorExpiracion()
            throw ErrorDiarix.DeBackend(
                CodigosDelBackend.NO_AUTENTICADO,
                "Tu sesión expiró. Inicia sesión de nuevo.",
            )
        }

        return try {
            val renovados = authApi.refrescar(actuales.refreshToken)
            // El refresh rota los dos tokens y no dice nada de cómo se entró:
            // sin este `copy`, renovar la sesión borraría la marca de Google y
            // la etiqueta de Configuración desaparecería a los 50 minutos.
            almacen.guardar(
                renovados.tokens.copy(conGoogle = actuales.conGoogle)
            )
            renovados.tokens
        } catch (e: ErrorDiarix) {
            if (esExpiracionConfirmada(e)) {
                // El servidor dice que el refresh ya no es válido: sesión
                // terminada. Se borra y se avisa para volver al login.
                expulsarPorExpiracion()
            }
            // Una Red / Inesperado NO borra: offline o servidor caído, se
            // reintentará con los mismos tokens.
            throw e
        }
    }

    /** Un refresh rechazado por el servidor con estos códigos significa que
     *  la sesión ya no existe (360 a veces, 401 lo habitual). */
    private fun esExpiracionConfirmada(e: ErrorDiarix): Boolean =
        e is ErrorDiarix.DeBackend &&
            (e.codigo == CodigosDelBackend.NO_AUTENTICADO ||
                e.codigo == CodigosDelBackend.CREDENCIALES_INVALIDAS)

    /** Borra local, limpia el perfil y avisa para volver al login. */
    private fun expulsarPorExpiracion() {
        almacen.borrar()
        perfilActual = null
        alExpiarSesion?.invoke()
    }

    private fun perfilDesdeLogin(resuelto: LoginResuelto): PerfilActual =
        PerfilActual(
            usuarioId = resuelto.email,
            email = resuelto.email,
            nombre = resuelto.email,
            rol = resuelto.rol,
            emailVerificado = true,
            empresa = resuelto.empresa,
            conductorId = null,
            plan = null,
        )
}