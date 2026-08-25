package com.example.totaldiaria.navigation

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.totaldiaria.ConfiguracionActivity
import com.example.totaldiaria.HistorialActivity
import com.example.totaldiaria.MainActivity
import com.example.totaldiaria.EstadisticasActivity
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
 *  - Otras pestañas reordenan la pila (REORDER_TO_FRONT) para que la
 *    transición sea instantánea y el menú nunca se mueva.
 */
object BottomNavigator {

    private var sincronizando = false

    fun configurar(activity: AppCompatActivity, itemActualId: Int) {

        val navView =
            activity.findViewById<BottomNavigationView>(R.id.bottomNav)
                ?: return

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

                val destino = destinoPara(item.itemId)

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
        navView.selectedItemId = itemActualId
        sincronizando = false
    }

    private fun AppCompatActivity.abrirDestino(destino: Class<out AppCompatActivity>) {

        val intent = Intent(this, destino)

        if (destino == MainActivity::class.java) {
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

    private fun destinoPara(itemId: Int): Class<out AppCompatActivity>? =

        when (itemId) {

            R.id.nav_inicio -> MainActivity::class.java
            R.id.nav_registros -> RegistrosActivity::class.java
            R.id.nav_historial -> HistorialActivity::class.java
            R.id.nav_estadisticas -> EstadisticasActivity::class.java
            R.id.nav_configuracion -> ConfiguracionActivity::class.java

            else -> null
        }
}
