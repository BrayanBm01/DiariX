package com.example.totaldiaria.core.modo

import android.content.Context

/**
 * Dónde se recuerda el área elegida.
 *
 * Un `SharedPreferences` corriente con UNA sola clave. No es un dato sensible
 * (no es un token ni un secreto): por eso NO se cifra y NO va por
 * [AlmacenSeguro], que existe para los tokens de sesión.
 *
 * Se guardan las cosas más simples posible a propósito: una clave, un texto.
 * Que sobreviva a cerrar la app es el objetivo; cualquier esquema más elaborado
 * (historial de modos, por área y por usuario) sería complejidad sin un solo
 * caso de uso detrás.
 */
class AlmacenDeModo(context: Context) {

    private val preferencias =
        context.applicationContext
            .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)

    /**
     * El área que la persona ELEGÍO, o `null` si todavía no ha elegido ninguna.
     *
     * Fase 0.1 — la diferencia con [modoActual] es toda la razón de existir de
     * este método, y conviene no perderla:
     *
     *  - `null` significa "todavía no sé qué área quieres". Es lo que hace que
     *    la app pregunte UNA vez después de entrar.
     *  - Un valor, sea [ModoDiarix.FACTURACION] o [ModoDiarix.PRESTAMOS],
     *    significa "ya lo elegiste, entra sin preguntar".
     *
     * Una clave ilegible (corrupta o de una versión futura) también devuelve
     * `null`: es indistinguible de "nunca eligió" y merece el mismo trato, que
     * es volver a preguntarlo. Al elegir se sobrescribe y el dato queda sano.
     */
    fun modoGuardado(): ModoDiarix? =
        ModoDiarix.desdeClave(
            preferencias.getString(CLAVE_MODO, null)
        )

    /**
     * El área vigente; [ModoDiarix.INICIAL] si aún no se eligió ninguna.
     *
     * Es la lectura que usan el menú y el arranque de cada pantalla, donde
     * siempre hace falta UN área y no tener ninguna sería un error. Quien
     * necesite distinguir "no eligió" de "eligió" debe usar [modoGuardado].
     */
    fun modoActual(): ModoDiarix =
        modoGuardado() ?: ModoDiarix.INICIAL

    /** Deja [modo] como área elegida. */
    fun guardar(modo: ModoDiarix) {
        preferencias
            .edit()
            .putString(CLAVE_MODO, modo.clave)
            .apply()
    }

    companion object {
        const val ARCHIVO = "diarix_modo"
        const val CLAVE_MODO = "modo_activo"
    }
}

/**
 * Atajo de lectura para quien solo necesita saber en qué área está.
 *
 * `getSharedPreferences` devuelve una instancia compartida por nombre, así que
 * construir [AlmacenDeModo] en cada consulta es barato y no deja estado que se
 * pueda desincronizar.
 */
fun modoVigente(context: Context): ModoDiarix =
    AlmacenDeModo(context).modoActual()