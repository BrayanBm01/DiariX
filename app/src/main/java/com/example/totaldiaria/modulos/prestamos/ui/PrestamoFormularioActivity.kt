package com.example.totaldiaria.modulos.prestamos.ui

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.example.totaldiaria.modulos.prestamos.modelo.CalculoDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.CalculoDePrestamoUtil
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePrestamoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.ModalidadesDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.TiposDeCalculoPrestamo
import com.example.totaldiaria.network.ErrorDiarix
import java.math.BigDecimal
import java.util.Calendar
import java.util.Locale

/**
 * DIARIX · Fase 3 · Alta de préstamo (RF-05 a RF-07).
 *
 * Arma el [DatosDePrestamoNuevo] y lo manda a `POST /prestamos`. La vista
 * previa (total y cuota) la calcula este módulo con [CalculoDePrestamo]
 * EXCLUSIVAMENTE para acompañar la escritura; el servidor recalcula todo con
 * su propio Decimal y devuelve la ficha YA con el calendario, que es lo que
 * esta pantalla abre al terminar. El `id` del préstamo creado es el único que
 * vale: si el servidor corrige centavos, se ven los del servidor.
 *
 * Reglas que se anticipan aquí (y que el backend impone): monto > 0, cuotas
 * de 1 a 60, PORCENTAJE entre 0,01 % y 1.000 %, o TOTAL mayor que el monto.
 * El botón se bloquea durante el envío para que un doble toque no registre
 * dos préstamos.
 */
class PrestamoFormularioActivity : AppCompatActivity() {

    private lateinit var edtMonto: EditText
    private lateinit var edtCuotas: EditText
    private lateinit var edtPorcentaje: EditText
    private lateinit var edtTotal: EditText
    private lateinit var txtCliente: TextView
    private lateinit var txtClienteDocumento: TextView
    private lateinit var txtFechaInicio: TextView
    private lateinit var txtPreviewTotal: TextView
    private lateinit var txtPreviewCuota: TextView
    private lateinit var txtPreviewNota: TextView
    private lateinit var btnRegistrar: Button

    private lateinit var chipsModalidad: Map<String, TextView>
    private lateinit var chipPorcentaje: TextView
    private lateinit var chipTotal: TextView

    private var clienteId: String? = null
    private var clienteNombre: String? = null
    private var clienteDocumento: String? = null

    private var fechaInicio: String = FormatoPrestamos.hoy()
    private var modalidad: String = ModalidadesDePrestamo.DIARIO
    private var tipoCalculo: String = TiposDeCalculoPrestamo.PORCENTAJE

    /** Bloquea el doble toque de "Registrar préstamo" mientras viaja el alta. */
    private var registrando = false

    private val resultadoCliente =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            val datos = resultado.data
            if (resultado.resultCode == Activity.RESULT_OK && datos != null) {
                clienteId = datos.getStringExtra(SelectorDeClienteActivity.EXTRA_CLIENTE_ID)
                clienteNombre = datos.getStringExtra(SelectorDeClienteActivity.EXTRA_CLIENTE_NOMBRE)
                clienteDocumento = datos.getStringExtra(SelectorDeClienteActivity.EXTRA_CLIENTE_DOCUMENTO)
                pintarCliente()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prestamo_formulario)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        vincularVistas()

        findViewById<View>(R.id.btnVolverPrestamo).setOnClickListener { finish() }

        txtFechaInicio.text = FormatoPrestamos.formatearFecha(fechaInicio)
    }

    private fun vincularVistas() {
        edtMonto = findViewById(R.id.edtMontoPrestamo)
        edtCuotas = findViewById(R.id.edtCuotasPrestamo)
        edtPorcentaje = findViewById(R.id.edtPorcentajePrestamo)
        edtTotal = findViewById(R.id.edtTotalPrestamo)
        txtCliente = findViewById(R.id.txtClienteSeleccionado)
        txtClienteDocumento = findViewById(R.id.txtClienteDocumentoSeleccionado)
        txtFechaInicio = findViewById(R.id.txtFechaInicio)
        txtPreviewTotal = findViewById(R.id.txtPreviewTotal)
        txtPreviewCuota = findViewById(R.id.txtPreviewCuota)
        txtPreviewNota = findViewById(R.id.txtPreviewNota)
        btnRegistrar = findViewById(R.id.btnRegistrarPrestamo)

        chipsModalidad = mapOf(
            ModalidadesDePrestamo.DIARIO to findViewById(R.id.txtModalidadDiario),
            ModalidadesDePrestamo.SEMANAL to findViewById(R.id.txtModalidadSemanal),
            ModalidadesDePrestamo.QUINCENAL to findViewById(R.id.txtModalidadQuincenal),
            ModalidadesDePrestamo.MENSUAL to findViewById(R.id.txtModalidadMensual),
        )
        chipPorcentaje = findViewById(R.id.txtTipoPorcentaje)
        chipTotal = findViewById(R.id.txtTipoTotal)

        // Cliente: se elige en otra pantalla y vuelve por resultado.
        findViewById<View>(R.id.tarjetaElegirCliente).setOnClickListener {
            resultadoCliente.launch(SelectorDeClienteActivity.abrir(this))
        }

        // Fecha de inicio: DatePicker, hoy por defecto.
        findViewById<View>(R.id.tarjetaFechaInicio).setOnClickListener { elegirFecha() }

        edtMonto.addTextChangedListener(MonedaPrestamoTextWatcher(edtMonto) { recalcular() })
        edtTotal.addTextChangedListener(MonedaPrestamoTextWatcher(edtTotal) { recalcular() })

        edtCuotas.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = recalcular()
        })

        edtPorcentaje.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = recalcular()
        })

        chipsModalidad.forEach { (clave, chip) ->
            chip.setOnClickListener {
                modalidad = clave
                pintarModalidad()
            }
        }

        chipPorcentaje.setOnClickListener {
            tipoCalculo = TiposDeCalculoPrestamo.PORCENTAJE
            pintarTipo()
            recalcular()
        }

        chipTotal.setOnClickListener {
            tipoCalculo = TiposDeCalculoPrestamo.TOTAL
            pintarTipo()
            recalcular()
        }

        btnRegistrar.setOnClickListener { guardar() }

        pintarModalidad()
        pintarTipo()
    }

    private fun pintarCliente() {
        val nombre = clienteNombre
        if (nombre == null) {
            txtCliente.text = "Elegir el cliente"
            txtCliente.setTextColor(0xFF7A7A9D.toInt())
            txtClienteDocumento.text = ""
            txtClienteDocumento.setTextColor(0xFFB4C1D2.toInt())
        } else {
            txtCliente.text = nombre
            txtCliente.setTextColor(0xFF1C2340.toInt())
            txtClienteDocumento.text = clienteDocumento.orEmpty()
            txtClienteDocumento.setTextColor(0xFF7A7A9D.toInt())
        }
    }

    private fun elegirFecha() {
        val hoy = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, anio, mes, dia ->
                fechaInicio = String.format(Locale.US, "%04d-%02d-%02d", anio, mes + 1, dia)
                txtFechaInicio.text = FormatoPrestamos.formatearFecha(fechaInicio)
            },
            hoy.get(Calendar.YEAR),
            hoy.get(Calendar.MONTH),
            hoy.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun pintarModalidad() {
        chipsModalidad.forEach { (clave, chip) -> pintarChip(chip, clave == modalidad) }
    }

    private fun pintarTipo() {
        val esPorcentaje = tipoCalculo == TiposDeCalculoPrestamo.PORCENTAJE
        edtPorcentaje.visibility = if (esPorcentaje) View.VISIBLE else View.GONE
        edtTotal.visibility = if (esPorcentaje) View.GONE else View.VISIBLE
        pintarChip(chipPorcentaje, esPorcentaje)
        pintarChip(chipTotal, !esPorcentaje)
        if (!esPorcentaje) edtPorcentaje.text?.clear()
        if (esPorcentaje) edtTotal.text?.clear()
    }

    private fun pintarChip(chip: TextView, activo: Boolean) {
        chip.setBackgroundResource(
            if (activo) R.drawable.bg_chip_filtro_activo else R.drawable.bg_chip_filtro,
        )
        chip.setTextColor(if (activo) 0xFFFFFFFF.toInt() else 0xFF4A5578.toInt())
        chip.typeface = if (activo) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    // ==========================================================================
    // Vista previa local (el cálculo autoritativo vive en el servidor)
    // ==========================================================================

    private fun recalcular() {
        val monto = FormatoPrestamos.parsear(edtMonto.text?.toString().orEmpty())
        val numeroCuotas = edtCuotas.text?.toString()?.toIntOrNull() ?: 0

        val calculo = when {
            monto <= BigDecimal.ZERO || numeroCuotas < 1 -> null
            tipoCalculo == TiposDeCalculoPrestamo.PORCENTAJE -> {
                val porcentaje = porcentajeElegido()
                if (porcentaje == null || porcentaje <= BigDecimal.ZERO) {
                    null
                } else {
                    val total = CalculoDePrestamoUtil.totalDesdePorcentaje(monto, porcentaje)
                    CalculoDePrestamoUtil.repartir(total, numeroCuotas)
                }
            }
            else -> {
                val total = FormatoPrestamos.parsear(edtTotal.text?.toString().orEmpty())
                if (total <= monto) null else CalculoDePrestamoUtil.repartir(total, numeroCuotas)
            }
        }

        pintarPreview(calculo, numeroCuotas)
    }

    private fun pintarPreview(calculo: CalculoDePrestamo?, numeroCuotas: Int) {
        if (calculo == null) {
            txtPreviewTotal.text = "0"
            txtPreviewCuota.text =
                "Completa el monto, las cuotas y la forma de cálculo para ver el acuerdo."
            txtPreviewNota.text = ""
            return
        }

        txtPreviewTotal.text = FormatoPrestamos.formatear(calculo.totalAPagar)
        txtPreviewCuota.text =
            "$numeroCuotas cuotas de ${FormatoPrestamos.formatear(calculo.valorCuota)} cada una"

        txtPreviewNota.text = if (calculo.residuoUltimaCuota > BigDecimal.ZERO) {
            val ultimaCuota = calculo.totalAPagar.subtract(
                calculo.valorCuota.multiply(BigDecimal.valueOf((numeroCuotas - 1).toLong())),
            )
            "Por el redondeo de centavos, la cuota $numeroCuotas queda en " +
                "${FormatoPrestamos.formatear(ultimaCuota)}."
        } else {
            ""
        }
    }

    private fun porcentajeElegido(): BigDecimal? {
        val texto = edtPorcentaje.text?.toString()?.trim().orEmpty()
        if (texto.isEmpty()) return null
        return runCatching { BigDecimal(texto.replace(',', '.')) }.getOrNull()
    }

    // ==========================================================================
    // Alta
    // ==========================================================================

    private fun guardar() {
        if (registrando) return

        val cliente = clienteId
        if (cliente == null) {
            avisar("Elige el cliente que recibirá el préstamo.")
            return
        }

        val monto = FormatoPrestamos.parsear(edtMonto.text?.toString().orEmpty())
        if (monto <= BigDecimal.ZERO) {
            avisar("Escribe el monto a prestar.")
            return
        }

        val cuotas = edtCuotas.text?.toString()?.toIntOrNull()
        if (cuotas == null || cuotas < 1 || cuotas > CalculoDePrestamoUtil.MAXIMO_NUMERO_DE_CUOTAS) {
            avisar("El número de cuotas va de 1 a ${CalculoDePrestamoUtil.MAXIMO_NUMERO_DE_CUOTAS}.")
            return
        }

        val datos = if (tipoCalculo == TiposDeCalculoPrestamo.PORCENTAJE) {
            val porcentaje = porcentajeElegido()
            if (porcentaje == null || porcentaje < BigDecimal("0.01") || porcentaje > BigDecimal("1000")) {
                avisar("El porcentaje va entre 0,01 % y 1.000 %.")
                return
            }
            DatosDePrestamoNuevo(
                clienteId = cliente,
                fechaInicio = fechaInicio,
                monto = monto,
                modalidad = modalidad,
                numeroDeCuotas = cuotas,
                tipoCalculo = TiposDeCalculoPrestamo.PORCENTAJE,
                porcentaje = porcentaje,
            )
        } else {
            val total = FormatoPrestamos.parsear(edtTotal.text?.toString().orEmpty())
            if (total <= monto) {
                avisar("El total a pagar debe ser mayor que el monto prestado.")
                return
            }
            DatosDePrestamoNuevo(
                clienteId = cliente,
                fechaInicio = fechaInicio,
                monto = monto,
                modalidad = modalidad,
                numeroDeCuotas = cuotas,
                tipoCalculo = TiposDeCalculoPrestamo.TOTAL,
                totalAPagar = total,
            )
        }

        registrar(datos)
    }

    private fun registrar(datos: DatosDePrestamoNuevo) {
        registrando = true
        btnRegistrar.isEnabled = false
        btnRegistrar.text = "Registrando…"

        Diarix.enHiloDeFondo {
            try {
                val detalle = Diarix.prestamos.crear(datos)

                runOnUiThread {
                    Toast.makeText(this, "Préstamo registrado.", Toast.LENGTH_SHORT).show()
                    startActivity(PrestamoDetalleActivity.abrir(this, detalle.prestamo.id))
                    finish()
                }
            } catch (e: ErrorDiarix) {
                runOnUiThread { volverADisponible(e.mensajeAmigable) }
            } catch (e: Exception) {
                val mensaje = ErrorDiarix.Inesperado(e.message ?: "").mensajeAmigable
                runOnUiThread { volverADisponible(mensaje) }
            }
        }
    }

    /** Desbloquea el botón tras un fallo: un error no deja el alta atascada. */
    private fun volverADisponible(mensaje: String) {
        registrando = false
        btnRegistrar.isEnabled = true
        btnRegistrar.text = "Registrar préstamo"
        avisar(mensaje)
    }

    private fun avisar(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
    }

    companion object {
        fun nueva(context: Context): Intent = Intent(context, PrestamoFormularioActivity::class.java)
    }
}