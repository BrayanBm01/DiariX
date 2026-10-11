package com.example.totaldiaria.network

import com.example.totaldiaria.sesion.PerfilEditable
import org.json.JSONObject

/**
 * DIARIX · Fase 0.1 · Los datos del perfil propio (`/usuarios`).
 *
 * Existe una API aparte de [AuthApi] por una razón que conviene que quede
 * escrita: `GET /auth/yo` responde con lo MÍNIMO que la app necesita para
 * funcionar (rol, empresa, plan) y no trae el teléfono. La ficha de "Mi perfil"
 * necesita el teléfono y, sobre todo, necesita poder GUARDAR cambios. El
 * endpoint que hace las dos cosas es `GET/PATCH /usuarios/yo`, así que se
 * habla con ese y no con el de autenticación.
 *
 * ## Contrato (ver `usuarios.controller.ts` del backend)
 *
 *  - `GET /usuarios/yo` → plano, sin envolver: `{id, email, nombreCompleto,
 *    telefono, rol, estado, correoVerificado, ultimoAcceso, creadoEn, empresa,
 *    conductor, plan, capacidades}`.
 *  - `PATCH /usuarios/yo` → cuerpo `ActualizarPerfilDto` (`nombreCompleto`,
 *    `telefono`); responde `{id, email, fullName, phone}`, es decir, con los
 *    nombres de la COLUMNA y no con los del DTO.
 *
 * Esa asimetría no es un descuido del backend sino lo que devuelve Prisma con
 * su `select`, y por eso [actualizarMiPerfil] NO reconstruye el perfil desde su
 * respuesta: si lo hiciera, el nombre volvería vacío. Se actualiza sobre lo que
 * ya se sabe y, si hace falta, se vuelve a pedir.
 *
 * Es `open` por la misma razón que [AuthApi]: que las pruebas lo sustituyan por
 * un doble en memoria sin levantar un servidor.
 */
open class UsuariosApi(private val api: ApiCliente) {

    /** La ficha completa de quien está dentro. */
    open fun miPerfil(accessToken: String): PerfilEditable {

        val json = JSONObject(api.getJson(RUTA_YO, token = accessToken))

        return PerfilEditable(
            id = json.texto("id").orEmpty(),
            email = json.texto("email").orEmpty(),
            nombreCompleto = json.texto("nombreCompleto").orEmpty(),
            telefono = json.texto("telefono"),
            rol = json.texto("rol").orEmpty(),
            estado = json.texto("estado").orEmpty(),
            correoVerificado = json.optBoolean("correoVerificado", false),
            empresaNombre = json.objeto("empresa")?.texto("nombre"),
            plan = json.objeto("plan")?.texto("plan"),
        )
    }

    /**
     * Guarda los datos editables y devuelve la ficha TAL COMO quedó en el
     * servidor.
     *
     * Se manda `null` en lo que no cambia, y no un texto vacío: `null` en el
     * DTO es "no lo toques" y `""` es "bórralo", que son cosas distintas. Por
     * eso un teléfono que la persona borra a propósito SÍ se manda como `""`.
     *
     * El PATCH no acepta el correo, así que no se manda. No es que se quiera
     * dejar de fuera: es que mandarlo lo ignoraría el servidor.
     */
    open fun actualizarMiPerfil(
        accessToken: String,
        nombreCompleto: String?,
        telefono: String?,
    ): PerfilEditable {

        val cuerpo = JSONObject().apply {
            nombreCompleto?.let { put("nombreCompleto", it) }
            telefono?.let { put("telefono", it) }
        }

        api.patchJson(RUTA_YO, token = accessToken, cuerpo = cuerpo)

        // El PATCH devuelve los nombres de la columna (`fullName`, `phone`), no
        // los del DTO. Releer la ficha completa es lo barato y lo que no puede
        // desincronizarse: lo que se pinta es siempre lo que tiene el servidor,
        // nunca una mezcla de lo local y lo remoto.
        return miPerfil(accessToken)
    }

    /**
     * Cierra la sesión en TODOS los dispositivos (Fase 0.1).
     *
     * Ya existía en el backend (`POST /auth/logout-todos`) y devuelve cuántas
     * sesiones revocó. Se añade a la API porque "Sesión y seguridad" no puede
     * ser una sección con una sola opción: revocar el acceso desde un teléfono
     * que se perdió es la medida que la gente busca cuando busca.
     */
    open fun cerrarSesionEnTodosLosDispositivos(accessToken: String): Int =
        JSONObject(
            api.postJson(RUTA_LOGOUT_TODOS, token = accessToken, cuerpo = JSONObject())
        ).optInt("sesionesRevocadas", 0)

    // ------------------------------------------------------------------
    // Lectura de JSON que no confunde "vacío" con "nulo"
    // ------------------------------------------------------------------

    /**
     * Un texto del JSON, o `null` si no hay.
     *
     * Existe porque `JSONObject.optString` devuelve la CADENA `"null"` cuando
     * el valor del JSON es un nulo (`JSONObject.NULL`), no un nulo de Kotlin. Es
     * una trampa real de `org.json` y aquí importaría de verdad: si alguien
     * guardó su ficha y el backend responde `"telefono": null`, con
     * `optString` la app le pintaría el texto **null** en el campo del teléfono,
     * y ese texto acabaría en el PATCH de vuelta al servidor.
     *
     * Se resuelve mirando si el valor es `JSONObject.NULL` o está ausente, en
     * vez de comprobar que el texto no sea "null": comprobar el texto
     * convierte de paso el nombre "null" (que un usuario sí podría tener, en
     * teoría) en ausencia de dato.
     */
    private fun JSONObject.texto(clave: String): String? {

        if (!has(clave) || isNull(clave)) return null

        return opt(clave)?.toString()?.takeIf { it.isNotBlank() }
    }

    /** Un objeto anidado, o `null` si no hay o viene nulo. */
    private fun JSONObject.objeto(clave: String): JSONObject? =
        if (!has(clave) || isNull(clave)) null else optJSONObject(clave)

    private companion object {
        const val RUTA_YO = "/usuarios/yo"
        const val RUTA_LOGOUT_TODOS = "/auth/logout-todos"
    }
}