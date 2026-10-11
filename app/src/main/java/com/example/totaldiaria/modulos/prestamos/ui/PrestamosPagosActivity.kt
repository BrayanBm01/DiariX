package com.example.totaldiaria.modulos.prestamos.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.core.navegacion.BottomNavigator
import com.example.totaldiaria.modulos.facturacion.ui.ConfiguracionActivity
import com.example.totaldiaria.modulos.prestamos.adaptador.PagosAdapter
import com.example.totaldiaria.modulos.prestamos.modelo.MetodosDePagoDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDePagos
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Fase 4 · PAGOS del área de Préstamos (historial general).
 *
 * La pestaña que en Fase 1 era una pantalla vacía a propósito muestra ahora el
 * historial REAL de `GET /prestamos/pagos`: todo pago de la cartera, con su
 * método, su distribución entre cuotas y si fue revertido. La búsqueda y el
 * filtro por método los resuelve el servidor; la paginación también.
 */
class PrestamosPagosActivity : AppCompatActivity() {

    private lateinit var lista: RecyclerView
    private lateinit var progreso: ProgressBar
    private lateinit var estadoVacio: LinearLayout
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var txtResumen: TextView
    private lateinit var txtVacioTitulo: TextView
    private lateinit var txtVacioMensaje: TextView
    private lateinit var edtBuscar: android.widget.EditText
    private lateinit var barraPaginacion: LinearLayout
    private lateinit var txtPagina: TextView
    private lateinit var btnAnterior: ImageButton
    private lateinit var btnSiguiente: ImageButton

    private val chips = mutableListOf<Pair<TextView, String?>>()

    private var filtroMetodo: String? = null

    private val reloj = Handler(Looper.getMainLooper())
    private var esperaDeBusqueda: Runnable? = null

    private var versionDeCarga = 0

    private var pagina = 1
    private var texto: String? = null
    private var total = 0
    private var totalPaginas = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prestamos_pagos)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        vincularVistas()

        BottomNavigator.configurar(this, R.id.nav_prestamos_pagos)

        findViewById<View>(R.id.btnAbrirConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        estadoVacio.visibility = LinearLayout.VISIBLE
        progreso.visibility = ProgressBar.GONE
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_prestamos_pagos)
        cargar()
    }

    override fun onDestroy() {
        super.onDestroy()
        esperaDeBusqueda?.let { reloj.removeCallbacks(it) }
    }

    private fun vincularVistas() {
        lista = findViewById(R.id.listaPagos)
        progreso = findViewById(R.id.progresoPagos)
        estadoVacio = findViewById(R.id.estadoVacioPagos)
        estadoError = findViewById(R.id.estadoErrorPagos)
        txtError = findViewById(R.id.txtErrorPagos)
        txtResumen = findViewById(R.id.txtResumenPagos)
        txtVacioTitulo = findViewById(R.id.txtVacioTituloPagos)
        txtVacioMensaje = findViewById(R.id.txtVacioMensajePagos)
        edtBuscar = findViewById(R.id.edtBuscarPago)
        barraPaginacion = findViewById(R.id.barraPaginacionPagos)
        txtPagina = findViewById(R.id.txtPaginaPagos)
        btnAnterior = findViewById(R.id.btnPaginaAnteriorPagos)
        btnSiguiente = findViewById(R.id.btnPaginaSiguientePagos)

        lista.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnReintentarPagos).setOnClickListener { cargar() }

        btnAnterior.setOnClickListener {
            if (pagina > 1) {
                pagina--
                cargar()
            }
        }

        btnSiguiente.setOnClickListener {
            if (pagina < totalPaginas) {
                pagina++
                cargar()
            }
        }

        configurarFiltros()

        edtBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                esperaDeBusqueda?.let { reloj.removeCallbacks(it) }

                val proximo = Runnable { aplicarBusqueda(s?.toString()) }
                esperaDeBusqueda = proximo
                reloj.postDelayed(proximo, 400)
            }
        })
    }

    private fun configurarFiltros() {
        chips.clear()
        chips += findViewById<TextView>(R.id.txtFiltroTodosPagos) to null
        chips += findViewById<TextView>(R.id.txtFiltroEfectivo) to MetodosDePagoDePrestamo.EFECTIVO
        chips += findViewById<TextView>(R.id.txtFiltroTransferencia) to
            MetodosDePagoDePrestamo.TRANSFERENCIA

        chips.forEach { (chip, metodo) ->
            chip.setOnClickListener {
                if (metodo != filtroMetodo) {
                    filtroMetodo = metodo
                    pagina = 1
                    pintarFiltros()
                    cargar()
                }
            }
        }

        pintarFiltros()
    }

    private fun pintarFiltros() {
        chips.forEach { (chip, metodo) ->
            val activo = metodo == filtroMetodo
            chip.setBackgroundResource(
                if (activo) R.drawable.bg_chip_filtro_activo else R.drawable.bg_chip_filtro,
            )
            chip.setTextColor(if (activo) 0xFFFFFFFF.toInt() else 0xFF4A5578.toInt())
            chip.typeface = if (activo) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
        }
    }

    private fun aplicarBusqueda(valor: String?) {
        val limpio = valor?.trim()?.ifEmpty { null }

        if (limpio == texto) return

        texto = limpio
        pagina = 1
        cargar()
    }

    // ==========================================================================
    // Carga
    // ==========================================================================

    private fun cargar() {
        val version = ++versionDeCarga

        mostrar(Estado.CARGANDO)

        Diarix.enHiloDeFondo {
            try {
                val pedida: PaginaDePagos =
                    Diarix.prestamos.pagos(pagina, POR_PAGINA, texto, filtroMetodo)

                runOnUiThread {
                    if (version != versionDeCarga) return@runOnUiThread
                    pintar(pedida)
                }
            } catch (e: ErrorDiarix) {
                fallar(version, e.mensajeAmigable)
            } catch (e: Exception) {
                fallar(version, ErrorDiarix.Inesperado(e.message ?: "").mensajeAmigable)
            }
        }
    }

    private fun fallar(version: Int, mensaje: String) {
        runOnUiThread {
            if (version != versionDeCarga) return@runOnUiThread
            mostrarError(mensaje)
        }
    }

    private fun pintar(pedida: PaginaDePagos) {
        total = pedida.total
        totalPaginas = maxOf(pedida.totalPaginas, 1)

        txtResumen.text = when {
            total == 1 -> "1 pago"
            texto == null && filtroMetodo == null -> "$total pagos"
            else -> "$total resultados"
        }

        if (pedida.datos.isEmpty()) {
            mostrar(Estado.VACIO)
            txtVacioTitulo.text = when {
                texto != null -> "Sin resultados"
                filtroMetodo != null -> "Sin pagos con ese método"
                else -> "Todavía no hay pagos"
            }
            txtVacioMensaje.text = when {
                texto != null ->
                    "No encontramos pagos que coincidan con \"$texto\"."
                filtroMetodo != null ->
                    "Ningún pago se registró por " +
                        "${MetodosDePagoDePrestamo.etiqueta(filtroMetodo!!).lowercase()}. " +
                        "Registra uno desde la ficha de un préstamo."
                else ->
                    "Cuando registres un pago desde la ficha de un préstamo, aparece aquí."
            }
            return
        }

        lista.adapter = PagosAdapter(pedida.datos)

        if (totalPaginas > 1) {
            txtPagina.text = "Página ${pedida.pagina} de $totalPaginas"
            btnAnterior.alpha = if (pagina > 1) 1f else 0.3f
            btnSiguiente.alpha = if (pagina < totalPaginas) 1f else 0.3f
            barraPaginacion.visibility = LinearLayout.VISIBLE
        } else {
            barraPaginacion.visibility = LinearLayout.GONE
        }

        lista.scrollToPosition(0)
        mostrar(Estado.LISTA)
    }

    private fun mostrar(estado: Estado) {
        progreso.visibility = if (estado == Estado.CARGANDO) ProgressBar.VISIBLE else ProgressBar.GONE
        estadoVacio.visibility = if (estado == Estado.VACIO) LinearLayout.VISIBLE else LinearLayout.GONE
        estadoError.visibility = if (estado == Estado.ERROR) LinearLayout.VISIBLE else LinearLayout.GONE
        lista.visibility = if (estado == Estado.LISTA) RecyclerView.VISIBLE else RecyclerView.GONE

        if (estado != Estado.LISTA) barraPaginacion.visibility = LinearLayout.GONE
    }

    private fun mostrarError(mensaje: String) {
        txtError.text = mensaje
        mostrar(Estado.ERROR)
    }

    private enum class Estado { CARGANDO, VACIO, ERROR, LISTA }

    companion object {
        private const val POR_PAGINA = 25
    }
}