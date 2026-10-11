package com.example.totaldiaria.modulos.prestamos.adaptador

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.prestamos.modelo.MetodosDePagoDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.PagoDePrestamo
import com.example.totaldiaria.modulos.prestamos.ui.FormatoPrestamos

/**
 * DIARIX · Fase 4 · Fila del historial de pagos.
 *
 * Muestra el pago y su distribución tal y como la calculó el servidor (las
 * asignaciones ya viajan ordenadas por número de cuota). Un pago revertido se
 * ve tachado con su estado y su motivo: la reversión no borra el registro.
 */
class PagosAdapter(
    private val lista: List<PagoDePrestamo>,
) : RecyclerView.Adapter<PagosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtCliente: TextView = view.findViewById(R.id.txtPagoCliente)
        val txtFecha: TextView = view.findViewById(R.id.txtPagoFecha)
        val txtMetodo: TextView = view.findViewById(R.id.txtPagoMetodo)
        val txtMonto: TextView = view.findViewById(R.id.txtPagoMonto)
        val txtEstado: TextView = view.findViewById(R.id.txtPagoEstado)
        val txtDistribucion: TextView = view.findViewById(R.id.txtPagoDistribucion)
        val txtComprobante: TextView = view.findViewById(R.id.txtPagoComprobante)
        val txtMotivo: TextView = view.findViewById(R.id.txtPagoMotivo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pago, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val pago = lista[position]

        holder.txtCliente.text = pago.cliente?.nombreCompleto ?: "Cliente eliminado"
        holder.txtFecha.text = FormatoPrestamos.formatearFecha(pago.fechaPago)
        holder.txtMetodo.text = MetodosDePagoDePrestamo.etiqueta(pago.metodoPago)
        holder.txtMonto.text = FormatoPrestamos.formatear(pago.monto)

        if (pago.revertido) {
            holder.txtEstado.visibility = View.VISIBLE
            holder.txtEstado.text = "REVERTIDO"
            holder.txtEstado.setTextColor(Color.parseColor("#D43D3D"))
            holder.itemView.alpha = 0.65f

            holder.txtMotivo.visibility = View.VISIBLE
            holder.txtMotivo.text = "Motivo: ${pago.motivoReversion ?: "sin detalle"}"
        } else {
            holder.txtEstado.visibility = View.GONE
            holder.itemView.alpha = 1f
            holder.txtMotivo.visibility = View.GONE
        }

        holder.txtDistribucion.text = if (pago.asignaciones.isEmpty()) {
            ""
        } else {
            pago.asignaciones.joinToString("  ·  ") { asignacion ->
                "Cuota ${asignacion.numeroCuota}: ${FormatoPrestamos.formatear(asignacion.montoAplicado)}"
            }
        }

        if (pago.comprobanteUrl != null) {
            holder.txtComprobante.visibility = View.VISIBLE
            holder.txtComprobante.text = "Con comprobante"
        } else {
            holder.txtComprobante.visibility = View.GONE
        }
    }
}