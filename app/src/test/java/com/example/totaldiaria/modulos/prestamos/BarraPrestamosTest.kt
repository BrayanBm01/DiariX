package com.example.totaldiaria.modulos.prestamos

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.R
import com.example.totaldiaria.core.modo.AlmacenDeModo
import com.example.totaldiaria.core.modo.MenuDeModo
import com.example.totaldiaria.core.modo.ModoDiarix
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
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * DIARIX · Fase 1 · La barra inferior de PRÉSTAMOS tiene sus cinco secciones.
 *
 * La Fase 0 dejó el área de préstamos con UNA pestaña y un comentario que decía
 * que las otras cuatro se añadían cuando existieran sus pantallas. Aquí están.
 * Lo que se comprueba es lo que, si se rompe, hace que el área deje de estar
 * separada de la otra mitad de la app o deje de ser usable:
 *
 *  1. El menú tiene CINCO pestañas y son las que pedía el área: Inicio,
 *     Clientes, Préstamos, Cobros y Pagos, en ese orden.
 *  2. CADA pestaña tiene una pantalla detrás. Una pestaña sin destino no
 *     crashea —se desmarca— pero no lleva a ninguna parte, y es la forma más
 *     difícil de detectar de que falta algo.
 *  3. El menú de Préstamos no ha cogido ninguna pestaña de Facturación, ni al
 *     revés. Es la comprobación de que las dos mitades siguen separadas.
 *  4. El menú de Facturación sigue siendo el de siempre: ni una pestaña menos
 *     ni una más. Agregar cuatro pestañas en un área es exactamente el cambio
 *     con más facilidad de arrastrar al otro.
 *  5. La barra se pinta en AZUL, que es el color del área, y el de
 *     Facturación sigue en verde. Es el requisito visual de que las dos mitades
 *     no se confundan.
 *  6. Cada pantalla marca SU pestaña, y no la primera.
 *  7. "Atrás" dentro del área no devuelve a Facturación.
 *  8. Configuración se sigue alcanzando desde las cinco pantallas.
 *
 * Las pantallas se abren de verdad con `Robolectric.setup()`, no se prueban los
 * mapas por reflexión: un mapa puede tener las cinco entradas y el menú
 * tener las cinco pestañas y aun así no encajar, porque lo que los une es el
 * `itemId`. Abrir la pantalla y mirar qué pestaña queda marcada es lo único
 * que comprueba ese enganche.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BarraPrestamosTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        limpiarModo()
        AlmacenDeModo(context).guardar(ModoDiarix.PRESTAMOS)
    }

    @After
    fun tearDown() {
        limpiarModo()
    }

    private fun limpiarModo() {
        context
            .getSharedPreferences(AlmacenDeModo.ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    /** Los ids de las pestañas de un menú, en el orden en que salen. */
    private fun itemsDelMenu(menuRes: Int): List<Int> {

        val nav = BottomNavigationView(
            ContextThemeWrapper(context, R.style.Theme_TotalDiaria)
        )

        nav.inflateMenu(menuRes)

        return (0 until nav.menu.size()).map { nav.menu.getItem(it).itemId }
    }

    private fun barraDe(activity: android.app.Activity): BottomNavigationView =
        activity.findViewById<BottomNavigationView>(R.id.bottomNav)
            ?: throw AssertionError("La pantalla ${activity.javaClass.simpleName} no tiene barra inferior")

    private fun abrir(clase: Class<out android.app.Activity>): ActivityController<*> =
        Robolectric.buildActivity(clase).setup()

    // ----------------------------------------------------------------
    // 1. Las cinco pestañas, y solo esas
    // ----------------------------------------------------------------

    @Test
    fun `el menu de prestamos tiene sus cinco secciones`() {

        assertEquals(
            listOf(
                R.id.nav_prestamos_inicio,
                R.id.nav_prestamos_clientes,
                R.id.nav_prestamos_prestamos,
                R.id.nav_prestamos_cobros,
                R.id.nav_prestamos_pagos,
            ),
            itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS))
        )
    }

    @Test
    fun `son cinco secciones y ni una mas`() {

        // El número va explícito porque `BottomNavigationView` de Material 3
        // está diseñado para un máximo de cinco: una sexta no falla, se dibuja
        // de otra manera (sin etiqueta, o con un menú deslizable), y el
        // resultado se descubre en un dispositivo.
        assertEquals(5, itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS)).size)
    }

    @Test
    fun `cada seccion se llama como se le pide`() {

        val nav = BottomNavigationView(
            ContextThemeWrapper(context, R.style.Theme_TotalDiaria)
        )

        nav.inflateMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS))

        val titulos = (0 until nav.menu.size())
            .map { nav.menu.getItem(it).title.toString() }

        assertEquals(
            listOf("Inicio", "Clientes", "Préstamos", "Cobros", "Pagos"),
            titulos
        )
    }

    // ----------------------------------------------------------------
    // 2. Cada pestaña tiene pantalla
    // ----------------------------------------------------------------

    /**
     * La prueba de que el menú y el mapa de destinos encajan.
     *
     * Se abre cada pantalla y se mira qué pestaña queda marcada. Si una
     * pantalla dijera `nav_prestamos_cobros` pero el mapa no la tuviera, la
     * barra se quedaría sin nada seleccionado y el toque no abriría nada: no
     * hay excepción, no hay aviso, y la pantalla parecería rota sin motivo
     * aparente.
     */
    @Test
    fun `cada pantalla del area marca su propia pestana`() {

        listOf(
            PrestamosInicioActivity::class.java to R.id.nav_prestamos_inicio,
            ClientesActivity::class.java to R.id.nav_prestamos_clientes,
            PrestamosPrestamosActivity::class.java to R.id.nav_prestamos_prestamos,
            PrestamosCobrosActivity::class.java to R.id.nav_prestamos_cobros,
            PrestamosPagosActivity::class.java to R.id.nav_prestamos_pagos,
        ).forEach { (clase, pestana) ->

            val controlador = abrir(clase)
            val activity = controlador.get()

            assertEquals(
                "La pantalla ${clase.simpleName} no marca su pestaña",
                pestana,
                barraDe(activity).selectedItemId
            )

            assertEquals(
                "La pantalla ${clase.simpleName} no muestra el menú del área",
                itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS)),
                itemsDelMenuDe(barraDe(activity))
            )

            controlador.close()
        }
    }

    /** Los ids que tiene inflados una barra concreta, sin volver a inflar el XML. */
    private fun itemsDelMenuDe(nav: BottomNavigationView): List<Int> =
        (0 until nav.menu.size()).map { nav.menu.getItem(it).itemId }

    @Test
    fun `tocar una seccion del menu abre su pantalla`() {

        val controlador = abrir(PrestamosInicioActivity::class.java)
        val activity = controlador.get()

        shadowOf(activity).clearNextStartedActivities()

        barraDe(activity).selectedItemId = R.id.nav_prestamos_cobros

        val destino = shadowOf(activity).nextStartedActivity

        assertNotNull("Tocar Cobros no abrió ninguna pantalla", destino)
        assertEquals(PrestamosCobrosActivity::class.java.name, destino.component?.className)

        controlador.close()
    }

    // ----------------------------------------------------------------
    // 3 y 4. Las dos mitades no se mezclan
    // ----------------------------------------------------------------

    @Test
    fun `el menu de prestamos no tiene ninguna pestana de facturacion`() {

        val dePrestamos = itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS))
        val deFacturacion = itemsDelMenu(MenuDeModo.de(ModoDiarix.FACTURACION))

        assertTrue(dePrestamos.isNotEmpty())
        assertTrue(
            "El menú de Préstamos no puede contener pestañas de Facturación",
            deFacturacion.none { it in dePrestamos }
        )
    }

    @Test
    fun `el menu de facturacion recupera sus cinco pestanas`() {

        // Esta es la comprobacion de no-rotura del cambio rápido: Facturación
        // volvió a las cinco pestañas de siempre (Inicio, Registros,
        // Estadísticas, Historial y Configuración) y Clientes ya no está en
        // su barra. Si mañana alguien añade o quita una pestaña sin
        // actualizarlo, o cambia el orden, falla aquí y no en un dispositivo.
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
    fun `mientras el area es prestamos la barra de todas sus pantallas es la de prestamos`() {

        // Y no la de facturación "por si acaso": si el área guardada se leyera
        // mal, cada pantalla se abriría con la barra del otro área y el mismo
        // título. Comprobarlo pantalla por pantalla es lo que hace que el fallo
        // aparezca en el nombre.
        listOf(
            PrestamosInicioActivity::class.java,
            ClientesActivity::class.java,
            PrestamosPrestamosActivity::class.java,
            PrestamosCobrosActivity::class.java,
            PrestamosPagosActivity::class.java,
        ).forEach { clase ->

            val controlador = abrir(clase)

            assertEquals(
                "La pantalla ${clase.simpleName} no lleva la barra de Préstamos",
                itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS)),
                itemsDelMenuDe(barraDe(controlador.get()))
            )

            controlador.close()
        }
    }

    // ----------------------------------------------------------------
    // 5. El color: azul en Préstamos, verde en Facturación
    // ----------------------------------------------------------------

    @Test
    fun `la barra de prestamos se pinta en azul y la de facturacion en verde`() {

        // El color de la pestaña marcada es lo único que dice, de un vistazo y
        // sin leer, en qué mitad de la app está alguien. Si las dos barras
        // tuvieran el mismo, las dos mitades serían visualmente la misma
        // pantalla con otros títulos.
        assertEquals(
            0xFF2B6ED4.toInt(),
            colorSeleccionado(R.color.menu_colors_prestamos)
        )

        assertEquals(
            0xFF0AA87A.toInt(),
            colorSeleccionado(R.color.menu_colors)
        )
    }

    @Test
    fun `la barra de cada pantalla usa el selector de su area`() {

        // Comprueba el ESTILO, no solo el recurso: un selector azul correcto al
        // que un layout no apunta sigue siendo un selector que no se ve, y eso
        // no lopillaría ninguna comprobación que mirara `res/color`.
        listOf(
            R.layout.activity_prestamos_inicio,
            R.layout.activity_prestamos_vacia,
        ).forEach { layout ->

            val inflado = android.view.LayoutInflater.from(
                ContextThemeWrapper(context, R.style.Theme_TotalDiaria)
            ).inflate(layout, null, false)

            val barra = inflado.findViewById<BottomNavigationView>(R.id.bottomNav)

            assertNotNull("El layout $layout no tiene barra inferior", barra)

            val tinte = barra.itemIconTintList

            assertNotNull(
                "La barra de $layout no tiene tinte de icono: heredaria el del tema",
                tinte
            )

            // Se pregunta por el color CON LA PESTAÑA MARCADA, no por
            // `defaultColor`. El `defaultColor` de un selector es el color de
            // su ÚLTIMO item —el que se aplica cuando no se sabe el estado—,
            // y en estos dos selectores ese es el gris. Preguntar por el
            // `defaultColor` daría el mismo gris en las dos mitades de la app y
            // la comprobación pasaría sin comprobar nada.
            assertEquals(
                "La barra de $layout no usa el azul de Prestamos al seleccionar",
                0xFF2B6ED4.toInt(),
                tinte!!.getColorForState(intArrayOf(android.R.attr.state_checked), 0)
            )

            // Y sin marcar tiene que ser el gris, no el azul: si coincidieran,
            // marcar una pestaña no cambiaría nada de lo que se ve.
            assertEquals(
                "La barra de $layout pinta igual marcada que sin marcar",
                0xFF8C8C8C.toInt(),
                tinte.getColorForState(IntArray(0), 0)
            )
        }
    }

    @Test
    fun `las pestanas sin seleccionar de prestamos son el mismo gris que en facturacion`() {

        // El gris NO es del área. Si también fuera azul, el área entera sería
        // azul y la barra dejaría de distinguir "elegida" de "no elegida", que
        // es justo lo que un menú inferior tiene que decir.
        assertEquals(
            colorSinSeleccionar(R.color.menu_colors),
            colorSinSeleccionar(R.color.menu_colors_prestamos)
        )

        // Y el gris no es el azul: si coincidieran, marcar una pestaña no
        // cambiaría nada de lo que se ve.
        assertFalse(
            colorSinSeleccionar(R.color.menu_colors_prestamos) ==
                colorSeleccionado(R.color.menu_colors_prestamos)
        )
    }

    /** El color del selector cuando la pestaña está marcada. */
    private fun colorSeleccionado(colorRes: Int): Int =
        colorEn(colorRes, intArrayOf(android.R.attr.state_checked))

    /** El color del selector cuando la pestaña NO está marcada. */
    private fun colorSinSeleccionar(colorRes: Int): Int =
        colorEn(colorRes, IntArray(0))

    /**
     * El color de un selector de color en un estado dado.
     *
     * Se leen los estados con `getColorForState` y no el color por defecto del
     * recurso, porque lo que hay que comprobar es qué se ve con la pestaña
     * marcada. El valor por defecto solo diría qué color se usa cuando no se
     * sabe el estado.
     */
    private fun colorEn(colorRes: Int, estados: IntArray): Int =
        requireNotNull(
            context.resources.getColorStateList(colorRes, context.theme)
        ) { "El recurso de color $colorRes no existe o no es un selector" }
            .getColorForState(estados, 0)

    // ----------------------------------------------------------------
    // 7. Atrás no devuelve a Facturación
    // ----------------------------------------------------------------

    /**
     * Moverse dentro del área no abre NINGUNA pantalla de Facturación.
     *
     * La forma de comprobarlo es mirar las activities que se han iniciado, y no
     * el color de la barra: lo que importa es que volver de Préstamos a
     * Facturación no sea un camino que exista. Si apareciera una, el botón
     * "atrás" de Préstamos dejaría de ser "salir de Préstamos" y pasaría a ser
     * un cambio de área sin pasar por Configuración, que es justo lo que la
     * Fase 0.1 quitó de todas partes.
     */
    @Test
    fun `moverse por el area nunca abre una pantalla de facturacion`() {

        val controlador = abrir(PrestamosInicioActivity::class.java)
        val activity = controlador.get()

        shadowOf(activity).clearNextStartedActivities()

        // Se tocan las CUATRO secciones que no son la de esta pantalla. La
        // quinta es Inicio, que es justo la pestaña en la que ya se está, y
        // tocarla no abre nada: `BottomNavigator` consume el toque de la
        // pestaña actual para no reconstruir la pantalla en la que ya se está.
        // Es el comportamiento correcto y lo comprueba el test de abajo.
        val otras = listOf(
            R.id.nav_prestamos_clientes,
            R.id.nav_prestamos_prestamos,
            R.id.nav_prestamos_cobros,
            R.id.nav_prestamos_pagos,
        )

        otras.forEach { barraDe(activity).selectedItemId = it }

        // Robolectric saca las activities iniciadas de una en una y en orden,
        // así que se vacía la cola con un tope. El tope es el número de
        // pestañas que se han tocado: más que eso y se estaría leyendo algo
        // que no hizo este test.
        val abiertas = (0 until otras.size)
            .mapNotNull { shadowOf(activity).nextStartedActivity }

        assertEquals(
            "Cada sección del área tiene que abrir su pantalla",
            otras.size,
            abiertas.size
        )

        val clasesDeFacturacion = listOf(
            MainActivity::class.java.name,
            RegistrosActivity::class.java.name,
            HistorialActivity::class.java.name,
            EstadisticasActivity::class.java.name,
            ConfiguracionActivity::class.java.name,
        )

        val deFacturacion = abiertas.filter { intent ->
            intent.component?.className in clasesDeFacturacion
        }

        assertTrue(
            "Navegar dentro de Prestamos abrio pantallas de Facturacion: " +
                deFacturacion.map { it.component?.className },
            deFacturacion.isEmpty()
        )

        controlador.close()
    }

    /**
     * Tocar la pestaña en la que ya se está NO abre la pantalla de nuevo.
     *
     * Sin esto, volver a Inicio desde Inicio dejaría una segunda copia en la
     * pila, y "atrás" sacaría al usuario a la misma pantalla una y otra vez.
     * Es también el motivo por el que la prueba anterior toca cuatro pestañas y
     * no cinco.
     */
    @Test
    fun `tocar la pestana en la que ya se esta no vuelve a abrirla`() {

        val controlador = abrir(ClientesActivity::class.java)
        val activity = controlador.get()

        shadowOf(activity).clearNextStartedActivities()

        // La pestaña marcada es la de Clientes.
        assertEquals(R.id.nav_prestamos_clientes, barraDe(activity).selectedItemId)

        barraDe(activity).selectedItemId = R.id.nav_prestamos_clientes

        assertNull(
            "Tocar la pestaña actual abrió otra copia de la pantalla",
            shadowOf(activity).nextStartedActivity
        )

        controlador.close()
    }

    @Test
    fun `volver de una seccion del area vuelve al inicio del area y no a facturacion`() {

        // El `REORDER_TO_FRONT` de `BottomNavigator` hace que volver atrás desde
        // una sección devuelva a la anterior, y la de Inicio es la primera del
        // área. Lo que se comprueba es que la pantalla desde la que se sale sea
        // una del área: el orden interno de la pila lo pone el sistema y no es
        // lo que esta fase controla.
        val controlador = abrir(PrestamosPagosActivity::class.java)
        val activity = controlador.get()

        shadowOf(activity).clearNextStartedActivities()

        barraDe(activity).selectedItemId = R.id.nav_prestamos_inicio

        val destino = shadowOf(activity).nextStartedActivity

        assertNotNull(destino)
        assertEquals(PrestamosInicioActivity::class.java.name, destino.component?.className)

        // `CLEAR_TOP | SINGLE_TOP` en el inicio del área: la pila no crece con
        // cada ida y vuelta entre secciones, que es lo que hace que "atrás" sea
        // predecible.
        assertTrue(
            "El inicio del área debe reaprovechar su instancia, no apilar otra",
            destino.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0
        )

        controlador.close()
    }

    // ----------------------------------------------------------------
    // 8. Configuración se alcanza desde las cinco
    // ----------------------------------------------------------------

    @Test
    fun `configuracion es alcanzable desde las cinco pantallas del area`() {

        listOf(
            PrestamosInicioActivity::class.java,
            ClientesActivity::class.java,
            PrestamosPrestamosActivity::class.java,
            PrestamosCobrosActivity::class.java,
            PrestamosPagosActivity::class.java,
        ).forEach { clase ->

            val controlador = abrir(clase)
            val activity = controlador.get()

            val engranaje = activity.findViewById<View>(R.id.btnAbrirConfiguracion)

            assertNotNull(
                "La pantalla ${clase.simpleName} no tiene el acceso a Configuración",
                engranaje
            )

            shadowOf(activity).clearNextStartedActivities()

            engranaje.performClick()

            val destino = shadowOf(activity).nextStartedActivity

            assertNotNull(
                "El engranaje de ${clase.simpleName} no abrió nada",
                destino
            )

            assertEquals(
                "El engranaje de ${clase.simpleName} abrió otra cosa",
                ConfiguracionActivity::class.java.name,
                destino.component?.className
            )

            controlador.close()
        }
    }

    @Test
    fun `ninguna pestana del area es un cambio de area disfrazado`() {

        // El menú de Préstamos abre cinco pantallas DEL ÁREA. Si una de ellas
        // fuera Configuración o la de Facturación, el área tendría dentro su
        // propio camino para cambiar de área, y ese camino no lo vigila nadie.
        assertFalse(
            itemsDelMenu(MenuDeModo.de(ModoDiarix.PRESTAMOS))
                .contains(R.id.nav_configuracion)
        )
    }
}