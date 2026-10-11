package com.example.totaldiaria.sesion

import org.json.JSONObject

/**
 * Guarda y recupera el par de tokens de la sesión.
 *
 * La implementación de producción serializa a JSON y lo entrega al
 * [AlmacenSeguro] (Keystore) para que NUNCA quede en claro en disco. Las
 * pruebas usan una implementación en memoria.
 */
interface TokenStore {
    fun leer(): TokensDeSesion?
    fun guardar(tokens: TokensDeSesion)
    fun borrar()
}

class TokenStoreSeguro(
    private val almacen: AlmacenSeguro,
) : TokenStore {

    override fun leer(): TokensDeSesion? {
        val blob = almacen.leer() ?: return null

        return try {
            val json = JSONObject(String(blob, Charsets.UTF_8))
            TokensDeSesion(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                accessExpiraAtMs = json.getLong("accessExpiraAtMs"),
                refreshExpiraAtMs = json.getLong("refreshExpiraAtMs"),
                // `opt` y no `get`: una sesión guardada por una versión
                // anterior de la app no tiene esta clave, y perder la sesión
                // entera por un campo que solo sirve para una etiqueta sería
                // un desastre por un detalle.
                conGoogle = json.optBoolean("conGoogle", false),
            )
        } catch (_: Exception) {
            // Archivo ilegible: mejor no tener sesión que una rota.
            null
        }
    }

    override fun guardar(tokens: TokensDeSesion) {
        val json = JSONObject().apply {
            put("accessToken", tokens.accessToken)
            put("refreshToken", tokens.refreshToken)
            put("accessExpiraAtMs", tokens.accessExpiraAtMs)
            put("refreshExpiraAtMs", tokens.refreshExpiraAtMs)
            put("conGoogle", tokens.conGoogle)
        }
        almacen.guardar(json.toString().toByteArray(Charsets.UTF_8))
    }

    override fun borrar() {
        almacen.borrar()
    }
}