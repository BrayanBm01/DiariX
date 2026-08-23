package com.example.totaldiaria

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.adapter.RegistroAdapter
import com.example.totaldiaria.database.RegistroRepository
import com.example.totaldiaria.models.PapeleraItem
import com.example.totaldiaria.navigation.BottomNavigator
import java.util.Calendar

class RegistrosActivity : AppCompatActivity() {

    private lateinit var txtFechaSeleccionada: TextView
    private lateinit var recyclerRegistros: RecyclerView

    private lateinit var registroRepository: RegistroRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registros)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        registroRepository = RegistroRepository(this)

        recyclerRegistros = findViewById(R.id.recyclerRegistros)
        recyclerRegistros.layoutManager = LinearLayoutManager(this)

        val btnCalendario = findViewById<ImageButton>(R.id.btnCalendario)
        txtFechaSeleccionada = findViewById(R.id.txtFechaSeleccionada)

        cargarRegistros()

        btnCalendario.setOnClickListener { mostrarSelectorDeFecha() }

        txtFechaSeleccionada.setOnClickListener {

            txtFechaSeleccionada.text = "Seleccionar fecha"

            cargarRegistros()
        }

        BottomNavigator.configurar(this, R.id.nav_registros)
    }

    private fun mostrarSelectorDeFecha() {

        val calendario = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, anio, mes, dia ->

                val fecha = String.format(
                    "%02d/%02d/%04d",
                    dia,
                    mes + 1,
                    anio
                )

                txtFechaSeleccionada.text = fecha

                mostrarRegistros(
                    registroRepository.obtenerRegistrosPorFecha(fecha)
                )

            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun cargarRegistros() {

        mostrarRegistros(registroRepository.obtenerRegistros())
    }

    private fun mostrarRegistros(lista: List<PapeleraItem>) {

        recyclerRegistros.adapter =
            RegistroAdapter(lista) { item -> abrirFacturasDelDia(item.fecha) }
    }

    private fun abrirFacturasDelDia(fecha: String) {

        val intent = Intent(this, FacturasDelDiaActivity::class.java)

        intent.putExtra(EXTRA_FECHA, fecha)

        startActivity(intent)
    }

    companion object {

        const val EXTRA_FECHA = "fecha"
    }
}
