package com.example.totaldiaria.modulos.facturacion.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.LoginActivity
import com.example.totaldiaria.R
import com.example.totaldiaria.core.modo.AlmacenDeModo
import com.example.totaldiaria.core.modo.ModoDiarix
import com.example.totaldiaria.core.modo.SelectorDeModo
import com.example.totaldiaria.core.modo.abrirSeleccionDeModo
import com.example.totaldiaria.core.modo.modoVigente
import com.example.totaldiaria.core.navegacion.BottomNavigator
import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import com.example.totaldiaria.modulos.facturacion.preferencias.QrManager
import com.example.totaldiaria.modulos.facturacion.repositorio.FacturaRepository
import com.example.totaldiaria.modulos.facturacion.servicio.CierreDiarioService
import com.example.totaldiaria.modulos.facturacion.servicio.DiaService
import com.example.totaldiaria.modulos.facturacion.sincronizacion.ResultadoDeSync
import com.example.totaldiaria.modulos.facturacion.ui.ComprobanteController
import com.example.totaldiaria.modulos.facturacion.ui.FormatoMoneda
import com.example.totaldiaria.modulos.facturacion.ui.MonedaTextWatcher
import com.example.totaldiaria.modulos.facturacion.ui.QrDialog
import com.example.totaldiaria.network.ErrorDiarix

class MainActivity : AppCompatActivity() {

    private lateinit var facturaRepository: FacturaRepository
    private lateinit var cierreDiarioService: CierreDiarioService
    private lateinit var qrManager: QrManager
    private lateinit var comprobantes: ComprobanteController

    private val diaService = DiaService()

    private lateinit var txtTotal: TextView
    private lateinit var txtCantidad: TextView
    private lateinit var txtEstadoSync: TextView
    private lateinit var txtTotalRegistro: TextView

    private lateinit var edtEfectivo: EditText
    private lateinit var edtTransferencia: EditText
    private lateinit var edtNumeroFactura: EditText

    private lateinit var btnEfectivo: Button
    private lateinit var btnTransferencia: Button
    private lateinit var btnAmbos: Button
    private lateinit var btnGuardar: Button

    private var modoPago = ""

    /** Evita repetir el aviso de límite varias veces en la misma sesión. */
    private var avisoLimiteMostrado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fase 4: sin sesión válida no hay registro local funcional (la
        // sincronización y el perfil vienen del backend). Primera pantalla
        // → login.
        Diarix.iniciar(this)
        if (!Diarix.tieneSesion()) {
            irAlLogin()
            return
        }

        // Fase 0.1: si todavía no se eligió área, hay que preguntar. Hace
        // falta aquí y no solo en el login porque ESTA pantalla es la del
        // icono de la app: quien ya tenía sesión y abre DiariX desde el
        // escritorio entraría directamente a facturación sin que nadie le
        // preguntara nunca.
        val modoGuardado = AlmacenDeModo(this).modoGuardado()

        if (SelectorDeModo.haceFaltaElegirModo(modoGuardado)) {
            abrirSeleccionDeModo(this)
            finish()
            return
        }

        // Esta pantalla es el inicio del área de FACTURACIÓN. Si el área elegida
        // es la de préstamos, quien abre la app entra al inicio de SU área, no al
        // de otra. El cambio de área se hace desde Configuración, que limpia la
        // pila (CLEAR_TASK), así que llegar aquí con otro modo solo puede ser el
        // arranque de la app.
        if (modoVigente(this) != ModoDiarix.FACTURACION) {
            SelectorDeModo.abrirInicio(this)
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        configurarBarraEstado()

        facturaRepository = FacturaRepository(this)
        cierreDiarioService = CierreDiarioService(this)
        qrManager = QrManager(this)

        aplicarCierreDiario()

        restaurarEstado(savedInstanceState)

        vincularVistas()

        configurarModoPago()
        configurarCamposMoneda()
        configurarAcciones()
        configurarQr()

        BottomNavigator.configurar(this, R.id.nav_inicio)

        actualizarDashboard()

        // Sesión expirada a mitad del uso (el servidor la revocó o el
        // refresh murió): volver al login explicando qué pasó.
        Diarix.sesion.alExpiarSesion = {
            runOnUiThread { mostrarSesionExpirada() }
        }

        // Cada pasada del sincronizador refresca el indicador de estado.
        Diarix.sincronizador.alTerminar = { resultado ->
            runOnUiThread { actualizarEstadoSync(resultado) }
        }

        // Al reabrir la app: perfil fresco y cola al día.
        recargarPerfilYSincronizar()
    }

    override fun onResume() {
        super.onResume()

        // Cubre el caso en que la app vuelve tras medianoche: el
        // cierre es idempotente, así que solo archiva si quedan
        // facturas de días anteriores.
        aplicarCierreDiario()

        BottomNavigator.sincronizarPestana(this, R.id.nav_inicio)
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
        txtEstadoSync = findViewById(R.id.txtEstadoSync)
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

        // Fase 4: en cuanto hay una factura local nueva, la cola intenta
        // subirla enseguida (si no hay red, queda PENDIENTE y se sincroniza
        // sola al reconectar).
        Diarix.enHiloDeFondo {
            Diarix.sincronizador.sincronizarPendientes()
        }
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

    /**
     * Fecha y hora local de DiariX (América/Bogotá): es la que el servidor
     * usa como día contable, de modo que "hoy" coincide dentro y fuera de la
     * app.
     */
    private fun fechaActual(): String =
        diaService.fechaYHoraLocal()

    /**
     * Cierre consolidado del día: idempotente, segura de ejecutar en
     * cada entrada a la pantalla principal.
     */
    private fun aplicarCierreDiario() {

        cierreDiarioService.archivarFacturasDeDiasAnteriores()
        cierreDiarioService.aplicarCicloRegistros()
    }

    private fun actualizarDashboard() {

        // El contador se deriva exclusivamente de las facturas del
        // día actual, no de un acumulado histórico ni de un estado
        // caché.
        val lista =
            facturaRepository.obtenerFacturasDelDia(
                diaService.hoy()
            )

        val total =
            lista.sumOf { it.efectivo + it.transferencia }

        txtTotal.text =
            FormatoMoneda.formatear(total)

        // Fase 4: si el servidor informó el cupo del plan, se muestra
        // "N de 15 facturas" (el servidor es la autoridad del límite).
        val plan = Diarix.sesion.perfilActual?.plan
        txtCantidad.text =
            if (plan != null && plan.facturasPorDia != null && plan.planVigente) {
                "${lista.size} de ${plan.facturasPorDia} facturas"
            } else {
                "${lista.size} facturas"
            }

        actualizarEstadoSync(Diarix.sincronizador.ultimoResultado)
    }

    // =========================================================================
    // Fase 4 · Sesión y sincronización
    // =========================================================================

    private fun irAlLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun mostrarSesionExpirada() {
        if (isFinishing || isDestroyed) return

        AlertDialog.Builder(this)
            .setTitle("Tu sesión expiró")
            .setMessage(
                "Tu acceso a DiariX venció o fue revocado. " +
                    "Inicia sesión de nuevo para seguir sincronizando tus facturas."
            )
            .setCancelable(false)
            .setPositiveButton("Iniciar sesión") { _, _ -> irAlLogin() }
            .show()
    }

    /** Perfil fresco (/auth/yo) y cola de sincronización al volver a la app. */
    private fun recargarPerfilYSincronizar() {
        Diarix.enHiloDeFondo {
            try {
                // El backend es la autoridad sobre rol, empresa, plan y estado.
                Diarix.sesion.recargarPerfil()
            } catch (e: ErrorDiarix) {
                // Sin red o sesión expirada: si expiró, alExpiarSesion ya avisó
                // y nos manda al login. Sin red, seguimos offline.
            }

            val resultado = Diarix.sincronizador.sincronizarPendientes()
            runOnUiThread { actualizarEstadoSync(resultado) }
        }
    }

    /**
     * Línea de estado: ONLINE/OFFLINE + pendientes de sincronización.
     * Los mensajes del servidor (límite, suscripción vencida, empresa o
     * conductor inactivo) se muestran tal cual para NUNCA caer en
     * "Error desconocido".
     */
    private fun actualizarEstadoSync(resultado: ResultadoDeSync?) {
        if (!::txtEstadoSync.isInitialized) return

        val pendientes = facturaRepository.contarPendientesDeSincronizacion()
        val enLinea = Diarix.red.hayInternet()
        val plan = Diarix.sesion.perfilActual?.plan

        when (resultado) {
            is ResultadoDeSync.Error -> {
                // Bloqueos de negocio: el mensaje del servidor es amigable y
                // le dice al usuario exactamente qué pasa y qué hacer.
                txtEstadoSync.text = resultado.mensaje
                txtEstadoSync.setTextColor(COLOR_NARANJA)
                txtEstadoSync.visibility = View.VISIBLE
            }
            else -> {
                val texto =
                    if (!enLinea) {
                        "Sin conexión · $pendientes pendientes de sincronizar"
                    } else if (pendientes > 0) {
                        "$pendientes pendientes de sincronizar"
                    } else {
                        "Sincronizado"
                    }

                txtEstadoSync.text = texto
                txtEstadoSync.setTextColor(
                    if (pendientes == 0 && enLinea) COLOR_VERDE_SYNC else COLOR_NARANJA
                )
                txtEstadoSync.visibility = View.VISIBLE
            }
        }

        // Límite del plan gratuito alcanzado: mensaje claro de actualización.
        if (resultado is ResultadoDeSync.Exito &&
            resultado.resumen?.limiteAlcanzado == true &&
            !avisoLimiteMostrado &&
            plan != null && !plan.graficasAvanzadas
        ) {
            avisoLimiteMostrado = true
            Toast.makeText(
                this,
                "Alcanzaste el límite de ${resultado.resumen.limiteDiario} facturas " +
                    "del plan gratuito. Actualiza a Premium para registrar hasta 30 " +
                    "facturas al día y desbloquear tus gráficas.",
                Toast.LENGTH_LONG
            ).show()
        }
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

        private val COLOR_VERDE =
            android.graphics.Color.parseColor("#087F5B")

        private val COLOR_AZUL =
            android.graphics.Color.parseColor("#1565C0")

        private val COLOR_VERDE_SYNC = android.graphics.Color.parseColor("#0AA87A")

        private val COLOR_NARANJA = android.graphics.Color.parseColor("#B08A0A")
    }
}
