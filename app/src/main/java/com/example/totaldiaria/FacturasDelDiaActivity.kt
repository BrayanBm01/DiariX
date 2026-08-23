package com.example.totaldiaria

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.adapter.FacturaAdapter
import com.example.totaldiaria.database.FacturaRepository

class FacturasDelDiaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_facturas_del_dia)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val recyclerFacturas =
            findViewById<RecyclerView>(R.id.recyclerFacturas)

        val txtTitulo =
            findViewById<TextView>(R.id.txtTitulo)

        val btnBack =
            findViewById<ImageButton>(R.id.btnBack)

        recyclerFacturas.layoutManager =
            LinearLayoutManager(this)

        val fecha =
            intent.getStringExtra(RegistrosActivity.EXTRA_FECHA) ?: ""

        txtTitulo.text =
            "Facturas del $fecha"

        btnBack.setOnClickListener {
            finish()
        }

        cargarFacturas(fecha)
    }

    private fun cargarFacturas(fecha: String) {

        val lista =
            FacturaRepository(this).obtenerFacturasPorFecha(fecha)

        findViewById<RecyclerView>(R.id.recyclerFacturas).adapter =
            FacturaAdapter(
                lista,
                onFacturaClick = {
                    // Solo lectura desde esta pantalla
                },
                onEliminarClick = {
                    // No se permite eliminar desde esta pantalla
                }
            )
    }
}
