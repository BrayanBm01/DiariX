package com.example.totaldiaria.ui.graficas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.totaldiaria.R
import com.example.totaldiaria.ui.FormatoMoneda
import kotlin.math.atan2

/**
 * Donut para la distribución de ingresos por método de pago. Al tocar
 * un segmento, el centro muestra su nombre, monto y porcentaje; sin
 * selección muestra el total del periodo.
 */
class GraficaDona @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Segmento(
        val nombre: String,
        val valor: Double,
        val color: Int
    )

    private var segmentos: List<Segmento> = emptyList()

    private var indiceSeleccionado = -1

    private val colorTexto =
        ContextCompat.getColor(context, R.color.gris_etiqueta)

    private val colorValor =
        ContextCompat.getColor(context, R.color.texto_principal_dark)

    private val pincelArco = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(38f)
        strokeCap = Paint.Cap.BUTT
    }

    private val pincelTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = colorTexto
        textAlign = Paint.Align.CENTER
    }

    private val pincelValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(16f)
        color = colorValor
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pincelDetalle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(11f)
        color = colorTexto
        textAlign = Paint.Align.CENTER
    }

    private var rectDona = RectF()

    /** Ángulos de inicio de cada segmento para detectar el toque. */
    private var inicios = listOf<Double>()

    private var totalGeneral = 0.0

    fun setDatos(nuevosSegmentos: List<Segmento>) {

        segmentos = nuevosSegmentos.filter { it.valor > 0 }

        indiceSeleccionado = -1

        totalGeneral = segmentos.sumOf { it.valor }

        contentDescription =
            if (segmentos.isEmpty()) "Sin datos"
            else "Distribución de métodos de pago"

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        if (segmentos.isEmpty() || totalGeneral <= 0) return

        val centroX = width / 2f

        val centroY = height / 2f

        val radio =

            (minOf(width, height) / 2f) - pincelArco.strokeWidth / 2 - dp(4f)

        if (rectDona.isEmpty) {

            rectDona = RectF(
                centroX - radio,
                centroY - radio,
                centroX + radio,
                centroY + radio
            )
        }

        var inicio = -90.0

        val iniciosNuevos = mutableListOf<Double>()

        for (segmento in segmentos) {

            iniciosNuevos.add(inicio)

            val barrido = segmento.valor / totalGeneral * 360.0

            pincelArco.color = segmento.color

            canvas.drawArc(
                rectDona,
                inicio.toFloat(),
                barrido.toFloat(),
                false,
                pincelArco
            )

            inicio += barrido
        }

        inicios = iniciosNuevos

        dibujarCentro(canvas, centroX, centroY)
    }

    private fun dibujarCentro(canvas: Canvas, centroX: Float, centroY: Float) {

        val seleccionado =

            if (indiceSeleccionado in segmentos.indices)
                segmentos[indiceSeleccionado]
            else null

        if (seleccionado == null) {

            canvas.drawText(
                "Total",
                centroX,
                centroY + dp(2f),
                pincelTitulo
            )

            canvas.drawText(
                FormatoMoneda.formatearCompacto(totalGeneral),
                centroX,
                centroY + dp(24f),
                pincelValor
            )

        } else {

            val porcentaje =

                if (totalGeneral > 0)
                    (seleccionado.valor / totalGeneral * 100).toInt()
                else 0

            canvas.drawText(
                seleccionado.nombre,
                centroX,
                centroY - dp(12f),
                pincelTitulo
            )

            canvas.drawText(
                FormatoMoneda.formatearCompacto(seleccionado.valor),
                centroX,
                centroY + dp(10f),
                pincelValor
            )

            canvas.drawText(
                "$porcentaje%",
                centroX,
                centroY + dp(30f),
                pincelDetalle
            )
        }
    }

    override fun onTouchEvent(evento: MotionEvent): Boolean {

        when (evento.actionMasked) {

            MotionEvent.ACTION_DOWN -> seleccionarSegmento(evento.x, evento.y)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> performClick()
        }

        return true
    }

    private fun seleccionarSegmento(xToque: Float, yToque: Float) {

        if (segmentos.isEmpty()) return

        val centroX = width / 2f

        val centroY = height / 2f

        val distancia = Math.hypot(
            (xToque - centroX).toDouble(),
            (yToque - centroY).toDouble()
        )

        val radioInterior =
            (minOf(width, height) / 2f) - pincelArco.strokeWidth - dp(4f)

        indiceSeleccionado =

            if (distancia >= radioInterior &&
                distancia <= radioInterior + pincelArco.strokeWidth * 2
            ) {

                var angulo = Math.toDegrees(
                    atan2(
                        (yToque - centroY).toDouble(),
                        (xToque - centroX).toDouble()
                    )
                ).let { if (it < 0) it + 360 else it }

                // Los arcos empiezan en -90° (arriba).
                angulo = (angulo + 90.0) % 360.0

                var elegido = -1

                for (i in segmentos.indices) {

                    val inicioNormalizado =
                        ((inicios[i] + 360.0) % 360.0)

                    val barrido =
                        segmentos[i].valor / totalGeneral * 360.0

                    if (angulo >= inicioNormalizado &&
                        angulo < inicioNormalizado + barrido
                    ) {
                        elegido = i
                        break
                    }
                }

                elegido

            } else -1

        invalidate()
    }

    override fun performClick(): Boolean {

        super.performClick()

        return true
    }

    override fun onSizeChanged(ancho: Int, alto: Int, prevA: Int, prevAl: Int) {

        super.onSizeChanged(ancho, alto, prevA, prevAl)

        rectDona.setEmpty()
    }

    private fun dp(valor: Float): Float =

        valor * resources.displayMetrics.density

    private fun sp(valor: Float): Float =

        valor * resources.displayMetrics.scaledDensity
}
