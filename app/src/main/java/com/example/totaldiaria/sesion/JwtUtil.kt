package com.example.totaldiaria.sesion

import java.util.Base64

/**
 * Lectura mínima del payload de un JWT (Fase 4).
 *
 * DiariX emite tokens JWT cuya caducidad viaja en el claim `exp` (segundos
 * desde epoch). Para saber cuándo hay que renovar el access token no hace
 * falta un parser de JSON: el payload es un objeto JSON plano y lo que nos
 * interesa de él (exp) se localiza sin dependencias, de forma pura y
 * comprobable en cualquier JVM.
 *
 * IMPORTANTE: aquí no se VALIDA el token (eso lo hace el backend). Solo se
 * lee la caducidad que el propio servidor firmó.
 */
object JwtUtil {

    /**
     * Caducidad en epoch ms del access y del refresh token, extraídas de sus
     * claims `exp`. Si un token no tuviera `exp`, se devuelve la caducidad
     * máxima (no es nuestro caso, pero la app no debe romperse por eso).
     */
    fun expiraAtMs(accessToken: String, refreshToken: String): Pair<Long, Long> =
        expDe(accessToken) to expDe(refreshToken)

    private fun expDe(token: String): Long {
        val partes = token.split(".")
        if (partes.size < 2) return Long.MAX_VALUE

        val payload = try {
            String(Base64.getUrlDecoder().decode(partes[1]))
        } catch (_: IllegalArgumentException) {
            return Long.MAX_VALUE
        }

        // Campo "exp": numero entero (segundos desde epoch).
        val match = Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)
            ?: return Long.MAX_VALUE

        return match.groupValues[1].toLongOrNull()?.times(1_000L)
            ?: Long.MAX_VALUE
    }
}