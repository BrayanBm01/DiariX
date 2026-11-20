package com.example.totaldiaria.modulos.notificaciones

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.notificaciones.adaptador.NotificacionesAdapter
import com.example.totaldiaria.modulos.notificaciones.modelo.Notificacion
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DIARIX · La fila de notificación pinta lo que manda el servidor.
 *
 * Lo que se comprueba es el contrato visible: el título y el mensaje son los
 * del backend (no textos inventados por la app), un pago y un comprobante se
 * distinguen por su tipo, y el punto de pendiente desaparece cuando la fila
 * está leída SIN borrarla de la lista.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificacionesAdapterTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_TotalDiaria,
        )
    }

    private fun notificacion(
        tipo: String,
        leidaEn: String? = null,
    ) = Notificacion(
        id = "n-1",
        tipo = tipo,
        titulo = if (tipo == Notificacion.TIPO_PAGO) "Pago recibido"
        else "Comprobante recibido",
        mensaje = if (tipo == Notificacion.TIPO_PAGO)
            "Tu pago ha sido recibido y confirmado correctamente."
        else "Tu comprobante ha sido recibido correctamente.",
        facturaId = "f-1",
        numero = "001",
        total = 120_000.0,
        modalidad = "transferencia",
        conductorNombre = "Camila Restrepo",
        diaContable = "2026-11-14",
        leidaEn = leidaEn,
        creadaEn = "2026-11-14T12:51:20.468Z",
    )

    private fun bindear(notificacion: Notificacion): View {
        val adaptador = NotificacionesAdapter {}
        adaptador.actualizar(listOf(notificacion))

        val padre = FrameLayout(context)
        val holder = adaptador.onCreateViewHolder(padre, 0)
        adaptador.onBindViewHolder(holder, 0)

        return holder.itemView
    }

    @Test
    fun `un pago muestra los textos exactos del servidor y el punto pendiente`() {
        val vista = bindear(notificacion(Notificacion.TIPO_PAGO))

        assertEquals(
            "Pago recibido",
            vista.findViewById<TextView>(R.id.txtTituloNotificacion).text.toString(),
        )
        assertEquals(
            "Tu pago ha sido recibido y confirmado correctamente.",
            vista.findViewById<TextView>(R.id.txtMensajeNotificacion).text.toString(),
        )
        assertEquals(
            View.VISIBLE,
            vista.findViewById<View>(R.id.puntoNoLeida).visibility,
        )
    }

    @Test
    fun `un comprobante conserva su propio texto y su punto`() {
        val vista = bindear(notificacion(Notificacion.TIPO_COMPROBANTE))

        assertEquals(
            "Comprobante recibido",
            vista.findViewById<TextView>(R.id.txtTituloNotificacion).text.toString(),
        )
        assertEquals(
            "Tu comprobante ha sido recibido correctamente.",
            vista.findViewById<TextView>(R.id.txtMensajeNotificacion).text.toString(),
        )
        assertEquals(
            View.VISIBLE,
            vista.findViewById<View>(R.id.puntoNoLeida).visibility,
        )
    }

    @Test
    fun `una notificacion leida sigue en la lista pero sin punto`() {
        val vista = bindear(
            notificacion(Notificacion.TIPO_PAGO, leidaEn = "2026-11-14T13:00:00.000Z")
        )

        assertEquals(
            View.GONE,
            vista.findViewById<View>(R.id.puntoNoLeida).visibility,
        )
        assertEquals(
            "Pago recibido",
            vista.findViewById<TextView>(R.id.txtTituloNotificacion).text.toString(),
        )
    }
}
