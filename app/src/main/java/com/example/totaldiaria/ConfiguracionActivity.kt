package com.example.totaldiaria

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.navigation.BottomNavigator
import com.example.totaldiaria.preferences.QrManager
import com.example.totaldiaria.service.RespaldoService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var qrManager: QrManager
    private lateinit var txtEstadoQr: TextView

    private lateinit var respaldoService: RespaldoService

    private var dialogoProgreso: AlertDialog? = null

    private val seleccionarQr =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            uri?.let {

                val guardado =
                    qrManager.guardarQr(it)

                mostrarEstadoQr(guardado)
            }
        }

    private val elegirDestinoExportacion =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->

            if (uri != null) {
                exportarRespaldo(uri)
            }
        }

    private val elegirOrigenImportacion =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {
                prepararImportacion(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        qrManager = QrManager(this)

        respaldoService = RespaldoService(this)

        val btnCambiarQr =
            findViewById<Button>(R.id.btnCambiarQr)

        val btnExportar =
            findViewById<Button>(R.id.btnExportarCsv)

        val btnImportar =
            findViewById<Button>(R.id.btnImportarCsv)

        txtEstadoQr =
            findViewById(R.id.txtEstadoQr)

        mostrarEstadoQr(qrManager.existeQr())

        btnCambiarQr.setOnClickListener {
            seleccionarQr.launch("image/*")
        }

        btnExportar.setOnClickListener {
            iniciarExportacion()
        }

        btnImportar.setOnClickListener {
            iniciarImportacion()
        }

        BottomNavigator.configurar(this, R.id.nav_configuracion)
    }

    // ------------------------------------------------------------------
    // Exportación
    // ------------------------------------------------------------------

    private fun iniciarExportacion() {

        val fecha = SimpleDateFormat(
            "dd-MM-yyyy",
            Locale.getDefault()
        ).format(Date())

        try {

            elegirDestinoExportacion.launch(
                "TotalDiaria_respaldo_$fecha.zip"
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                RespaldoService.MENSAJE_ERROR_EXPORTAR,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportarRespaldo(uri: Uri) {

        mostrarProgreso("Generando respaldo…")

        Thread {

            try {

                val salida =
                    contentResolver.openOutputStream(uri)

                    ?: throw RespaldoService.RespaldoException(
                        RespaldoService.MENSAJE_ERROR_EXPORTAR
                    )

                val resumen = salida.use {
                    respaldoService.exportar(it)
                }

                runOnUiThread {

                    ocultarProgreso()

                    Toast.makeText(
                        this,
                        "Exportación completada correctamente.\n" +
                                "Facturas: ${resumen.facturas} · " +
                                "Comprobantes: ${resumen.comprobantes}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    ocultarProgreso()

                    Toast.makeText(
                        this,
                        mensajeDeError(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // Importación
    // ------------------------------------------------------------------

    private fun iniciarImportacion() {

        try {

            elegirOrigenImportacion.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                RespaldoService.MENSAJE_ARCHIVO_INVALIDO,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Valida el respaldo elegido antes de modificar nada y pide
     * confirmación mostrando un resumen del contenido.
     */
    private fun prepararImportacion(uri: Uri) {

        mostrarProgreso("Validando respaldo…")

        Thread {

            val validacion = respaldoService.validar {

                contentResolver.openInputStream(uri)
            }

            runOnUiThread {

                ocultarProgreso()

                when (validacion) {

                    is RespaldoService.Validacion.Ok ->
                        pedirConfirmacion(uri, validacion.resumen)

                    else ->

                        Toast.makeText(
                            this,
                            validacion.mensaje,
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }.start()
    }

    private fun pedirConfirmacion(
        uri: Uri,
        resumen: RespaldoService.ResumenBackup
    ) {

        val detalle = StringBuilder()

        detalle.append("Facturas: ")
            .append(resumen.facturas)
            .append('\n')

        detalle.append("Cierres diarios: ")
            .append(resumen.registros)
            .append('\n')

        detalle.append("Días en papelera: ")
            .append(resumen.papelera)
            .append('\n')

        detalle.append("Comprobantes incluidos: ")
            .append(resumen.comprobantes)

        if (resumen.fechaExportacion.isNotEmpty()) {

            detalle.append('\n')
                .append("Fecha del respaldo: ")
                .append(resumen.fechaExportacion)
        }

        AlertDialog.Builder(this)
            .setTitle("Respaldo encontrado")
            .setMessage(detalle)
            .setPositiveButton("Importar") { _, _ ->
                importarRespaldo(uri)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun importarRespaldo(uri: Uri) {

        mostrarProgreso("Importando respaldo…")

        Thread {

            try {

                val resultado = respaldoService.importar {

                    contentResolver.openInputStream(uri)
                }

                runOnUiThread {

                    ocultarProgreso()

                    val mensaje = StringBuilder()

                    mensaje.append(
                        "Importación completada correctamente.\n"
                    )

                    mensaje.append("Nuevas: ")
                        .append(resultado.insertadas)
                        .append('\n')

                    mensaje.append("Actualizadas: ")
                        .append(resultado.actualizadas)
                        .append('\n')

                    mensaje.append("Comprobantes restaurados: ")
                        .append(resultado.comprobantesRestaurados)

                    if (resultado.qrRestaurado) {

                        mensaje.append('\n')
                            .append("Código QR restaurado.")
                    }

                    Toast.makeText(
                        this,
                        mensaje,
                        Toast.LENGTH_LONG
                    ).show()

                    mostrarEstadoQr(qrManager.existeQr())
                }

            } catch (e: Exception) {

                runOnUiThread {

                    ocultarProgreso()

                    Toast.makeText(
                        this,
                        mensajeDeError(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // Utilidades de interfaz
    // ------------------------------------------------------------------

    private fun mostrarProgreso(mensaje: String) {

        ocultarProgreso()

        dialogoProgreso = AlertDialog.Builder(this)
            .setMessage(mensaje)
            .setCancelable(false)
            .create()

        dialogoProgreso?.show()
    }

    private fun ocultarProgreso() {

        dialogoProgreso?.dismiss()

        dialogoProgreso = null
    }

    private fun mensajeDeError(e: Exception): String =

        (e as? RespaldoService.RespaldoException)?.mensajeUsuario
            ?: RespaldoService.MENSAJE_ERROR_IMPORTAR

    private fun mostrarEstadoQr(configurado: Boolean) {

        if (configurado) {

            txtEstadoQr.text =
                "Código QR cargado correctamente"

            txtEstadoQr.setTextColor(
                ContextCompat.getColor(
                    this,
                    android.R.color.holo_green_dark
                )
            )

        } else {

            txtEstadoQr.text =
                "No hay un código QR configurado"

            txtEstadoQr.setTextColor(
                ContextCompat.getColor(
                    this,
                    android.R.color.holo_red_dark
                )
            )
        }
    }
}
