package com.example.totaldiaria.core.cuenta

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Foto de perfil guardada SOLO en el dispositivo y dentro de la carpeta de la
 * cuenta activa. Reglas que se fijan aquí:
 *
 *  - la foto elegida por la persona tiene prioridad sobre la de Google;
 *  - volver a la de Google NO borra la personalizada;
 *  - otra cuenta no ve la foto de la primera;
 *  - la foto sobrevive a una nueva instancia (no vive solo en memoria).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FotoPerfilStoreTest {

    private lateinit var context: android.content.Context

    private val cuentaA = "user-a"
    private val cuentaB = "user-b"

    /** JPEG válido por firma (los 2 primeros bytes son los que mira el store). */
    private val jpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 1
    )

    /** PNG válido por firma. */
    private val png = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0, 2
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        CuentaLocal.limpiar()
        carpetaFotos().deleteRecursively()
    }

    @After
    fun tearDown() {
        carpetaFotos().deleteRecursively()
        CuentaLocal.limpiar()
    }

    private fun carpetaFotos(): File = File(context.filesDir, "fotos_perfil")

    @Test
    fun `la foto personalizada tiene prioridad y sobrevive a una nueva instancia`() {
        CuentaLocal.fijar(cuentaA)
        val store = FotoPerfilStore(context)

        assertTrue(store.guardarGoogle(jpeg))
        assertTrue(store.guardarPersonalizada(png))

        assertTrue(store.usandoPersonalizada())
        assertEquals("personalizada.jpg", store.fotoActual()?.name)

        // Nueva instancia (equivale a reabrir la app): sigue ahí.
        val releida = FotoPerfilStore(context)
        assertTrue(releida.usandoPersonalizada())
        assertEquals("personalizada.jpg", releida.fotoActual()?.name)
    }

    @Test
    fun `volver a la foto de Google no borra la personalizada`() {
        CuentaLocal.fijar(cuentaA)
        val store = FotoPerfilStore(context)

        store.guardarGoogle(jpeg)
        store.guardarPersonalizada(png)

        store.usarFotoDeGoogle()

        assertEquals("google.jpg", store.fotoActual()?.name)
        assertTrue(store.tieneFotoGoogle())
        assertTrue(File(carpetaFotos().resolve(cuentaA), "personalizada.jpg").exists())
    }

    @Test
    fun `otra cuenta no ve la foto de la primera`() {
        CuentaLocal.fijar(cuentaA)
        val storeA = FotoPerfilStore(context)
        storeA.guardarPersonalizada(png)
        storeA.guardarUrlGoogle("https://ejemplo.invalid/foto.png")

        CuentaLocal.fijar(cuentaB)
        val storeB = FotoPerfilStore(context)

        assertNull(storeB.fotoActual())
        assertNull(storeB.urlGoogle())
        assertFalse(storeB.usandoPersonalizada())
    }

    @Test
    fun `no se guardan bytes que no sean una imagen`() {
        CuentaLocal.fijar(cuentaA)
        val store = FotoPerfilStore(context)

        assertFalse(store.guardarPersonalizada("<html>error</html>".toByteArray()))
        assertFalse(store.guardarGoogle("texto".toByteArray()))
        assertNull(store.fotoActual())
    }
}
