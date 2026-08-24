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
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Gráfica de barras verticales para la cantidad de facturas por día.
 * Rejilla con pasos enteros, etiquetas de fechas espaciadas y globo
 * informativo al tocar una barra.
 */
class GraficaBarras @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Barra(val etiqueta: String, val valor: Int)

    private var barras: List<Barra> = emptyList()

    private var indiceSeleccionado = -1

    private val colorBarra =
        ContextCompat.getColor(context, R.color.azul_pago)

    private val colorTexto =
        ContextCompat.getColor(context, R.color.gris_etiqueta)

    private val colorValor =
        ContextCompat.getColor(context, R.color.texto_principal_dark)

    private val colorRejilla = 0xFFE8ECF5.toInt()

    private val colorPista = 0xFFF0F3FA.toInt()

    private val pincelBarra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorBarra
    }

    private val pincelRejilla = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = colorRejilla
    }

    private val pincelEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = colorTexto
        textAlign = Paint.Align.LEFT
    }

    private val pincelValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = colorValor
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pincelTooltipTitulo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = colorTexto
        textAlign = Paint.Align.CENTER
    }

    private val pincelTooltipValor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(12f)
        color = colorValor
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val pincelGlobo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
        setShadowLayer(dp(4f), 0f, dp(2f), 0x33000000)
    }

    private var areaGrafica = RectF()

    fun setDatos(nuevasBarras: List<Barra>) {

        barras = nuevasBarras

        indiceSeleccionado = -1

        contentDescription =
            if (barras.isEmpty()) "Sin datos"
            else "Gráfica de facturas por día"

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        if (barras.isEmpty()) return

        val maximo = valorMaximoRedondo(
            barras.maxOf { it.valor }
        )

        dibujarRejilla(canvas, maximo)

        dibujarBarras(canvas, maximo)

        dibujarEtiquetasX(canvas)

        if (indiceSeleccionado in barras.indices) {

            dibujarTooltip(canvas, maximo)
        }
    }

    private fun dibujarRejilla(canvas: Canvas, maximo: Double) {

        val lineas = 4

        for (i in 0..lineas) {

            val y = areaGrafica.bottom -
                    (areaGrafica.height() * i / lineas)

            canvas.drawLine(
                dp(6f),
                y,
                width - dp(6f),
                y,
                pincelRejilla
            )

            canvas.drawText(
                formatoEntero(maximo * i / lineas),
                dp(6f),
                y - dp(4f),
                pincelEtiqueta
            )
        }
    }

    private fun dibujarBarras(canvas: Canvas, maximo: Double) {

        val cantidad = barras.size

        val anchoTotal = areaGrafica.width()

        val espacio = anchoTotal / cantidad.coerceAtLeast(1)

        val anchoBarra = (espacio * 0.55f).coerceAtMost(dp(34f))

        for (i in barras.indices) {

            val centro = areaGrafica.left + espacio * i + espacio / 2

            val proporcion =

                if (maximo > 0) barras[i].valor / maximo else 0.0

            val alto = (areaGrafica.height() * proporcion).toFloat()

            val rect = RectF(
                centro - anchoBarra / 2,
                areaGrafica.bottom - alto,
                centro + anchoBarra / 2,
                areaGrafica.bottom
            )

            pincelBarra.color =

                if (i == indiceSeleccionado) colorValor
                else colorBarra

            canvas.drawRoundRect(
                rect,
                dp(5f),
                dp(5f),
                pincelBarra
            )

            if (cantidad <= 8 && barras[i].valor > 0) {

                canvas.drawText(
                    "${barras[i].valor}",
                    centro,
                    rect.top - dp(5f),
                    pincelValor
                )
            }
        }
    }

    /**
     * Espaciado uniforme entre días; cada posición corresponde a un
     * día del periodo.
     */
    private fun dibujarEtiquetasX(canvas: Canvas) {

        if (barras.isEmpty()) return

        val cantidad = barras.size

        val espacio = areaGrafica.width() / cantidad.coerceAtLeast(1)

        val anchoDisponible = areaGrafica.width()

        val maximoEtiquetas =
            (anchoDisponible / dp(38f)).toInt().coerceAtLeast(1)

        val salto =

            if (cantidad <= maximoEtiquetas) 1
            else ceil(cantidad / maximoEtiquetas.toDouble()).toInt()

        val yTexto = height - dp(6f)

        pincelEtiqueta.textAlign = Paint.Align.CENTER

        for (i in barras.indices step salto) {

            canvas.drawText(
                barras[i].etiqueta,
                areaGrafica.left + espacio * i + espacio / 2,
                yTexto,
                pincelEtiqueta
            )
        }
    }

    private fun dibujarTooltip(canvas: Canvas, maximo: Double) {

        val dato = barras[indiceSeleccionado]

        val titulo = dato.etiqueta

        val valor = "${dato.valor} facturas"

        val ancho = maxOf(
            pincelTooltipTitulo.measureText(titulo),
            pincelTooltipValor.measureText(valor)
        ).plus(dp(24f))

        val alto = dp(44f)

        val cantidad = barras.size

        val espacio = areaGrafica.width() / cantidad.coerceAtLeast(1)

        val centroX =
            areaGrafica.left + espacio * indiceSeleccionado + espacio / 2

        var izquierda = centroX - ancho / 2

        izquierda = izquierda.coerceIn(dp(4f), width - ancho - dp(4f))

        val proporcion =

            if (maximo > 0) dato.valor / maximo else 0.0

        val topeBarra =
            areaGrafica.bottom -
                    (areaGrafica.height() * proporcion).toFloat()

        var arriba = topeBarra - alto - dp(12f)

        if (arriba < dp(2f)) arriba = areaGrafica.bottom - alto - dp(2f)

        val rect = RectF(
            izquierda,
            arriba,
            izquierda + ancho,
            arriba + alto
        )

        canvas.drawRoundRect(rect, dp(10f), dp(10f), pincelGlobo)

        canvas.drawText(
            titulo,
            rect.centerX(),
            arriba + dp(17f),
            pincelTooltipTitulo
        )

        canvas.drawText(
            valor,
            rect.centerX(),
            arriba + dp(35f),
            pincelTooltipValor
        )
    }

    override fun onTouchEvent(evento: MotionEvent): Boolean {

        when (evento.actionMasked) {

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> seleccionarMasCercano(evento.x)

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> performClick()
        }

        return true
    }

    private fun seleccionarMasCercano(xToque: Float) {

        if (barras.isEmpty()) return

        val espacio =
            areaGrafica.width() / barras.size.coerceAtLeast(1)

        val indice = ((xToque - areaGrafica.left) / espacio).toInt()

        val tolerancia = areaGrafica.width().toInt()

        indiceSeleccionado =

            if (indice in barras.indices &&
                xToque >= areaGrafica.left - tolerancia
            ) indice
            else -1

        invalidate()
    }

    override fun performClick(): Boolean {

        super.performClick()

        return true
    }

    override fun onSizeChanged(ancho: Int, alto: Int, prevA: Int, prevAl: Int) {

        super.onSizeChanged(ancho, alto, prevA, prevAl)

        areaGrafica = RectF(
            dp(40f),
            dp(14f),
            width - dp(8f),
            height - dp(26f)
        )
    }

    private fun valorMaximoRedondo(valor: Int): Double {

        if (valor <= 0) return 1.0

        val magnitud = Math.pow(
            10.0,
            Math.floor(Math.log10(valor.toDouble()))
        )

        val normalizado = valor / magnitud

        val techo =

            if (normalizado <= 1) 1.0
            else if (normalizado <= 2) 2.0
            else if (normalizado <= 5) 5.0
            else 10.0

        return techo * magnitud
    }

    private fun formatoEntero(valor: Double): String {

        val entero = ceil(valor - 0.001).toLong()

        return entero.toString()
    }

    private fun dp(valor: Float): Float =

        valor * resources.displayMetrics.density

    private fun sp(valor: Float): Float =

        valor * resources.displayMetrics.scaledDensity
}
