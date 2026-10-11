package com.example.totaldiaria.modulos.facturacion.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.core.navegacion.BottomNavigator
import com.example.totaldiaria.modulos.facturacion.adaptador.ClientesAdapter
import com.example.totaldiaria.modulos.facturacion.modelo.PaginaDeClientes
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Fase 2 · Listado de clientes (RF-02).
 *
 * El padrón de la empresa o del usuario individual, con búsqueda y con la
 * paginación que calcula el SERVIDOR. Esta pantalla solo pide "página N con
 * M por página" y pinta lo que llega: no filtra, no corta y no reordena
 * localmente, porque hacerlo produciría un "Página 1 de 3" que no coincide
 * con lo que el servidor cree que hay.
 *
 * CUATRO ESTADOS, y solo uno visible cada vez (ver `activity_clientes.xml`):
 * Cargando, Vacío, Error y Lista. La app no guarda clientes en SQLite, así
 * que sin conexión el estado es Error con su botón de Reintentar: es lo que
 * ocurre y no se disimula con una lista que parece cargada.
 *
 * La búsqueda escribe con retardo (400 ms) y CANCELA la anterior: sin eso,
 * cada pulsación de tecla lanza una petición y cuatro tecleos producen cuatro
 * respuestas que llegan fuera de orden y pegan la última antes que la
 * penúltima. El contador `versionDeCarga` descarta respuestas tardías.
 *
 * NO hay caja de búsqueda en el layout con resultados locales: buscar contra
 * la base del teléfono y buscar contra la del servidor serían dos
 * comportamientos distintos con el mismo cuadro de texto.
 */
class ClientesActivity : AppCompatActivity() {

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
        setContentView(R.layout.activity_clientes)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        vincularVistas()

        // Cambio de rumbo pos-Fase 2: Clientes pertenece al área de PRÉSTAMOS
        // y su pestaña es `nav_prestamos_clientes`.
        BottomNavigator.configurar(this, R.id.nav_prestamos_clientes)

        // El engranaje del encabezado, igual que en el resto de pantallas del
        // área: Configuración se alcanza desde cualquiera de ellas.
        findViewById<View>(R.id.btnAbrirConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        estadoVacio.visibility = LinearLayout.VISIBLE
        progreso.visibility = ProgressBar.GONE
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_prestamos_clientes)
        cargar()
    }

    override fun onDestroy() {
        super.onDestroy()
        esperaDeBusqueda?.let { reloj.removeCallbacks(it) }
    }

    private fun vincularVistas() {
        lista = findViewById(R.id.listaClientes)
        progreso = findViewById(R.id.progresoClientes)
        estadoVacio = findViewById(R.id.estadoVacioClientes)
        estadoError = findViewById(R.id.estadoErrorClientes)
        txtError = findViewById(R.id.txtErrorClientes)
        txtResumen = findViewById(R.id.txtResumenClientes)
        txtVacioTitulo = findViewById(R.id.txtVacioTitulo)
        txtVacioMensaje = findViewById(R.id.txtVacioMensaje)
        edtBuscar = findViewById(R.id.edtBuscarCliente)
        barraPaginacion = findViewById(R.id.barraPaginacion)
        txtPagina = findViewById(R.id.txtPaginaClientes)
        btnAnterior = findViewById(R.id.btnPaginaAnterior)
        btnSiguiente = findViewById(R.id.btnPaginaSiguiente)

        lista.layoutManager = LinearLayoutManager(this)

        findViewById<Button>(R.id.btnNuevoCliente).setOnClickListener {
            startActivity(ClienteFormularioActivity.nueva(this))
        }

        findViewById<Button>(R.id.btnReintentarClientes).setOnClickListener { cargar() }

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
                // JSON raro, estado que nadie preveía: mensaje genérico y ya.
                // La traza no sale de esta capa.
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
        total = pedida.total
        totalPaginas = maxOf(pedida.totalPaginas, 1)

        txtResumen.text = when {
            total == 1 -> "1 cliente"
            texto == null -> "$total clientes"
            else -> "$total resultados"
        }

        if (pedida.datos.isEmpty()) {
            mostrar(Estado.VACIO)

            if (texto == null) {
                txtVacioTitulo.text = "Todavía no hay clientes"
                txtVacioMensaje.text = "Registra el primero con el botón de abajo."
            } else {
                txtVacioTitulo.text = "Sin resultados"
                txtVacioMensaje.text = "No encontramos clientes que coincidan con \"$texto\"."
            }
            return
        }

        lista.adapter = ClientesAdapter(pedida.datos) { cliente ->
            startActivity(ClienteFormularioActivity.editar(this, cliente.id))
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

        // Solo cuando hay algo que mostrar: una barra de páginas de 1 de 1
        // sobre el estado vacío confunde más de lo que informa.
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
