package com.example.totaldiaria.modulos.prestamos.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.prestamos.adaptador.CuotasAdapter
import com.example.totaldiaria.modulos.prestamos.modelo.DetalleDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.EstadosDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.ModalidadesDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.ReglasDeEscrituraDePagos
import com.example.totaldiaria.network.ErrorDiarix

/**
 * DIARIX · Fase 3 · Detalle de un préstamo con su calendario (RF-07/RF-08).
 *
 * Trae la ficha desde `GET /prestamos/:id` y pinta el acuerdo + el atraso
 * derivado + el calendario de cuotas generado por el servidor. Igual que el
 * listado, sin conexión dice el Error con Reintentar: nada de una ficha a
 * medias que parezca cargada.
 *
 * Fase 4: desde aquí se registra un pago (abre [PrestamoPagoActivity]) y se
 * revierte SOLO el último pago vivo del préstamo, por el backend. El botón de
 * reversión se habilita según lo que diga `GET /prestamos/:id/pagos`.
 */
class PrestamoDetalleActivity : AppCompatActivity() {

    private lateinit var progreso: ProgressBar
    private lateinit var estadoError: LinearLayout
    private lateinit var txtError: TextView
    private lateinit var scroll: ScrollView
    private lateinit var listaCuotas: RecyclerView
    private lateinit var btnRegistrar: Button
    private lateinit var btnRevertir: Button

    private lateinit var txtCliente: TextView
    private lateinit var txtDocumento: TextView
    private lateinit var txtEstado: TextView
    private lateinit var tarjetaAtraso: LinearLayout
    private lateinit var txtAtraso: TextView
    private lateinit var txtValorAtrasado: TextView
    private lateinit var txtTotal: TextView
    private lateinit var txtPrestado: TextView
    private lateinit var txtValorCuota: TextView
    private lateinit var txtCuotas: TextView
    private lateinit var txtRitmo: TextView
    private lateinit var txtInicio: TextView
    private lateinit var txtPagado: TextView
    private lateinit var txtSaldo: TextView
    private lateinit var txtEstadoCuotas: TextView

    private var prestamoId: String? = null

    /** Id del último pago VIVO (no revertido), si lo hay. */
    private var ultimoPagoId: String? = null

    /** Cada recarga sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    private val registroDePago =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            if (resultado.resultCode == Activity.RESULT_OK) cargar()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prestamo_detalle)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        prestamoId = intent.getStringExtra(EXTRA_PRESTAMO_ID)

        vincularVistas()

        findViewById<View>(R.id.btnVolverDetalle).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnReintentarDetalle).setOnClickListener { cargar() }

        cargar()
    }

    private fun vincularVistas() {
        progreso = findViewById(R.id.progresoDetalle)
        estadoError = findViewById(R.id.estadoErrorDetalle)
        txtError = findViewById(R.id.txtErrorDetalle)
        scroll = findViewById(R.id.scrollDetalle)
        listaCuotas = findViewById(R.id.listaCuotas)
        btnRegistrar = findViewById(R.id.btnRegistrarPagoDetalle)
        btnRevertir = findViewById(R.id.btnRevertirPagoDetalle)

        txtCliente = findViewById(R.id.txtDetalleCliente)
        txtDocumento = findViewById(R.id.txtDetalleDocumento)
        txtEstado = findViewById(R.id.txtDetalleEstado)
        tarjetaAtraso = findViewById(R.id.tarjetaAtraso)
        txtAtraso = findViewById(R.id.txtDetalleAtraso)
        txtValorAtrasado = findViewById(R.id.txtDetalleValorAtrasado)
        txtTotal = findViewById(R.id.txtDetalleTotal)
        txtPrestado = findViewById(R.id.txtDetallePrestado)
        txtValorCuota = findViewById(R.id.txtDetalleValorCuota)
        txtCuotas = findViewById(R.id.txtDetalleCuotas)
        txtRitmo = findViewById(R.id.txtDetalleRitmo)
        txtInicio = findViewById(R.id.txtDetalleInicio)
        txtPagado = findViewById(R.id.txtDetallePagado)
        txtSaldo = findViewById(R.id.txtDetalleSaldo)
        txtEstadoCuotas = findViewById(R.id.txtDetalleEstadoCuotas)

        listaCuotas.layoutManager = LinearLayoutManager(this)

        btnRegistrar.setOnClickListener {
            val id = prestamoId ?: return@setOnClickListener
            registroDePago.launch(PrestamoPagoActivity.nueva(this, id))
        }

        btnRevertir.setOnClickListener {
            val pagoId = ultimoPagoId ?: return@setOnClickListener
            preguntarReversion(pagoId)
        }
    }

    // ==========================================================================
    // Carga
    // ==========================================================================

    private fun cargar() {
        val id = prestamoId
        if (id == null) {
            mostrarError("Este préstamo no existe.")
            return
        }

        val version = ++versionDeCarga

        bloquearAcciones()
        mostrar(Estado.CARGANDO)

        Diarix.enHiloDeFondo {
            try {
                val detalle = Diarix.prestamos.obtener(id)

                // El último pago VIVO (no revertido) decide si la reversión es
                // posible: el servidor solo deja revertir el último.
                val paginaDePagos = Diarix.prestamos.pagosDePrestamo(id, 1, 1)
                val ultimoVivo = paginaDePagos.datos.firstOrNull { !it.revertido }?.id

                runOnUiThread {
                    if (version != versionDeCarga) return@runOnUiThread
                    ultimoPagoId = ultimoVivo
                    pintar(detalle)
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
            bloquearAcciones()
            mostrarError(mensaje)
        }
    }

    private fun bloquearAcciones() {
        btnRegistrar.isEnabled = false
        btnRevertir.isEnabled = false
    }

    private fun pintar(detalle: DetalleDePrestamo) {
        val prestamo = detalle.prestamo

        txtCliente.text = prestamo.cliente?.nombreCompleto ?: "Cliente eliminado"
        txtDocumento.text = prestamo.cliente?.documentoConEtiqueta()
            ?: "Préstamo cerrado, sin referencia al cliente"

        val color = Color.parseColor(EstadosDePrestamo.color(prestamo.estado))
        txtEstado.text = EstadosDePrestamo.etiqueta(prestamo.estado)
        txtEstado.setTextColor(color)

        // Atraso: solo si el servidor lo derivó.
        if (detalle.diasDeAtraso > 0) {
            tarjetaAtraso.visibility = LinearLayout.VISIBLE
            txtAtraso.text = buildString {
                append("${detalle.diasDeAtraso} ")
                append(if (detalle.diasDeAtraso == 1) "día" else "días")
                append(" de atraso · ")
                append(detalle.numeroDeCuotasAtrasadas)
                append(if (detalle.numeroDeCuotasAtrasadas == 1) " cuota vencida" else " cuotas vencidas")
            }
            txtValorAtrasado.text =
                "Capital en mora: ${FormatoPrestamos.formatear(detalle.valorAtrasado)}"
        } else {
            tarjetaAtraso.visibility = LinearLayout.GONE
        }

        txtTotal.text = FormatoPrestamos.formatear(prestamo.totalAPagar)
        txtPrestado.text = FormatoPrestamos.formatear(prestamo.montoPrestado)
        txtValorCuota.text = FormatoPrestamos.formatear(prestamo.valorCuota)
        txtCuotas.text = buildString {
            append("${prestamo.numeroDeCuotas} en total · ")
            append("${prestamo.cuotasPagadas} pagadas")
        }
        txtRitmo.text = ModalidadesDePrestamo.etiqueta(prestamo.modalidad)
        txtInicio.text = FormatoPrestamos.formatearFecha(prestamo.fechaInicio)
        txtPagado.text = FormatoPrestamos.formatear(prestamo.totalPagado)
        txtSaldo.text = FormatoPrestamos.formatear(prestamo.saldoPendiente)

        txtEstadoCuotas.text = "${prestamo.cuotasPendientes} por pagar"
        listaCuotas.adapter = CuotasAdapter(detalle.cuotas)

        btnRegistrar.isEnabled = true
        btnRevertir.isEnabled = ultimoPagoId != null

        mostrar(Estado.LISTA)
    }

    // ==========================================================================
    // Reversión del último pago (solo por el backend, con motivo)
    // ==========================================================================

    private fun preguntarReversion(pagoId: String) {
        val campo = EditText(this)
        campo.hint = "Motivo (obligatorio)"

        AlertDialog.Builder(this)
            .setTitle("Revertir último pago")
            .setMessage(
                "La reversión la hace el servidor: las cuotas vuelven a quedar " +
                    "pendientes y el motivo queda en la auditoría. El pago no se borra.",
            )
            .setView(campo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Revertir") { _, _ ->
                revertir(pagoId, campo.text?.toString().orEmpty())
            }
            .show()
    }

    private fun revertir(pagoId: String, motivo: String) {
        val fallo = ReglasDeEscrituraDePagos.motivoDeReversionValido(motivo)
        if (fallo != null) {
            Toast.makeText(this, fallo, Toast.LENGTH_LONG).show()
            return
        }

        bloquearAcciones()

        Diarix.enHiloDeFondo {
            try {
                Diarix.prestamos.revertirPago(pagoId, motivo.trim())
                runOnUiThread {
                    Toast.makeText(this, "Pago revertido.", Toast.LENGTH_SHORT).show()
                    cargar()
                }
            } catch (e: ErrorDiarix) {
                runOnUiThread {
                    Toast.makeText(this, e.mensajeAmigable, Toast.LENGTH_LONG).show()
                    cargar()
                }
            } catch (e: Exception) {
                val mensaje = ErrorDiarix.Inesperado(e.message ?: "").mensajeAmigable
                runOnUiThread {
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    cargar()
                }
            }
        }
    }

    private fun mostrar(estado: Estado) {
        progreso.visibility = if (estado == Estado.CARGANDO) ProgressBar.VISIBLE else ProgressBar.GONE
        estadoError.visibility = if (estado == Estado.ERROR) LinearLayout.VISIBLE else LinearLayout.GONE
        scroll.visibility = if (estado == Estado.LISTA) ScrollView.VISIBLE else ScrollView.GONE
    }

    private fun mostrarError(mensaje: String) {
        txtError.text = mensaje
        mostrar(Estado.ERROR)
    }

    private enum class Estado { CARGANDO, ERROR, LISTA }

    companion object {
        private const val EXTRA_PRESTAMO_ID = "prestamo_id"

        fun abrir(context: Context, prestamoId: String): Intent =
            Intent(context, PrestamoDetalleActivity::class.java)
                .putExtra(EXTRA_PRESTAMO_ID, prestamoId)
    }
}