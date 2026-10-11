package com.example.totaldiaria.network

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas unitarias del mapeo de errores a mensajes amigables (Fase 4).
 * Garantiza que NUNCA se exponen detalles internos ni stack traces al
 * usuario: solo textos seguros, con el mensaje del backend como fuente para
 * los códigos conocidos.
 */
class ErrorApiTest {

    @Test
    fun `el mensaje de credenciales invalidas es claro`() {
        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.CREDENCIALES_INVALIDAS,
            mensaje = "Correo o contraseña incorrectos."
        )

        assertEquals("Correo o contraseña incorrectos.", error.mensajeAmigable)
    }

    @Test
    fun `cuenta con rol reservado muestra el mensaje del backend`() {
        val msg =
            "Este correo pertenece a una cuenta empresarial o de administración. Inicia sesión con el acceso empresarial de tu empresa."

        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.CUENTA_CON_ROL_RESERVADO,
            mensaje = msg
        )

        assertEquals(msg, error.mensajeAmigable)
    }

    @Test
    fun `sin conexion siempre devuelve un solo mensaje amigable`() {
        val error = ErrorDiarix.Red("timeout in Connect")

        assertEquals(
            "No hay conexión con el servidor. Revisa tu internet y vuelve a intentar.",
            error.mensajeAmigable
        )
    }

    @Test
    fun `error inesperado no filtra detalles internos`() {
        val error = ErrorDiarix.Inesperado("boom: 500 /api")

        assertEquals(
            "Ocurrió un error inesperado. Intenta de nuevo en un momento.",
            error.mensajeAmigable
        )
    }

    @Test
    fun `limite de facturas usa el texto del backend cuando existe`() {
        val msg =
            "Alcanzaste el límite de 15 facturas de este día. Elimina o anula una factura para poder registrar esta."

        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.LIMITE_FACTURAS_DIARIAS,
            mensaje = msg
        )

        assertEquals(msg, error.mensajeAmigable)
    }
}