package com.example.totaldiaria.modulos.prestamos.adaptador

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.prestamos.modelo.Cobro
import com.example.totaldiaria.modulos.prestamos.modelo.EstadosDeCobro
import com.example.totaldiaria.modulos.prestamos.modelo.ModalidadesDePrestamo
import com.example.totaldiaria.modulos.prestamos.ui.FormatoPrestamos
import java.math.BigDecimal

/**
 * DIARIX · Fase 4 · Lista de cobros (HOY / VENCIDAS / PRÓXIMAS).
 *
 * Simple: la categoría ya la filtró el SERVIDOR por fecha real y saldo; aquí
 * solo se pinta la cuota con su saldo y su atraso. Tocar la fila abre la
 * ficha del préstamo para poder registrar el pago desde ahí.
 */
class CobrosAdapter(
    private val lista: List<Cobro>,
    private val onCobroClick: (Cobro) -> Unit,
) : RecyclerView.Adapter<CobrosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtCliente: TextView = view.findViewById(R.id.txtCobroCliente)
        val txtReferencia: TextView = view.findViewById(R.id.txtCobroReferencia)
        val txtFecha: TextView = view.findViewById(R.id.txtCobroFecha)
        val txtEsperado: TextView = view.findViewById(R.id.txtCobroEsperado)
        val txtSaldo: TextView = view.findViewById(R.id.txtCobroSaldo)
        val txtEstado: TextView = view.findViewById(R.id.txtCobroEstado)
        val txtAtraso: TextView = view.findViewById(R.id.txtCobroAtraso)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cobro, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cobro = lista[position]

        holder.txtCliente.text = cobro.cliente?.nombreCompleto ?: "Cliente eliminado"
        holder.txtReferencia.text = buildString {
            append("Cuota ${cobro.numeroCuota}")
            append("  ·  ")
            append(ModalidadesDePrestamo.etiqueta(cobro.modalidad))
        }

        val estado = cobro.estado
        val color = Color.parseColor(EstadosDeCobro.color(estado))
        holder.txtEstado.text = EstadosDeCobro.etiqueta(estado)
        holder.txtEstado.setTextColor(color)

        holder.txtFecha.text = "Vence el ${FormatoPrestamos.formatearFecha(cobro.fechaVencimiento)}"

        if (cobro.diasDeAtraso > 0) {
            holder.txtAtraso.visibility = View.VISIBLE
            holder.txtAtraso.text = buildString {
                append(cobro.diasDeAtraso)
                append(if (cobro.diasDeAtraso == 1) " día" else " días")
                append(" de atraso")
            }
        } else {
            holder.txtAtraso.visibility = View.GONE
        }

        holder.txtEsperado.text = FormatoPrestamos.formatear(cobro.valorEsperado)

        if (cobro.saldo > BigDecimal.ZERO) {
            holder.txtSaldo.visibility = View.VISIBLE
            holder.txtSaldo.text = "Saldo ${FormatoPrestamos.formatear(cobro.saldo)}"
        } else {
            holder.txtSaldo.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onCobroClick(cobro) }
    }
}