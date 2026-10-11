package com.example.totaldiaria.modulos.prestamos.ui

import android.content.Intent
import android.os.Bundle
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
import com.example.totaldiaria.modulos.prestamos.adaptador.CobrosAdapter
import com.example.totaldiaria.modulos.prestamos.modelo.CategoriasDeCobro
import com.example.totaldiaria.modulos.prestamos.modelo.Cobro
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDeCobros
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Fase 4 · COBROS del área de Préstamos.
 *
 * La pestaña que en Fase 1 era una pantalla vacía a propósito, ahora muestra
 * los cobros con datos REALES de `GET /cobros`. La categoría —Hoy, Vencidas,
 * Próximas— la filtra el SERVIDOR por fecha y saldo: la app repite el chip,
 * nunca una copia local de la cartera.
 *
 * Tocar una fila abre la ficha del préstamo y desde ahí se registra el pago.
 */
class PrestamosCobrosActivity : AppCompatActivity() {

    private lateinit var lista: RecyclerView
    private lateinit var progreso: ProgressBar
    private lateinit var estadoVacio: LinearLayout
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var txtResumen: TextView
    private lateinit var txtVacioTitulo: TextView
    private lateinit var txtVacioMensaje: TextView
    private lateinit var barraPaginacion: LinearLayout
    private lateinit var txtPagina: TextView
    private lateinit var btnAnterior: ImageButton
    private lateinit var btnSiguiente: ImageButton

    /** Los chips de categoría (TextView) con el valor que piden al servidor. */
    private val chips = mutableListOf<Pair<TextView, String>>()

    private var categoria: String = CategoriasDeCobro.HOY

    /** Cada recarga sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    private var pagina = 1
    private var total = 0
    private var totalPaginas = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prestamos_cobros)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        vincularVistas()

        BottomNavigator.configurar(this, R.id.nav_prestamos_cobros)

        findViewById<View>(R.id.btnAbrirConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        estadoVacio.visibility = LinearLayout.VISIBLE
        progreso.visibility = ProgressBar.GONE
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_prestamos_cobros)
        cargar()
    }

    private fun vincularVistas() {
        lista = findViewById(R.id.listaCobros)
        progreso = findViewById(R.id.progresoCobros)
        estadoVacio = findViewById(R.id.estadoVacioCobros)
        estadoError = findViewById(R.id.estadoErrorCobros)
        txtError = findViewById(R.id.txtErrorCobros)
        txtResumen = findViewById(R.id.txtResumenCobros)
        txtVacioTitulo = findViewById(R.id.txtVacioTituloCobros)
        txtVacioMensaje = findViewById(R.id.txtVacioMensajeCobros)
        barraPaginacion = findViewById(R.id.barraPaginacionCobros)
        txtPagina = findViewById(R.id.txtPaginaCobros)
        btnAnterior = findViewById(R.id.btnPaginaAnteriorCobros)
        btnSiguiente = findViewById(R.id.btnPaginaSiguienteCobros)

        lista.layoutManager = LinearLayoutManager(this)

        findViewById<View>(R.id.btnReintentarCobros).setOnClickListener { cargar() }

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

        configurarChips()
    }

    private fun configurarChips() {
        chips.clear()
        val chipHoy: TextView = findViewById(R.id.txtCategoriaHoy)
        val chipVencidas: TextView = findViewById(R.id.txtCategoriaVencidas)
        val chipProximas: TextView = findViewById(R.id.txtCategoriaProximas)
        chips += chipHoy to CategoriasDeCobro.HOY
        chips += chipVencidas to CategoriasDeCobro.VENCIDAS
        chips += chipProximas to CategoriasDeCobro.PROXIMAS

        chips.forEach { (chip, valor) ->
            chip.setOnClickListener {
                if (valor != categoria) {
                    categoria = valor
                    pagina = 1
                    pintarChips()
                    cargar()
                }
            }
        }

        pintarChips()
    }

    private fun pintarChips() {
        chips.forEach { (chip, valor) ->
            val activo = valor == categoria
            chip.setBackgroundResource(
                if (activo) R.drawable.bg_chip_filtro_activo else R.drawable.bg_chip_filtro,
            )
            chip.setTextColor(if (activo) 0xFFFFFFFF.toInt() else 0xFF4A5578.toInt())
            chip.typeface = if (activo) android.graphics.Typeface.DEFAULT_BOLD
            else android.graphics.Typeface.DEFAULT
        }
    }

    // ==========================================================================
    // Carga
    // ==========================================================================

    private fun cargar() {
        val version = ++versionDeCarga

        mostrar(Estado.CARGANDO)

        Diarix.enHiloDeFondo {
            try {
                val pedida: PaginaDeCobros =
                    Diarix.prestamos.cobros(categoria, pagina, POR_PAGINA)

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

    private fun pintar(pedida: PaginaDeCobros) {
        total = pedida.total
        totalPaginas = maxOf(pedida.totalPaginas, 1)

        txtResumen.text = when {
            total == 1 -> "1 cobro · ${CategoriasDeCobro.etiqueta(categoria)}"
            else -> "$total cobros · ${CategoriasDeCobro.etiqueta(categoria)}"
        }

        if (pedida.datos.isEmpty()) {
            mostrar(Estado.VACIO)
            txtVacioTitulo.text = cuandoVacio()
            txtVacioMensaje.text = cuandoVacioDetalle()
            return
        }

        lista.adapter =
            CobrosAdapter(pedida.datos, ::abrirDetalle)

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

    private fun abrirDetalle(cobro: Cobro) {
        startActivity(PrestamoDetalleActivity.abrir(this, cobro.prestamoId))
    }

    private fun cuandoVacio(): String = when (categoria) {
        CategoriasDeCobro.HOY -> "No hay cobros para hoy"
        CategoriasDeCobro.VENCIDAS -> "No hay cuotas vencidas"
        else -> "No hay cobros próximos"
    }

    private fun cuandoVacioDetalle(): String = when (categoria) {
        CategoriasDeCobro.HOY ->
            "Nada por cobrar hoy. Los demás días aparecen en su categoría."
        CategoriasDeCobro.VENCIDAS ->
            "Ninguna cuota pasó su fecha de vencimiento sin pagarse."
        else ->
            "No hay cuotas por vencer en los próximos días."
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