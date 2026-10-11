package com.example.totaldiaria.sesion

/**
 * Par de tokens de una sesión, con su caducidad ya resuelta a epoch ms.
 *
 * `conGoogle` es un dato de la Fase 0.1 y está aquí, y no en otro sitio, por
 * una razón práctica: es el ÚNICO indicador de que la cuenta está vinculada a
 * Google que existe. `GET /usuarios/yo` no lo devuelve y `users` solo guarda el
 * `googleSub`, así que ni el perfil ni el token lo dicen. Lo que sí lo sabe con
 * certeza es el propio inicio de sesión: si se apretó "Continuar con Google",
 * la cuenta es de Google.
 *
 * Viaja dentro del mismo blob cifrado que los tokens a propósito, en vez de
 * quedarse en una variable en memoria: si solo viviera en memoria, la etiqueta
 * "Cuenta de Google" desaparecería de Configuración en cuanto el sistema
 * matara el proceso, que es justo lo que pasa cuando el usuario deja el móvil
 * en la mesa un rato.
 *
 * Es `data class` únicamente por el `copy()`: al renovar los tokens hay que
 * reescribirlos SIN perder esta marca, porque el backend rota el par entero y
 * no manda nada sobre cómo se entró.
 */
data class TokensDeSesion(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiraAtMs: Long,
    val refreshExpiraAtMs: Long,
    val conGoogle: Boolean = false,
)

/** Datos que devuelve el login /auth/login y /auth/google. */
data class LoginResuelto(
    val tokens: TokensDeSesion,
    val rol: String,
    val email: String,
    val empresa: EmpresaSesion?,
    val cuentaNueva: Boolean,
)

/** Empresa del contador o del conductor, tal como la manda el servidor. */
data class EmpresaSesion(
    val id: String,
    val nit: String,
    val nombre: String,
    val plan: String?,
    val cupoConductores: Int?,
)

/**
 * Plan EFECTIVO de la sesión (bloque `plan` de /auth/yo). El servidor es la
 * autoridad: la app solo refleja estos números en la interfaz.
 */
data class PlanSesion(
    val rol: String,
    val plan: String,
    val planVigente: Boolean,
    val facturasPorDia: Int?,
    val graficasAvanzadas: Boolean,
)

/** Perfil completo que devuelve GET /auth/yo. */
data class PerfilActual(
    val usuarioId: String,
    val email: String,
    val nombre: String,
    val rol: String,
    val emailVerificado: Boolean,
    val empresa: EmpresaSesion?,
    val conductorId: String?,
    val plan: PlanSesion?,
)

/** Límites de uso del día, de GET /facturas/uso-diario. */
data class UsoDiario(
    val dia: String,
    val plan: String,
    val planVigente: Boolean,
    val limite: Int,
    val usadas: Int,
    val restantes: Int,
)

/**
 * DIARIX · Fase 0.1 · Lo que la persona puede VER y CAMBIAR de sí misma.
 *
 * Es un tipo DISTINTO de [PerfilActual] y la razón es concreta: [PerfilActual]
 * es lo mínimo que necesita el resto de la app para funcionar (rol, empresa y
 * plan, que se leen al entrar y se usan para ocultar funciones), mientras que
 * esto es la ficha de "Mi perfil" de Configuración, con los campos que una
 * persona puede tocar.
 *
 * ## Qué es editable y qué no, y por qué
 *
 * Lo editable es exactamente lo que acepta `PATCH /usuarios/yo`
 * (`ActualizarPerfilDto` del backend):
 *
 *  - [nombreCompleto] → columna `users.fullName`.
 *  - [telefono] → columna `users.phone`.
 *
 * Lo que NO se puede editar desde aquí, ni se presenta como editable:
 *
 *  - [email]: es la IDENTIDAD de la cuenta. Con Google es además la que Google
 *    firmó, y el backend la reescribe desde Google si cambia allí. Tocarla en
 *    la app solo produciría un cambio aparente que el servidor revierte.
 *  - [rol] y [estado]: los fija el servidor (invitación de empresa, CLI del
 *    primer administrador). No hay endpoint para que alguien se los cambie a sí
 *    mismo, y si lo hubiera sería un agujero de privilegios.
 *  - [empresa] y [plan]: pertenecen a la cuenta de empresa, no a la persona.
 *
 * ## El avatar
 *
 * No hay foto. Ni `users` tiene columna y ni el backend la recibe de Google
 * (solo conserva `googleSub` y el nombre). Un avatar dibujado a partir del
 * nombre real no es un dato inventado: es una forma de escribir el nombre que
 * sí existe. En cuanto exista una columna de foto, se sustituye el dibujo por
 * la imagen y no hay que tocar nada más.
 */
data class PerfilEditable(
    val id: String,
    val email: String,
    val nombreCompleto: String,
    val telefono: String?,
    val rol: String,
    val estado: String,
    val correoVerificado: Boolean,
    val empresaNombre: String?,
    val plan: String?,
) {

    /** El teléfono tal como se escribe y como se guarda: texto libre. */
    val telefonoEditable: String
        get() = telefono.orEmpty()

    companion object {

        /**
         * A partir del perfil que ya tenía la sesión, para pintar la ficha
         * SIN esperar a la red.
         *
         * Se usa al abrir Configuración para que la pantalla no aparezca
         * vacía, y se sustituye en cuanto llegue `GET /usuarios/yo`. Todo lo
         * que aquí no exista (el teléfono) se muestra vacío, nunca inventado.
         */
        fun desde(perfil: PerfilActual): PerfilEditable =
            PerfilEditable(
                id = perfil.usuarioId,
                email = perfil.email,
                nombreCompleto = perfil.nombre,
                telefono = null,
                rol = perfil.rol,
                estado = "ACTIVE",
                correoVerificado = perfil.emailVerificado,
                empresaNombre = perfil.empresa?.nombre,
                plan = perfil.plan?.plan,
            )

        /** Sin sesión: la ficha vacía, con el correo que se conozca. */
        fun vacio(email: String = ""): PerfilEditable =
            PerfilEditable(
                id = "",
                email = email,
                nombreCompleto = "",
                telefono = null,
                rol = "",
                estado = "",
                correoVerificado = false,
                empresaNombre = null,
                plan = null,
            )

        /**
         * Las iniciales del avatar.
         *
         * Con dos o más palabras se toma la primera letra de las dos PRIMERAS
         * ("Ana María Torres" → "AM", "Ana Torres" → "AT"), que es como se leen
         * unas iniciales. Con una sola palabra, sus dos primeras letras
         * ("Ana" → "AN"), porque con una palabra no hay con qué hacer dos
         * iniciales.
         *
         * Sin texto del que sacar nada devuelve vacío, y quien lo pinte decide
         * qué hacer, en vez de poner un símbolo arbitrario.
         */
        fun iniciales(nombreCompleto: String): String {

            val palabras = nombreCompleto
                .trim()
                .split(' ', '\t', '\n')
                .filter { it.isNotBlank() }

            return when (palabras.size) {
                0 -> ""
                1 -> palabras[0].take(2)
                else -> "${palabras[0].take(1)}${palabras[1].take(1)}"
            }.uppercase()
        }
    }
}