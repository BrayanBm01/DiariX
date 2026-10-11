package com.example.totaldiaria.sesion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/**
 * Pruebas de la lectura de caducidad del JWT (Fase 4).
 *
 * La app necesita saber cuándo renovar el access token ANTES de que expire.
 * Estos tokens son de prueba: solo importan la forma del payload (claim
 * `exp` en segundos), que es exactamente lo que el backend firma.
 */
class JwtUtilTest {

    private fun tokenConExp(expSegundos: Long): String {
        val payload = """{"sub":"usr_1","email":"a@b.co","exp":$expSegundos}"""
        val codificado = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        return "header.$codificado.firma"
    }

    @Test
    fun `extrae la caducidad del access y del refresh`() {
        val expAccess = 1_760_000_000L
        val expRefresh = 1_800_000_000L

        val (access, refresh) = JwtUtil.expiraAtMs(
            tokenConExp(expAccess),
            tokenConExp(expRefresh)
        )

        assertEquals(expAccess * 1_000L, access)
        assertEquals(expRefresh * 1_000L, refresh)
    }

    @Test
    fun `un token sin claim exp se trata como no caducable`() {
        val payload = """{"sub":"usr_1","email":"a@b.co"}"""
        val codificado = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(payload.toByteArray(Charsets.UTF_8))
        val token = "header.$codificado.firma"

        val (access, refresh) = JwtUtil.expiraAtMs(token, token)

        // La app no debe romperse: antes de que caduque de verdad, trata el
        // token como vigente y deja que el servidor sea quien rechace.
        assertTrue(access > 0)
        assertEquals(access, refresh)
    }

    @Test
    fun `un token malformado no rompe la app`() {
        val (access, refresh) = JwtUtil.expiraAtMs("no-es-un-jwt", "tampoco")

        assertTrue(access > 0)
        assertTrue(refresh > 0)
    }
}