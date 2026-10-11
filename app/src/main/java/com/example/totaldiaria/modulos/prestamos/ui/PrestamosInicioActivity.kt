package com.example.totaldiaria.modulos.prestamos.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.core.navegacion.BottomNavigator
import com.example.totaldiaria.modulos.facturacion.ui.ConfiguracionActivity
import com.example.totaldiaria.modulos.prestamos.modelo.ResumenDePrestamos
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Inicio del área de PRÉSTAMOS.
 *
 * Pinta el resumen REAL del área desde `GET /prestamos/resumen`: préstamos
 * activos, saldo pendiente, cobros de hoy, cuotas vencidas, próximos cobros y
 * recaudado de hoy. TODO lo calcula el servidor con los datos de la cartera;
 * esta pantalla solo repite los números.
 *
 * La lista de "PRÓXIMAMENTE" de la Fase 1 desapareció con la Fase 4: las tres
 * secciones del menú (Préstamos, Cobros y Pagos) ya abren pantallas reales.
 * Debajo del resumen quedan dos accesos directos a Cobros y Pagos, que son
 * los dos lugares desde los que se cobra y se revisa la cartera.
 */
class PrestamosInicioActivity : AppCompatActivity() {

    private lateinit var progreso: ProgressBar
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var panelResumen: ScrollView

    private lateinit var txtResumen: TextView
    private lateinit var txtPrestamosActivos: TextView
    private lateinit var txtSaldoPendiente: TextView
    private lateinit var txtCobrosHoyCantidad: TextView
    private lateinit var txtCobrosHoyValor: TextView
    private lateinit var txtVencidasCantidad: TextView
    private lateinit var txtVencidasValor: TextView
    private lateinit var txtVencidasDias: TextView
    private lateinit var txtProximosCantidad: TextView
    private lateinit var txtProximosValor: TextView
    private lateinit var txtRecaudadoHoyCantidad: TextView
    private lateinit var txtRecaudadoHoyValor: TextView

    /** Cada recarga sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Diarix.iniciar(this)

        setContentView(R.layout.activity_prestamos_inicio)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        vincularVistas()

        // El engranaje del encabezado: la salida del área. Configuración es la
        // única pantalla desde la que se cambia de área, así que tiene que ser
        // alcanzable desde las DOS áreas.
        findViewById<View>(R.id.btnAbrirConfiguracion).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        // Menú propio del área: aquí no aparece ninguna pestaña de facturación.
        BottomNavigator.configurar(this, R.id.nav_prestamos_inicio)
    }

    override fun onResume() {
        super.onResume()

        BottomNavigator.sincronizarPestana(this, R.id.nav_prestamos_inicio)
        cargarResumen()
    }

    private fun vincularVistas() {
        progreso = findViewById(R.id.progresoInicio)
        estadoError = findViewById(R.id.estadoErrorInicio)
        txtError = findViewById(R.id.txtErrorInicio)
        panelResumen = findViewById(R.id.panelResumenInicio)

        txtResumen = findViewById(R.id.txtResumenInicio)
        txtPrestamosActivos = findViewById(R.id.txtPrestamosActivos)
        txtSaldoPendiente = findViewById(R.id.txtSaldoPendiente)
        txtCobrosHoyCantidad = findViewById(R.id.txtCobrosHoyCantidad)
        txtCobrosHoyValor = findViewById(R.id.txtCobrosHoyValor)
        txtVencidasCantidad = findViewById(R.id.txtVencidasCantidad)
        txtVencidasValor = findViewById(R.id.txtVencidasValor)
        txtVencidasDias = findViewById(R.id.txtVencidasDias)
        txtProximosCantidad = findViewById(R.id.txtProximosCantidad)
        txtProximosValor = findViewById(R.id.txtProximosValor)
        txtRecaudadoHoyCantidad = findViewById(R.id.txtRecaudadoHoyCantidad)
        txtRecaudadoHoyValor = findViewById(R.id.txtRecaudadoHoyValor)

        findViewById<View>(R.id.btnReintentarInicio).setOnClickListener { cargarResumen() }

        findViewById<View>(R.id.tarjetaAccesoCobros).setOnClickListener {
            startActivity(Intent(this, PrestamosCobrosActivity::class.java))
        }

        findViewById<View>(R.id.tarjetaAccesoPagos).setOnClickListener {
            startActivity(Intent(this, PrestamosPagosActivity::class.java))
        }
    }

    // ==========================================================================
    // Resumen
    // ==========================================================================

    private fun cargarResumen() {
        val version = ++versionDeCarga

        mostrar(Estado.CARGANDO)

        Diarix.enHiloDeFondo {
            try {
                val resumen = Diarix.prestamos.resumen()

                runOnUiThread {
                    if (version != versionDeCarga) return@runOnUiThread
                    pintar(resumen)
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
            txtError.text = mensaje
            mostrar(Estado.ERROR)
        }
    }

    private fun pintar(resumen: ResumenDePrestamos) {
        txtResumen.text = buildString {
            append(resumen.prestamosActivos)
            append(if (resumen.prestamosActivos == 1) " préstamo activo" else " préstamos activos")
        }

        txtPrestamosActivos.text = resumen.prestamosActivos.toString()
        txtSaldoPendiente.text = FormatoPrestamos.formatear(resumen.saldoPendienteTotal)

        val hoy = resumen.cobrosDeHoy
        txtCobrosHoyCantidad.text = hoy.cantidad.toString()
        txtCobrosHoyValor.text = FormatoPrestamos.formatear(hoy.valor)

        val vencidas = resumen.cuotasVencidas
        txtVencidasCantidad.text = vencidas.cantidad.toString()
        txtVencidasValor.text = FormatoPrestamos.formatear(vencidas.valor)
        txtVencidasDias.text = if (vencidas.diasMaximosDeAtraso > 0) {
            "${vencidas.diasMaximosDeAtraso} " +
                (if (vencidas.diasMaximosDeAtraso == 1) "día" else "días") +
                " de atraso"
        } else {
            ""
        }

        val proximos = resumen.proximosCobros
        txtProximosCantidad.text = proximos.cantidad.toString()
        txtProximosValor.text = FormatoPrestamos.formatear(proximos.valor)

        val recaudado = resumen.recaudadoHoy
        txtRecaudadoHoyCantidad.text = recaudado.cantidad.toString()
        txtRecaudadoHoyValor.text = FormatoPrestamos.formatear(recaudado.valor)

        mostrar(Estado.LISTA)
    }

    private fun mostrar(estado: Estado) {
        progreso.visibility = if (estado == Estado.CARGANDO) ProgressBar.VISIBLE else ProgressBar.GONE
        estadoError.visibility = if (estado == Estado.ERROR) LinearLayout.VISIBLE else LinearLayout.GONE
        panelResumen.visibility = if (estado == Estado.LISTA) ScrollView.VISIBLE else ScrollView.GONE
    }

    private enum class Estado { CARGANDO, ERROR, LISTA }
}