package com.example.totaldiaria.core.navegacion

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.LoginActivity

/**
 * Puerta de entrada de las pantallas que leen datos privados.
 *
 * Inicializa el grafo (que es donde se recupera el dueño de la base desde el
 * almacén cifrado) y, si no hay sesión, manda al login limpiando la pila.
 * Devuelve `false` cuando la actividad debe detener su `onCreate`: así ninguna
 * pantalla abre SQLite con la cuenta equivocada ni deja un backstack privado.
 */
fun AppCompatActivity.exigirSesion(): Boolean {
    Diarix.iniciar(this)

    if (Diarix.tieneSesion()) return true

    startActivity(
        Intent(this, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    )
    finish()
    return false
}
