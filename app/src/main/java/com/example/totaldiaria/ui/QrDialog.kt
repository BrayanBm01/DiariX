package com.example.totaldiaria.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import com.example.totaldiaria.R

object QrDialog {

    fun mostrar(context: Context, archivoQr: java.io.File) {

        val dialog = Dialog(context)

        dialog.setContentView(R.layout.dialog_qr)

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

        val imgQrGrande =
            dialog.findViewById<ImageView>(R.id.imgQrGrande)

        imgQrGrande.setImageURI(
            android.net.Uri.fromFile(archivoQr)
        )

        dialog.setCanceledOnTouchOutside(true)

        imgQrGrande.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
