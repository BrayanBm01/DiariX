package com.example.totaldiaria.core.cuenta

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R

/**
 * DIARIX · Fase 0.1 · Ayuda.
 *
 * No hay soporte, tickets, chat ni correo: DiariX no tiene nada detrás. Esta
 * pantalla responde a las preguntas que esta versión puede responder de verdad
 * —dónde está cada cosa— y lo dice explícitamente en su última frase para que
 * quien no encuentre su respuesta sepa distinguir "DiariX no lo tiene" de "no
 * me lo he encontrado".
 *
 * Los textos viven en `strings.xml` y no en el código, a diferencia del resto de
 * la app, porque es una pantalla de lectura larga: es la que más se va a
 * traducir y la que menos se va a tocar. Que esté entera en un sitio es lo que
 * hace que traducirla no se convierta en una tarea de arqueología.
 *
 * El contenido está escrito contra el estado REAL de la app. Cuando el área de
 * préstamos tenga sus módulos, el bloque que hoy dice que están en construcción
 * se sustituye por sus instrucciones; hasta entonces, escribirlo de otra forma
 * sería prometer algo que no está.
 */
class AyudaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_ayuda)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        Diarix.iniciar(this)
    }
}