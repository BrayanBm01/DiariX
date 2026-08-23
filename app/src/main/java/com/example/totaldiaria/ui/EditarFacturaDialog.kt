package com.example.totaldiaria.ui

import android.app.Activity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.totaldiaria.R
import com.example.totaldiaria.database.FacturaRepository
import com.example.totaldiaria.models.Factura

object EditarFacturaDialog {

    fun mostrar(
        activity: Activity,
        factura: Factura,
        repository: FacturaRepository,
        onActualizada: () -> Unit
    ) {

        val view = activity.layoutInflater.inflate(
            R.layout.dialog_editar_factura,
            null
        )

        val edtEfectivo =
            view.findViewById<EditText>(R.id.edtEfectivo)

        val edtTransferencia =
            view.findViewById<EditText>(R.id.edtTransferencia)

        val layoutEfectivo =
            view.findViewById<LinearLayout>(R.id.layoutEfectivo)

        val layoutTransferencia =
            view.findViewById<LinearLayout>(R.id.layoutTransferencia)

        prepararCampos(
            factura,
            edtEfectivo,
            edtTransferencia,
            layoutEfectivo,
            layoutTransferencia
        )

        AlertDialog.Builder(activity)
            .setTitle("Editar factura")
            .setView(view)

            .setPositiveButton("Guardar") { _, _ ->

                val efectivo =
                    if (layoutEfectivo.visibility == View.VISIBLE) {
                        FormatoMoneda.parsear(edtEfectivo.text.toString())
                    } else {
                        factura.efectivo
                    }

                val transferencia =
                    if (layoutTransferencia.visibility == View.VISIBLE) {
                        FormatoMoneda.parsear(edtTransferencia.text.toString())
                    } else {
                        factura.transferencia
                    }

                if (
                    efectivo < 0 ||
                    transferencia < 0
                ) {

                    Toast.makeText(
                        activity,
                        "Los valores no pueden ser negativos",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setPositiveButton
                }

                val actualizado =
                    repository.actualizarFactura(
                        factura.id,
                        efectivo,
                        transferencia
                    )

                if (actualizado) {

                    Toast.makeText(
                        activity,
                        "Factura actualizada",
                        Toast.LENGTH_SHORT
                    ).show()

                    onActualizada()

                } else {

                    Toast.makeText(
                        activity,
                        "Error al actualizar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            .setNegativeButton(
                "Cancelar",
                null
            )

            .show()
    }

    private fun prepararCampos(
        factura: Factura,
        edtEfectivo: EditText,
        edtTransferencia: EditText,
        layoutEfectivo: LinearLayout,
        layoutTransferencia: LinearLayout
    ) {

        val tieneEfectivo = factura.efectivo > 0

        val tieneTransferencia = factura.transferencia > 0

        when {

            tieneEfectivo && tieneTransferencia -> {

                layoutEfectivo.visibility = View.VISIBLE
                layoutTransferencia.visibility = View.VISIBLE

                edtEfectivo.setText(
                    FormatoMoneda.paraEdicion(factura.efectivo)
                )

                edtTransferencia.setText(
                    FormatoMoneda.paraEdicion(factura.transferencia)
                )
            }

            tieneEfectivo -> {

                layoutEfectivo.visibility = View.VISIBLE
                layoutTransferencia.visibility = View.GONE

                edtEfectivo.setText(
                    FormatoMoneda.paraEdicion(factura.efectivo)
                )
            }

            tieneTransferencia -> {

                layoutEfectivo.visibility = View.GONE
                layoutTransferencia.visibility = View.VISIBLE

                edtTransferencia.setText(
                    FormatoMoneda.paraEdicion(factura.transferencia)
                )
            }

            else -> {

                layoutEfectivo.visibility = View.VISIBLE
                layoutTransferencia.visibility = View.GONE

                edtEfectivo.setText("0")
            }
        }
    }
}
