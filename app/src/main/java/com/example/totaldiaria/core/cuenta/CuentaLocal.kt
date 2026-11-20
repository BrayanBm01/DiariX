package com.example.totaldiaria.core.cuenta

import android.content.Context
import java.io.File

/**
 * Dueño de los datos locales de esta instalación.
 *
 * La base SQLite anterior no tenía columna de dueño: todos los usuarios del
 * dispositivo compartían `facturas.db`. Para aislar cuentas SIN cambiar ese
 * esquema, cada cuenta usa su propia base (`facturas_<id>.db`), su propia
 * carpeta de comprobantes y su propia foto. El identificador se fija al entrar
 * (viene del `usuario.id` del backend, que es la autoridad) y se limpia al
 * salir o cuando el servidor declara la sesión expirada.
 *
 * Mientras no hay cuenta confirmada se usa un segmento vacío: la base resulta
 * nueva y vacía, de modo que ninguna pantalla puede mostrar restos de la
 * cuenta anterior aunque se abra antes de validar el perfil.
 */
object CuentaLocal {

    @Volatile
    var ownerId: String? = null
        private set

    private const val SIN_CUENTA = "sin-cuenta"

    fun fijar(id: String?) {
        ownerId = id?.takeIf { it.isNotBlank() }
    }

    fun limpiar() {
        ownerId = null
    }

    /** Segmento de nombre de archivo, seguro para SQLite y para directorios. */
    fun id(): String =
        ownerId?.let(::segmento) ?: SIN_CUENTA

    /** Nombre de la base privada de la cuenta indicada. */
    fun nombreBase(id: String): String = "facturas_${segmento(id)}.db"

    /**
     * Purga ÚNICA de los datos globales de la versión anterior.
     *
     * Eran de todos y de nadie: no se puede inferir a qué cuenta pertenece cada
     * fila, así que se descartan una vez. El usuario autorizó expresamente esta
     * purga. El marcador evita repetirla en cada arranque.
     */
    fun purgarLegacy(context: Context) {
        val app = context.applicationContext
        val preferencias = app.getSharedPreferences(
            "diarix_migracion_cuentas",
            Context.MODE_PRIVATE
        )

        if (preferencias.getBoolean(CLAVE_PURGA, false)) return

        runCatching { app.deleteDatabase("facturas.db") }
        runCatching {
            listOf("-journal", "-wal", "-shm").forEach { sufijo ->
                File(app.filesDir, "facturas.db$sufijo").delete()
            }
        }
        runCatching { File(app.filesDir, "comprobantes").deleteRecursively() }
        runCatching { File(app.filesDir, "qr.png").delete() }
        runCatching { File(app.filesDir, "fotos_perfil").deleteRecursively() }

        preferencias.edit().putBoolean(CLAVE_PURGA, true).apply()
    }

    private fun segmento(id: String): String =
        id.replace(Regex("[^A-Za-z0-9_-]"), "_").take(80)
            .ifBlank { SIN_CUENTA }

    private const val CLAVE_PURGA = "legacy_global_purgado_v1"
}
