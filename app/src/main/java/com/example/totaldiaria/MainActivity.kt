package com.example.totaldiaria

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.database.FacturaRepository
import com.example.totaldiaria.models.Factura
import com.example.totaldiaria.navigation.BottomNavigator
import com.example.totaldiaria.preferences.QrManager
import com.example.totaldiaria.service.CierreDiarioService
import com.example.totaldiaria.ui.ComprobanteController
import com.example.totaldiaria.ui.FormatoMoneda
import com.example.totaldiaria.ui.MonedaTextWatcher
import com.example.totaldiaria.ui.QrDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var facturaRepository: FacturaRepository
    private lateinit var cierreDiarioService: CierreDiarioService
    private lateinit var qrManager: QrManager
    private lateinit var comprobantes: ComprobanteController

    private lateinit var txtTotal: TextView
    private lateinit var txtCantidad: TextView
    private lateinit var txtTotalRegistro: TextView

    private lateinit var edtEfectivo: EditText
    private lateinit var edtTransferencia: EditText
    private lateinit var edtNumeroFactura: EditText

    private lateinit var btnEfectivo: Button
    private lateinit var btnTransferencia: Button
    private lateinit var btnAmbos: Button
    private lateinit var btnGuardar: Button

    private var modoPago = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        configurarBarraEstado()

        facturaRepository = FacturaRepository(this)
        cierreDiarioService = CierreDiarioService(this)
        qrManager = QrManager(this)

        cierreDiarioService.archivarFacturasDeDiasAnteriores()
        cierreDiarioService.moverRegistrosExcedentesAPapelera()

        restaurarEstado(savedInstanceState)

        vincularVistas()

        configurarModoPago()
        configurarCamposMoneda()
        configurarAcciones()
        configurarQr()

        BottomNavigator.configurar(this, R.id.nav_inicio)

        actualizarDashboard()
    }

    override fun onResume() {
        super.onResume()

        actualizarDashboard()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        comprobantes.guardarEstado(outState)
        outState.putString(CLAVE_MODO_PAGO, modoPago)
    }

    private fun restaurarEstado(savedInstanceState: Bundle?) {

        modoPago =
            savedInstanceState?.getString(CLAVE_MODO_PAGO)
                ?: ""
    }

    private fun vincularVistas() {

        txtTotal = findViewById(R.id.txtTotal)
        txtCantidad = findViewById(R.id.txtCantidad)
        txtTotalRegistro = findViewById(R.id.txtTotalRegistro)

        edtEfectivo = findViewById(R.id.edtEfectivo)
        edtTransferencia = findViewById(R.id.edtTransferencia)
        edtNumeroFactura = findViewById(R.id.edtNumeroFactura)

        btnEfectivo = findViewById(R.id.btnEfectivo)
        btnTransferencia = findViewById(R.id.btnTransferencia)
        btnAmbos = findViewById(R.id.btnAmbos)
        btnGuardar = findViewById(R.id.btnGuardar)

        comprobantes = ComprobanteController(
            this,
            ComprobanteController.Vistas(
                layoutComprobante = findViewById(R.id.layoutComprobante),
                btnSeleccionar = findViewById(R.id.btnSeleccionarComprobante),
                btnTomarFoto = findViewById(R.id.btnTomarFoto),
                imgComprobante = findViewById(R.id.imgComprobante),
                btnCambiar = findViewById(R.id.btnCambiarComprobante)
            )
        )
    }

    private fun configurarModoPago() {

        btnEfectivo.setOnClickListener { aplicarModoPago(MODO_EFECTIVO) }
        btnTransferencia.setOnClickListener { aplicarModoPago(MODO_TRANSFERENCIA) }
        btnAmbos.setOnClickListener { aplicarModoPago(MODO_AMBOS) }

        // El layout arranca sin modo elegido; solo se restaura si había uno guardado.
        if (modoPago.isNotEmpty()) {
            aplicarModoPago(modoPago, limpiarCamposOcultos = false)
        }
    }

    private fun aplicarModoPago(
        nuevoModo: String,
        limpiarCamposOcultos: Boolean = true
    ) {

        modoPago = nuevoModo

        val muestraEfectivo = nuevoModo != MODO_TRANSFERENCIA
        val muestraTransferencia = nuevoModo != MODO_EFECTIVO

        edtEfectivo.visibility =
            if (muestraEfectivo) View.VISIBLE else View.GONE

        edtTransferencia.visibility =
            if (muestraTransferencia) View.VISIBLE else View.GONE

        findViewById<View>(R.id.layoutComprobante).visibility =
            if (nuevoModo == MODO_EFECTIVO) View.GONE else View.VISIBLE

        if (limpiarCamposOcultos && !muestraEfectivo) {
            edtEfectivo.text.clear()
        }

        if (limpiarCamposOcultos && !muestraTransferencia) {
            edtTransferencia.text.clear()
        }

        edtEfectivo.setHintTextColor(COLOR_VERDE)
        edtTransferencia.setHintTextColor(COLOR_AZUL)

        comprobantes.restaurarVistas()

        calcularTotalRegistro()
    }

    private fun configurarCamposMoneda() {

        edtEfectivo.addTextChangedListener(
            MonedaTextWatcher(edtEfectivo) { calcularTotalRegistro() }
        )

        edtTransferencia.addTextChangedListener(
            MonedaTextWatcher(edtTransferencia) { calcularTotalRegistro() }
        )

        calcularTotalRegistro()
    }

    private fun configurarAcciones() {

        btnGuardar.setOnClickListener {
            guardarFactura()
        }
    }

    private fun configurarQr() {

        val imgQr = findViewById<ImageView>(R.id.imgQr)

        imgQr.setOnClickListener {

            if (!qrManager.existeQr()) {

                Toast.makeText(
                    this,
                    "No hay un QR configurado.",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            QrDialog.mostrar(this, qrManager.obtenerQr())
        }
    }

    private fun guardarFactura() {

        val numeroFactura =
            edtNumeroFactura.text.toString().trim()

        val efectivo =
            FormatoMoneda.parsear(edtEfectivo.text.toString())

        val transferencia =
            FormatoMoneda.parsear(edtTransferencia.text.toString())

        if (!entradaValida(numeroFactura, efectivo, transferencia)) return

        val factura = Factura(
            id = 0,
            numeroFactura = numeroFactura,
            efectivo = efectivo,
            transferencia = transferencia,
            fecha = fechaActual(),
            comprobanteUri =
                if (transferencia > 0) comprobantes.uriComprobante?.toString()
                else null
        )

        val guardada =
            facturaRepository.insertarFactura(factura)

        if (!guardada) {

            Toast.makeText(
                this,
                "Error al guardar",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        Toast.makeText(
            this,
            "Factura guardada",
            Toast.LENGTH_SHORT
        ).show()

        limpiarFormulario()
        actualizarDashboard()
    }

    private fun entradaValida(
        numeroFactura: String,
        efectivo: Double,
        transferencia: Double
    ): Boolean {

        if (numeroFactura.isEmpty()) {

            Toast.makeText(
                this,
                "Ingresa el número de la factura",
                Toast.LENGTH_SHORT
            ).show()

            return false
        }

        if (
            efectivo == 0.0 &&
            transferencia == 0.0
        ) {

            Toast.makeText(
                this,
                "Ingresa un valor",
                Toast.LENGTH_SHORT
            ).show()

            return false
        }

        return true
    }

    private fun limpiarFormulario() {

        edtEfectivo.text.clear()
        edtTransferencia.text.clear()
        edtNumeroFactura.text.clear()

        comprobantes.limpiar()

        calcularTotalRegistro()
    }

    private fun fechaActual(): String =

        SimpleDateFormat(
            FORMATO_FECHA_HORA,
            Locale.getDefault()
        ).format(Date())

    private fun actualizarDashboard() {

        val lista =
            facturaRepository.obtenerFacturas()

        val total =
            lista.sumOf { it.efectivo + it.transferencia }

        txtTotal.text =
            FormatoMoneda.formatear(total)

        txtCantidad.text =
            "${lista.size} facturas"
    }

    private fun calcularTotalRegistro() {

        val total =
            FormatoMoneda.parsear(edtEfectivo.text.toString()) +
                    FormatoMoneda.parsear(edtTransferencia.text.toString())

        txtTotalRegistro.text =
            "Total: ${FormatoMoneda.formatear(total)}"
    }

    private fun configurarBarraEstado() {

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true
    }

    companion object {

        const val MODO_EFECTIVO = "efectivo"
        const val MODO_TRANSFERENCIA = "transferencia"
        const val MODO_AMBOS = "ambos"

        private const val CLAVE_MODO_PAGO = "modo_pago"
        private const val FORMATO_FECHA_HORA = "dd/MM/yyyy HH:mm"

        private val COLOR_VERDE =
            android.graphics.Color.parseColor("#087F5B")

        private val COLOR_AZUL =
            android.graphics.Color.parseColor("#1565C0")
    }
}
