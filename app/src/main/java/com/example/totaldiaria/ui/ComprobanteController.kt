package com.example.totaldiaria.ui

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.example.totaldiaria.service.ComprobanteStore

/**
 * Encapsula la adquisición del comprobante de transferencia:
 * selección desde galería, captura con cámara y estado del Uri,
 * incluida su persistencia ante cambios de configuración.
 */
class ComprobanteController(
    private val activity: ComponentActivity,
    private val vistas: Vistas
) {

    class Vistas(
        val layoutComprobante: View,
        val btnSeleccionar: View,
        val btnTomarFoto: Button,
        val imgComprobante: ImageView,
        val btnCambiar: Button
    )

    var uriComprobante: Uri? = null
        private set

    var uriFotoComprobante: Uri? = null
        private set

    private val almacenComprobantes = ComprobanteStore(activity)

    private val camaraLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { guardada ->

            if (guardada && uriFotoComprobante != null) {

                uriComprobante = uriFotoComprobante

                mostrarComprobanteSeleccionado()

            } else {

                Toast.makeText(
                    activity,
                    "La cámara no pudo guardar la fotografía",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val galeriaLauncher =
        activity.registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->

            if (uri != null) {

                // El permiso sobre el URI del selector puede perderse
                // al cerrar la app; se conserva una copia interna.
                val copia = almacenComprobantes.guardarCopia(uri)

                if (copia != null) {

                    uriComprobante = copia

                } else {

                    uriComprobante = uri

                    Toast.makeText(
                        activity,
                        "No se pudo guardar una copia del comprobante",
                        Toast.LENGTH_LONG
                    ).show()
                }

                mostrarComprobanteSeleccionado()
            }
        }

    init {

        vistas.btnSeleccionar.setOnClickListener {
            abrirGaleria()
        }

        vistas.btnCambiar.setOnClickListener {
            abrirGaleria()
        }

        vistas.btnTomarFoto.setOnClickListener {
            iniciarCamara()
        }
    }

    fun abrirGaleria() {

        galeriaLauncher.launch(
            PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    fun iniciarCamara() {

        val uri = crearArchivoComprobante()

        if (uri != null) {

            camaraLauncher.launch(uri)

        } else {

            Toast.makeText(
                activity,
                "No se pudo preparar la cámara",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Restablece la vista al estado inicial (tras guardar una factura).
     */
    fun limpiar() {

        uriComprobante = null
        uriFotoComprobante = null

        restaurarVistas()
    }

    /**
     * Aplica a las vistas el estado actual de los Uris.
     */
    fun restaurarVistas() {

        val comprobante = uriComprobante

        if (comprobante != null) {

            vistas.imgComprobante.setImageURI(comprobante)

            vistas.btnSeleccionar.visibility = View.GONE
            vistas.imgComprobante.visibility = View.VISIBLE
            vistas.btnCambiar.visibility = View.VISIBLE

        } else {

            vistas.imgComprobante.setImageDrawable(null)
            vistas.imgComprobante.visibility = View.GONE
            vistas.btnCambiar.visibility = View.GONE
            vistas.btnSeleccionar.visibility = View.VISIBLE
        }
    }

    fun guardarEstado(outState: android.os.Bundle) {

        outState.putString(CLAVE_URI_COMPROBANTE, uriComprobante?.toString())
        outState.putString(CLAVE_URI_FOTO, uriFotoComprobante?.toString())
    }

    fun restaurarEstado(savedInstanceState: android.os.Bundle?) {

        if (savedInstanceState == null) return

        uriComprobante =
            savedInstanceState.getString(CLAVE_URI_COMPROBANTE)
                ?.let { Uri.parse(it) }

        uriFotoComprobante =
            savedInstanceState.getString(CLAVE_URI_FOTO)
                ?.let { Uri.parse(it) }
    }

    private fun mostrarComprobanteSeleccionado() {

        val uri = uriComprobante ?: return

        vistas.imgComprobante.setImageURI(uri)

        vistas.btnSeleccionar.visibility = View.GONE
        vistas.imgComprobante.visibility = View.VISIBLE
        vistas.btnCambiar.visibility = View.VISIBLE
    }

    private fun crearArchivoComprobante(): Uri? {

        val nombreArchivo =
            "Comprobante_${System.currentTimeMillis()}.jpg"

        val valores = ContentValues().apply {

            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                nombreArchivo
            )

            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )

            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/TotalDiaria"
            )
        }

        val uri =
            activity.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                valores
            )

        uriFotoComprobante = uri

        return uri
    }

    companion object {

        private const val CLAVE_URI_COMPROBANTE = "uri_comprobante"
        private const val CLAVE_URI_FOTO = "uri_foto_comprobante"
    }
}
