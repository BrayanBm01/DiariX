package com.example.totaldiaria.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.models.PapeleraItem
import com.example.totaldiaria.ui.FormatoMoneda

class PapeleraAdapter(
    private val lista: List<PapeleraItem>
) : RecyclerView.Adapter<PapeleraAdapter.ViewHolder>() {

    class ViewHolder(view: View) :
        RecyclerView.ViewHolder(view) {

        val txtFecha: TextView =
            view.findViewById(R.id.txtFecha)

        val txtCantidad: TextView =
            view.findViewById(R.id.txtCantidad)

        val txtTotal: TextView =
            view.findViewById(R.id.txtTotal)

        val btnRestaurar: Button =
            view.findViewById(R.id.btnRestaurar)
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {

        val view =
            LayoutInflater.from(parent.context)
                .inflate(
                    R.layout.item_papelera,
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
            "${item.cantidadFacturas} facturas"

        holder.txtTotal.text =
            FormatoMoneda.formatear(item.total)

        holder.btnRestaurar.setOnClickListener {
            // Después programaremos restaurar
        }
    }

    override fun getItemCount(): Int {
        return lista.size
    }
}