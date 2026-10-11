package com.example.totaldiaria.modulos.prestamos.adaptador

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.prestamos.modelo.EstadosDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.Prestamo
import com.example.totaldiaria.modulos.prestamos.ui.FormatoPrestamos

/**
 * DIARIX · Fase 3 · Lista de préstamos.
 *
 * Mismo patrón que [com.example.totaldiaria.modulos.facturacion.adaptador.ClientesAdapter]:
 * lista inmutable recibida de fuera, sin estado propio y sin paginar (la
 * página la pide la pantalla). El dinero se pinta con [FormatoPrestamos]
 * (BigDecimal, es-CO) y el estado derivado con su etiqueta y color de
 * [EstadosDePrestamo].
 */
class PrestamosAdapter(
    private val lista: List<Prestamo>,
    private val onPrestamoClick: (Prestamo) -> Unit,
) : RecyclerView.Adapter<PrestamosAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtNombre: TextView = view.findViewById(R.id.txtNombrePrestamo)
        val txtEstado: TextView = view.findViewById(R.id.txtEstadoPrestamo)
        val txtDocumento: TextView = view.findViewById(R.id.txtDocumentoPrestamo)
        val txtMonto: TextView = view.findViewById(R.id.txtMontoPrestado)
        val txtTotal: TextView = view.findViewById(R.id.txtTotalPrestamo)
        val txtSaldo: TextView = view.findViewById(R.id.txtSaldoPrestamo)
        val txtCuotas: TextView = view.findViewById(R.id.txtCuotasPrestamo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prestamo, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val prestamo = lista[position]

        holder.txtNombre.text = prestamo.cliente?.nombreCompleto ?: "Cliente eliminado"
        holder.txtDocumento.text = prestamo.cliente?.documentoConEtiqueta()
            ?: "Préstamo cerrado, sin referencia al cliente"

        holder.txtMonto.text = FormatoPrestamos.formatear(prestamo.montoPrestado)
        holder.txtTotal.text = FormatoPrestamos.formatear(prestamo.totalAPagar)
        holder.txtSaldo.text = FormatoPrestamos.formatear(prestamo.saldoPendiente)
        holder.txtCuotas.text =
            "${prestamo.cuotasPagadas} de ${prestamo.numeroDeCuotas} cuotas"

        val color = Color.parseColor(EstadosDePrestamo.color(prestamo.estado))
        holder.txtEstado.text = EstadosDePrestamo.etiqueta(prestamo.estado)
        holder.txtEstado.setTextColor(color)

        holder.itemView.setOnClickListener { onPrestamoClick(prestamo) }
    }
}