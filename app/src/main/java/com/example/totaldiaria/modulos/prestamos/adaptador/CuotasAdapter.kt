package com.example.totaldiaria.modulos.prestamos.adaptador

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.prestamos.modelo.CuotaDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.EstadosDePrestamo
import com.example.totaldiaria.modulos.prestamos.ui.FormatoPrestamos
import java.math.BigDecimal

/**
 * DIARIX · Fase 3 · Calendario de cuotas de la ficha de un préstamo.
 *
 * Simple: el servidor ya puso cada cuota en su orden (y en su fecha), así que
 * aquí solo se pinta. El fondo de la fila acompaña al estado: la vencida se
 * ve rojiza y la pagada verdosa aunque no se lea el texto.
 */
class CuotasAdapter(
    private val lista: List<CuotaDePrestamo>,
) : RecyclerView.Adapter<CuotasAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNumero: TextView = view.findViewById(R.id.txtCuotaNumero)
        val txtFecha: TextView = view.findViewById(R.id.txtCuotaFecha)
        val txtValor: TextView = view.findViewById(R.id.txtCuotaValor)
        val txtEstado: TextView = view.findViewById(R.id.txtCuotaEstado)
        val txtPagos: TextView = view.findViewById(R.id.txtCuotaPagos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cuota, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cuota = lista[position]

        holder.txtNumero.text = "Cuota ${cuota.numero}"
        holder.txtFecha.text = "Vence el ${FormatoPrestamos.formatearFecha(cuota.fechaVencimiento)}"
        holder.txtValor.text = FormatoPrestamos.formatear(cuota.valorEsperado)
        holder.txtEstado.text = EstadosDePrestamo.etiqueta(cuota.estado)

        val color = Color.parseColor(EstadosDePrestamo.color(cuota.estado))
        holder.txtEstado.setTextColor(color)
        holder.itemView.setBackgroundColor(
            Color.parseColor(
                when (cuota.estado) {
                    EstadosDePrestamo.VENCIDA -> "#FDEDED"
                    EstadosDePrestamo.PAGADA -> "#EDFAF4"
                    else -> "#FFFFFF"
                },
            ),
        )

        if (cuota.valorPagado > BigDecimal.ZERO) {
            holder.txtPagos.visibility = View.VISIBLE
            holder.txtPagos.text = buildString {
                append("Pagado ${FormatoPrestamos.formatear(cuota.valorPagado)}")
                if (cuota.saldo > BigDecimal.ZERO) {
                    append("  ·  Saldo ${FormatoPrestamos.formatear(cuota.saldo)}")
                }
            }
        } else {
            holder.txtPagos.visibility = View.GONE
        }
    }
}