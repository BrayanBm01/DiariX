package com.example.totaldiaria.network

/**
 * Motivo concreto de un fallo de red.
 *
 * Antes TODA `IOException` se convertía en el mismo texto («No hay conexión
 * con el servidor»), así que un DNS roto, un certificado rechazado, un
 * timeout de Render arrancando y un servidor apagado eran indistinguibles.
 * El motivo se usa para elegir el mensaje y para el Logcat de soporte.
 */
enum class MotivoRed {
    SIN_INTERNET,
    DNS,
    TIMEOUT,
    TLS,
    SERVIDOR_NO_ALCANZABLE,
    DESCONOCIDO,
}

private fun mensajeDeRed(motivo: MotivoRed): String = when (motivo) {
    MotivoRed.SIN_INTERNET ->
        "No hay conexión a Internet. Revisa tus datos móviles o la Wi-Fi y vuelve a intentar."

    MotivoRed.DNS ->
        "No se pudo encontrar el servidor de DiariX. Comprueba tu conexión a Internet " +
            "o cambia de red."

    MotivoRed.TIMEOUT ->
        "El servidor de DiariX tardó demasiado en responder. Puede estar iniciándose; " +
            "espera unos segundos y vuelve a intentar."

    MotivoRed.TLS ->
        "No se pudo establecer una conexión segura con el servidor. Revisa la fecha y hora " +
            "de tu teléfono o cambia de red."

    MotivoRed.SERVIDOR_NO_ALCANZABLE ->
        "No se pudo conectar con el servidor de DiariX. Comprueba tu conexión o inténtalo " +
            "de nuevo en unos segundos."

    MotivoRed.DESCONOCIDO ->
        "No hay conexión con el servidor. Revisa tu internet y vuelve a intentar."
}

/**
 * Capa única de errores (Fase 4).
 *
 * Todo lo que sale del backend llega aquí convertido en [ErrorDiarix], y del
 * [ErrorDiarix] solo se saca [mensajeAmigable] para mostrar al usuario. Los
 * detalles internos (trazas, status HTTP, stack traces) jamás salen de esta
 * capa: "Error desconocido" es el peor caso, nunca un mensaje con datos
 * internos. La excepción ORIGINAL sí se registra en Logcat (ver ApiCliente).
 *
 * Es un Throwable para poder capturarse con `catch (e: ErrorDiarix)`.
 */
sealed class ErrorDiarix(
    val mensajeAmigable: String,
) : RuntimeException(mensajeAmigable) {

    /** Código del backend, útil para la lógica (no para mostrar). */
    open val codigo: String? get() = null

    /**
     * Sin conexión o el servidor no respondió.
     *
     * [detalle] lleva la clase y el mensaje de la excepción original para el
     * Logcat; NUNCA se muestra al usuario. [motivo] decide el texto visible.
     * El orden de los parámetros conserva `Red("texto")` de las llamadas
     * anteriores.
     */
    data class Red(
        val detalle: String = "",
        val motivo: MotivoRed = MotivoRed.DESCONOCIDO,
    ) : ErrorDiarix(mensajeDeRed(motivo))

    /**
     * El servidor respondió con un error de negocio que trae su código.
     *
     * En `VALIDACION` (400) el mensaje genérico "Revisa los datos enviados."
     * no dice NADA al usuario: el detalle concreto ("Falta el token de
     * Google.", "El NIT debe tener 9 dígitos.") vive en `detalles`, ya
     * redactado en español por el backend y seguro de mostrar. Ahí se une al
     * mensaje para que el fallo sea explicable sin depurar nada.
     */
    data class DeBackend(
        override val codigo: String,
        val mensaje: String,
        val detalles: List<String> = emptyList(),
    ) : ErrorDiarix(
        if (codigo == CodigosDelBackend.VALIDACION && detalles.isNotEmpty()) {
            "${CodigosDelBackend.mensajeAmigable(codigo, mensaje)} ${detalles.joinToString(" ")}"
        } else {
            CodigosDelBackend.mensajeAmigable(codigo, mensaje)
        },
    )

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

    // Sesión (409/401): existen desde la Fase 1 del backend y el login de la
    // app los recibe a diario, pero estaban sin traducir y quedaban ocultos
    // tras "No se pudo completar la operación". El caso real: entrar en la app
    // con una cuenta que acaba de entrar en la web devuelve
    // SESION_ACTIVA_EXISTENTE (409), y sin esta entrada la app no explicaba
    // que hay que cerrar la sesión de la web o esperar unos minutos.
    const val SESION_ACTIVA_EXISTENTE = "SESION_ACTIVA_EXISTENTE"
    const val SESION_CERRADA_POR_INACTIVIDAD = "SESION_CERRADA_POR_INACTIVIDAD"

    // Límites (429): `DEMASIADOS_INTENTOS` lo manda el backend con mensaje en
    // español; `LIMITE_EXCEDIDO` es el del limitador general de peticiones,
    // cuya respuesta por defecto viene en inglés y por eso lleva mensaje propio.
    const val DEMASIADOS_INTENTOS = "DEMASIADOS_INTENTOS"
    const val LIMITE_EXCEDIDO = "LIMITE_EXCEDIDO"

    // Fallo interno (500). Se nombra para poder decir "el servidor tuvo un
    // problema" y no confundirlo con un error de credenciales o de red.
    const val ERROR_INTERNO = "ERROR_INTERNO"

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
        when {
            // El limitador general de peticiones responde por defecto en
            // inglés ("ThrottlerException: Too Many Requests"), así que aquí
            // SIEMPRE se usa el mensaje propio.
            codigo == LIMITE_EXCEDIDO ->
                "El servidor está limitando las peticiones en este momento. " +
                    "Espera un minuto y vuelve a intentarlo."

            // Render (y cualquier proxy) devuelve 502/503/504 mientras la
            // instancia arranca o se reinicia. No es "sin internet" ni una
            // dirección equivocada: el servidor existe, pero aún no está
            // listo. Se nombra para que el usuario sepa que basta reintentar.
            codigo == "HTTP_502" || codigo == "HTTP_503" || codigo == "HTTP_504" ->
                "El servidor de DiariX está iniciando o no está disponible en este momento. " +
                    "Espera unos segundos y vuelve a intentar."

            // Un 5xx sin cuerpo de negocio (o con un cuerpo que no es el del
            // filtro global) también se nombra: "el servidor tuvo un problema"
            // es distinto de "tus credenciales no valen" y de "no hay red".
            codigo == ERROR_INTERNO || codigo.startsWith("HTTP_5") ->
                "El servidor encontró un problema interno. Intenta de nuevo en unos minutos."

            // Respuesta que NO viene del backend de DiariX (HTML de un proxy o
            // de la SPA, o ruta inexistente): el cuerpo no traía `codigo`, así
            // que ApiCliente solo pudo guardar el estado HTTP. Nombrarlo es lo
            // que permite distinguir en soporte un "404/405 = la app apunta a
            // una dirección que no es la API" de un fallo real de la API.
            codigo.startsWith("HTTP_") ->
                "El servidor no respondió como la API de DiariX ($codigo). " +
                    "Puede que la app esté apuntando a una dirección equivocada: contacta al soporte."

            else -> when (codigo) {
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
                SESION_ACTIVA_EXISTENTE,
                SESION_CERRADA_POR_INACTIVIDAD,
                DEMASIADOS_INTENTOS,
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

                // Código nuevo o inesperado. El filtro global del backend solo
                // emite mensajes pensados para mostrarse (nunca trazas ni
                // datos internos), así que un texto del servidor se muestra
                // tal cual: un "No se pudo completar la operación" mudo deja
                // sin diagnóstico al usuario Y a soporte.
                else -> if (mensajeDelBackend.isBlank()) {
                    "No se pudo completar la operación. Intenta de nuevo."
                } else {
                    mensajeDelBackend
                }
            }
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
            SESION_ACTIVA_EXISTENTE ->
                "Tu cuenta ya tiene una sesión activa en otro dispositivo. " +
                    "Cierra sesión desde ese dispositivo antes de entrar aquí."
            SESION_CERRADA_POR_INACTIVIDAD ->
                "Tu sesión se cerró por inactividad. Inicia sesión nuevamente."
            DEMASIADOS_INTENTOS ->
                "Demasiados intentos fallidos. Espera un momento antes de volver a intentarlo."
            else -> "No se pudo completar la operación. Intenta de nuevo."
        }
}