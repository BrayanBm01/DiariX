package com.example.totaldiaria.modulos.prestamos.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
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
import com.example.totaldiaria.modulos.facturacion.adaptador.ClientesAdapter
import com.example.totaldiaria.modulos.facturacion.modelo.PaginaDeClientes
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Fase 3 · Selector de cliente del alta de préstamo.
 *
 * No es un padrón nuevo ni una copia de [ClientesActivity]: es el MISMO
 * repositorio de clientes (misma búsqueda `q`, misma paginación del
 * servidor), solo que el toque en una fila devuelve ese cliente por
 * `setResult` en lugar de abrir su ficha. Una sola fuente de clientes para
 * toda la app: si un día cambia la búsqueda, cambia en un sitio.
 *
 * El cliente elegido viaja como ID + nombre + documento mostrable (extras de
 * [resultadoExtras]), porque un [com.example.totaldiaria.modulos.facturacion.modelo.Cliente]
 * no es Parcelable y los extras de un Intent tienen que serlo.
 */
class SelectorDeClienteActivity : AppCompatActivity() {

    private lateinit var lista: RecyclerView
    private lateinit var progreso: ProgressBar
    private lateinit var estadoVacio: LinearLayout
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var txtVacio: TextView
    private lateinit var txtVacioMensaje: TextView
    private lateinit var edtBuscar: EditText

    private val reloj = Handler(Looper.getMainLooper())
    private var esperaDeBusqueda: Runnable? = null

    /** Cada recarga sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    private var pagina = 1
    private var texto: String? = null
    private var totalPaginas = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_selector_cliente)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        vincularVistas()

        findViewById<View>(R.id.btnVolverSelector).setOnClickListener { finish() }

        // La pantalla arranca cargando; el contenido llega con la primera página.
        progreso.visibility = ProgressBar.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    override fun onDestroy() {
        super.onDestroy()
        esperaDeBusqueda?.let { reloj.removeCallbacks(it) }
    }

    private fun vincularVistas() {
        lista = findViewById(R.id.listaClientesSelector)
        progreso = findViewById(R.id.progresoSelector)
        estadoVacio = findViewById(R.id.estadoVacioSelector)
        estadoError = findViewById(R.id.estadoErrorSelector)
        txtError = findViewById(R.id.txtErrorSelector)
        txtVacio = findViewById(R.id.txtVacioSelector)
        txtVacioMensaje = findViewById(R.id.txtVacioSelectorMensaje)
        edtBuscar = findViewById(R.id.edtBuscarClienteSelector)

        lista.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnReintentarSelector).setOnClickListener { cargar() }

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
                val pedida = Diarix.clientes.listar(pagina, POR_PAGINA, texto)

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

    private fun pintar(pedida: PaginaDeClientes) {
        totalPaginas = maxOf(pedida.totalPaginas, 1)

        if (pedida.datos.isEmpty()) {
            mostrar(Estado.VACIO)
            if (texto == null) {
                txtVacio.text = "Todavía no hay clientes"
                txtVacioMensaje.text = "Registra clientes en la pestaña Clientes para poder prestarles."
            } else {
                txtVacio.text = "Sin resultados"
                txtVacioMensaje.text = "No encontramos clientes que coincidan con \"$texto\"."
            }
            return
        }

        lista.adapter = ClientesAdapter(pedida.datos) { cliente ->
            setResult(
                Activity.RESULT_OK,
                Intent().putExtras(resultadoExtras(cliente)),
            )
            finish()
        }

        lista.scrollToPosition(0)
        mostrar(Estado.LISTA)
    }

    private fun resultadoExtras(
        cliente: com.example.totaldiaria.modulos.facturacion.modelo.Cliente,
    ): android.os.Bundle =
        Bundle().apply {
            putString(EXTRA_CLIENTE_ID, cliente.id)
            putString(EXTRA_CLIENTE_NOMBRE, cliente.nombreCompleto)
            putString(EXTRA_CLIENTE_DOCUMENTO, cliente.documentoConEtiqueta())
        }

    private fun mostrar(estado: Estado) {
        progreso.visibility = if (estado == Estado.CARGANDO) ProgressBar.VISIBLE else ProgressBar.GONE
        estadoVacio.visibility = if (estado == Estado.VACIO) LinearLayout.VISIBLE else LinearLayout.GONE
        estadoError.visibility = if (estado == Estado.ERROR) LinearLayout.VISIBLE else LinearLayout.GONE
        lista.visibility = if (estado == Estado.LISTA) RecyclerView.VISIBLE else RecyclerView.GONE
    }

    private fun mostrarError(mensaje: String) {
        txtError.text = mensaje
        mostrar(Estado.ERROR)
    }

    private enum class Estado { CARGANDO, VACIO, ERROR, LISTA }

    companion object {
        const val EXTRA_CLIENTE_ID = "selector_cliente_id"
        const val EXTRA_CLIENTE_NOMBRE = "selector_cliente_nombre"
        const val EXTRA_CLIENTE_DOCUMENTO = "selector_cliente_documento"

        fun abrir(context: Context): Intent = Intent(context, SelectorDeClienteActivity::class.java)

        private const val POR_PAGINA = 25
    }
}