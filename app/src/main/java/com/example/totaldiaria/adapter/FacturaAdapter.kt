package com.example.totaldiaria.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.models.Factura
import com.example.totaldiaria.ui.FormatoHora
import com.example.totaldiaria.ui.FormatoMoneda
import com.example.totaldiaria.ui.ComprobanteDialog
import androidx.core.content.ContextCompat

class FacturaAdapter(
    private val lista: List<Factura>,
    private val onFacturaClick: (Factura) -> Unit,
    private val onEliminarClick: (Factura) -> Unit
) : RecyclerView.Adapter<FacturaAdapter.ViewHolder>() {

    class ViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val txtNumero: TextView =
            view.findViewById(R.id.txtFacturaNumero)

        val txtMetodo: TextView =
            view.findViewById(R.id.txtMetodo)

        val txtFecha: TextView =
            view.findViewById(R.id.txtFecha)

        val txtValor: TextView =
            view.findViewById(R.id.txtValor)

        val btnComprobante: ImageButton =
            view.findViewById(R.id.btnComprobante)

        val btnEliminar: ImageButton =
            view.findViewById(R.id.btnEliminar)

    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_factura,
                    parent,
                    false
                )

        return ViewHolder(view)
    }

    override fun getItemCount(): Int =
        lista.size

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val factura = lista[position]

        val total =
            factura.efectivo +
                    factura.transferencia

        val metodo = when {

            factura.efectivo > 0 &&
                    factura.transferencia > 0 -> {
                "AMBOS"
            }

            factura.efectivo > 0 -> {
                "EFECTIVO"
            }

            else -> {
                "TRANSFERENCIA"
            }
        }

        holder.txtNumero.text =
            "Factura #${factura.numeroFactura}"

        holder.txtMetodo.text = metodo

        when (metodo) {

            "EFECTIVO" -> {
                holder.txtMetodo.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.verde_pago)
                )
            }

            "TRANSFERENCIA" -> {
                holder.txtMetodo.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.azul_pago)
                )
            }

            "AMBOS" -> {
                holder.txtMetodo.setTextColor(
                    ContextCompat.getColor(holder.itemView.context, R.color.naranja_pago)
                )
            }
        }

        holder.txtFecha.text =
            FormatoHora.fechaConHora12(factura.fecha)

        holder.txtValor.text =
            FormatoMoneda.formatear(total)

        if (!factura.comprobanteUri.isNullOrEmpty()) {

            holder.btnComprobante.visibility =
                View.VISIBLE

        } else {

            holder.btnComprobante.visibility =
                View.GONE
        }

        holder.btnComprobante.setOnClickListener {

            ComprobanteDialog.mostrar(
                holder.itemView.context,
                factura.comprobanteUri
            )
        }

        holder.itemView.setOnClickListener {
            onFacturaClick(factura)
        }

        holder.btnEliminar.setOnClickListener {
            onEliminarClick(factura)
        }
    }
}