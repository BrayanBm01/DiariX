package com.example.totaldiaria

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.navigation.BottomNavigator
import com.example.totaldiaria.preferences.QrManager

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var qrManager: QrManager
    private lateinit var txtEstadoQr: TextView

    private val seleccionarQr =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            uri?.let {

                val guardado =
                    qrManager.guardarQr(it)

                mostrarEstadoQr(guardado)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        qrManager = QrManager(this)

        val btnCambiarQr =
            findViewById<Button>(R.id.btnCambiarQr)

        txtEstadoQr =
            findViewById(R.id.txtEstadoQr)

        mostrarEstadoQr(qrManager.existeQr())

        btnCambiarQr.setOnClickListener {
            seleccionarQr.launch("image/*")
        }

        BottomNavigator.configurar(this, R.id.nav_configuracion)
    }

    private fun mostrarEstadoQr(configurado: Boolean) {

        if (configurado) {

            txtEstadoQr.text =
                "Código QR cargado correctamente"

            txtEstadoQr.setTextColor(
                ContextCompat.getColor(
                    this,
                    android.R.color.holo_green_dark
                )
            )

        } else {

            txtEstadoQr.text =
                "No hay un código QR configurado"

            txtEstadoQr.setTextColor(
                ContextCompat.getColor(
                    this,
                    android.R.color.holo_red_dark
                )
            )
        }
    }
}
