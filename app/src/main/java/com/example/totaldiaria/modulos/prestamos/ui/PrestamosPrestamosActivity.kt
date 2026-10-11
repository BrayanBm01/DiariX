package com.example.totaldiaria.modulos.prestamos.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.example.totaldiaria.modulos.prestamos.adaptador.PrestamosAdapter
import com.example.totaldiaria.modulos.prestamos.modelo.EstadosDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDePrestamos
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Fase 3 · Listado de préstamos (RF-05 a RF-09).
 *
 * Deja de ser la pestaña vacía de la Fase 1 y se vuelve la pantalla completa
 * del módulo: pide al servidor "página N con M por página, texto `q` y estado
 * derivado `X`" y pinta lo que llega. No filtra, no corta ni reordena en el
 * teléfono, igual que Clientes: el sobre paginado y el estado son del backend.
 *
 * CUATRO ESTADOS, y solo uno visible cada vez: Cargando, Vacío, Error y
 * Lista. Sin conexión no hay lista parcial: la app no guarda préstamos en
 * SQLite, así que dice el Error con su Reintentar.
 *
 * El filtro de estado y la búsqueda conviven: ambos viajan en la misma
 * petición y ambos reinician la página a 1.
 */
class PrestamosPrestamosActivity : AppCompatActivity() {

    private lateinit var lista: RecyclerView
    private lateinit var progreso: ProgressBar
    private lateinit var estadoVacio: LinearLayout
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var txtResumen: TextView
    private lateinit var txtVacioTitulo: TextView
    private lateinit var txtVacioMensaje: TextView
    private lateinit var edtBuscar: EditText
    private lateinit var barraPaginacion: LinearLayout
    private lateinit var txtPagina: TextView
    private lateinit var btnAnterior: ImageButton
    private lateinit var btnSiguiente: ImageButton

    /** El chip de filtro (TextView) con el estado que representa; `null` = Todos. */
    private val chips = mutableListOf<Pair<TextView, String?>>()

    private var filtroEstado: String? = null

    private val reloj = Handler(Looper.getMainLooper())
    private var esperaDeBusqueda: Runnable? = null

    /** Cada recarga sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    private var pagina = 1
    private var texto: String? = null
    private var total = 0
    private var totalPaginas = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prestamos_prestamos)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        vincularVistas()

        BottomNavigator.configurar(this, R.id.nav_prestamos_prestamos)

        findViewById<View>(R.id.btnAbrirConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        estadoVacio.visibility = LinearLayout.VISIBLE
        progreso.visibility = ProgressBar.GONE
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_prestamos_prestamos)
        cargar()
    }

    override fun onDestroy() {
        super.onDestroy()
        esperaDeBusqueda?.let { reloj.removeCallbacks(it) }
    }

    private fun vincularVistas() {
        lista = findViewById(R.id.listaPrestamos)
        progreso = findViewById(R.id.progresoPrestamos)
        estadoVacio = findViewById(R.id.estadoVacioPrestamos)
        estadoError = findViewById(R.id.estadoErrorPrestamos)
        txtError = findViewById(R.id.txtErrorPrestamos)
        txtResumen = findViewById(R.id.txtResumenPrestamos)
        txtVacioTitulo = findViewById(R.id.txtVacioTituloPrestamos)
        txtVacioMensaje = findViewById(R.id.txtVacioMensajePrestamos)
        edtBuscar = findViewById(R.id.edtBuscarPrestamo)
        barraPaginacion = findViewById(R.id.barraPaginacionPrestamos)
        txtPagina = findViewById(R.id.txtPaginaPrestamos)
        btnAnterior = findViewById(R.id.btnPaginaAnteriorPrestamos)
        btnSiguiente = findViewById(R.id.btnPaginaSiguientePrestamos)

        lista.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnNuevoPrestamo).setOnClickListener {
            startActivity(PrestamoFormularioActivity.nueva(this))
        }

        findViewById<Button>(R.id.btnReintentarPrestamos).setOnClickListener { cargar() }

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

        // Retardo de la búsqueda: sin él, una petición por tecla.
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
        chips += findViewById<TextView>(R.id.txtFiltroTodos) to null
        chips += findViewById<TextView>(R.id.txtFiltroActivos) to EstadosDePrestamo.ACTIVO
        chips += findViewById<TextView>(R.id.txtFiltroVencidos) to EstadosDePrestamo.VENCIDO
        chips += findViewById<TextView>(R.id.txtFiltroPorIniciar) to
            EstadosDePrestamo.PENDIENTE_DE_INICIO
        chips += findViewById<TextView>(R.id.txtFiltroPagados) to EstadosDePrestamo.PAGADO
        chips += findViewById<TextView>(R.id.txtFiltroCancelados) to EstadosDePrestamo.CANCELADO

        chips.forEach { (chip, estado) ->
            chip.setOnClickListener {
                if (estado != filtroEstado) {
                    filtroEstado = estado
                    pagina = 1
                    pintarFiltros()
                    cargar()
                }
            }
        }

        pintarFiltros()
    }

    private fun pintarFiltros() {
        chips.forEach { (chip, estado) ->
            val activo = estado == filtroEstado
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
                val pedida = Diarix.prestamos.listar(pagina, POR_PAGINA, texto, filtroEstado)

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

    private fun pintar(pedida: PaginaDePrestamos) {
        total = pedida.total
        totalPaginas = maxOf(pedida.totalPaginas, 1)

        txtResumen.text = when {
            total == 1 -> "1 préstamo"
            texto == null && filtroEstado == null -> "$total préstamos"
            else -> "$total resultados"
        }

        if (pedida.datos.isEmpty()) {
            mostrar(Estado.VACIO)

            when {
                texto == null && filtroEstado == null -> {
                    txtVacioTitulo.text = "Todavía no hay préstamos"
                    txtVacioMensaje.text = "Registra el primero con el botón de abajo."
                }
                texto != null -> {
                    txtVacioTitulo.text = "Sin resultados"
                    txtVacioMensaje.text =
                        "No encontramos préstamos que coincidan con \"$texto\"."
                }
                else -> {
                    txtVacioTitulo.text = "Sin préstamos en este estado"
                    txtVacioMensaje.text =
                        "Ningún préstamo está ${EstadosDePrestamo.etiqueta(filtroEstado ?: "").lowercase()}. " +
                            "Cambia el filtro o registra uno nuevo."
                }
            }
            return
        }

        lista.adapter = PrestamosAdapter(pedida.datos) { prestamo ->
            startActivity(PrestamoDetalleActivity.abrir(this, prestamo.id))
        }

        // Una sola página no lleva controles: flechas que no pueden moverse
        // parecen rotas.
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

        // Solo cuando hay algo que mostrar.
        if (estado != Estado.LISTA) barraPaginacion.visibility = LinearLayout.GONE
    }

    private fun mostrarError(mensaje: String) {
        txtError.text = mensaje
        mostrar(Estado.ERROR)
    }

    private enum class Estado { CARGANDO, VACIO, ERROR, LISTA }

    companion object {
        /**
         * 25, que es el valor por defecto del servidor. No es un tope local:
         * el backend rechaza cualquier `porPagina` mayor que 200.
         */
        private const val POR_PAGINA = 25
    }
}