package com.example.totaldiaria.core.modo

import android.content.Context
import android.content.pm.PackageManager
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.facturacion.ui.ClienteFormularioActivity
import com.example.totaldiaria.modulos.facturacion.ui.ClientesActivity
import com.example.totaldiaria.modulos.facturacion.ui.ConfiguracionActivity
import com.example.totaldiaria.modulos.facturacion.ui.EstadisticasActivity
import com.example.totaldiaria.modulos.facturacion.ui.HistorialActivity
import com.example.totaldiaria.modulos.facturacion.ui.MainActivity
import com.example.totaldiaria.modulos.facturacion.ui.RegistrosActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamosCobrosActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamosInicioActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamosPagosActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamosPrestamosActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamoPagoActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
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

/**
 * DIARIX · Fase 0 · Garantías del cambio de área.
 *
 * Lo que se comprueba aquí es lo que, si se rompe, hace que DiariX deje de
 * funcionar como hasta ahora o de verdad se mezclen las dos mitades:
 *
 *  1. Quien nunca eligió un área entra en FACTURACIÓN: la app actualizada se
 *     abre exactamente igual que antes.
 *  2. El área elegida sobrevive al cierre de la app.
 *  3. Una clave ilegible no deja la app sin pantalla de inicio.
 *  4. Las cinco pestañas de facturación son las del diseño, ni una más ni
 *     una menos (Inicio, Registros, Estadísticas, Historial y Configuración).
 *  5. El menú de préstamos NO contiene ninguna pestaña de facturación, y el de
 *     facturación NO contiene ninguna de préstamos. Esta es la prueba de que
 *     las dos mitades no se mezclan en pantalla.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ModoDiarixTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        limpiar()
    }

    @After
    fun tearDown() {
        limpiar()
    }

    private fun limpiar() {
        context
            .getSharedPreferences(AlmacenDeModo.ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun itemsDelMenu(menuRes: Int): List<Int> {

        val nav = BottomNavigationView(
            ContextThemeWrapper(context, R.style.Theme_TotalDiaria)
        )

        nav.inflateMenu(menuRes)

        return (0 until nav.menu.size()).map { nav.menu.getItem(it).itemId }
    }

    // ----------------------------------------------------------------
    // 1. Por defecto, facturación
    // ----------------------------------------------------------------

    @Test
    fun `sin elegir nada se entra en facturacion`() {

        assertEquals(ModoDiarix.FACTURACION, AlmacenDeModo(context).modoActual())
    }

    @Test
    fun `el area inicial abre la pantalla de inicio de facturacion`() {

        assertEquals(MainActivity::class.java, ModoDiarix.INICIAL.pantallaDeInicio)
    }

    // ----------------------------------------------------------------
    // 2. El área elegida se recuerda
    // ----------------------------------------------------------------

    @Test
    fun `el area elegida se conserva al releerla`() {

        AlmacenDeModo(context).guardar(ModoDiarix.PRESTAMOS)

        // Instancia nueva: lo que se comprobó es que quedó PERSISTIDO, no que
        // una variable en memoria lo recuerde.
        assertEquals(ModoDiarix.PRESTAMOS, AlmacenDeModo(context).modoActual())
    }

    @Test
    fun `cada modo tiene su propia pantalla de inicio`() {

        assertEquals(
            MainActivity::class.java,
            ModoDiarix.FACTURACION.pantallaDeInicio
        )

        assertEquals(
            PrestamosInicioActivity::class.java,
            ModoDiarix.PRESTAMOS.pantallaDeInicio
        )
    }

    @Test
    fun `las claves de los modos no se repiten`() {

        assertEquals(
            ModoDiarix.values().size,
            ModoDiarix.values().map { it.clave }.distinct().size
        )
    }

    // ----------------------------------------------------------------
    // 3. Una clave ilegible no rompe la app
    // ----------------------------------------------------------------

    @Test
    fun `una clave desconocida no corresponde a ningun modo`() {

        assertNull(ModoDiarix.desdeClave("prestamos_v1_renombrado"))
        assertNull(ModoDiarix.desdeClave(null))
    }

    @Test
    fun `una clave desconocida cae en facturacion en vez de fallar`() {

        assertEquals(
            ModoDiarix.FACTURACION,
            ModoDiarix.desdeClaveOInicial("lo_que_haya_por_ahi")
        )
    }

    @Test
    fun `una clave guardada a mano e ilegible se lee como facturacion`() {

        context
            .getSharedPreferences(AlmacenDeModo.ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(AlmacenDeModo.CLAVE_MODO, "@#\$%&")
            .commit()

        assertEquals(ModoDiarix.FACTURACION, AlmacenDeModo(context).modoActual())
    }

    // ----------------------------------------------------------------
    // 4 y 5. Las dos mitades no se mezclan
    // ----------------------------------------------------------------

    @Test
    fun `el menu de facturacion conserva sus cinco pestanas`() {

        assertEquals(
            listOf(
                R.id.nav_inicio,
                R.id.nav_registros,
                R.id.nav_estadisticas,
                R.id.nav_historial,
                R.id.nav_configuracion,
            ),
            itemsDelMenu(MenuDeModo.de(ModoDiarix.FACTURACION))
        )
    }

    @Test
    fun `el menu de prestamos no tiene ninguna pestaña de facturacion`() {

        val dePrestamos = itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS))
        val deFacturacion = itemsDelMenu(MenuDeModo.de(ModoDiarix.FACTURACION))

        assertTrue(dePrestamos.isNotEmpty())

        assertTrue(
            "El menú de préstamos no puede contener pestañas de facturación",
            deFacturacion.none { it in dePrestamos }
        )
    }

    @Test
    fun `el menu de facturacion no tiene ninguna pestaña de prestamos`() {

        val dePrestamos = itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS))
        val deFacturacion = itemsDelMenu(MenuDeModo.de(ModoDiarix.FACTURACION))

        assertTrue(
            "El menú de facturación no puede contener pestañas de préstamos",
            dePrestamos.none { it in deFacturacion }
        )
    }

    @Test
    fun `cada modo infla un menu distinto`() {

        assertFalse(
            MenuDeModo.de(ModoDiarix.FACTURACION) ==
                MenuDeModo.de(ModoDiarix.PRESTAMOS)
        )
    }

    /**
     * Las NUE pantallas de las dos áreas están declaradas en el manifiesto.
     *
     * Un id de menú sin Activity declarada en el manifiesto es una pestaña que
     * crashea AL TOCARLA, en un dispositivo, con un build en verde. Es la
     * razón de que la comprobación esté aquí y no en el build.
     *
     * La Fase 1 añadió pantallas al área de préstamos y el nombre del test
     * pasó de "seis" a "diez". Un número escrito a mano en el nombre de un
     * test es una cuenta que se queda vieja sin avisar, así que se lee el
     * número de la lista en vez de ponerlo en el nombre.
     */
    @Test
    fun `todas las pantallas de las dos areas estan declaradas en el manifiesto`() {

        val declaradas =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
                .activities.orEmpty()
                .map { it.name }
                .toSet()

        val esperadas = listOf(
            MainActivity::class.java.name,
            ClientesActivity::class.java.name,
            ClienteFormularioActivity::class.java.name,
            RegistrosActivity::class.java.name,
            HistorialActivity::class.java.name,
            EstadisticasActivity::class.java.name,
            ConfiguracionActivity::class.java.name,
            PrestamosInicioActivity::class.java.name,
            PrestamosPrestamosActivity::class.java.name,
            PrestamosCobrosActivity::class.java.name,
            PrestamosPagosActivity::class.java.name,
            PrestamoPagoActivity::class.java.name,
        )

        esperadas.forEach { nombre ->
            assertTrue(
                "Falta declarar $nombre en el AndroidManifest",
                declaradas.contains(nombre)
            )
        }
    }
}