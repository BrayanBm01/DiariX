package com.example.totaldiaria.modulos.notificaciones.adaptador

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.facturacion.ui.FormatoMoneda
import com.example.totaldiaria.modulos.notificaciones.modelo.Notificacion
import com.example.totaldiaria.modulos.notificaciones.ui.FormatoNotificacion

/**
 * DIARIX · Lista de notificaciones
 *
 * Recibe la página que ya recortó el servidor y solo la pinta. El toque en una
 * fila la marca como leída (la pantalla hace la llamada y recarga); el punto
 * verde es lo único que cambia de una fila leída a una pendiente.
 *
 * La lista es MUTABLE y el adaptador se conserva entre recargas: la pantalla
 * refresca cada 30 s y reemplazar el adaptador entero devolvería el scroll al
 * principio, justo mientras alguien lee un aviso. `notifyDataSetChanged`
 * mantiene la posición y repinta solo lo que llega.
 *
 * El icono y su color salen del TIPO que manda el servidor, no del texto del
 * título: si el backend añade un tipo nuevo, se muestra el icono de
 * comprobante por defecto en vez de reventar el `when`.
 */
class NotificacionesAdapter(
    private val onNotificacionClick: (Notificacion) -> Unit,
) : RecyclerView.Adapter<NotificacionesAdapter.ViewHolder>() {

    private val lista = mutableListOf<Notificacion>()

    /** Sustituye los datos conservando el adaptador (y por tanto el scroll). */
    fun actualizar(nuevas: List<Notificacion>) {
        lista.clear()
        lista.addAll(nuevas)
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val contenedorIcono: FrameLayout =
            view.findViewById(R.id.contenedorIconoNotificacion)

        val imgIcono: ImageView =
            view.findViewById(R.id.imgIconoNotificacion)

        val txtTitulo: TextView =
            view.findViewById(R.id.txtTituloNotificacion)

        val txtMensaje: TextView =
            view.findViewById(R.id.txtMensajeNotificacion)

        val txtMeta: TextView =
            view.findViewById(R.id.txtMetaNotificacion)

        val txtFecha: TextView =
            view.findViewById(R.id.txtFechaNotificacion)

        val puntoNoLeida: ImageView =
            view.findViewById(R.id.puntoNoLeida)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notificacion, parent, false)

        return ViewHolder(view)
    }

    override fun getItemCount(): Int = lista.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notificacion = lista[position]

        holder.txtTitulo.text = notificacion.titulo
        holder.txtMensaje.text = notificacion.mensaje

        holder.txtMeta.text =
            "Factura #${notificacion.numero}  ·  " +
                FormatoMoneda.formatear(notificacion.total)

        holder.txtFecha.text = FormatoNotificacion.fechaHora(notificacion.creadaEn)

        if (notificacion.esPago) {
            holder.contenedorIcono.setBackgroundResource(R.drawable.bg_punto_verde)
            holder.imgIcono.setImageResource(R.drawable.ic_pagos)
        } else {
            holder.contenedorIcono.setBackgroundResource(R.drawable.bg_punto_azul)
            holder.imgIcono.setImageResource(R.drawable.ic_receipt)
        }

        holder.puntoNoLeida.visibility =
            if (notificacion.leida) View.GONE else View.VISIBLE

        holder.itemView.setOnClickListener { onNotificacionClick(notificacion) }
    }
}
