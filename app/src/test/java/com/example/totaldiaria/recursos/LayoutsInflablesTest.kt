package com.example.totaldiaria.recursos

import android.content.Context
import android.view.LayoutInflater
import android.view.View
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
import java.io.File

/**
 * DIARIX · Fase 0.1 · Toda vista de la app se puede INFLAR.
 *
 * Este test existe por un fallo concreto y real. Al reorganizar los paquetes en
 * la Fase 0, las cuatro gráficas se movieron de
 * `com.example.totaldiaria.ui.graficas` a
 * `com.example.totaldiaria.modulos.facturacion.ui`, pero `activity_estadisticas.xml`
 * siguió nombrando el paquete viejo. Resultado: la app COMPILABA (`assembleDebug`
 * en verde, porque AAPT2 no comprueba que la clase de una vista personalizada
 * exista) y fallaba solo al abrir Estadísticas, con un `InflateException` y la
 * app cerrándose. Un fallo de runtime invisible al build.
 *
 * La lección es la que motivó el test: en Android, un layout puede ser válido
 * para el compilador de recursos y aun así ser inválido de verdad. Por eso aquí
 * no se mira el XML: se infla cada layout de verdad, y si una clase de vista
 * personalizada no existe, el inflado falla y el test lo dice.
 *
 * Además comprueba que la lista de layouts que este test recorre es la lista
 * COMPLETA. Si alguien añade un layout y no lo añade aquí, el aviso lo dice,
 * en vez de dejar un hueco silencioso que es justo lo que pasó con las gráficas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LayoutsInflablesTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_TotalDiaria
        )
    }

    // ----------------------------------------------------------------
    // El guardián principal
    // ----------------------------------------------------------------

    @Test
    fun `todos los layouts de la app se inflan sin error`() {

        LAYOUTS.forEach { layout ->

            // Un fallo aquí debe nombrar el layout exacto: si no, el mensaje
            // es "ClassNotFoundException" y no dice de qué pantalla venía,
            // que es justo la información que hizo falta depurar.
            try {
                val vista = LayoutInflater.from(context).inflate(layout, null, false)

                assertNotNull(
                    "El layout $layout se infló pero devolvió null",
                    vista
                )
            } catch (e: Exception) {
                throw AssertionError(
                    "No se pudo inflar R.layout.${nombreDe(layout)}. " +
                        "Causa: ${e.javaClass.name}: ${e.message}",
                    e
                )
            }
        }
    }

    /**
     * Las cuatro gráficas de estadísticas se dibujan con vistas propias de la
     * app, no con librerías. Si el layout volviera a apuntar a un paquete que ya
     * no existe, este test es el que lo dirá por nombre.
     */
    @Test
    fun `las graficas de estadisticas usan las clases que existen de verdad`() {

        val raiz = LayoutInflater.from(context)
            .inflate(R.layout.activity_estadisticas, null, false)

        listOf(
            "graficaLineas",
            "graficaBarras",
            "graficaDona",
            "graficaComparacion",
        ).forEach { id ->

            val vista = raiz.findViewById<View>(
                context.resources.getIdentifier(id, "id", context.packageName)
            )

            assertNotNull(
                "La vista $id no se encontró inflada en activity_estadisticas",
                vista
            )
        }
    }

    // ----------------------------------------------------------------
    // La lista no se queda corta sin que nadie lo note
    // ----------------------------------------------------------------

    /**
     * Si se añade un layout y no se registra en [LAYOUTS], este test falla.
     * Es la red de seguridad para la red de seguridad: sin ella, el test
     * anterior seguiría en verde con un layout nuevo sin comprobar.
     */
    @Test
    fun `la lista de layouts bajo prueba esta completa`() {

        val declarados =
            File("src/main/res/layout")
                .listFiles()
                .orEmpty()
                .map { it.name.removeSuffix(".xml") }
                .toSet()

        assertTrue(
            "No se pudo leer res/layout del proyecto: la comprobación no sería válida",
            declarados.isNotEmpty()
        )

        val cubiertos = LAYOUTS.map { nombreDe(it) }.toSet()

        assertEquals(
            "Estos layouts no los infla ningún test y por tanto nadie comprueba que " +
                "sean válidos: ${declarados - cubiertos}",
            declarados,
            cubiertos
        )
    }

    // ----------------------------------------------------------------
    // Utilidades
    // ----------------------------------------------------------------

    private fun nombreDe(layout: Int): String =
        context.resources.getResourceEntryName(layout)

    private companion object {

        /**
         * TODOS los layouts de `res/layout`, sin excepción.
         *
         * Si añades un layout, añádelo aquí: es el precio de que la prueba siga
         * valiendo. La lista se compara contra el directorio del proyecto en
         * `la lista de layouts bajo prueba esta completa`, que es lo que
         * convierte este comentario en una regla y no en un deseo.
         */
        val LAYOUTS = listOf(
            R.layout.activity_login,
            R.layout.activity_main,
            R.layout.activity_facturas_del_dia,
            R.layout.activity_historial,
            R.layout.activity_registros,
            R.layout.activity_estadisticas,
            R.layout.activity_configuracion,
            R.layout.activity_prestamos_inicio,
            R.layout.activity_prestamos_vacia,
            R.layout.activity_prestamos_prestamos,
            R.layout.activity_prestamos_cobros,
            R.layout.activity_prestamos_pagos,
            R.layout.activity_prestamo_formulario,
            R.layout.activity_prestamo_detalle,
            R.layout.activity_prestamo_pago,
            R.layout.activity_selector_cliente,
            R.layout.activity_clientes,
            R.layout.activity_cliente_formulario,
            R.layout.item_cliente,
            R.layout.item_prestamo,
            R.layout.item_cuota,
            R.layout.item_cobro,
            R.layout.item_pago,
            R.layout.activity_seleccion_modo,
            R.layout.activity_editar_perfil,
            R.layout.activity_ayuda,
            R.layout.activity_acerca_de,
            R.layout.dialog_selector_modo,
            R.layout.dialog_comprobante,
            R.layout.dialog_editar_factura,
            R.layout.dialog_qr,
            R.layout.item_factura,
            R.layout.item_papelera,
            R.layout.item_registro,
        )
    }
}