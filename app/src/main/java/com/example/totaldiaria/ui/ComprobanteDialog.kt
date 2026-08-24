package com.example.totaldiaria.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import com.example.totaldiaria.R
import java.io.FileNotFoundException

object ComprobanteDialog {

    private const val TAG = "Comprobante"

    private const val MAX_LADO = 1200

    fun mostrar(context: Context, uriComprobante: String?) {

        if (uriComprobante.isNullOrEmpty()) {

            Log.w(TAG, "Sin comprobante: valor='$uriComprobante'")

            Toast.makeText(
                context,
                "Esta factura no tiene comprobante",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        try {

            val uri = Uri.parse(uriComprobante)

            Log.i(TAG, "Abriendo comprobante uri=$uri")

            val bitmap = decodificarReduzido(context, uri)

            if (bitmap == null) {

                Log.w(TAG, "Bitmap nulo para uri=$uri")

                Toast.makeText(
                    context,
                    "El archivo del comprobante no es una imagen válida",
                    Toast.LENGTH_LONG
                ).show()

                return
            }

            mostrarDialogo(context, bitmap)

        } catch (e: FileNotFoundException) {

            Log.w(TAG, "Archivo no encontrado: $uriComprobante", e)

            Toast.makeText(
                context,
                "El archivo del comprobante ya no está disponible",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: SecurityException) {

            Log.w(TAG, "Sin permiso para leer: $uriComprobante", e)

            Toast.makeText(
                context,
                "No hay permiso para acceder al comprobante",
                Toast.LENGTH_LONG
            ).show()

        } catch (e: Exception) {

            Log.w(TAG, "Error abriendo comprobante: $uriComprobante", e)

            Toast.makeText(
                context,
                "Error al abrir el comprobante",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun mostrarDialogo(context: Context, bitmap: Bitmap) {

        val dialog = Dialog(context)

        dialog.setContentView(R.layout.dialog_qr)

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        val imagen =
            dialog.findViewById<ImageView>(R.id.imgQrGrande)

        imagen.scaleType = ImageView.ScaleType.FIT_CENTER

        imagen.setImageBitmap(bitmap)

        dialog.setCanceledOnTouchOutside(true)

        imagen.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun decodificarReduzido(
        context: Context,
        uri: Uri
    ): Bitmap? {

        val limites = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        abrirStream(context, uri)?.use { stream ->

            BitmapFactory.decodeStream(stream, null, limites)
        }

        var sampleSize = 1

        while (
            limites.outWidth / sampleSize > MAX_LADO ||
            limites.outHeight / sampleSize > MAX_LADO
        ) {
            sampleSize *= 2
        }

        val opciones = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return abrirStream(context, uri)?.use { stream ->

            BitmapFactory.decodeStream(stream, null, opciones)
        }
    }

    /**
     * Los comprobantes pueden ser content:// (filas antiguas),
     * file:// (copias internas) o una ruta absoluta.
     */
    private fun abrirStream(context: Context, uri: Uri) = try {

        when (uri.scheme) {

            "file" -> java.io.File(uri.path ?: "").inputStream()

            null -> java.io.File(uri.toString()).inputStream()

            else -> context.contentResolver.openInputStream(uri)
        }

    } catch (e: FileNotFoundException) {

        throw e
    }
}
