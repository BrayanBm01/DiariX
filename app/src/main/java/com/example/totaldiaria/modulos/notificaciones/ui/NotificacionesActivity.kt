package com.example.totaldiaria.modulos.notificaciones.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.notificaciones.adaptador.NotificacionesAdapter
import com.example.totaldiaria.modulos.notificaciones.modelo.Notificacion
import com.example.totaldiaria.modulos.notificaciones.modelo.PaginaDeNotificaciones
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Bandeja de notificaciones propias
 *
 * Lista los avisos que la web generó al confirmar un pago o la recepción de
 * un comprobante. El servidor decide qué ve cada cuenta: esta pantalla no
 * manda ni acepta un id de destinatario.
 *
 * Se refresca sola cada [INTERVALO_REFRESCO_MS] mientras está visible y al
 * volver a ella. La recarga periódica es SILENCIOSA: no cambia a la pantalla
 * de carga ni salta al principio, solo repinta cuando llegan datos. Así un
 * aviso que entra mientras la persona lee la lista aparece sin interrumpirla.
 *
 * Marcar como leída no borra nada: la fila sigue en el historial y solo
 * desaparece el punto de pendiente. La marca se hace contra el servidor y
 * después se recarga la página, porque el estado autoritativo es el suyo.
 */
class NotificacionesActivity : AppCompatActivity() {

    private lateinit var lista: RecyclerView
    private lateinit var adaptador: NotificacionesAdapter
    private lateinit var progreso: ProgressBar
    private lateinit var estadoVacio: LinearLayout
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var txtResumen: TextView
    private lateinit var btnMarcarTodas: TextView
    private lateinit var barraPaginacion: LinearLayout
    private lateinit var txtPagina: TextView
    private lateinit var btnAnterior: ImageButton
    private lateinit var btnSiguiente: ImageButton

    private val reloj = Handler(Looper.getMainLooper())

    /** Cada recarga sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    private var pagina = 1
    private var totalPaginas = 1
    private var pendientes = 0
    private var total = 0
    private var estadoActual = Estado.CARGANDO

    private var cargando = false

    private val refrescoEnVivo = object : Runnable {
        override fun run() {
            cargar(silencioso = true)
            reloj.postDelayed(this, INTERVALO_REFRESCO_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notificaciones)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        vincularVistas()
        mostrar(Estado.VACIO)
    }

    override fun onResume() {
        super.onResume()

        cargar(silencioso = false)
        reloj.removeCallbacks(refrescoEnVivo)
        reloj.postDelayed(refrescoEnVivo, INTERVALO_REFRESCO_MS)
    }

    override fun onPause() {
        super.onPause()
        reloj.removeCallbacks(refrescoEnVivo)
    }

    override fun onDestroy() {
        super.onDestroy()
        reloj.removeCallbacks(refrescoEnVivo)
    }

    private fun vincularVistas() {
        lista = findViewById(R.id.listaNotificaciones)
        progreso = findViewById(R.id.progresoNotificaciones)
        estadoVacio = findViewById(R.id.estadoVacioNotificaciones)
        estadoError = findViewById(R.id.estadoErrorNotificaciones)
        txtError = findViewById(R.id.txtErrorNotificaciones)
        txtResumen = findViewById(R.id.txtResumenNotificaciones)
        btnMarcarTodas = findViewById(R.id.btnMarcarTodasNotificaciones)
        barraPaginacion = findViewById(R.id.barraPaginacionNotificaciones)
        txtPagina = findViewById(R.id.txtPaginaNotificaciones)
        btnAnterior = findViewById(R.id.btnPaginaAnteriorNotificaciones)
        btnSiguiente = findViewById(R.id.btnPaginaSiguienteNotificaciones)

        lista.layoutManager = LinearLayoutManager(this)

        adaptador = NotificacionesAdapter { notificacion ->
            marcarLeida(notificacion)
        }

        lista.adapter = adaptador

        findViewById<ImageButton>(R.id.btnVolverNotificaciones)
            .setOnClickListener { finish() }

        findViewById<Button>(R.id.btnReintentarNotificaciones)
            .setOnClickListener { cargar(silencioso = false) }

        btnMarcarTodas.setOnClickListener { marcarTodas() }

        btnAnterior.setOnClickListener {
            if (pagina > 1) {
                pagina--
                cargar(silencioso = false)
            }
        }

        btnSiguiente.setOnClickListener {
            if (pagina < totalPaginas) {
                pagina++
                cargar(silencioso = false)
            }
        }
    }

    // ==========================================================================
    // Carga
    // ==========================================================================

    /**
     * [silencioso] es la recarga periódica: no muestra el indicador de carga,
     * no cambia a Vacío/Error si ya había una lista y no desplaza el scroll.
     * Un fallo de red en esa recarga no tapa lo que la persona está leyendo.
     */
    private fun cargar(silencioso: Boolean) {
        if (cargando) return

        val version = ++versionDeCarga
        cargando = true

        if (!silencioso) mostrar(Estado.CARGANDO)

        Diarix.enHiloDeFondo {
            try {
                val pedida = Diarix.notificaciones.listar(pagina, POR_PAGINA)

                runOnUiThread {
                    cargando = false
                    if (version != versionDeCarga) return@runOnUiThread
                    pintar(pedida, silencioso)
                }
            } catch (e: ErrorDiarix) {
                runOnUiThread {
                    cargando = false
                    if (version != versionDeCarga) return@runOnUiThread
                    fallar(e.mensajeAmigable, silencioso)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    cargando = false
                    if (version != versionDeCarga) return@runOnUiThread
                    fallar(
                        ErrorDiarix.Inesperado(e.message ?: "").mensajeAmigable,
                        silencioso,
                    )
                }
            }
        }
    }

    private fun fallar(mensaje: String, silencioso: Boolean) {
        // Si ya hay contenido en pantalla, una recarga de fondo fallida no lo
        // borra: se conserva la última lista buena y se informa por Toast.
        if (silencioso && (estadoActual == Estado.LISTA || estadoActual == Estado.VACIO)) {
            Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
            return
        }

        txtError.text = mensaje
        mostrar(Estado.ERROR)
    }

    private fun pintar(pedida: PaginaDeNotificaciones, silencioso: Boolean) {
        total = pedida.total
        pendientes = pedida.pendientes
        totalPaginas = maxOf(pedida.totalPaginas, 1)

        actualizarResumen()

        if (pedida.datos.isEmpty()) {
            adaptador.actualizar(emptyList())

            // Si la bandeja está vacía porque no hay nada, es el estado Vacío.
            // Si había una lista y la página quedó vacía (por ejemplo, al
            // marcar todas y recargar), también: el servidor ya no tiene filas.
            mostrar(Estado.VACIO)
            return
        }

        adaptador.actualizar(pedida.datos)

        if (totalPaginas > 1) {
            txtPagina.text = "Página ${pedida.pagina} de $totalPaginas"
            btnAnterior.alpha = if (pagina > 1) 1f else 0.3f
            btnSiguiente.alpha = if (pagina < totalPaginas) 1f else 0.3f
            barraPaginacion.visibility = LinearLayout.VISIBLE
        } else {
            barraPaginacion.visibility = LinearLayout.GONE
        }

        if (!silencioso) lista.scrollToPosition(0)

        mostrar(Estado.LISTA)
    }

    private fun actualizarResumen() {
        txtResumen.text = when {
            total == 0 -> "0 notificaciones"
            pendientes == 0 -> "$total ${plural(total, "notificación", "notificaciones")}"
            else ->
                "$total ${plural(total, "notificación", "notificaciones")} · " +
                    "$pendientes sin leer"
        }

        btnMarcarTodas.visibility =
            if (pendientes > 0) View.VISIBLE else View.GONE
    }

    private fun mostrar(estado: Estado) {
        estadoActual = estado

        progreso.visibility =
            if (estado == Estado.CARGANDO) ProgressBar.VISIBLE else ProgressBar.GONE

        estadoVacio.visibility =
            if (estado == Estado.VACIO) LinearLayout.VISIBLE else LinearLayout.GONE

        estadoError.visibility =
            if (estado == Estado.ERROR) LinearLayout.VISIBLE else LinearLayout.GONE

        lista.visibility =
            if (estado == Estado.LISTA) RecyclerView.VISIBLE else RecyclerView.GONE

        if (estado != Estado.LISTA) {
            barraPaginacion.visibility = LinearLayout.GONE
        }
    }

    // ==========================================================================
    // Leer
    // ==========================================================================

    private fun marcarLeida(notificacion: Notificacion) {
        if (notificacion.leida) return

        Diarix.enHiloDeFondo {
            try {
                Diarix.notificaciones.marcarLeida(notificacion.id)

                runOnUiThread { cargar(silencioso = true) }
            } catch (e: ErrorDiarix) {
                runOnUiThread {
                    Toast.makeText(this, e.mensajeAmigable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun marcarTodas() {
        if (pendientes == 0) return

        btnMarcarTodas.isEnabled = false

        Diarix.enHiloDeFondo {
            try {
                val marcadas = Diarix.notificaciones.marcarTodas()

                runOnUiThread {
                    btnMarcarTodas.isEnabled = true
                    Toast.makeText(
                        this,
                        if (marcadas == 1) {
                            "1 notificación marcada como leída"
                        } else {
                            "$marcadas notificaciones marcadas como leídas"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()

                    cargar(silencioso = true)
                }
            } catch (e: ErrorDiarix) {
                runOnUiThread {
                    btnMarcarTodas.isEnabled = true
                    Toast.makeText(this, e.mensajeAmigable, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun plural(cantidad: Int, singular: String, plural: String): String =
        if (cantidad == 1) singular else plural

    private enum class Estado {
        CARGANDO,
        VACIO,
        ERROR,
        LISTA,
    }

    private companion object {
        const val POR_PAGINA = 20

        /** 30 s: suficiente para que un aviso llegue «solo» sin castigar la red. */
        const val INTERVALO_REFRESCO_MS = 30_000L
    }
}
