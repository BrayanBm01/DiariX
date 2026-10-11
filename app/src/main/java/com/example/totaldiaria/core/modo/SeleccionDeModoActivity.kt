package com.example.totaldiaria.core.modo

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R

/**
 * DIARIX · Fase 0.1 · "¿Qué quieres gestionar?", a pantalla completa.
 *
 * Esta es la respuesta a una pregunta que hasta la Fase 0 no hacía falta hacer:
 * **en qué área de DiariX vas a trabajar**. Antes no se preguntaba porque solo
 * había una. Ahora hay dos, y entramos en la app sin saber cuál.
 *
 * ## Cuándo aparece
 *
 * Solo la primera vez. Al terminar de iniciar sesión se pregunta
 * "¿hay que elegir?" con [SelectorDeModo.haceFaltaElegirModo], que mira el
 * área guardada:
 *
 *  - Sin área guardada (o con una clave ilegible) → esta pantalla.
 *  - Con área guardada → se entra directo a ella, sin preguntar nada. Quien ya
 *    eligió no ve esta pantalla ni al volver a abrir la app días después.
 *
 * ## Por qué una pantalla y no el diálogo
 *
 * El diálogo de [SelectorDeModo.mostrarComoDialogo] sirve para cambiar de área
 * desde dentro, donde el usuario ya sabe qué es. Aquí la persona acaba de
 * entrar y no conoce la app: necesita ver las dos áreas, leer de qué va cada
 * una y elegir sin que haya un "Cancelar" que la devuelva a un sitio del que
 * no puede seguir. Un diálogo encima del login con una opción de cancelar es
 * exactamente el momento en el que alguien entra sin decidir y se queda
 * mirando el mismo menú de siempre sin saber por qué.
 *
 * ## Lo que NO hace
 *
 * No es un sistema de navegación paralelo: guarda con el MISMO
 * [AlmacenDeModo], decide con el MISMO [SelectorDeModo] y entra con la misma
 * `pantallaDeInicio` que declara [ModoDiarix]. Si mañana se añade un área, esta
 * pantalla no cambia: lo que cambia es [ModoDiarix].
 */
class SeleccionDeModoActivity : AppCompatActivity() {

    /**
     * No se puede salir sin elegir.
     *
     * Se avisa en vez de cerrar la app a medias, porque el botón "atrás" en
     * este punto dejaría al usuario en el escritorio sin recordar por qué.
     * La única salida real es cerrar sesión, y esa está en Configuración.
     */
    private val noSalirSinElegir =
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Toast.makeText(
                    this@SeleccionDeModoActivity,
                    R.string.selector_modo_elige_primero,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        Diarix.iniciar(this)

        // Atajo: si aquí ya hay un área guardada (el proceso se recreó, el
        // usuario volvió atrás desde una pantalla anterior...), no tiene
        // sentido preguntar. Se entra y se sale.
        val guardado = AlmacenDeModo(this).modoGuardado()

        if (SelectorDeModo.haceFaltaElegirModo(guardado) == false) {
            finish()
            SelectorDeModo.elegirYEntrar(this, requireNotNull(guardado))
            return
        }

        setContentView(R.layout.activity_seleccion_modo)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        onBackPressedDispatcher.addCallback(this, noSalirSinElegir)

        opciones().forEach { (vista, modo) ->

            // Cada opción se pinta desde el propio [ModoDiarix]: el título y la
            // descripción salen de ahí, no de textos repetidos en el layout, para
            // que añadir un área no obligue a reescribir esta pantalla.
            // Los ids de los textos propios de cada tarjeta son únicos por área
            // (Fase 2, lint): la Facturación conserva `txtTituloOpcion` y la
            // tarjeta de préstamos tiene los suyos con sufijo.
            vista.findViewById<TextView>(idTituloDe(modo)).setText(modo.titulo)

            vista.findViewById<TextView>(idResumenDe(modo)).setText(modo.resumen)

            vista.setOnClickListener {
                noSalirSinElegir.isEnabled = false

                // Guardar y entrar, siempre: aunque se elija el área por
                // defecto, aquí es la primera decisión y tiene que quedar
                // escrita para no volver a preguntar.
                SelectorDeModo.elegirYEntrar(this, modo)

                finish()
            }
        }
    }

    /**
     * Las áreas de la pantalla, en el orden en que se pintan.
     *
     * Sin ninguna marcada como activa: en este punto no hay un "modo actual"
     * todavía, y enseñar una marca de "AQUÍ" sería mentir.
     */
    private fun opciones(): List<Pair<View, ModoDiarix>> =
        ModoDiarix.entries.map { modo ->
            findViewById<View>(idDeLaOpcion(modo)) to modo
        }

    private fun idDeLaOpcion(modo: ModoDiarix): Int =
        when (modo) {
            ModoDiarix.FACTURACION -> R.id.opcionFacturacion
            ModoDiarix.PRESTAMOS -> R.id.opcionPrestamos
        }

    private fun idTituloDe(modo: ModoDiarix): Int =
        when (modo) {
            ModoDiarix.FACTURACION -> R.id.txtTituloOpcion
            ModoDiarix.PRESTAMOS -> R.id.txtTituloOpcionPrestamos
        }

    private fun idResumenDe(modo: ModoDiarix): Int =
        when (modo) {
            ModoDiarix.FACTURACION -> R.id.txtResumenOpcion
            ModoDiarix.PRESTAMOS -> R.id.txtResumenOpcionPrestamos
        }
}

/**
 * Abre la pantalla de selección de área.
 *
 * Se deja aquí y no dentro de la Activity para que el login, el arranque desde
 * el icono y quien quiera llamarla usen el mismo camino con `CLEAR_TASK`:
 * entrar a esta pantalla nunca debe dejar debajo una pantalla desde la que se
 * pueda volver a un área anterior.
 */
fun abrirSeleccionDeModo(activity: AppCompatActivity) {

    activity.startActivity(
        Intent(activity, SeleccionDeModoActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        }
    )
}