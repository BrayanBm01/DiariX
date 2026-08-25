package com.example.totaldiaria

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.adapter.PapeleraAdapter
import com.example.totaldiaria.adapter.RegistroAdapter
import com.example.totaldiaria.database.PapeleraRepository
import com.example.totaldiaria.database.RegistroRepository
import com.example.totaldiaria.models.PapeleraItem
import com.example.totaldiaria.navigation.BottomNavigator
import com.google.android.material.tabs.TabLayout
import java.util.Calendar

class RegistrosActivity : AppCompatActivity() {

    private lateinit var txtFechaSeleccionada: TextView
    private lateinit var txtContadorRegistros: TextView
    private lateinit var recyclerRegistros: RecyclerView
    private lateinit var recyclerPapelera: RecyclerView
    private lateinit var cardFiltroFecha: View
    private lateinit var txtVacioRegistros: TextView
    private lateinit var txtVaciaPapelera: TextView
    private lateinit var tabLayout: TabLayout

    private lateinit var registroRepository: RegistroRepository
    private lateinit var papeleraRepository: PapeleraRepository

    /** Fecha elegida en el calendario, o null para ver todo. */
    private var fechaFiltro: String? = null

    private var hayRegistros = false
    private var hayPapelera = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registros)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        registroRepository = RegistroRepository(this)
        papeleraRepository = PapeleraRepository(this)

        tabLayout = findViewById(R.id.tabLayoutRegistros)

        recyclerRegistros = findViewById(R.id.recyclerRegistros)
        recyclerPapelera = findViewById(R.id.recyclerPapelera)

        cardFiltroFecha = findViewById(R.id.cardFiltroFecha)
        txtVacioRegistros = findViewById(R.id.txtVacioRegistros)
        txtVaciaPapelera = findViewById(R.id.txtVaciaPapelera)

        val btnCalendario = findViewById<ImageButton>(R.id.btnCalendario)
        txtFechaSeleccionada = findViewById(R.id.txtFechaSeleccionada)
        txtContadorRegistros = findViewById(R.id.txtContadorRegistros)

        recyclerRegistros.layoutManager =
            LinearLayoutManager(this)

        recyclerPapelera.layoutManager =
            LinearLayoutManager(this)

        cargarRegistros()
        cargarPapelera()

        tabLayout.addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {

                override fun onTabSelected(tab: TabLayout.Tab) {
                    actualizarVisibilidad(tab.position == 0)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) {
                }

                override fun onTabReselected(tab: TabLayout.Tab) {
                }
            }
        )

        btnCalendario.setOnClickListener { mostrarSelectorDeFecha() }

        txtFechaSeleccionada.setOnClickListener {

            fechaFiltro = null

            txtFechaSeleccionada.text = "Seleccionar fecha"

            cargarRegistros()
        }

        BottomNavigator.configurar(this, R.id.nav_registros)
    }

    override fun onResume() {

        super.onResume()

        BottomNavigator.sincronizarPestana(this, R.id.nav_registros)
        actualizarContador()
        cargarRegistros()
        cargarPapelera()
    }

    /**
     * Muestra la cantidad real de registros diarios activos; no
     * incluye los archivados en papelera ni cuenta facturas.
     */
    private fun actualizarContador() {

        val cantidad = registroRepository.contarRegistros()

        val texto =

            if (cantidad == 1) "1 día registrado"
            else "$cantidad días registrados"

        txtContadorRegistros.text = texto
    }

    /**
     * Alterna entre las dos pestañas. No se usa ViewPager para no
     * introducir dependencias nuevas: solo visibilidad de vistas.
     */
    private fun actualizarVisibilidad(esRegistros: Boolean) {

        cardFiltroFecha.visibility =
            if (esRegistros) View.VISIBLE else View.GONE

        recyclerRegistros.visibility =
            if (esRegistros) View.VISIBLE else View.GONE

        txtVacioRegistros.visibility =
            if (esRegistros && !hayRegistros) View.VISIBLE else View.GONE

        recyclerPapelera.visibility =
            if (esRegistros) View.GONE else View.VISIBLE

        txtVaciaPapelera.visibility =
            if (!esRegistros && !hayPapelera) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------
    // Carga de datos
    // ------------------------------------------------------------------

    private fun cargarRegistros() {

        val lista: List<PapeleraItem> =

            if (fechaFiltro == null) {

                registroRepository.obtenerRegistros()

            } else {

                registroRepository.obtenerRegistrosPorFecha(
                    fechaFiltro!!
                )
            }

        hayRegistros = lista.isNotEmpty()

        recyclerRegistros.adapter =
            RegistroAdapter(lista) { item ->
                abrirFacturasDelDia(item.fecha)
            }

        actualizarVisibilidad(
            tabLayout.selectedTabPosition == 0
        )
    }

    private fun cargarPapelera() {

        val lista = papeleraRepository.obtenerPapelera()

        hayPapelera = lista.isNotEmpty()

        recyclerPapelera.adapter =
            PapeleraAdapter(lista)
    }

    // ------------------------------------------------------------------
    // Filtro por calendario
    // ------------------------------------------------------------------

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

                fechaFiltro = fecha

                txtFechaSeleccionada.text = fecha

                cargarRegistros()

            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
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
