package com.example.totaldiaria.modulos.prestamos.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.prestamos.modelo.ComprobanteElegido
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePagoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.DetalleDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.MetodosDePagoDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.ReglasDeEscrituraDePagos
import com.example.totaldiaria.network.ErrorDiarix
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * DIARIX · Fase 4 · Registrar un pago de un préstamo (RF-10/RF-11).
 *
 * Formulario mínimo que arma [DatosDePagoNuevo] y lo manda a
 * `POST /prestamos/:id/pagos`. El servidor reparte el monto entre las cuotas
 * pendientes (la más antigua primero), devuelve el pago y la ficha
 * actualizada; nadie en la app calcula el saldo. `claveUnica` (UUID del
 * móvil) hace que un doble envío no cobre dos veces.
 *
 * El comprobante (solo TRANSFERENCIA) se elige AQUÍ pero se adjunta DESPUÉS
 * del pago, con el `pago.id` que devuelve el servidor, igual que en facturas.
 * Si el archivo rebota (formato o tamaño), el pago ya está registrado y se
 * avisa por separado.
 */
class PrestamoPagoActivity : AppCompatActivity() {

    private lateinit var edtMonto: EditText
    private lateinit var txtCliente: TextView
    private lateinit var txtSaldo: TextView
    private lateinit var txtFecha: TextView
    private lateinit var txtComprobante: TextView
    private lateinit var btnRegistrar: Button

    private lateinit var chipEfectivo: TextView
    private lateinit var chipTransferencia: TextView
    private lateinit var tarjetaComprobante: View

    private var prestamoId: String? = null
    private var saldoPendiente = BigDecimal.ZERO
    private var metodoPago: String = MetodosDePagoDePrestamo.EFECTIVO
    private var fechaPago: String = FormatoPrestamos.hoy()
    private var comprobante: ComprobanteElegido? = null

    /** Bloquea el doble envío mientras viaja el pago. */
    private var registrando = false

    private val elegirComprobante =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) leerComprobante(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prestamo_pago)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        prestamoId = intent.getStringExtra(EXTRA_PRESTAMO_ID)

        vincularVistas()

        findViewById<View>(R.id.btnVolverPago).setOnClickListener { finish() }

        txtFecha.text = FormatoPrestamos.formatearFecha(fechaPago)

        cargarPrestamo()
    }

    private fun vincularVistas() {
        edtMonto = findViewById(R.id.edtMontoPago)
        txtCliente = findViewById(R.id.txtPagoCliente)
        txtSaldo = findViewById(R.id.txtPagoSaldo)
        txtFecha = findViewById(R.id.txtPagoFecha)
        txtComprobante = findViewById(R.id.txtPagoComprobante)
        btnRegistrar = findViewById(R.id.btnRegistrarPago)
        chipEfectivo = findViewById(R.id.txtMetodoEfectivo)
        chipTransferencia = findViewById(R.id.txtMetodoTransferencia)
        tarjetaComprobante = findViewById(R.id.tarjetaComprobante)

        edtMonto.addTextChangedListener(MonedaPrestamoTextWatcher(edtMonto))

        findViewById<View>(R.id.tarjetaFechaPago).setOnClickListener { elegirFecha() }

        chipEfectivo.setOnClickListener {
            metodoPago = MetodosDePagoDePrestamo.EFECTIVO
            pintarMetodo()
        }

        chipTransferencia.setOnClickListener {
            metodoPago = MetodosDePagoDePrestamo.TRANSFERENCIA
            pintarMetodo()
        }

        findViewById<View>(R.id.tarjetaComprobante).setOnClickListener {
            elegirComprobante.launch(arrayOf("image/*", "application/pdf"))
        }

        btnRegistrar.setOnClickListener { guardar() }

        pintarMetodo()
    }

    private fun cargarPrestamo() {
        val id = prestamoId
        if (id == null) {
            avisar("No se sabe para qué préstamo es el pago.")
            finish()
            return
        }

        btnRegistrar.isEnabled = false

        Diarix.enHiloDeFondo {
            try {
                val detalle = Diarix.prestamos.obtener(id)

                runOnUiThread {
                    pintarPrestamo(detalle)
                    btnRegistrar.isEnabled = true
                }
            } catch (e: ErrorDiarix) {
                runOnUiThread {
                    avisar(e.mensajeAmigable)
                    finish()
                }
            } catch (e: Exception) {
                val mensaje = ErrorDiarix.Inesperado(e.message ?: "").mensajeAmigable
                runOnUiThread {
                    avisar(mensaje)
                    finish()
                }
            }
        }
    }

    private fun pintarPrestamo(detalle: DetalleDePrestamo) {
        txtCliente.text = detalle.prestamo.cliente?.nombreCompleto ?: "Cliente eliminado"
        saldoPendiente = detalle.prestamo.saldoPendiente
        txtSaldo.text = "Saldo pendiente: ${FormatoPrestamos.formatear(saldoPendiente)}"
    }

    // ==========================================================================
    // Datos del formulario
    // ==========================================================================

    private fun elegirFecha() {
        val hoy = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, anio, mes, dia ->
                fechaPago = String.format(Locale.US, "%04d-%02d-%02d", anio, mes + 1, dia)
                txtFecha.text = FormatoPrestamos.formatearFecha(fechaPago)
            },
            hoy.get(Calendar.YEAR),
            hoy.get(Calendar.MONTH),
            hoy.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun pintarMetodo() {
        val esTransferencia =
            MetodosDePagoDePrestamo.admiteComprobante(metodoPago)

        pintarChip(chipEfectivo, metodoPago == MetodosDePagoDePrestamo.EFECTIVO)
        pintarChip(chipTransferencia, esTransferencia)

        tarjetaComprobante.visibility = if (esTransferencia) View.VISIBLE else View.GONE

        if (!esTransferencia) comprobante = null
        pintarComprobante()
    }

    private fun pintarChip(chip: TextView, activo: Boolean) {
        chip.setBackgroundResource(
            if (activo) R.drawable.bg_chip_filtro_activo else R.drawable.bg_chip_filtro,
        )
        chip.setTextColor(if (activo) 0xFFFFFFFF.toInt() else 0xFF4A5578.toInt())
        chip.typeface = if (activo) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun pintarComprobante() {
        val elegido = comprobante
        if (elegido == null) {
            txtComprobante.text = "Adjuntar comprobante (JPG, PNG, WEBP, HEIC o PDF, hasta 6 MB)"
            txtComprobante.setTextColor(0xFF7A7A9D.toInt())
        } else {
            txtComprobante.text = elegido.nombre
            txtComprobante.setTextColor(0xFF1C2340.toInt())
        }
    }

    private fun leerComprobante(uri: Uri) {
        val nombre = nombreDe(uri) ?: "comprobante"
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"

        Diarix.enHiloDeFondo {
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (_: Exception) {
                null
            }

            runOnUiThread {
                if (bytes == null) {
                    avisar("No se pudo leer el archivo elegido. Elige otro.")
                    return@runOnUiThread
                }
                if (bytes.size > MAXIMO_BYTES_COMPROBANTE) {
                    avisar("El comprobante supera el máximo de 6 MB. Elige otro.")
                    return@runOnUiThread
                }
                comprobante = ComprobanteElegido(bytes = bytes, nombre = nombre, mime = mime)
                pintarComprobante()
            }
        }
    }

    private fun nombreDe(uri: Uri): String? {
        val campos = arrayOf(OpenableColumns.DISPLAY_NAME)
        return contentResolver.query(uri, campos, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    }

    // ==========================================================================
    // Registro
    // ==========================================================================

    private fun guardar() {
        if (registrando) return

        val id = prestamoId ?: return

        val monto = FormatoPrestamos.parsear(edtMonto.text?.toString().orEmpty())

        val fallo = ReglasDeEscrituraDePagos.montoValido(monto, saldoPendiente)
        if (fallo != null) {
            avisar(fallo)
            return
        }

        registrando = true
        btnRegistrar.isEnabled = false
        btnRegistrar.text = "Registrando…"

        val datos = DatosDePagoNuevo(
            monto = monto,
            metodoPago = metodoPago,
            // Idempotencia: si el viaje se repite, el servidor devuelve el
            // mismo pago (misma clave) en vez de cobrar dos veces.
            claveUnica = UUID.randomUUID().toString(),
            fechaPago = fechaPago,
        )

        Diarix.enHiloDeFondo {
            try {
                val operacion = Diarix.prestamos.registrarPago(id, datos)

                val pendiente = comprobante
                if (MetodosDePagoDePrestamo.admiteComprobante(metodoPago) &&
                    pendiente != null
                ) {
                    adjuntarYTerminar(operacion.pago.id, pendiente)
                } else {
                    runOnUiThread { terminarRegistro(null) }
                }
            } catch (e: ErrorDiarix) {
                runOnUiThread { volverADisponible(e.mensajeAmigable) }
            } catch (e: Exception) {
                val mensaje = ErrorDiarix.Inesperado(e.message ?: "").mensajeAmigable
                runOnUiThread { volverADisponible(mensaje) }
            }
        }
    }

    /** Adjunta el comprobante DESPUÉS del pago; el pago no se deshace si el
     * adjunto falla, solo se avisa. */
    private fun adjuntarYTerminar(pagoId: String, pendiente: ComprobanteElegido) {
        try {
            Diarix.prestamos.adjuntarComprobante(
                pagoId = pagoId,
                nombre = pendiente.nombre,
                mime = pendiente.mime,
                archivo = pendiente.bytes,
            )
            runOnUiThread { terminarRegistro(null) }
        } catch (e: ErrorDiarix) {
            runOnUiThread {
                terminarRegistro(
                    "El pago quedó registrado, pero no se pudo adjuntar el comprobante: " +
                        e.mensajeAmigable,
                )
            }
        } catch (e: Exception) {
            runOnUiThread {
                terminarRegistro(
                    "El pago quedó registrado, pero no se pudo adjuntar el comprobante.",
                )
            }
        }
    }

    private fun terminarRegistro(avisoExtra: String?) {
        Toast.makeText(this, "Pago registrado.", Toast.LENGTH_SHORT).show()
        if (avisoExtra != null) {
            Toast.makeText(this, avisoExtra, Toast.LENGTH_LONG).show()
        }
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun volverADisponible(mensaje: String) {
        registrando = false
        btnRegistrar.isEnabled = true
        btnRegistrar.text = "Registrar pago"
        avisar(mensaje)
    }

    private fun avisar(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val EXTRA_PRESTAMO_ID = "prestamo_id"
        private const val MAXIMO_BYTES_COMPROBANTE = 6 * 1024 * 1024

        fun nueva(context: Context, prestamoId: String): Intent =
            Intent(context, PrestamoPagoActivity::class.java)
                .putExtra(EXTRA_PRESTAMO_ID, prestamoId)
    }
}