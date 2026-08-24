package com.example.totaldiaria.ui.graficas

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.totaldiaria.R
import com.example.totaldiaria.ui.FormatoMoneda
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Gráfica de líneas para la evolución diaria. Dibuja rejilla,
 * etiquetas compactas de dinero, línea con área sombreada, puntos y
 * un globo informativo al tocar el punto más cercano.
 */
class GraficaLineas @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class Punto(val etiqueta: String, val valor: Double)

    private var puntos: List<Punto> = emptyList()

    private var indiceSeleccionado = -1

    private val colorLinea =
        ContextCompat.getColor(context, R.color.verde_pago)

    private val colorTexto =
        ContextCompat.getColor(context, R.color.gris_etiqueta)

    private val colorValor =
        ContextCompat.getColor(context, R.color.texto_principal_dark)

    private val colorRejilla = 0xFFE8ECF5.toInt()

    private val colorArea = 0x2E0AA87A

    private val pincelLinea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = colorLinea
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pincelArea = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorArea
    }

    private val pincelPunto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorLinea
    }

    private val pincelRejilla = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = colorRejilla
    }

    private val pincelEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sp(10f)
        color = colorTexto
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

    fun setDatos(nuevosPuntos: List<Punto>) {

        puntos = nuevosPuntos

        indiceSeleccionado = -1

        contentDescription =
            if (puntos.isEmpty()) "Sin datos"
            else "Gráfica de ingresos por día"

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {

        super.onDraw(canvas)

        if (puntos.isEmpty()) return

        val maximo = valorMaximoRedondo(
            puntos.maxOf { it.valor }
        )

        dibujarRejilla(canvas, maximo)

        val coordenadas = calcularCoordenadas(maximo)

        if (coordenadas.size > 1) {

            dibujarLinea(canvas, coordenadas)
        }

        dibujarEtiquetasX(canvas)

        if (indiceSeleccionado in puntos.indices) {

            dibujarTooltip(canvas, coordenadas[indiceSeleccionado])
        }
    }

    private fun dibujarRejilla(canvas: Canvas, maximo: Double) {

        val lineas = 4

        for (i in 0..lineas) {

            val y = areaGrafica.bottom -
                    (areaGrafica.height() * i / lineas)

            canvas.drawLine(
                areaGrafica.left,
                y,
                areaGrafica.right,
                y,
                pincelRejilla
            )

            val valor = maximo * i / lineas

            val etiqueta = FormatoMoneda.formatearCompacto(valor)

            pincelEtiqueta.textAlign = Paint.Align.LEFT

            canvas.drawText(
                etiqueta,
                dp(6f),
                y - dp(4f),
                pincelEtiqueta
            )
        }
    }

    /**
     * Espaciado uniforme entre días: cada día del periodo ocupa su
     * posición aunque no tenga facturas.
     */
    private fun calcularCoordenadas(
        maximo: Double
    ): MutableList<Pair<Float, Float>> {

        val lista = mutableListOf<Pair<Float, Float>>()

        if (puntos.isEmpty()) return lista

        val ancho = areaGrafica.width()

        val paso =

            if (puntos.size == 1) 0f
            else ancho / (puntos.size - 1)

        for (i in puntos.indices) {

            val x = areaGrafica.left + paso * i

            val proporcion =

                if (maximo > 0) puntos[i].valor / maximo else 0.0

            val y = areaGrafica.bottom -
                    (areaGrafica.height() * proporcion).toFloat()

            lista.add(Pair(x, y))
        }

        return lista
    }

    private fun dibujarLinea(
        canvas: Canvas,
        coordenadas: List<Pair<Float, Float>>
    ) {

        val trazo = Path()

        coordenadas.forEachIndexed { indice, (x, y) ->

            if (indice == 0) trazo.moveTo(x, y) else trazo.lineTo(x, y)
        }

        val relleno = Path(trazo)

        relleno.lineTo(
            coordenadas.last().first,
            areaGrafica.bottom
        )

        relleno.lineTo(
            coordenadas.first().first,
            areaGrafica.bottom
        )

        relleno.close()

        canvas.drawPath(relleno, pincelArea)

        canvas.drawPath(trazo, pincelLinea)

        coordenadas.forEach { (x, y) ->
            canvas.drawCircle(x, y, dp(4f), pincelPunto)
        }

        indiceSeleccionado.takeIf { it in coordenadas.indices }
            ?.let { indice ->

                val punto = coordenadas[indice]

                canvas.drawCircle(
                    punto.first,
                    punto.second,
                    dp(6.5f),
                    pincelAnillo()
                )
            }
    }

    private fun pincelAnillo(): Paint =

        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(2.5f)
            color = colorValor
        }

    /**
     * Muestra como máximo tantas fechas como quepan sin encimarse.
     */
    private fun dibujarEtiquetasX(canvas: Canvas) {

        if (puntos.isEmpty()) return

        val anchoDisponible = width - dp(52f)

        val maximoEtiquetas =
            (anchoDisponible / dp(38f)).toInt().coerceAtLeast(1)

        val salto =

            if (puntos.size <= maximoEtiquetas) 1
            else ceil(puntos.size / maximoEtiquetas.toDouble()).toInt()

        val paso =

            if (puntos.size == 1) 0f
            else areaGrafica.width() / (puntos.size - 1)

        val yTexto = height - dp(6f)

        for (i in puntos.indices step salto) {

            pincelEtiqueta.textAlign = Paint.Align.CENTER

            canvas.drawText(
                puntos[i].etiqueta,
                areaGrafica.left + paso * i,
                yTexto,
                pincelEtiqueta
            )
        }
    }

    private fun dibujarTooltip(
        canvas: Canvas,
        punto: Pair<Float, Float>
    ) {

        val dato = puntos[indiceSeleccionado]

        val titulo = dato.etiqueta

        val valor = FormatoMoneda.formatear(dato.valor)

        val ancho = maxOf(
            pincelTooltipTitulo.measureText(titulo),
            pincelTooltipValor.measureText(valor)
        ).plus(dp(24f))

        val alto = dp(44f)

        var izquierda = punto.first - ancho / 2

        izquierda = izquierda.coerceIn(dp(4f), width - ancho - dp(4f))

        var arriba = punto.second - alto - dp(14f)

        if (arriba < dp(2f)) arriba = punto.second + dp(14f)

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

        if (puntos.isEmpty()) return

        val paso =

            if (puntos.size == 1) 0f
            else areaGrafica.width() / (puntos.size - 1)

        var mejorIndice = -1

        var mejorDistancia = Float.MAX_VALUE

        for (i in puntos.indices) {

            val distancia = abs(areaGrafica.left + paso * i - xToque)

            if (distancia < mejorDistancia) {

                mejorDistancia = distancia

                mejorIndice = i
            }
        }

        val tolerancia = dp(34f)

        indiceSeleccionado =

            if (mejorDistancia <= tolerancia) mejorIndice
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
            dp(52f),
            dp(14f),
            width - dp(10f),
            height - dp(26f)
        )
    }

    private fun valorMaximoRedondo(valor: Double): Double {

        if (valor <= 0) return 1.0

        val magnitud = Math.pow(
            10.0,
            Math.floor(Math.log10(valor))
        )

        val normalizado = valor / magnitud

        val techo =

            if (normalizado <= 1) 1.0
            else if (normalizado <= 2) 2.0
            else if (normalizado <= 5) 5.0
            else 10.0

        return techo * magnitud
    }

    private fun dp(valor: Float): Float =

        valor * resources.displayMetrics.density

    private fun sp(valor: Float): Float =

        valor * resources.displayMetrics.scaledDensity
}
