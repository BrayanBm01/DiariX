package com.example.totaldiaria.core.cuenta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

/**
 * Vista de ajuste de la foto de perfil (SOLO «Ver y editar perfil»).
 *
 * La imagen se pinta completa y TODO lo que queda fuera del círculo se
 * oscurece con una máscara par-impar: lo que se ve dentro del círculo es
 * EXACTAMENTE lo que guarda [recortar], así que la vista previa no puede
 * discrepar del resultado.
 *
 * Gestos y controles:
 *
 *  - pellizco con dos dedos → zoom (limitado: nunca se descubre un hueco);
 *  - arrastre con uno o dos dedos → mueve la imagen para centrar el rostro;
 *  - [masZoom] / [menosZoom] → botones grandes para quien no use gestos.
 *
 * [recortar] produce el círculo REAL: esquinas transparentes, sin deformar
 * (el contenido se toma tal cual está en pantalla y se enmascara).
 */
class VistaRecorteCircular @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var imagen: Bitmap? = null
    private val matriz = Matrix()
    private val rectImagen = RectF()

    /** Lado del cuadrado de recorte (la vista es siempre cuadrada). */
    private var lado = 0f

    /** Escala mínima: la imagen SIEMPRE cubre el círculo completo. */
    private var escalaMin = 1f

    private val pintorImagen = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Velo sobre lo que quedará fuera del círculo. */
    private val pintorFuera = Paint().apply {
        style = Paint.Style.FILL
        color = VELO_FUERA
    }

    private val pintorBorde = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 2f
        color = Color.WHITE
    }

    private val zonaRecorte = Path()
    private val mascara = Path()

    private val detectorZoom = ScaleGestureDetector(context, EscuchaZoom())

    /** Foco del gesto anterior, para que el arrastre no dé un salto. */
    private var focoPrevioX = 0f
    private var focoPrevioY = 0f

    /** Aviso de que el recorte cambió: quien pinta la vista previa la refresca. */
    var alAjustar: (() -> Unit)? = null

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    /** Fija la imagen a ajustar y la encuadra centrada, cubriendo el círculo. */
    fun setImagen(bitmap: Bitmap?) {
        imagen = bitmap
        if (bitmap != null && lado > 0f) encuadrar()
        invalidate()
        alAjustar?.invoke()
    }

    /** Zoom con el botón «+»: alrededor del centro del círculo. */
    fun masZoom() {
        aplicarZoom(FACTOR_ZOOM_BOTON)
        alAjustar?.invoke()
    }

    /** Zoom con el botón «−»: alrededor del centro del círculo. */
    fun menosZoom() {
        aplicarZoom(1f / FACTOR_ZOOM_BOTON)
        alAjustar?.invoke()
    }

    /** Zoom en torno a un punto (el foco del gesto o el centro en los botones). */
    fun aplicarZoom(factor: Float, focoX: Float = lado / 2f, focoY: Float = lado / 2f) {
        val actual = escalaActual()
        if (actual <= 0f) return

        val nueva = (actual * factor).coerceIn(escalaMin, escalaMin * ZOOM_MAXIMO)
        val efectivo = nueva / actual

        matriz.postScale(efectivo, efectivo, focoX, focoY)
        limitar()
        invalidate()
    }

    /** Desplaza la imagen (gesto de arrastre y pruebas). Siempre limitada. */
    fun desplazar(dx: Float, dy: Float) {
        matriz.postTranslate(dx, dy)
        limitar()
        invalidate()
    }

    /**
     * El resultado del recorte: círculo perfecto de [ladoSalida] por lado,
     * esquinas transparentes, contenido idéntico al del círculo en pantalla.
     */
    fun recortar(ladoSalida: Int = LADO_SALIDA_POR_DEFECTO): Bitmap? {
        val origen = imagen ?: return null
        if (lado <= 0f || ladoSalida <= 0) return null
        if (origen.isRecycled || origen.width <= 0 || origen.height <= 0) return null

        // 1. El cuadrado tal y como se ve en pantalla.
        val cuadrado = Bitmap.createBitmap(
            lado.toInt(),
            lado.toInt(),
            Bitmap.Config.ARGB_8888,
        )
        Canvas(cuadrado).drawBitmap(origen, matriz, pintorImagen)

        // 2. Máscara circular real sobre ese cuadrado.
        val salida = Bitmap.createBitmap(ladoSalida, ladoSalida, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(salida)
        val shader = BitmapShader(cuadrado, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val escala = ladoSalida.toFloat() / lado
        shader.setLocalMatrix(Matrix().apply { setScale(escala, escala) })

        val pintor = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
        val radio = ladoSalida / 2f
        lienzo.drawCircle(radio, radio, radio, pintor)

        return salida
    }

    // ------------------------------------------------------------------
    // Medida y dibujo
    // ------------------------------------------------------------------

    /** La vista SIEMPRE es cuadrada: el alto iguala al ancho disponible. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val ancho = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> suggestedMinimumWidth.coerceAtLeast(200)
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }

        setMeasuredDimension(ancho, ancho)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        lado = minOf(w, h).toFloat()
        if (lado <= 0f) return

        val margen = pintorBorde.strokeWidth
        val radio = lado / 2f - margen

        zonaRecorte.reset()
        zonaRecorte.addCircle(lado / 2f, lado / 2f, radio, Path.Direction.CW)

        // Par-impar: se rellena TODO el cuadrado MENOS el círculo.
        mascara.reset()
        mascara.fillType = Path.FillType.EVEN_ODD
        mascara.addRect(0f, 0f, lado, lado, Path.Direction.CW)
        mascara.addCircle(lado / 2f, lado / 2f, radio, Path.Direction.CW)

        if (imagen != null) {
            encuadrar()
            alAjustar?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = imagen ?: return

        canvas.drawBitmap(bitmap, matriz, pintorImagen)
        canvas.drawPath(mascara, pintorFuera)
        canvas.drawPath(zonaRecorte, pintorBorde)
    }

    // ------------------------------------------------------------------
    // Gestos
    // ------------------------------------------------------------------

    override fun onTouchEvent(evento: MotionEvent): Boolean {
        detectorZoom.onTouchEvent(evento)

        when (evento.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                focoPrevioX = evento.x
                focoPrevioY = evento.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val foco = focoActual(evento)

                if (!detectorZoom.isInProgress) {
                    desplazar(foco[0] - focoPrevioX, foco[1] - focoPrevioY)
                }

                focoPrevioX = foco[0]
                focoPrevioY = foco[1]
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // El recorte quedó estable: buen momento para la vista previa.
                alAjustar?.invoke()
            }
        }

        return true
    }

    /** Centro de los dedos: con dos dedos el punto medio, con uno el toque. */
    private fun focoActual(evento: MotionEvent): FloatArray =
        if (evento.pointerCount >= 2) {
            floatArrayOf(
                (evento.getX(0) + evento.getX(1)) / 2f,
                (evento.getY(0) + evento.getY(1)) / 2f,
            )
        } else {
            floatArrayOf(evento.x, evento.y)
        }

    private inner class EscuchaZoom : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            aplicarZoom(detector.scaleFactor, detector.focusX, detector.focusY)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // Sin este reanclaje, el primer arrastre tras el pellizco saltaría.
            focoPrevioX = detector.focusX
            focoPrevioY = detector.focusY
            alAjustar?.invoke()
        }
    }

    // ------------------------------------------------------------------
    // Interno
    // ------------------------------------------------------------------

    /** Encuadre inicial: cubrir el círculo y centrar. */
    private fun encuadrar() {
        val bitmap = imagen ?: return
        if (lado <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return

        escalaMin = lado / minOf(bitmap.width, bitmap.height).toFloat()

        matriz.reset()

        // "Cover": la dimensión menor llena el cuadrado, la otra la pasa.
        val escala = maxOf(lado / bitmap.width, lado / bitmap.height)
        matriz.setScale(escala, escala)

        matriz.postTranslate(
            (lado - bitmap.width * escala) / 2f,
            (lado - bitmap.height * escala) / 2f,
        )

        limitar()
        invalidate()
    }

    /**
     * La imagen nunca deja de cubrir el cuadrado: sin huecos vacíos dentro del
     * círculo y sin bordes visibles.
     */
    private fun limitar() {
        val bitmap = imagen ?: return
        if (lado <= 0f || bitmap.width <= 0 || bitmap.height <= 0) return

        rectImagen.set(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matriz.mapRect(rectImagen)

        var dx = 0f
        var dy = 0f

        if (rectImagen.left > 0f) {
            dx = -rectImagen.left
        } else if (rectImagen.right < lado) {
            dx = lado - rectImagen.right
        }

        if (rectImagen.top > 0f) {
            dy = -rectImagen.top
        } else if (rectImagen.bottom < lado) {
            dy = lado - rectImagen.bottom
        }

        if (dx != 0f || dy != 0f) matriz.postTranslate(dx, dy)
    }

    private fun escalaActual(): Float {
        val valores = FloatArray(9)
        matriz.getValues(valores)
        return valores[Matrix.MSCALE_X]
    }

    private companion object {
        const val FACTOR_ZOOM_BOTON = 1.25f
        const val ZOOM_MAXIMO = 8f
        const val LADO_SALIDA_POR_DEFECTO = 512
        const val VELO_FUERA = 0xB3000000.toInt()
    }
}
