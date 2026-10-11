package com.example.totaldiaria.network

import com.example.totaldiaria.sesion.EmpresaSesion
import com.example.totaldiaria.sesion.JwtUtil
import com.example.totaldiaria.sesion.LoginResuelto
import com.example.totaldiaria.sesion.PerfilActual
import com.example.totaldiaria.sesion.PlanSesion
import com.example.totaldiaria.sesion.TokensDeSesion
import org.json.JSONObject

/**
 * Endpoints de autenticación de DiariX (Fase 4).
 *
 * Contrato (ver `auth.controller.ts`): login/google/refresh responden 200
 * con `{tokens, usuario, empresa, cuentaNueva}`; `GET /auth/yo` responde
 * `{usuario, empresa, conductorId, plan}`.
 *
 * Es `open` para que las pruebas puedan sustituirla por un doble en memoria
 * (no hace falta levantar un servidor para probar la lógica de sesión).
 */
open class AuthApi(private val api: ApiCliente) {

    open fun loginEmpresarial(
        nitEmpresa: String?,
        email: String,
        password: String,
    ): LoginResuelto {
        val cuerpo = JSONObject().apply {
            put("email", email)
            put("password", password)
            nitEmpresa?.let { put("nitEmpresa", it) }
        }

        val respuesta = api.postJson(RUTA_LOGIN, cuerpo = cuerpo)

        return LoginResuelto(
            tokens = tokensDe(respuesta),
            rol = rolDe(respuesta),
            email = usuarioDe(respuesta).optString("email", email),
            empresa = empresaDe(JSONObject(respuesta)),
            cuentaNueva = optBoolean(respuesta, "cuentaNueva"),
        )
    }

    open fun loginGoogle(idToken: String): LoginResuelto {
        val cuerpo = JSONObject().apply { put("idToken", idToken) }
        return loginResueltoDe(api.postJson(RUTA_GOOGLE, cuerpo = cuerpo))
    }

    /** Renueva el par con el refresh token (el servidor rota ambos). */
    open fun refrescar(refreshToken: String): LoginResuelto {
        val cuerpo = JSONObject().apply { put("refreshToken", refreshToken) }
        return loginResueltoDe(api.postJson(RUTA_REFRESH, cuerpo = cuerpo))
    }

    /** Revoca la sesión en el servidor. Si no va, el cliente igualmente
     *  borra sus tokens (ver SessionManager). */
    open fun logout(accessToken: String?, refreshToken: String?) {
        val cuerpo = JSONObject().apply {
            refreshToken?.let { put("refreshToken", it) }
        }
        api.postJson(RUTA_LOGOUT, token = accessToken, cuerpo = cuerpo)
    }

    /** El backend es la autoridad sobre identidad, rol, empresa y plan. */
    open fun yo(accessToken: String): PerfilActual {
        val respuesta = api.getJson(RUTA_YO, token = accessToken)
        val json = JSONObject(respuesta)

        val usuario = json.optJSONObject("usuario") ?: JSONObject()

        val plan = json.optJSONObject("plan")?.let {
            PlanSesion(
                rol = it.optString("rol"),
                plan = it.optString("plan"),
                planVigente = it.optBoolean("planVigente", false),
                facturasPorDia = it.optInt("facturasPorDia", -1).takeIf { v -> v >= 0 },
                graficasAvanzadas = it.optBoolean("graficasAvanzadas", false),
            )
        }

        return PerfilActual(
            usuarioId = usuario.optString("id"),
            email = usuario.optString("email"),
            nombre = usuario.optString("nombre"),
            rol = usuario.optString("rol"),
            emailVerificado = usuario.optBoolean("emailVerificado", false),
            empresa = empresaDe(json),
            conductorId = json.optString("conductorId").takeIf { it.isNotBlank() },
            plan = plan,
        )
    }

    // =========================================================================
    // Interno
    // =========================================================================

    private fun loginResueltoDe(respuesta: String): LoginResuelto {
        val json = JSONObject(respuesta)
        return LoginResuelto(
            tokens = tokensDe(respuesta),
            rol = rolDe(respuesta),
            email = usuarioDe(respuesta).optString("email"),
            empresa = empresaDe(json),
            cuentaNueva = optBoolean(respuesta, "cuentaNueva"),
        )
    }

    private fun tokensDe(respuesta: String): TokensDeSesion {
        val tokens = JSONObject(respuesta).getJSONObject("tokens")
        val access = tokens.getString("accessToken")
        val refresh = tokens.getString("refreshToken")

        val (accessExp, refreshExp) = JwtUtil.expiraAtMs(access, refresh)

        return TokensDeSesion(
            accessToken = access,
            refreshToken = refresh,
            accessExpiraAtMs = accessExp,
            refreshExpiraAtMs = refreshExp,
        )
    }

    private fun rolDe(respuesta: String): String =
        usuarioDe(respuesta).optString("rol")

    private fun usuarioDe(respuesta: String): JSONObject =
        JSONObject(respuesta).optJSONObject("usuario") ?: JSONObject()

    private fun empresaDe(json: JSONObject): EmpresaSesion? {
        val empresa = json.optJSONObject("empresa") ?: return null

        return EmpresaSesion(
            id = empresa.optString("id"),
            nit = empresa.optString("nit"),
            nombre = empresa.optString("nombre"),
            plan = empresa.optString("plan").takeIf { it.isNotBlank() },
            cupoConductores = empresa.optInt("cupoConductores", -1)
                .takeIf { it >= 0 },
        )
    }

    private fun optBoolean(json: String, clave: String): Boolean =
        JSONObject(json).optBoolean(clave, false)

    private companion object {
        const val RUTA_LOGIN = "/auth/login"
        const val RUTA_GOOGLE = "/auth/google"
        const val RUTA_REFRESH = "/auth/refresh"
        const val RUTA_LOGOUT = "/auth/logout"
        const val RUTA_YO = "/auth/yo"
    }
}