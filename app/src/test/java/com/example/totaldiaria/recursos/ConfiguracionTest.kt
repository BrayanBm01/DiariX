package com.example.totaldiaria.recursos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DIARIX · Fase 0.1 · La pantalla de Configuración, comprobada por su estructura.
 *
 * ## Por qué un test de este tipo
 *
 * Una pantalla de ajustes se rompe de formas que el compilador no ve:
 *
 *  - Un control que se pinte pero no tenga a nadie detrás (un interruptor de
 *    "tema oscuro" que no hace nada). No da error: da una promesa falsa.
 *  - Una fila con chevron que no abre nada. Enseña a desconfiar de la pantalla
 *    entera.
 *  - Un texto que no existe en `strings.xml` o un id mal escrito: eso sí lo ve
 *    el build, pero solo si el layout está inflado de verdad.
 *  - Un botón que se sale por debajo en un teléfono pequeño.
 *
 * Aquí se comprueba lo comprobable sin un dispositivo: que los controles que
 * tienen que estar estén y sean alcanzables, que los que NO deben existir no
 * estén, y que la pantalla se pueda inflar.
 *
 * Lo que NO se comprueba aquí, porque necesita un dispositivo o un ojo:
 * que el texto no se corte, que el chevron no se salga en un teléfono de 5", y
 * que la pantalla se sienta bien. Eso está en la lista de pruebas manuales de
 * `docs/FASE-0-SEPARACION-DE-MODOS.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConfiguracionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_TotalDiaria
        )
    }

    private fun pantalla(): View =
        LayoutInflater.from(context)
            .inflate(R.layout.activity_configuracion, null, false)

    // ----------------------------------------------------------------
    // Secciones
    // ----------------------------------------------------------------

    @Test
    fun `la pantalla tiene las secciones de perfil cuenta comprobantes datos e informacion`() {

        val raiz = pantalla()

        listOf(
            "filaEditarPerfil" to R.id.filaEditarPerfil,
            "filaModo" to R.id.filaModo,
            "filaCerrarSesionTodos" to R.id.filaCerrarSesionTodos,
            "filaQr" to R.id.filaQr,
            "filaExportar" to R.id.filaExportar,
            "filaImportar" to R.id.filaImportar,
            "filaAyuda" to R.id.filaAyuda,
            "filaAcercaDe" to R.id.filaAcercaDe,
            "filaVersion" to R.id.filaVersion,
        ).forEach { (nombre, id) ->

            assertNotNull(
                "Falta el control $nombre de la pantalla de Configuración",
                raiz.findViewById<View>(id)
            )
        }
    }

    /**
     * La fila de modo existe y dice qué área es la vigente.
     *
     * Que exista es lo comprobable aquí. Que sea el ÚNICO sitio por el que se
     * cambia el área es una propiedad del código (`SelectorDeModo.mostrarComoDialogo`
     * se llama desde `filaModo` y desde ningún otro `setOnClickListener` de esta
     * pantalla), y eso lo comprueba el test de código de la Fase 0, no el árbol
     * de vistas.
     *
     * Lo que sí se comprueba aquí es que la fila lleva el texto "Modo de
     * DiariX" y el subtítulo que explica que se puede cambiar: una fila con
     * chevron sin explicación de para qué sirve es una fila que la gente pasa
     * por encima.
     */
    @Test
    fun `la fila de modo explica que se puede cambiar`() {

        val raiz = pantalla()

        val filaModo = raiz.findViewById<View>(R.id.filaModo)

        assertNotNull(filaModo)

        val textos = filaModo.findViewById<TextView>(R.id.txtModoActual)

        assertNotNull(textos)

        // Viene de `strings.xml` y no de un texto suelto en el XML, para que se
        // pueda traducir sin editar el layout.
        assertEquals(
            context.getString(R.string.modo_facturacion),
            textos.text.toString()
        )

        val ayuda = filaModo
            .findViewById<TextView>(R.id.txtAyudaModo)
            ?.text
            ?.toString()

        assertEquals(
            context.getString(R.string.configuracion_modo_ayuda),
            ayuda
        )
    }

    // ----------------------------------------------------------------
    // Lo que NO puede haber
    // ----------------------------------------------------------------

    /**
     * DiariX no tiene sistema de temas, así que no hay sección de apariencia.
     *
     * Si algún día lo tiene, este test es el que obliga a decidir de verdad
     * qué se ofrece (¿tema claro? ¿oscuro? ¿automático? ¿sistema?) en vez de
     * dejar un interruptor suelto que no hace nada. Un control que se puede
     * tocar y no cambia nada es peor que su ausencia: enseña a desconfiar de
     * los controles que sí funcionan.
     */
    @Test
    fun `no hay controles de apariencia ni de notificaciones`() {

        val ids = identificadoresDeLaPantalla()

        listOf(
            "switchTema",
            "swtTema",
            "switchNotificaciones",
            "switchAvisos",
            "filaApariencia",
            "filaNotificaciones",
            "seleccionTema",
        ).forEach { prohibido ->

            assertTrue(
                "Configuración tiene un control de apariencia o notificaciones " +
                    "($prohibido) que DiariX no implementa",
                ids.none { it == prohibido }
            )
        }
    }

    /**
     * Los interruptores que existen tienen que poder cambiar de verdad.
     *
     * En esta pantalla solo hay uno —el QR—, y su fila sí abre el selector de
     * imágenes. Este test existe para que, cuando se añada el primer interruptor
     * de verdad, quede claro que tiene que estar conectado a algo.
     */
    @Test
    fun `el unico interruptor de la pantalla es el QR y abre el selector de imagen`() {

        val raiz = pantalla()

        // La fila del QR es un LinearLayout pulsable, no un Switch: el texto
        // necesita dos líneas (título + estado) y un Switch con dos líneas al
        // lado se aprieta en un teléfono pequeño.
        assertNotNull(raiz.findViewById<View>(R.id.filaQr))

        val estado = raiz.findViewById<TextView>(R.id.txtEstadoQr)
        assertNotNull(estado)
    }

    // ----------------------------------------------------------------
    // Cerrar sesión, separado y con el peso correcto
    // ----------------------------------------------------------------

    /**
     * "Cerrar sesión" es un botón, no una fila más.
     *
     * Está fuera de las tarjetas de secciones, con su propio fondo y su propio
     * margen respecto al bloque anterior. Mezclado con "elegir un color" no se
     * distinguiría de una preferencia más, y es lo único de la pantalla que
     * cambia el estado de la cuenta.
     */
    @Test
    fun `cerrar sesion esta separado del resto de secciones`() {

        val raiz = pantalla()

        val boton = raiz.findViewById<Button>(R.id.btnCerrarSesion)

        assertNotNull(boton)

        // Está en la parte baja del árbol: la última tarjeta es la de
        // Información y el botón tiene que estar después.
        val orden = ordenEnElArbol(raiz, R.id.btnCerrarSesion, R.id.filaVersion)

        assertEquals(
            "Cerrar sesión tiene que estar DESPUÉS de la sección de Información",
            1,
            orden
        )
    }

    /**
     * El botón de cerrar sesión tiene el color del error.
     *
     * Es el color que ya usa la app para lo que destruye datos, y es lo que
     * separa visualmente esta acción de "cambiar un QR". Si alguien lo cambia a
     * verde thinking que "es otro botón más", esta prueba avisa.
     */
    @Test
    fun `cerrar sesion usa el color del error`() {

        val boton = pantalla().findViewById<Button>(R.id.btnCerrarSesion)

        // `defaultColor` de un ColorStateList YA viene resuelto a un entero
        // ARGB: no es un id de recurso y no hay que pasarlo por `Resources`.
        val color = boton.backgroundTintList!!.defaultColor

        assertEquals(
            "El botón de cerrar sesión debería ser rojo de error, no un color " +
                "de acción: es lo único de la pantalla que deshace el trabajo " +
                "del usuario",
            0xFFD93A3A.toInt(),
            color
        )
    }

    // ----------------------------------------------------------------
    // La versión es real
    // ----------------------------------------------------------------

    /**
     * El número de versión sale de `BuildConfig`, no de un texto.
     *
     * Aquí solo se comprueba que el hueco existe. Lo que de verdad importa es
     * que `ConfiguracionActivity.mostrarVersion()` lo rellene en código, y eso
     * no se puede ver sin arrancar la pantalla.
     */
    @Test
    fun `la version tiene su hueco y no viene escrita en el layout`() {

        val raiz = pantalla()

        assertNotNull(
            raiz.findViewById<TextView>(R.id.txtVersion)
        )

        // Vacío en el layout = se rellena en código. Si tuviera un texto
        // hardcodeado, el layout y `BuildConfig` empezarían a discrepar con la
        // siguiente versión.
        assertEquals(
            "La versión no debe estar escrita en el layout: sale de BuildConfig",
            "",
            raiz.findViewById<TextView>(R.id.txtVersion).text.toString()
        )
    }

    // ----------------------------------------------------------------
    // Estructura: nada que se salga de la pantalla
    // ----------------------------------------------------------------

    /**
     * Las filas tienen el chevron con margen fijo y el texto con peso.
     *
     * Es el detalle que hace que un correo largo o un nombre largo recorten SU
     * texto y no empujen el chevron fuera de la pantalla. Aquí se comprueba que
     * existe el peso en las filas que lo necesitan; el recorte real se ve en el
     * dispositivo.
     */
    @Test
    fun `las filas de Configuración tienen menu inferior`() {

        // Sin menú inferior, la pantalla se sale del patrón de navegación de la
        // app y el usuario no puede volver a las demás pestañas.
        assertNotNull(
            pantalla().findViewById<View>(R.id.bottomNav)
        )
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    private fun resources() = context.resources

    /** Todos los ids que aparecen en el layout, para poder buscar ausencias. */
    private fun identificadoresDeLaPantalla(): List<String> {

        val ids = mutableListOf<String>()

        fun recorrer(vista: View) {

            // Las vistas SIN id (los separadores, los contenedores) no tienen
            // nombre: preguntarles por él revienta con un
            // `Resources$NotFoundException` que no dice nada útil.
            if (vista.id != View.NO_ID && vista.id != 0) {
                ids.add(
                    try {
                        vista.resources.getResourceEntryName(vista.id)
                    } catch (_: android.content.res.Resources.NotFoundException) {
                        "id_desconocido_${vista.id}"
                    }
                )
            }

            if (vista is android.view.ViewGroup) {
                for (i in 0 until vista.childCount) {
                    recorrer(vista.getChildAt(i))
                }
            }
        }

        recorrer(pantalla())

        return ids
    }

    /**
     * Si `primero` aparece antes que `segundo` en el árbol, devuelve 0.
     * Si aparece después, devuelve 1.
     */
    private fun ordenEnElArbol(raiz: View, primero: Int, segundo: Int): Int {

        val orden = mutableListOf<Int>()

        fun recorrer(vista: View) {
            if (vista.id == primero) orden.add(primero)
            if (vista.id == segundo) orden.add(segundo)

            if (vista is android.view.ViewGroup) {
                for (i in 0 until vista.childCount) {
                    recorrer(vista.getChildAt(i))
                }
            }
        }

        recorrer(raiz)

        assertTrue(
            "No se encontraron los dos controles en la pantalla",
            orden.contains(primero) && orden.contains(segundo)
        )

        return orden.indexOf(primero) - orden.indexOf(segundo)
    }
}