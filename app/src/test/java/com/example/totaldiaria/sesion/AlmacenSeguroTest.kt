package com.example.totaldiaria.sesion

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pruebas del almacenamiento seguro de tokens (Fase 4).
 *
 * El requisito es tajante: los tokens NUNCA en claro. Aquí se comprueba que
 * lo que llega a disco es material cifrado, que se recupera íntegro y que
 * cerrar sesión lo elimina.
 *
 * La cifración se prueba con un [Cifrador] de doble para que el test sea
 * determinista en cualquier máquina; el cifrador real de Keystore se
 * comprueba aparte y solo si el entorno lo soporta (no todos los emuladores
 * ni versiones de Robolectric exponen AndroidKeyStore).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlmacenSeguroTest {

    private lateinit var context: android.content.Context

    /** Cifrador de doble: no es criptografía, pero invierte y desinvierte como
     *  el real, así que sirve para verificar el ciclo de archivo. */
    private class CifradorDePrueba : Cifrador {
        override fun cifrar(plano: ByteArray): ByteArray =
            ByteArray(plano.size) { (plano[it].toInt() xor 0x5A).toByte() }

        override fun descifrar(cifrado: ByteArray): ByteArray =
            cifrar(cifrado)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        File(context.filesDir, NOMBRE_ARCHIVO).delete()
    }

    private val archivo: File
        get() = File(context.filesDir, NOMBRE_ARCHIVO)

    private fun tokens(acceso: String = "access-secreto") = TokensDeSesion(
        accessToken = acceso,
        refreshToken = "refresh-secreto",
        accessExpiraAtMs = 1_760_000_900_000L,
        refreshExpiraAtMs = 1_800_000_000_000L,
    )

    private fun almacen() = AlmacenSeguro(context, CifradorDePrueba())

    // ------------------------------------------------------------------
    // AlmacenSeguro (archivo + cifrado inyectado)
    // ------------------------------------------------------------------

    @Test
    fun `guarda y recupera los tokens correctamente`() {
        val store = TokenStoreSeguro(almacen())

        store.guardar(tokens())

        val leido = store.leer()

        assertNotNull(leido)
        assertEquals("access-secreto", leido!!.accessToken)
        assertEquals("refresh-secreto", leido.refreshToken)
        assertEquals(1_760_000_900_000L, leido.accessExpiraAtMs)
        assertEquals(1_800_000_000_000L, leido.refreshExpiraAtMs)
    }

    @Test
    fun `los tokens no se escriben en claro en el archivo`() {
        val store = TokenStoreSeguro(almacen())

        store.guardar(tokens("access-que-no-debe-aparecer"))

        assertTrue("se esperaba un archivo cifrado", archivo.exists())

        val contenidoBruto = String(archivo.readBytes(), Charsets.ISO_8859_1)

        assertFalse(
            "el access token en claro no debe estar en el archivo",
            contenidoBruto.contains("access-que-no-debe-aparecer")
        )
        assertFalse(
            "el refresh token en claro no debe estar en el archivo",
            contenidoBruto.contains("refresh-secreto")
        )
    }

    @Test
    fun `borrar deja la sesion sin datos`() {
        val store = TokenStoreSeguro(almacen())

        store.guardar(tokens())
        assertNotNull(store.leer())

        store.borrar()

        assertNull(store.leer())
        assertFalse(archivo.exists())
    }

    @Test
    fun `sin guardar nada no hay sesion`() {
        assertNull(TokenStoreSeguro(almacen()).leer())
    }

    @Test
    fun `un archivo corrupto se trata como sesion inexistente`() {
        // Se simula material ilegible escribiendo basura donde vive el
        // contenido cifrado.
        archivo.parentFile?.mkdirs()
        archivo.writeBytes(ByteArray(32) { 7 })

        // La app no debe romperse: se trata como sesión inexistente y el
        // flujo vuelve a pedir el login. Nunca queda un token a medias.
        assertNull(TokenStoreSeguro(almacen()).leer())
    }

    @Test
    fun `sobrescribir no deja tokens viejos en el archivo`() {
        val store = TokenStoreSeguro(almacen())

        store.guardar(tokens("access-primero"))
        store.guardar(tokens("access-segundo"))

        val leido = store.leer()

        assertEquals("access-segundo", leido!!.accessToken)
        assertFalse(
            String(archivo.readBytes(), Charsets.ISO_8859_1)
                .contains("access-primero")
        )
    }

    // ------------------------------------------------------------------
    // Cifrador real (Android Keystore)
    // ------------------------------------------------------------------

    @Test
    fun `el keystore real cifra y descifra cuando el entorno lo soporta`() {
        val almacen = AlmacenSeguro(context)

        val texto = "token-de-prueba-keystore".toByteArray()

        // Algunos entornos de prueba no implementan AndroidKeyStore; en ese
        // caso la verificación la hace el resto de la suite con el doble.
        assumeTrue(
            "el entorno no expone AndroidKeyStore",
            try {
                almacen.guardar(texto)
            } catch (_: Exception) {
                false
            }
        )

        assertTrue(archivo.exists())
        assertEquals("token-de-prueba-keystore", String(almacen.leer()!!))
        assertFalse(String(archivo.readBytes(), Charsets.ISO_8859_1)
            .contains("token-de-prueba-keystore"))

        almacen.borrar()
    }

    private companion object {
        const val NOMBRE_ARCHIVO = "diarix_credenciales.bin"
    }
}