package com.example.totaldiaria.navigation

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.totaldiaria.ConfiguracionActivity
import com.example.totaldiaria.HistorialActivity
import com.example.totaldiaria.MainActivity
import com.example.totaldiaria.PapeleraActivity
import com.example.totaldiaria.RegistrosActivity
import com.example.totaldiaria.R
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Punto único de navegación del menú inferior.
 *
 * Reglas:
 *  - Tocar la pestaña actual no hace nada (se consume el evento).
 *  - Ir a Inicio reutiliza la instancia existente (CLEAR_TOP | SINGLE_TOP)
 *    para conservar su estado y evitar Mains apilados.
 *  - Salir hacia otra pestaña finaliza la activity actual,
 *    manteniendo la pila acotada: [Main, pantalla actual].
 */
object BottomNavigator {

    fun configurar(activity: AppCompatActivity, itemActualId: Int) {

        val navView =
            activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                ?: return

        navView.selectedItemId = itemActualId

        navView.setOnItemSelectedListener { item ->

            if (item.itemId == itemActualId) {

                true

            } else {

                val destino = destinoPara(item.itemId)

                if (destino != null) {
                    activity.abrirDestino(destino)
                }

                destino != null
            }
        }
    }

    private fun AppCompatActivity.abrirDestino(destino: Class<out AppCompatActivity>) {

        val intent = Intent(this, destino)

        if (destino == MainActivity::class.java) {
            intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        startActivity(intent)

        if (destino != MainActivity::class.java) {
            finish()
        }
    }

    private fun destinoPara(itemId: Int): Class<out AppCompatActivity>? =

        when (itemId) {

            R.id.nav_inicio -> MainActivity::class.java
            R.id.nav_registros -> RegistrosActivity::class.java
            R.id.nav_historial -> HistorialActivity::class.java
            R.id.nav_papelera -> PapeleraActivity::class.java
            R.id.nav_configuracion -> ConfiguracionActivity::class.java

            else -> null
        }
}
