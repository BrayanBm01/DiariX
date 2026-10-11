package com.example.totaldiaria.sesion

import com.example.totaldiaria.network.ApiCliente
import com.example.totaldiaria.network.AuthApi
import com.example.totaldiaria.network.ErrorDiarix
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dobles de prueba para [SessionManager] (Fase 4).
 *
 * Permiten verificar las reglas de sesión —renovación anticipada, vuelo
 * único, caducidad, cierre— sin red ni Android.
 */

/** TokenStore en memoria: equivale a lo que hace el Keystore, sin disco. */
class TokenStoreEnMemoria(
    inicial: TokensDeSesion? = null
) : TokenStore {

    var tokens: TokensDeSesion? = inicial
        private set

    var lecturas: Int = 0
        private set

    override fun leer(): TokensDeSesion? {
        lecturas++
        return tokens
    }

    override fun guardar(tokens: TokensDeSesion) {
        this.tokens = tokens
    }

    override fun borrar() {
        tokens = null
    }
}

/**
 * AuthApi falsa: responde a refresh con tokens calculados sobre un reloj
 * inyectable y permite forzar fallos (servidor caído, sesión revocada).
 */
class AuthApiFalsa(
    private val ahoraMs: () -> Long,
    private val duracionAccessMs: Long = 15 * 60_000L,
    private val duracionRefreshMs: Long = 30 * 24 * 60 * 60_000L,
) : AuthApi(ApiCliente("http://localhost:0/api/v1")) {

    val refreshCalls = AtomicInteger(0)
    val logoutCalls = AtomicInteger(0)
    val yoCalls = AtomicInteger(0)

    /** Si está a true, /auth/refresh responde como sesión inválida. */
    var refreshRechazado = false

    /** Si está a true, /auth/refresh falla por red. */
    var refreshFallaPorRed = false

    /** Perfil devuelto por /auth/yo. */
    var perfil: PerfilActual = perfilDePrueba()

    override fun refrescar(refreshToken: String): LoginResuelto {
        refreshCalls.incrementAndGet()

        if (refreshFallaPorRed) throw ErrorDiarix.Red("sin red")

        if (refreshRechazado) {
            throw ErrorDiarix.DeBackend(
                codigo = com.example.totaldiaria.network.CodigosDelBackend.NO_AUTENTICADO,
                mensaje = "El refresh token no es válido."
            )
        }

        return LoginResuelto(
            tokens = tokensNuevos(),
            rol = "USER",
            email = "usuario@prueba.co",
            empresa = null,
            cuentaNueva = false,
        )
    }

    override fun loginEmpresarial(
        nitEmpresa: String?,
        email: String,
        password: String
    ): LoginResuelto =
        throw ErrorDiarix.DeBackend(
            codigo = com.example.totaldiaria.network.CodigosDelBackend.CREDENCIALES_INVALIDAS,
            mensaje = "Correo o contraseña incorrectos."
        )

    override fun loginGoogle(idToken: String): LoginResuelto =
        throw ErrorDiarix.DeBackend(
            codigo = com.example.totaldiaria.network.CodigosDelBackend.CUENTA_CON_ROL_RESERVADO,
            mensaje = "Este correo pertenece a una cuenta empresarial o de administración."
        )

    override fun logout(accessToken: String?, refreshToken: String?) {
        logoutCalls.incrementAndGet()

        if (refreshFallaPorRed) throw ErrorDiarix.Red("sin red")
    }

    override fun yo(accessToken: String): PerfilActual {
        yoCalls.incrementAndGet()
        return perfil
    }

    private fun tokensNuevos(): TokensDeSesion {
        val ahora = ahoraMs()
        return TokensDeSesion(
            accessToken = "access-${refreshCalls.get()}",
            refreshToken = "refresh-${refreshCalls.get()}",
            accessExpiraAtMs = ahora + duracionAccessMs,
            refreshExpiraAtMs = ahora + duracionRefreshMs,
        )
    }

    companion object {
        fun perfilDePrueba(
            plan: PlanSesion = PlanSesion(
                rol = "INDIVIDUAL",
                plan = "FREE",
                planVigente = true,
                facturasPorDia = 15,
                graficasAvanzadas = false,
            )
        ): PerfilActual =
            PerfilActual(
                usuarioId = "usr_1",
                email = "usuario@prueba.co",
                nombre = "Usuario de Prueba",
                rol = "USER",
                emailVerificado = true,
                empresa = null,
                conductorId = null,
                plan = plan,
            )
    }
}
