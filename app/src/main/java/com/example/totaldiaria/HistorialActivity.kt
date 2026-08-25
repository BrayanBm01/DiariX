package com.example.totaldiaria

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.adapter.FacturaAdapter
import com.example.totaldiaria.database.FacturaRepository
import com.example.totaldiaria.navigation.BottomNavigator
import com.example.totaldiaria.ui.EditarFacturaDialog
import com.example.totaldiaria.ui.FormatoMoneda
import com.example.totaldiaria.models.Factura

class HistorialActivity : AppCompatActivity() {

    private lateinit var recyclerHistorial: RecyclerView

    private lateinit var txtCantidad: TextView
    private lateinit var txtTotal: TextView
    private lateinit var txtFacturasEfectivo: TextView
    private lateinit var txtFacturasTransferencia: TextView
    private lateinit var txtCantidadEfectivo: TextView
    private lateinit var txtCantidadTransferencia: TextView

    private lateinit var facturaRepository: FacturaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historial)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        facturaRepository = FacturaRepository(this)

        vincularVistas()

        BottomNavigator.configurar(this, R.id.nav_historial)

        cargarFacturas()
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_historial)
        cargarFacturas()
    }

    private fun vincularVistas() {

        recyclerHistorial =
            findViewById(R.id.recyclerHistorial)

        txtCantidad =
            findViewById(R.id.txtCantidadHistorial)

        txtTotal =
            findViewById(R.id.txtTotalHistorial)

        txtFacturasEfectivo =
            findViewById(R.id.txtFacturasEfectivo)

        txtFacturasTransferencia =
            findViewById(R.id.txtFacturasTransferencia)

        txtCantidadEfectivo =
            findViewById(R.id.txtCantidadEfectivo)

        txtCantidadTransferencia =
            findViewById(R.id.txtCantidadTransferencia)

        recyclerHistorial.layoutManager =
            LinearLayoutManager(this)
    }

    private fun cargarFacturas() {

        val lista =
            facturaRepository.obtenerFacturas()

        recyclerHistorial.adapter =
            FacturaAdapter(
                lista,

                { factura ->
                    EditarFacturaDialog.mostrar(
                        this,
                        factura,
                        facturaRepository
                    ) { cargarFacturas() }
                },

                { factura ->
                    confirmarEliminacion(factura)
                }
            )

        actualizarResumen(lista)
    }

    private fun confirmarEliminacion(factura: Factura) {

        AlertDialog.Builder(this)
            .setTitle("Eliminar factura")
            .setMessage("¿Deseas eliminar esta factura?")
            .setPositiveButton("Eliminar") { _, _ ->

                facturaRepository.eliminarFactura(
                    factura.id
                )

                Toast.makeText(
                    this,
                    "Factura eliminada",
                    Toast.LENGTH_SHORT
                ).show()

                cargarFacturas()
            }
            .setNegativeButton(
                "Cancelar",
                null
            )
            .show()
    }

    private fun actualizarResumen(lista: List<Factura>) {

        txtCantidad.text =
            lista.size.toString()

        val totalEfectivo = lista.sumOf { it.efectivo }
        val totalTransferencia = lista.sumOf { it.transferencia }

        txtTotal.text =
            FormatoMoneda.formatear(totalEfectivo + totalTransferencia)

        txtFacturasEfectivo.text =
            FormatoMoneda.formatear(totalEfectivo)

        txtFacturasTransferencia.text =
            FormatoMoneda.formatear(totalTransferencia)

        txtCantidadEfectivo.text =
            "${lista.count { it.efectivo > 0 }} facturas"

        txtCantidadTransferencia.text =
            "${lista.count { it.transferencia > 0 }} facturas"
    }
}
