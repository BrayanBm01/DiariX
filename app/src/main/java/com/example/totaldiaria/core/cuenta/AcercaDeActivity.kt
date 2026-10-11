package com.example.totaldiaria.core.cuenta

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.BuildConfig
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R

/**
 * DIARIX · Fase 0.1 · Acerca de DiariX.
 *
 * ## La versión es REAL
 *
 * Sale de [BuildConfig.VERSION_NAME] en código, que es lo que lleva grabado el
 * APK instalado en el móvil de la persona. Escribir el número en un texto
 * (`"1.0.1"`) es una mentira en cuanto se publica la siguiente versión, y el
 * número que ve alguien al abrir esta pantalla es exactamente el que se le
 * dice a soporte, así que tiene que ser el de verdad.
 *
 * ## Lo que NO lleva
 *
 *  - **Enlaces a términos, política de privacidad o contacto.** No existen:
 *    no hay sitio web ni textos escritos. Un enlace que lleva a una página que
 *    no está escrita es un problema legal y de confianza a la vez.
 *  - **Licencias de terceros.** La app usa librerías con licencia abierta, pero
 *    los textos de licencia hay que generarlos desde las dependencias reales
 *    (`aboutLibraries` o un `NOTICE` generado en build), no transcribirlos a
 *    mano. Es una tarea de su propia fase, y aquí no se finge que esté hecha.
 */
class AcercaDeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_acerca_de)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        Diarix.iniciar(this)

        findViewById<TextView>(R.id.txtVersionAcercaDe).text =
            getString(R.string.acerca_version, BuildConfig.VERSION_NAME)
    }
}