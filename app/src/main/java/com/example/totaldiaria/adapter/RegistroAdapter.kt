package com.example.totaldiaria.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.models.PapeleraItem
import com.example.totaldiaria.ui.FormatoMoneda

class RegistroAdapter(
    private val lista: List<PapeleraItem>,
    private val onItemClick: (PapeleraItem) -> Unit
) : RecyclerView.Adapter<RegistroAdapter.ViewHolder>() {

    class ViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val txtFecha: TextView =
            view.findViewById(R.id.txtFecha)

        val txtCantidad: TextView =
            view.findViewById(R.id.txtCantidad)

        val txtEfectivo: TextView =
            view.findViewById(R.id.txtEfectivo)

        val txtTransferencia: TextView =
            view.findViewById(R.id.txtTransferencia)

        val txtCantidadEfectivo: TextView =
            view.findViewById(R.id.txtCantidadEfectivo)

        val txtCantidadTransferencia: TextView =
            view.findViewById(R.id.txtCantidadTransferencia)

        val txtTotal: TextView =
            view.findViewById(R.id.txtTotal)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_registro,
                    parent,
                    false
                )

        return ViewHolder(view)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {

        val item = lista[position]

        holder.txtFecha.text =
            item.fecha

        holder.txtCantidad.text =
            item.cantidadFacturas.toString()

        holder.txtEfectivo.text =
            FormatoMoneda.formatear(item.efectivo)

        holder.txtTransferencia.text =
            FormatoMoneda.formatear(item.transferencia)

        holder.txtCantidadEfectivo.text =
            "${item.cantidadEfectivo} facturas"

        holder.txtCantidadTransferencia.text =
            "${item.cantidadTransferencia} facturas"

        holder.txtTotal.text =
            FormatoMoneda.formatear(item.total)

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int {
        return lista.size
    }
}