package com.example.totaldiaria.sesion

import com.example.totaldiaria.core.cuenta.CuentaLocal
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
 *    servidor) y solo borra los tokens locales si el servidor confirma el
 *    cierre. Ante un fallo de red se mantiene la sesión y se avisa.
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
     * Avisa de que la identidad local cambió. Diarix reconstruye el
     * sincronizador para que apunte a la base de la cuenta nueva; sin esto,
     * una sesión anterior podría seguir subiendo filas con el token ajeno.
     */
    @Volatile
    var alCambiarCuenta: ((String) -> Unit)? = null

    /** Guarda la copia local de la foto de Google de la cuenta que entra. */
    @Volatile
    var alGuardarFotoGoogle: ((String) -> Unit)? = null

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

    /** Dueño persistido de los datos locales, o vacío si no hay sesión. */
    fun dueñoLocal(): String = almacen.leer()?.ownerId.orEmpty()

    /** URL de la foto de Google de la sesión guardada. */
    fun fotoGoogleDeSesion(): String? = almacen.leer()?.fotoUrl

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

    /** Login con Google: `nonce` es el anti-replay generado por esta pantalla. */
    fun entrarConGoogle(idToken: String, nonce: String? = null): PerfilActual =
        completarLogin(authApi.loginGoogle(idToken, nonce), conGoogle = true)

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

        // La identidad del dueño la da el SERVIDOR: primero `usuario.id`,
        // después el `sub` firmado del JWT y, solo como último recurso, el
        // correo que el propio servidor devolvió. Nunca se toma de Android.
        val owner = resuelto.usuarioId
            .ifBlank { JwtUtil.subDe(resuelto.tokens.accessToken) }
            ?.takeIf { it.isNotBlank() }
            ?: resuelto.email.takeIf { it.isNotBlank() }
            ?: ""

        almacen.guardar(
            resuelto.tokens.copy(
                conGoogle = conGoogle,
                ownerId = owner,
                fotoUrl = if (conGoogle) resuelto.fotoUrl else null,
            )
        )

        // Fijar la cuenta ANTES de que cualquier pantalla abra la base: así
        // ningún repositorio creado en este instante puede leer la BD ajena.
        CuentaLocal.fijar(owner)
        alCambiarCuenta?.invoke(CuentaLocal.id())

        val perfil = try {
            authApi.yo(resuelto.tokens.accessToken)
        } catch (_: ErrorDiarix) {
            // El login ya fue 200; si /auth/yo falla por red no se bloquea la
            // entrada: se usa lo que trajo la respuesta del login.
            perfilDesdeLogin(resuelto, owner)
        }

        // /auth/yo manda: si confirma otro dueño, se corrige antes de pintar.
        val confirmado = perfil.usuarioId.takeIf { it.isNotBlank() } ?: owner
        if (confirmado != owner) {
            almacen.leer()?.let { almacen.guardar(it.copy(ownerId = confirmado)) }
            CuentaLocal.fijar(confirmado)
            alCambiarCuenta?.invoke(CuentaLocal.id())
        }

        perfilActual = perfil

        if (conGoogle) {
            resuelto.fotoUrl?.let { alGuardarFotoGoogle?.invoke(it) }
        }

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

        // Si el servidor dice que esta sesión pertenece a otra cuenta (p. ej.
        // el token fue reemitido tras un cambio de cuenta), se corrige el
        // dueño local ANTES de seguir mostrando datos.
        val confirmado = perfil.usuarioId.takeIf { it.isNotBlank() }
        if (confirmado != null && confirmado != CuentaLocal.ownerId) {
            almacen.leer()?.let { almacen.guardar(it.copy(ownerId = confirmado)) }
            CuentaLocal.fijar(confirmado)
            alCambiarCuenta?.invoke(CuentaLocal.id())
        }

        perfilActual = perfil
        return perfil
    }

    /**
     * Cierra sesión: PRIMERO revoca en el servidor, después borra lo local.
     *
     * `POST /auth/logout` exige un access token válido (`@ApiBearerAuth`): el
     * refresh del cuerpo dice QUÉ sesión revocar y el access demuestra QUIÉN
     * la revoca. Se pide un access listo (renovándolo si estaba por vencer) y
     * se RELEE el almacén después: la renovación ROTA el refresh, y mandar el
     * token anterior dejaría viva la sesión real en el servidor.
     *
     * Si el servidor no responde (red, timeout, 5xx) se lanza [ErrorDiarix] y
     * NO se borra nada: la sesión local sigue abierta y la remota también, así
     * que la app puede reintentar. Solo se limpia lo local cuando el servidor
     * confirma el cierre o cuando responde que la sesión ya no existe
     * ([CodigosDelBackend.NO_AUTENTICADO]): ahí no queda nada vivo que revocar.
     */
    fun cerrarSesion() {
        val tokens = almacen.leer()

        if (tokens == null) {
            limpiarLocal()
            return
        }

        val access = try {
            tokensListos().accessToken
        } catch (e: ErrorDiarix) {
            if (e is ErrorDiarix.DeBackend && esExpiracionConfirmada(e)) {
                // El servidor ya no reconoce la sesión: no hay nada remoto que
                // revocar y conservar los tokens solo generaría un bucle.
                limpiarLocal()
                return
            }

            // Red/timeout/5xx: no se borra la sesión local.
            throw e
        }

        // `tokensListos()` pudo renovar y guardar tokens nuevos.
        val actuales = almacen.leer() ?: run {
            limpiarLocal()
            return
        }

        try {
            authApi.logout(
                accessToken = access,
                refreshToken = actuales.refreshToken,
            )
        } catch (e: ErrorDiarix) {
            if (e is ErrorDiarix.DeBackend && esExpiracionConfirmada(e)) {
                // Ya estaba cerrada en el servidor: el objetivo se cumplió.
            } else {
                // Red/timeout/5xx: se mantiene la sesión para reintentar.
                throw e
            }
        }

        limpiarLocal()
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
            val vigentes = renovados.tokens.copy(
                conGoogle = actuales.conGoogle,
                ownerId = actuales.ownerId,
                fotoUrl = actuales.fotoUrl,
            )
            almacen.guardar(vigentes)
            vigentes
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

    /**
     * Un refresh rechazado por el servidor con estos códigos significa que
     * la sesión ya no existe (360 a veces, 401 lo habitual), así que conservar
     * los tokens solo produce un bucle de errores: LoginActivity los vería
     * válidos (el refresh dura 30 días) y devolvería a la app rota una y otra
     * vez. Aquí se borran y se avisa para volver al login.
     *
     * Incluye SESION_CERRADA_POR_INACTIVIDAD: es otro cierre CONFIRMADO por el
     * servidor, no un fallo temporal. Un error de RED o un timeout NO entra
     * aquí (es `ErrorDiarix.Red`, no `DeBackend`), y por eso una caída del
     * backend o una mala conexión nunca cierra la sesión.
     */
    private fun esExpiracionConfirmada(e: ErrorDiarix): Boolean =
        e is ErrorDiarix.DeBackend &&
            (e.codigo == CodigosDelBackend.NO_AUTENTICADO ||
                e.codigo == CodigosDelBackend.CREDENCIALES_INVALIDAS ||
                e.codigo == CodigosDelBackend.SESION_CERRADA_POR_INACTIVIDAD)

    /** Borra local, limpia el perfil y avisa para volver al login. */
    private fun expulsarPorExpiracion() {
        limpiarLocal()
        alExpiarSesion?.invoke()
    }

    /**
     * Borra credenciales, perfil y el apuntador a la cuenta local. El
     * sincronizador se reconstruye vacío mediante [alCambiarCuenta]: ningún
     * hilo puede seguir usando la base de la cuenta anterior con un token
     * nuevo.
     */
    private fun limpiarLocal() {
        almacen.borrar()
        perfilActual = null
        CuentaLocal.limpiar()
        alCambiarCuenta?.invoke(CuentaLocal.id())
    }

    private fun perfilDesdeLogin(resuelto: LoginResuelto, owner: String): PerfilActual =
        PerfilActual(
            usuarioId = resuelto.usuarioId.ifBlank { owner }.ifBlank { resuelto.email },
            email = resuelto.email,
            nombre = resuelto.email,
            rol = resuelto.rol,
            emailVerificado = true,
            empresa = resuelto.empresa,
            conductorId = null,
            plan = null,
        )
}