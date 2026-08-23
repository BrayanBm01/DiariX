package com.example.totaldiaria.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import com.example.totaldiaria.R

object ComprobanteDialog {

    private const val MAX_LADO = 1200

    fun mostrar(context: Context, uriComprobante: String?) {

        if (uriComprobante.isNullOrEmpty()) return

        try {

            val uri = Uri.parse(uriComprobante)

            val bitmap = decodificarReduzido(context, uri)

            if (bitmap == null) {

                Toast.makeText(
                    context,
                    "No se pudo cargar la imagen",
                    Toast.LENGTH_SHORT
                ).show()

                return
            }

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

        } catch (_: Exception) {

            Toast.makeText(
                context,
                "Error al abrir el comprobante",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun decodificarReduzido(
        context: Context,
        uri: Uri
    ): Bitmap? {

        val limites = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->

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

        return context.contentResolver.openInputStream(uri)?.use { stream ->

            BitmapFactory.decodeStream(stream, null, opciones)
        }
    }
}
