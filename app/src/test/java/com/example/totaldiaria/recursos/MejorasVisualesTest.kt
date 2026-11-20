package com.example.totaldiaria.recursos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.R
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Las dos mejoras visuales pedidas, comprobadas por estructura:
 *
 *  - las pestañas de Registros existen y su texto cabe (padding medido y
 *    modo "fixed" para repartir el ancho);
 *  - el icono de Google del login NO lleva tinte: la G multicolor se ve con
 *    sus colores reales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MejorasVisualesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_TotalDiaria
        )
    }

    // ----------------------------------------------------------------
    // Pestañas de Registros
    // ----------------------------------------------------------------

    @Test
    fun `las pestañas de registros son dos y usan el modo repartido`() {

        val raiz = inflar(R.layout.activity_registros)
        val pestañas = raiz.findViewById<TabLayout>(R.id.tabLayoutRegistros)

        assertNotNull(pestañas)
        assertEquals(2, pestañas.tabCount)
        assertEquals(
            "Con dos pestañas cortas, 'fixed' + 'fill' reparte el ancho y evita " +
                "texto comprimido en teléfonos pequeños",
            TabLayout.MODE_FIXED,
            pestañas.tabMode
        )
        assertEquals(
            TabLayout.GRAVITY_FILL,
            pestañas.tabGravity
        )
    }

    @Test
    fun `el padding interior de las pestañas deja aire al texto`() {

        val xml = textoDeLayout("activity_registros.xml")

        assertTrue(
            "Falta app:tabPaddingStart en las pestañas de Registros",
            xml.contains("app:tabPaddingStart=\"12dp\"")
        )
        assertTrue(
            "Falta app:tabPaddingEnd en las pestañas de Registros",
            xml.contains("app:tabPaddingEnd=\"12dp\"")
        )
    }

    // ----------------------------------------------------------------
    // Icono de Google
    // ----------------------------------------------------------------

    @Test
    fun `el botón de Google no aplica tinte al icono`() {

        val bloque = bloqueDelBotonGoogle()

        assertTrue(
            "El botón de Google cambió de id o desapareció",
            bloque.contains("android:id=\"@+id/btnGoogle\"")
        )

        assertTrue(
            "El icono de Google no debe cambiar de drawable",
            bloque.contains("app:icon=\"@drawable/ic_google\"")
        )

        assertTrue(
            "La G de Google debe verse multicolor: falta app:iconTint=\"@null\"",
            bloque.contains("app:iconTint=\"@null\"")
        )
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    private fun inflar(layout: Int): View =
        LayoutInflater.from(context).inflate(layout, null, false)

    /** Texto fuente del layout, desde el módulo `app`. */
    private fun textoDeLayout(nombre: String): String {
        val raices = listOf(
            java.io.File("src/main/res/layout/$nombre"),
            java.io.File("app/src/main/res/layout/$nombre"),
        )

        val archivo = raices.firstOrNull { it.exists() }
            ?: throw AssertionError("No se encontró el layout $nombre")

        return archivo.readText()
    }

    /** El fragmento del XML del botón de Google, sin importar el orden. */
    private fun bloqueDelBotonGoogle(): String {
        val xml = textoDeLayout("activity_login.xml")
        // La primera mención puede estar en un comentario: se busca el id real.
        val marca = xml.indexOf("@+id/btnGoogle")

        if (marca < 0) throw AssertionError("No está btnGoogle en el login")

        // El bloque empieza en la etiqueta que contiene el id, no en el id.
        val etiqueta = xml.lastIndexOf('<', marca)
        val fin = xml.indexOf("/>", etiqueta)

        return xml.substring(etiqueta, minOf(xml.length, fin + 2))
    }
}
