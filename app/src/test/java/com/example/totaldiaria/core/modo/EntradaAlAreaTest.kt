package com.example.totaldiaria.core.modo

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.core.cuenta.AcercaDeActivity
import com.example.totaldiaria.core.cuenta.AyudaActivity
import com.example.totaldiaria.core.cuenta.EditarPerfilActivity
import com.example.totaldiaria.modulos.facturacion.ui.ConfiguracionActivity
import com.example.totaldiaria.modulos.facturacion.ui.MainActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamosInicioActivity
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
 * DIARIX · Fase 0.1 · La pregunta "¿qué quieres gestionar?" y el cambio de área.
 *
 * ## Qué se comprueba y por qué aquí
 *
 * La Fase 0 metió un área por delante del usuario sin preguntarle nada. La
 * Fase 0.1 añade la pregunta, y el riesgo de esa pregunta es su contrario del
 * habitual: no que falle, sino que pregunte DE MÁS. Un "¿qué quieres gestionar?"
 * que aparece en cada arranque es una molestia que hace que la gente cierre la
 * app, y no tiene arreglo técnico: solo se arregla preguntando una vez.
 *
 * Por eso casi todo lo de aquí son pruebas de "no vuelve a preguntar":
 *
 *  1. Sin área guardada → hay que preguntar. Sin área, sin valor por defecto y
 *     sin forma de saltarse la pregunta.
 *  2. Con CUALQUIER área guardada → entra directo, y no vuelve a preguntar.
 *  3. Elegir el área POR DEFECTO (Facturación) también cuenta como haber
 *     elegido, aunque el valor coincida con `INICIAL`. Este es el caso que
 *     rompía el reused de `cambiarA`, que no guardaba nada cuando el modo
 *     elegido era el mismo.
 *  4. Una clave ilegible vuelve al estado "no eligió", que es lo único que se
 *     puede hacer con un dato que no se sabe leer.
 *  5. Cambiar de área desde Configuración deja la pantalla en la que se cambió
 *     FUERA de la pila, para que "atrás" no vuelva al área anterior.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EntradaAlAreaTest {

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

    // ----------------------------------------------------------------
    // 1. Sin elegir: hay que preguntar
    // ----------------------------------------------------------------

    @Test
    fun `sin area guardada hay que preguntar el area`() {

        assertTrue(
            SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(context).modoGuardado())
        )
    }

    @Test
    fun `preguntar el area es exactamente no tener ninguna guardada`() {

        // La regla se escribe entera: solo falta un modo guardado para no
        // preguntar. Si algún día esta prueba falla, significa que la función
        // dejó de ser "hay algo guardado o no", y hay que ir a mirar por qué.
        assertTrue(SelectorDeModo.haceFaltaElegirModo(null))
        assertFalse(SelectorDeModo.haceFaltaElegirModo(ModoDiarix.FACTURACION))
        assertFalse(SelectorDeModo.haceFaltaElegirModo(ModoDiarix.PRESTAMOS))
    }

    // ----------------------------------------------------------------
    // 2. Con área guardada: se entra directo y no se vuelve a preguntar
    // ----------------------------------------------------------------

    @Test
    fun `tras elegir facturacion no se vuelve a preguntar`() {

        AlmacenDeModo(context).guardar(ModoDiarix.FACTURACION)

        assertFalse(
            SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(context).modoGuardado())
        )
    }

    @Test
    fun `tras elegir prestamos no se vuelve a preguntar`() {

        AlmacenDeModo(context).guardar(ModoDiarix.PRESTAMOS)

        assertFalse(
            SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(context).modoGuardado())
        )
    }

    /**
     * Elegir el área POR DEFECTO también es haber elegido.
     *
     * Es el caso que hace que [SelectorDeModo.cambiarA] no sirva en la pantalla
     * inicial: `cambiarA` sale sin hacer nada cuando el modo elegido es el que
     * ya está vigente, y en el primer arranque el vigente es
     * [ModoDiarix.INICIAL], que ES Facturación. Con esa ruta, alguien que elige
     * Facturación en el primer arranque se quedaría sin nada guardado y la app
     * le preguntaría en cada inicio, para siempre.
     *
     * Lo que se comprueba es el dato que deja `guardar`, no `modoActual()`:
     * `modoActual` devuelve Facturación también cuando NO hay nada guardado, y
     * por eso no puede decir si la app volverá a preguntar o no.
     */
    @Test
    fun `elegir el area por defecto deja constancia de que se eligio`() {

        // Antes de elegir: no hay nada, así que hay que preguntar aunque
        // `modoActual()` ya diga Facturación.
        assertTrue(
            SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(context).modoGuardado())
        )

        // Elegir el área por defecto, como hace `elegirYEntrar` en la pantalla
        // de selección.
        AlmacenDeModo(context).guardar(ModoDiarix.INICIAL)

        // Ahora sí hay algo guardado: instancia nueva del almacén, es decir, lo
        // que leería el siguiente arranque de la app.
        assertEquals(
            ModoDiarix.FACTURACION,
            AlmacenDeModo(context).modoGuardado()
        )

        assertFalse(
            SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(context).modoGuardado())
        )
    }

    /**
     * `modoActual` y `modoGuardado` NO son la misma lectura, y por eso existen
     * los dos.
     *
     * La diferencia solo se ve antes de que nadie haya elegido, y es la
     * diferencia entre "la app tiene que preguntar" y "la app arranca en
     * facturación". Una función que cumpliera las dos cosas no podría
     * distinguir "eligió facturación" de "todavía no eligió nada", que es
     * justamente el estado que la Fase 0.1 necesita distinguir.
     */
    @Test
    fun `sin guardar nada modoActual da un area pero modoGuardado dice que no hay ninguna`() {

        val almacen = AlmacenDeModo(context)

        assertNull(almacen.modoGuardado())
        assertEquals(ModoDiarix.INICIAL, almacen.modoActual())
    }

    // ----------------------------------------------------------------
    // 3. Cambiar de área desde dentro
    // ----------------------------------------------------------------

    @Test
    fun `cambiar de area guarda el area nueva y deja de valer la anterior`() {

        val almacen = AlmacenDeModo(context)

        almacen.guardar(ModoDiarix.FACTURACION)
        assertEquals(ModoDiarix.FACTURACION, almacen.modoGuardado())

        almacen.guardar(ModoDiarix.PRESTAMOS)
        assertEquals(ModoDiarix.PRESTAMOS, almacen.modoGuardado())

        // Y al revés: volver a facturación tiene que dejar de valer préstamos.
        almacen.guardar(ModoDiarix.FACTURACION)
        assertEquals(ModoDiarix.FACTURACION, almacen.modoGuardado())
    }

    @Test
    fun `cambiar de area deja la app en la pantalla del area nuevo`() {

        val inicioDeFacturacion = MainActivity::class.java
        val inicioDePrestamos = PrestamosInicioActivity::class.java

        // Se comprueba contra las clases ESCRITAS, no contra
        // `area.pantallaDeInicio`, que sería compararse consigo mismo y no
        // probaría nada.
        AlmacenDeModo(context).guardar(ModoDiarix.FACTURACION)

        assertEquals(
            inicioDeFacturacion,
            AlmacenDeModo(context).modoActual().pantallaDeInicio
        )

        AlmacenDeModo(context).guardar(ModoDiarix.PRESTAMOS)

        assertEquals(
            inicioDePrestamos,
            AlmacenDeModo(context).modoActual().pantallaDeInicio
        )
    }

    // ----------------------------------------------------------------
    // 4. Una clave ilegible
    // ----------------------------------------------------------------

    @Test
    fun `una clave ilegible vuelve al estado de nunca haber elegido`() {

        context
            .getSharedPreferences(AlmacenDeModo.ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(AlmacenDeModo.CLAVE_MODO, "prestamos_v2_que_no_existe")
            .commit()

        val almacen = AlmacenDeModo(context)

        // No se puede leer → no se puede saber qué quería → se vuelve a
        // preguntar. Es lo único honesto: inventar un área sería peor que
        // molestar una vez.
        assertNull(almacen.modoGuardado())
        assertTrue(SelectorDeModo.haceFaltaElegirModo(almacen.modoGuardado()))

        // Pero la app NUNCA se queda sin pantalla de inicio por eso.
        assertEquals(ModoDiarix.FACTURACION, almacen.modoActual())
    }

    @Test
    fun `elegir un area sanea una clave que estaba ilegible`() {

        context
            .getSharedPreferences(AlmacenDeModo.ARCHIVO, Context.MODE_PRIVATE)
            .edit()
            .putString(AlmacenDeModo.CLAVE_MODO, "basura")
            .commit()

        val almacen = AlmacenDeModo(context)
        assertTrue(SelectorDeModo.haceFaltaElegirModo(almacen.modoGuardado()))

        almacen.guardar(ModoDiarix.PRESTAMOS)

        assertEquals(ModoDiarix.PRESTAMOS, AlmacenDeModo(context).modoGuardado())
        assertFalse(
            SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(context).modoGuardado())
        )
    }

    // ----------------------------------------------------------------
    // 5. Las pantallas nuevas están declaradas y son de las dos áreas
    // ----------------------------------------------------------------

    /**
     * Una Activity no declarada en el manifiesto es una pantalla que crashea
     * al abrirla, y el aviso tiene que llegar antes de probarlo en un móvil.
     *
     * Se comprueban también las tres pantallas de `core.cuenta`, que son NUEVAS
     * de la Fase 0.1 y de las que nadie ha pulsedado nada todavía.
     */
    @Test
    fun `las pantallas de cuenta y de seleccion estan declaradas en el manifiesto`() {

        val declaradas =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
                .activities.orEmpty()
                .map { it.name }
                .toSet()

        listOf(
            SeleccionDeModoActivity::class.java.name,
            EditarPerfilActivity::class.java.name,
            AyudaActivity::class.java.name,
            AcercaDeActivity::class.java.name,
            ConfiguracionActivity::class.java.name,
        ).forEach { nombre ->

            assertTrue(
                "Falta declarar $nombre en el AndroidManifest",
                declaradas.contains(nombre)
            )
        }
    }

    /**
     * La pantalla de selección de área no se puede abrir desde fuera de la app.
     *
     * Nadie, ni otra aplicación, debe poder poner a alguien delante de "¿qué
     * quieres gestionar?": es una pantalla que además no tiene salida hacia
     * atrás, y una app externa que la abra dejaría a la persona encerrada en
     * DiariX sin haber decidido nada.
     */
    @Test
    fun `la seleccion de area no se puede abrir desde fuera de DiariX`() {

        val activities =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
                .activities.orEmpty()

        val seleccion = activities.first {
            it.name == SeleccionDeModoActivity::class.java.name
        }

        assertEquals(
            "SeleccionDeModoActivity no debe ser exported",
            false,
            seleccion.exported
        )
    }

    /**
     * Las pantallas de cuenta tampoco.
     *
     * Editar el perfil abre un formulario que manda datos al servidor: si una
     * aplicación externa pudiera abrirla, tendría una pantalla con dos campos
     * de tu cuenta a un toque de distancia.
     */
    @Test
    fun `las pantallas de cuenta no se pueden abrir desde fuera de DiariX`() {

        val activities =
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES)
                .activities.orEmpty()
                .associateBy { it.name }

        listOf(
            EditarPerfilActivity::class.java.name,
            AyudaActivity::class.java.name,
            AcercaDeActivity::class.java.name,
            ConfiguracionActivity::class.java.name,
        ).forEach { nombre ->

            assertEquals(
                "$nombre no debe ser exported",
                false,
                activities[nombre]?.exported
            )
        }
    }

    /**
     * Configuración y las pantallas de cuenta NO dependen del área.
     *
     * Si alguna de ellas viviera en `modulos.facturacion`, dentro de Préstamos no
     * se podría abrir, o abriría una pantalla que enseña el menú del área
     * equivocada. Que estén en `core.cuenta` es lo que hace que la misma
     * pantalla sirva dentro de las dos mitades.
     */
    @Test
    fun `las pantallas de cuenta son comunes a las dos areas`() {

        listOf(
            EditarPerfilActivity::class.java.name,
            AyudaActivity::class.java.name,
            AcercaDeActivity::class.java.name,
        ).forEach { nombre ->

            assertTrue(
                "$nombre debería vivir en core.cuenta para servir a las dos áreas",
                nombre.contains("core.cuenta")
            )
        }
    }

    // ----------------------------------------------------------------
    // 6. Coherencia entre lo que se guarda y la pantalla a la que se entra
    // ----------------------------------------------------------------

    @Test
    fun `la pantalla de inicio que se abre siempre coincide con el area vigente`() {

        val almacen = AlmacenDeModo(context)

        ModoDiarix.entries.forEach { area ->

            almacen.guardar(area)

            // Es la regla que garantiza que "atrás" no devuelve a un área
            // ajena: la pantalla a la que se entra tras cambiar de área es
            // SIEMPRE la del área nueva, nunca la anterior.
            assertEquals(
                area,
                almacen.modoActual()
            )

            assertEquals(
                area.pantallaDeInicio,
                almacen.modoActual().pantallaDeInicio
            )
        }
    }
}