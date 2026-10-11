package com.example.totaldiaria.modulos.prestamos.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.core.navegacion.BottomNavigator
import com.example.totaldiaria.modulos.facturacion.ui.ConfiguracionActivity

/**
 * DIARIX · Fase 1 · Pantalla base de una sección del área de PRÉSTAMOS.
 *
 * Las secciones —Préstamos, Cobros y Pagos— son la MISMA pantalla con otros
 * textos y otro icono, y por eso hay una clase base y tres subclases de tres
 * líneas en vez de clases copiadas. Clientes fue la primera en dejar de estar
 * vacía y por eso tiene su propia pantalla (`ClientesActivity`, que no depende
 * de esta base): cuando una sección tiene contenido deja la base y se lleva
 * su pantalla, sin tocar a las demás.
 *
 * LO QUE ESTA CLASE NO HACE, Y POR QUÉ:
 *
 *  - **No pide datos.** No hay repositorio de préstamos porque el módulo no
 *    está construido, y llamar a un endpoint que no existe no produce una
 *    pantalla vacía: produce un error que nadie sabe explicar. El día que
 *    exista, la carga va en la subclase y no aquí, porque una base que sabe
 *    cargar los datos de una sección que todavía no existe es el sitio
 *    equivocado para aprender cómo se cargan.
 *  - **No inventa datos.** Ni una fila de ejemplo. Un nombre y un monto
 *    ficticios en una pantalla de producción se terminan enseñando a alguien.
 *  - **No lleva al cambio de área.** El engranaje abre Configuración, que es
 *    la única pantalla desde la que se cambia de área en toda la app.
 *
 * Lo que sí hace, que es lo que la fase pide: pintar el menú del área con sus
 * cinco pestañas, marcar la suya y tener una salida hacia Configuración.
 */
abstract class PantallaDePrestamos : AppCompatActivity() {

    /**
     * Título de la sección, el que va grande arriba.
     *
     * Va aparte del título de la pestaña porque no tienen por qué coincidir: el
     * de la barra es corto porque tiene que caber junto a cuatro iconos, y el
     * de aquí tiene sitio de sobra.
     */
    @get:StringRes
    protected abstract val tituloSeccion: Int

    /** La pestaña de esta pantalla dentro de `menu_prestamos`. */
    @get:MenuRes
    protected abstract val idPestana: Int

    /** El icono grande del estado vacío. */
    @get:DrawableRes
    protected abstract val iconoSeccion: Int

    @get:StringRes
    protected abstract val tituloEstadoVacio: Int

    @get:StringRes
    protected abstract val detalleEstadoVacio: Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Diarix.iniciar(this)

        setContentView(R.layout.activity_prestamos_vacia)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        pintarSeccion()

        findViewById<View>(R.id.btnAbrirConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        // El menú del ÁREA, no el de la pantalla: las cinco pestañas, y la de
        // esta sección ya marcada.
        BottomNavigator.configurar(this, idPestana)
    }

    /**
     * `onResume` y no `onStart`, y no por gusto.
     *
     * Vuelve a marcar la pestaña porque se puede volver desde Configuración,
     * desde otra sección o desde atrás, y en todos esos casos la barra se ha
     * vuelto a pintar desde cero con [BottomNavigator.configurar] en una
     * pantalla que ya tenía otra pestaña seleccionada.
     */
    override fun onResume() {
        super.onResume()

        BottomNavigator.sincronizarPestana(this, idPestana)
    }

    private fun pintarSeccion() {

        findViewById<TextView>(R.id.txtTituloSeccion).setText(tituloSeccion)

        findViewById<TextView>(R.id.txtVacioTitulo).setText(tituloEstadoVacio)

        findViewById<TextView>(R.id.txtVacioDetalle).setText(detalleEstadoVacio)

        findViewById<ImageView>(R.id.icSeccion).setImageResource(iconoSeccion)
    }
}