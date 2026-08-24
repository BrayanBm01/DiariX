package com.example.totaldiaria.ui.graficas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.example.totaldiaria.ui.FormatoMoneda

/**
 * Barras horizontales para comparar montos entre métodos de pago.
 * Cada fila muestra el nombre, la barra proporcional y el monto real.
 */
class GraficaBarrasHorizontales @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Fila(
        val nombre: String,
        val valor: Double,
        val color: Int
    )

    private var filas: List<Fila> = emptyList()

    private val colorTexto = 0xFF7A7A9D.toInt()

    private val colorValor = 0xFF1C2340.toInt()

    private val colorPista = 0xFFF0F3FA.toInt()

    private val pincelNombre = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        color = colorValor
        isFakeBoldText = true
    }

    private val pincelMonto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        color = colorValor
        textAlign = Paint.Align.RIGHT
        isFakeBoldText = true
    }

    private val pincelPista = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorPista
    }

    private val pincelBarra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    fun setDatos(nuevasFilas: List<Fila>) {

        filas = nuevasFilas.filter { it.valor > 0 }

        contentDescription =
            if (filas.isEmpty()) "Sin datos"
            else "Comparación de ingresos por método de pago"

        requestLayout()

        invalidate()
    }

    override fun onMeasure(
        ancho: Int,
        alto: Int
    ) {

        val anchoDeseado = View.getDefaultSize(
            suggestedMinimumWidth,
            ancho
        )

        val altoDeseado =

            if (filas.isEmpty()) 0
            else paddingHeight() + filas.size * altoFila()

        setMeasuredDimension(anchoDeseado, altoDeseado)
    }

    private fun paddingHeight(): Int = dp(6f).toInt() * 2

    private fun altoFila(): Int = dp(46f).toInt()

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        if (filas.isEmpty()) return

        val maximo = filas.maxOf { it.valor }

        for (i in filas.indices) {

            val fila = filas[i]

            val tope = (i * altoFila()).toFloat()

            canvas.drawText(
                fila.nombre,
                dp(4f),
                tope + dp(14f),
                pincelNombre
            )

            val yBarra = tope + dp(22f)

            val altoBarra = dp(14f)

            val izquierda = dp(4f)

            val derecha = width - dp(4f)

            val pista = RectF(
                izquierda,
                yBarra,
                derecha,
                yBarra + altoBarra
            )

            canvas.drawRoundRect(
                pista,
                dp(7f),
                dp(7f),
                pincelPista
            )

            val proporcion =

                if (maximo > 0) fila.valor / maximo else 0.0

            val anchoBarra =
                ((derecha - izquierda) * proporcion).toFloat()

            if (anchoBarra > dp(10f)) {

                pincelBarra.color = fila.color

                canvas.drawRoundRect(
                    RectF(
                        izquierda,
                        yBarra,
                        izquierda + anchoBarra,
                        yBarra + altoBarra
                    ),
                    dp(7f),
                    dp(7f),
                    pincelBarra
                )
            }

            val textoMonto = FormatoMoneda.formatear(fila.valor)

            val anchoTexto = pincelMonto.measureText(textoMonto)

            // El monto va justo después de la barra si alcanza;
            // si no, dentro del borde derecho de la pista.
            val xMonto =

                if (izquierda + anchoBarra + dp(12f) + anchoTexto <= derecha)
                    izquierda + anchoBarra + dp(12f) + anchoTexto
                else derecha - dp(8f)

            canvas.drawText(
                textoMonto,
                xMonto,
                yBarra + dp(11.5f),
                pincelMonto
            )
        }
    }

    private fun dp(valor: Float): Float =

        valor * resources.displayMetrics.density

    private fun sp(valor: Float): Float =

        valor * resources.displayMetrics.scaledDensity
}
