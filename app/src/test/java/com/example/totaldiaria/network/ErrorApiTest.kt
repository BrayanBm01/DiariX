package com.example.totaldiaria.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // =========================================================================
    // Fase G · códigos de sesión y de límites que antes quedaban ocultos
    // tras el mensaje genérico
    // =========================================================================

    @Test
    fun `sesion activa existente muestra el mensaje del backend`() {
        val msg = "Tu cuenta ya tiene una sesión activa en otro dispositivo. " +
            "Ciérrala desde ese dispositivo o espera unos minutos sin usarla antes de entrar aquí."

        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.SESION_ACTIVA_EXISTENTE,
            mensaje = msg
        )

        assertEquals(msg, error.mensajeAmigable)
    }

    @Test
    fun `sesion activa existente sin mensaje explica que hay que cerrar la otra sesion`() {
        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.SESION_ACTIVA_EXISTENTE,
            mensaje = ""
        )

        assertEquals(
            "Tu cuenta ya tiene una sesión activa en otro dispositivo. " +
                "Cierra sesión desde ese dispositivo antes de entrar aquí.",
            error.mensajeAmigable
        )
    }

    @Test
    fun `sesion cerrada por inactividad usa el mensaje del backend`() {
        val msg = "Tu sesión se cerró por inactividad. Inicia sesión nuevamente."

        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.SESION_CERRADA_POR_INACTIVIDAD,
            mensaje = msg
        )

        assertEquals(msg, error.mensajeAmigable)
    }

    @Test
    fun `demasiados intentos usa el mensaje del backend`() {
        val msg = "Demasiados intentos fallidos. Espera un momento antes de volver a intentarlo."

        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.DEMASIADOS_INTENTOS,
            mensaje = msg
        )

        assertEquals(msg, error.mensajeAmigable)
    }

    @Test
    fun `el limite general del servidor se explica en espanol aunque venga en ingles`() {
        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.LIMITE_EXCEDIDO,
            mensaje = "ThrottlerException: Too Many Requests"
        )

        assertEquals(
            "El servidor está limitando las peticiones en este momento. " +
                "Espera un minuto y vuelve a intentarlo.",
            error.mensajeAmigable
        )
    }

    @Test
    fun `un fallo interno del servidor se nombra como tal`() {
        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.ERROR_INTERNO,
            mensaje = "Ocurrió un error inesperado. Intenta de nuevo."
        )

        assertEquals(
            "El servidor encontró un problema interno. Intenta de nuevo en unos minutos.",
            error.mensajeAmigable
        )

        val sinCuerpo = ErrorDiarix.DeBackend(codigo = "HTTP_500", mensaje = "")
        assertTrue(sinCuerpo.mensajeAmigable.contains("servidor encontró un problema interno"))
    }

    @Test
    fun `una respuesta que no es de la API se nombra con su estado`() {
        val error = ErrorDiarix.DeBackend(codigo = "HTTP_405", mensaje = "")

        assertTrue(error.mensajeAmigable.contains("HTTP_405"))
    }

    @Test
    fun `un codigo desconocido muestra el mensaje seguro del backend`() {
        val error = ErrorDiarix.DeBackend(
            codigo = "CODIGO_NUEVO_DEL_BACKEND",
            mensaje = "El mensaje en español que redactó el backend."
        )

        assertEquals("El mensaje en español que redactó el backend.", error.mensajeAmigable)
    }

    @Test
    fun `un codigo desconocido sin mensaje cae en el texto generico`() {
        val error = ErrorDiarix.DeBackend(codigo = "CODIGO_NUEVO_DEL_BACKEND", mensaje = "")

        assertEquals("No se pudo completar la operación. Intenta de nuevo.", error.mensajeAmigable)
    }

    // =========================================================================
    // Fase G · el fallo de red ya no es siempre "sin conexión"
    // =========================================================================

    @Test
    fun `cada motivo de red tiene un mensaje distinto y accionable`() {
        val textos = MotivoRed.entries.map { ErrorDiarix.Red(motivo = it).mensajeAmigable }

        assertEquals(
            "Los motivos deben producir mensajes distintos",
            MotivoRed.entries.size,
            textos.toSet().size,
        )
        assertTrue(
            ErrorDiarix.Red(motivo = MotivoRed.DNS).mensajeAmigable.contains("encontrar el servidor")
        )
        assertTrue(
            ErrorDiarix.Red(motivo = MotivoRed.TIMEOUT).mensajeAmigable.contains("tardó demasiado")
        )
        assertTrue(
            ErrorDiarix.Red(motivo = MotivoRed.TLS).mensajeAmigable.contains("conexión segura")
        )
        assertTrue(
            ErrorDiarix.Red(motivo = MotivoRed.SIN_INTERNET).mensajeAmigable.contains("Internet")
        )
    }

    @Test
    fun `un 502 503 o 504 sin cuerpo dice que el servidor esta iniciando`() {
        for (codigo in listOf("HTTP_502", "HTTP_503", "HTTP_504")) {
            val error = ErrorDiarix.DeBackend(codigo = codigo, mensaje = "")

            assertTrue(
                "$codigo debe explicar que el servidor está iniciando",
                error.mensajeAmigable.contains("iniciando") &&
                    error.mensajeAmigable.contains("intentar"),
            )
        }
    }

    @Test
    fun `un 503 con codigo del backend conserva el mensaje del backend`() {
        val msg = "El acceso con Google no está configurado en el servidor: falta GOOGLE_CLIENT_ID."

        val error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.GOOGLE_NO_DISPONIBLE,
            mensaje = msg,
        )

        assertEquals(msg, error.mensajeAmigable)
    }
}