package com.example.totaldiaria.modulos.facturacion.adaptador

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.facturacion.modelo.Cliente

/**
 * DIARIX · Fase 2 · Lista de clientes.
 *
 * Patrón idéntico a [FacturaAdapter]: lista inmutable recibida de fuera,
 * dos `TextView` de identidad y un tercero de contacto, y el toque en la fila
 * abriendo la ficha. No se mantiene estado propio ni pagina: la página la
 * decide el servidor y la decide la pantalla que le pide datos.
 *
 * El contacto es UNA sola línea y no tres. En un listado de un teléfono de
 * 5 pulgadas, tres líneas por cliente deja cuatro o cinco contactos en
 * pantalla; una sola con "teléfono · correo" muestra lo que hace falta para
 * reconocer a alguien y el resto está a un toque, en la ficha.
 */
class ClientesAdapter(
    private val lista: List<Cliente>,
    private val onClienteClick: (Cliente) -> Unit,
) : RecyclerView.Adapter<ClientesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val txtNombre: TextView = view.findViewById(R.id.txtClienteNombre)
        val txtDocumento: TextView = view.findViewById(R.id.txtClienteDocumento)
        val txtContacto: TextView = view.findViewById(R.id.txtClienteContacto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cliente, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cliente = lista[position]

        holder.txtNombre.text = cliente.nombreCompleto
        holder.txtDocumento.text = cliente.documentoConEtiqueta()

        val contacto = listOfNotNull(
            cliente.telefono,
            cliente.email,
        ).joinToString("  ·  ")

        holder.txtContacto.text = contacto.ifEmpty { "Sin datos de contacto" }

        holder.itemView.setOnClickListener { onClienteClick(cliente) }
    }
}
