package com.example.totaldiaria.network

/**
 * Capa única de errores (Fase 4).
 *
 * Todo lo que sale del backend llega aquí convertido en [ErrorDiarix], y del
 * [ErrorDiarix] solo se saca [mensajeAmigable] para mostrar al usuario. Los
 * detalles internos (trazas, status HTTP, stack traces) jamás salen de esta
 * capa: "Error desconocido" es el peor caso, nunca un mensaje con datos
 * internos.
 *
 * Es un Throwable para poder capturarse con `catch (e: ErrorDiarix)`.
 */
sealed class ErrorDiarix(
    val mensajeAmigable: String,
) : RuntimeException(mensajeAmigable) {

    /** Código del backend, útil para la lógica (no para mostrar). */
    open val codigo: String? get() = null

    /** Sin conexión o el servidor no respondió (timeout, caído). */
    data class Red(val detalle: String = "") : ErrorDiarix(
        "No hay conexión con el servidor. Revisa tu internet y vuelve a intentar."
    )

    /** El servidor respondió con un error de negocio que trae su código. */
    data class DeBackend(
        override val codigo: String,
        val mensaje: String,
        val detalles: List<String> = emptyList(),
    ) : ErrorDiarix(CodigosDelBackend.mensajeAmigable(codigo, mensaje))

    /** Algo salió mal sin código claro (estado raro, JSON raro). */
    data class Inesperado(val detalle: String = "") : ErrorDiarix(
        "Ocurrió un error inesperado. Intenta de nuevo en un momento."
    )
}

/**
 * Traducción de los códigos del backend a mensajes. El backend ya manda
 * mensajes en español y bien redactados: aquí se usan tal cual para los
 * códigos conocidos y se reserva un mensaje genérico para lo desconocido.
 */
object CodigosDelBackend {

    // Códigos que el contrato de DiariX define (ver `dominio.error.ts`).
    const val CREDENCIALES_INVALIDAS = "CREDENCIALES_INVALIDAS"
    const val EMPRESA_INACTIVA = "EMPRESA_INACTIVA"
    const val CONDUCTOR_INACTIVO = "CONDUCTOR_INACTIVO"
    const val CONDUCTOR_NO_PERTENECE = "CONDUCTOR_NO_PERTENECE"
    const val SUSCRIPCION_VENCIDA = "SUSCRIPCION_VENCIDA"
    const val USUARIO_SUSPENDIDO = "USUARIO_SUSPENDIDO"
    const val NO_AUTENTICADO = "NO_AUTENTICADO"
    const val NO_AUTORIZADO = "NO_AUTORIZADO"
    const val TOKEN_GOOGLE_INVALIDO = "TOKEN_GOOGLE_INVALIDO"
    const val GOOGLE_NO_DISPONIBLE = "GOOGLE_NO_DISPONIBLE"
    const val NIT_INVALIDO = "NIT_INVALIDO"
    const val NIT_EN_USO = "NIT_EN_USO"
    const val EMAIL_EN_USO = "EMAIL_EN_USO"
    const val LIMITE_FACTURAS_DIARIAS = "LIMITE_FACTURAS_DIARIAS"
    const val SIN_SUSCRIPCION = "SIN_SUSCRIPCION"
    const val CUENTA_CON_ROL_RESERVADO = "CUENTA_CON_ROL_RESERVADO"
    const val SERVICIO_NO_DISPONIBLE = "SERVICIO_NO_DISPONIBLE"

    // Fase 2 · Clientes. Son los tres modos en que la ficha puede rechazarse
    // con un mensaje escrito en español por el backend. Sin ellos, la app
    // respondería el genérico "No se pudo completar la operación" cuando el
    // servidor ya había dicho, por ejemplo, "El cliente no existe." o
    // "Debe indicar los apellidos.", que es información que el usuario
    // necesita y que ya está en la respuesta.
    const val NO_ENCONTRADO = "NO_ENCONTRADO"
    const val ENTRADA_INVALIDA = "ENTRADA_INVALIDA"
    const val VALIDACION = "VALIDACION"

    // Fase 4 · Pagos y cobros. El servidor es la fuente de los mensajes
    // (español, legibles, sin internos) para cada código de negocio con el
    // que puede rebotar un pago, una reversión o un comprobante.
    const val PAGO_SIN_SALDO = "PAGO_SIN_SALDO"
    const val PAGO_SUPERA_SALDO = "PAGO_SUPERA_SALDO"
    const val PAGO_YA_REVERTIDO = "PAGO_YA_REVERTIDO"
    const val SOLO_ULTIMO_PAGO = "SOLO_ULTIMO_PAGO"
    const val CLAVE_UNICA_REUTILIZADA = "CLAVE_UNICA_REUTILIZADA"
    const val COMPROBANTE_REQUERIDO = "COMPROBANTE_REQUERIDO"
    const val COMPROBANTE_FORMATO = "COMPROBANTE_FORMATO"
    const val COMPROBANTE_TAMANO = "COMPROBANTE_TAMANO"
    const val COMPROBANTE_NO_DISPONIBLE = "COMPROBANTE_NO_DISPONIBLE"
    const val COMPROBANTE_SOLO_TRANSFERENCIA = "COMPROBANTE_SOLO_TRANSFERENCIA"

    /** Trae el mensaje del backend para los códigos conocidos. */
    fun mensajeAmigable(codigo: String, mensajeDelBackend: String): String =
        when (codigo) {
            CREDENCIALES_INVALIDAS,
            EMPRESA_INACTIVA,
            CONDUCTOR_INACTIVO,
            CONDUCTOR_NO_PERTENECE,
            SUSCRIPCION_VENCIDA,
            USUARIO_SUSPENDIDO,
            NO_AUTENTICADO,
            NO_AUTORIZADO,
            TOKEN_GOOGLE_INVALIDO,
            GOOGLE_NO_DISPONIBLE,
            NIT_INVALIDO,
            NIT_EN_USO,
            EMAIL_EN_USO,
            LIMITE_FACTURAS_DIARIAS,
            SIN_SUSCRIPCION,
            CUENTA_CON_ROL_RESERVADO,
            SERVICIO_NO_DISPONIBLE,
            NO_ENCONTRADO,
            ENTRADA_INVALIDA,
            VALIDACION,
            PAGO_SIN_SALDO,
            PAGO_SUPERA_SALDO,
            PAGO_YA_REVERTIDO,
            SOLO_ULTIMO_PAGO,
            CLAVE_UNICA_REUTILIZADA,
            COMPROBANTE_REQUERIDO,
            COMPROBANTE_FORMATO,
            COMPROBANTE_TAMANO,
            COMPROBANTE_NO_DISPONIBLE,
            COMPROBANTE_SOLO_TRANSFERENCIA,
            -> if (mensajeDelBackend.isBlank()) {
                mensajeParaCodigoSinMensaje(codigo)
            } else {
                // El backend es la fuente del mensaje: ya está en español y es
                // seguro mostrarlo tal cual.
                mensajeDelBackend
            }

            else -> "No se pudo completar la operación. Intenta de nuevo."
        }

    private fun mensajeParaCodigoSinMensaje(codigo: String): String =
        when (codigo) {
            CREDENCIALES_INVALIDAS ->
                "Correo o contraseña incorrectos. Revisa tus datos e inténtalo de nuevo."
            SUSCRIPCION_VENCIDA ->
                "La suscripción de tu empresa está vencida. Pide su renovación para seguir registrando facturas."
            NO_AUTENTICADO -> "Tu sesión expiró. Vuelve a iniciar sesión."
            LIMITE_FACTURAS_DIARIAS ->
                "Alcanzaste el límite de facturas de este día."
            else -> "No se pudo completar la operación. Intenta de nuevo."
        }
}