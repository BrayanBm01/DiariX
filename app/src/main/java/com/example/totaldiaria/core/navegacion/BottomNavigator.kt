package com.example.totaldiaria.core.navegacion

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.totaldiaria.R
import com.example.totaldiaria.core.modo.MenuDeModo
import com.example.totaldiaria.core.modo.ModoDiarix
import com.example.totaldiaria.core.modo.modoVigente
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

/**
 * Punto único de navegación del menú inferior.
 *
 * Reglas (las de siempre, sin cambios):
 *  - Tocar la pestaña actual no hace nada (se consume el evento).
 *  - Ir a Inicio reutiliza la instancia existente (CLEAR_TOP | SINGLE_TOP)
 *    para conservar su estado y evitar Mains apilados.
 *  - Otras pestañas reordenan la pila (REORDER_TO_FRONT) para que la
 *    transición sea instantánea y el menú nunca se mueva.
 *
 * Regla nueva de la Fase 0 — **el menú pertenece a un área**:
 *
 *  - El menú se infla con [MenuDeModo] según el área vigente, no con
 *    `app:menu` del layout. Por eso las pestañas de facturación NUNCA
 *    aparecen en el área de préstamos, ni al revés.
 *  - Cada item se resuelve contra SU PROPIO mapa ([destinosDeFacturacion] /
 *    [destinosDePrestamos]) en vez de contra un `when` único mezclado: aunque
 *    un id se colara en el menú equivocado, se cairía a `null` y la
 *    navegación se cancelaría en vez de abrir una pantalla del otro área.
 *  - La pantalla de Inicio de cada área es la que ya está en la pila y por
 *    tanto no se reconstruye; así el menú no "salta" al volver del historial.
 */
object BottomNavigator {

    private var sincronizando = false

    fun configurar(activity: AppCompatActivity, itemActualId: Int) {

        val navView =
            activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                ?: return

        // El XML ya no declara `app:menu`: el menú lo decide el área.
        navView.menu.clear()
        navView.inflateMenu(MenuDeModo.de(modoVigente(activity)))

        ViewCompat.setOnApplyWindowInsetsListener(navView) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                insets.bottom
            )
            windowInsets
        }

        navView.setOnItemSelectedListener { item ->

            if (sincronizando) return@setOnItemSelectedListener true

            if (item.itemId == itemActualId) {

                true

            } else {

                val destino = destinoPara(activity, item.itemId)

                if (destino != null) {
                    activity.abrirDestino(destino)
                }

                destino != null
            }
        }

        sincronizarPestana(activity, itemActualId)
    }

    fun sincronizarPestana(activity: AppCompatActivity, itemActualId: Int) {

        val navView =
            activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                ?: return

        sincronizando = true

        val destino = if (contieneLaPestana(navView, itemActualId)) itemActualId
        else inicioDelArea(navView)

        if (destino != SIN_PESTANA) navView.selectedItemId = destino

        sincronizando = false
    }

    private fun contieneLaPestana(navView: BottomNavigationView, itemId: Int): Boolean =
        (0 until navView.menu.size()).any { navView.menu.getItem(it).itemId == itemId }

    /**
     * El primer item del menú, que en las dos áreas es su pantalla de inicio.
     *
     * Fase 0.1: hay pantallas que NO son una pestaña del área en la que se
     * están viendo, y es a propósito. Configuración se abre desde las dos áreas
     * y `nav_configuracion` solo existe en el menú de facturación: abierta desde
     * Préstamos, marcar esa pestaña dejaría el menú inferior sin nada
     * seleccionado. En ese caso se marca el inicio del área, que es lo que el
     * propio menú está diciendo con su única pestaña.
     */
    private fun inicioDelArea(navView: BottomNavigationView): Int =
        if (navView.menu.size() == 0) SIN_PESTANA else navView.menu.getItem(0).itemId

    /** `0` no es el id de ningún item de menú, así que es un "no hay" claro. */
    private const val SIN_PESTANA = 0

    private fun AppCompatActivity.abrirDestino(destino: Class<out AppCompatActivity>) {

        val intent = Intent(this, destino)

        if (destino == MainActivity::class.java ||
            destino == PrestamosInicioActivity::class.java
        ) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        }

        startActivity(intent)

        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /**
     * Item pulsado → pantalla, DENTRO del área al que pertenece ese item.
     *
     * Se consulta primero el mapa del área vigente. Un id que no exista ahí
     * devuelve `null` y la pestaña se desmarca: es la barrera que garantiza que
     * ninguna pantalla de un área se abra desde el menú del otro.
     */
    private fun destinoPara(
        activity: AppCompatActivity,
        itemId: Int
    ): Class<out AppCompatActivity>? = when (modoVigente(activity)) {

        ModoDiarix.FACTURACION ->
            destinosDeFacturacion[itemId]

        ModoDiarix.PRESTAMOS ->
            destinosDePrestamos[itemId]
    }

    /**
     * Pestañas del área de FACTURACIÓN: Inicio, Registros, Estadísticas,
     * Historial y Configuración, en ese orden.
     *
     * CAMBIO DE RUMBO (pos-Fase 2): la lista vuelve de CUATRO a CINCO. En la
     * Fase 2, `nav_registros` y `nav_estadisticas` habían dejado de ser
     * pestañas (se abrían desde los accesos del final de Inicio) y `nav_clientes`
     * ocupaba su lugar. Eso se revierte: Registros y Estadísticas vuelven a
     * ser pestañas, y Clientes deja Facturación para vivir en el área de
     * Préstamos, donde es `nav_prestamos_clientes`.
     *
     * El orden de este mapa es el orden del menú XML, y no es casualidad que
     * coincidan: `BottomNavigationView` dibuja según el menú y este mapa solo
     * resuelve a dónde lleva cada uno. Si el menú trajera una pestaña sin
     * entrada aquí, [destinoPara] devolvería `null` y la pestaña se desmarcaría
     * sin más (no crashea, pero tampoco lleva a ninguna parte); si este mapa
     * tuviera una entrada ausente del menú, la pantalla existiría sin forma de
     * abrirla.
     */
    private val destinosDeFacturacion =
        mapOf(
            R.id.nav_inicio to MainActivity::class.java,
            R.id.nav_registros to RegistrosActivity::class.java,
            R.id.nav_estadisticas to EstadisticasActivity::class.java,
            R.id.nav_historial to HistorialActivity::class.java,
            R.id.nav_configuracion to ConfiguracionActivity::class.java,
        )

    /**
     * Pestañas del área de PRÉSTAMOS: Inicio, Clientes, Préstamos, Cobros y
     * Pagos, en ese orden.
     *
     * Son CINCO y no seis porque `BottomNavigationView` de Material 3 está
     * diseñado para un máximo de cinco: a la sexta ya la tiene que resolver él,
     * y el resultado — iconos sin etiqueta, o un menú deslizable — es peor que
     * no añadirla. Configuración, que sería la sexta, no es un módulo del área
     * y por eso no está: se abre desde el engranaje del encabezado de las
     * pantallas.
     *
     * CAMBIO DE RUMBO (pos-Fase 2): `nav_prestamos_clientes` deja de ser la
     * pantalla vacía de la Fase 1 y pasa a abrir el padrón de clientes real
     * ([ClientesActivity]). Clientes pertenece a Préstamos; en Facturación ya
     * no existe su pestaña.
     *
     * La clave es que el mapa y el menú se declaran en el MISMO orden: si el
     * menú tuviera una pestaña sin entrada aquí, [destinoPara] devolvería
     * `null` y la pestaña se desmarcaría sin más (no crashea, pero tampoco
     * lleva a ninguna parte), y si este mapa tuviera una entrada que no está
     * en el menú, la pantalla existiría sin forma de abrirla.
     */
    private val destinosDePrestamos =
        mapOf(
            R.id.nav_prestamos_inicio to PrestamosInicioActivity::class.java,
            R.id.nav_prestamos_clientes to ClientesActivity::class.java,
            R.id.nav_prestamos_prestamos to PrestamosPrestamosActivity::class.java,
            R.id.nav_prestamos_cobros to PrestamosCobrosActivity::class.java,
            R.id.nav_prestamos_pagos to PrestamosPagosActivity::class.java,
        )
}