package com.example.totaldiaria.modulos.prestamos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DIARIX · Fase 4 · El Inicio del área dejó de decir "PRÓXIMAMENTE".
 *
 * Antes del resumen real, el Inicio era una lista informativa de módulos que
 * no existían ("PRÓXIMAMENTE", con `item_modulo_previsto`). Esa lista salió:
 *
 *  - el texto `modulo_proximamente` y el layout `item_modulo_previsto` ya no
 *    existen como recursos (una pantalla no puede dibujar lo que se borró);
 *  - el layout del Inicio nuevo trae el panel del resumen del servidor
 *    (activos, saldo, indicadores) y los dos accesos a Cobros y Pagos;
 *  - la barra inferior sigue siendo la azul del área, sin cambios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InicioRedisenadoTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_TotalDiaria,
        )
    }

    @Test
    fun `el inicio de prestamos ya no tiene el texto proximamente`() {
        assertEquals(
            "El string modulo_proximamente no debería existir",
            0,
            context.resources.getIdentifier(
                "modulo_proximamente",
                "string",
                context.packageName,
            ),
        )
    }

    @Test
    fun `el layout del proximamente desaparecio del proyecto`() {
        assertEquals(
            "item_modulo_previsto no debería existir",
            0,
            context.resources.getIdentifier(
                "item_modulo_previsto",
                "layout",
                context.packageName,
            ),
        )
    }

    @Test
    fun `el inicio nuevo infla con su panel de resumen y sus accesos`() {

        val vista = LayoutInflater.from(context)
            .inflate(R.layout.activity_prestamos_inicio, null, false)

        // El panel del resumen del servidor.
        assertNotNull(vista.findViewById<View>(R.id.panelResumenInicio))
        assertNotNull(vista.findViewById<View>(R.id.txtPrestamosActivos))
        assertNotNull(vista.findViewById<View>(R.id.txtSaldoPendiente))
        assertNotNull(vista.findViewById<View>(R.id.txtCobrosHoyCantidad))
        assertNotNull(vista.findViewById<View>(R.id.txtVencidasDias))
        assertNotNull(vista.findViewById<View>(R.id.txtProximosValor))
        assertNotNull(vista.findViewById<View>(R.id.txtRecaudadoHoyValor))

        // Los dos accesos directos a Cobros y Pagos.
        assertNotNull(vista.findViewById<View>(R.id.tarjetaAccesoCobros))
        assertNotNull(vista.findViewById<View>(R.id.tarjetaAccesoPagos))

        // La lista vieja no queda ni en el layout.
        assertEquals(
            "La lista de próximamente no debería existir",
            0,
            context.resources.getIdentifier(
                "listaProximamente",
                "id",
                context.packageName,
            ),
        )
    }

    @Test
    fun `el inicio nuevo mantiene la barra azul del area de prestamos`() {

        val vista = LayoutInflater.from(context)
            .inflate(R.layout.activity_prestamos_inicio, null, false)

        val barra = vista.findViewById<BottomNavigationView>(R.id.bottomNav)
        assertNotNull("El Inicio debe conservar el menú inferior", barra)

        val tinte = barra.itemIconTintList
        assertNotNull(tinte)

        assertEquals(
            "La pestaña marcada tiene que pintarse del azul de Préstamos",
            0xFF2B6ED4.toInt(),
            tinte!!.getColorForState(intArrayOf(android.R.attr.state_checked), 0),
        )
    }
}