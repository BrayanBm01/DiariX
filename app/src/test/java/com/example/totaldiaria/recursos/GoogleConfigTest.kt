package com.example.totaldiaria.recursos

import com.example.totaldiaria.BuildConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DIARIX · Configuración de Google empaquetada en el artefacto.
 *
 * El botón de Google se deshabilita en `LoginActivity` cuando el BuildConfig
 * sale vacío. Ese fallo no lo ve el compilador: el APK se instala y el usuario
 * ve un error local sin que se haga ninguna petición al backend. Esta prueba
 * lo convierte en un fallo de CI.
 *
 * No se imprime el Client ID: es un dato público, pero tampoco hace falta
 * exponerlo en los logs de pruebas.
 */
class GoogleConfigTest {

    @Test
    fun `el artefacto incluye el Web Client ID de Google`() {
        val clientId = BuildConfig.DIARIX_GOOGLE_WEB_CLIENT_ID

        assertTrue(
            "El Web Client ID de Google está vacío: el botón de acceso queda deshabilitado.",
            clientId.isNotBlank(),
        )
        assertTrue(
            "El Web Client ID no parece un Client ID de Google.",
            clientId.endsWith(".apps.googleusercontent.com"),
        )
    }
}
