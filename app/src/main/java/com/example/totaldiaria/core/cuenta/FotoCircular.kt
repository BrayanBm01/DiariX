package com.example.totaldiaria.core.cuenta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import java.io.File

/**
 * Recorte CIRCULAR real para la foto de perfil.
 *
 * No es un borde redondeado: la salida es un círculo perfecto con las esquinas
 * transparentes. Se hace en tres pasos garantizados:
 *
 *  1. centro-cuadrado: se recorta el cuadrado central, así la imagen NUNCA se
 *     deforma (el recorte elimina bordes, no estira);
 *  2. escala exacta al lado pedido (cuadrado → cuadrado, misma proporción);
 *  3. máscara circular con `BitmapShader` CLAMP: solo se pinta el círculo.
 *
 * La usa el avatar de «Ver y editar perfil» y el de Configuración, que solo
 * MUESTRA la foto; el ajuste interactivo vive en [VistaRecorteCircular].
 */
object FotoCircular {

    private const val LADO_DECODIFICACION = 1600

    /**
     * Decodifica un archivo sin cargar una foto de 12 MP solo para pintar un
     * avatar de 112dp. Se reduce por potencias de 2 (lo que entiende Android).
     */
    fun decodificar(archivo: File, ladoMaximo: Int = LADO_DECODIFICACION): Bitmap? {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(archivo.absolutePath, opciones)
        opciones.inJustDecodeBounds = false
        opciones.inSampleSize = muestraPara(opciones.outWidth, opciones.outHeight, ladoMaximo)
        return BitmapFactory.decodeFile(archivo.absolutePath, opciones)
    }

    /** Igual que [decodificar] pero para los bytes que llegan de la galería. */
    fun decodificar(bytes: ByteArray, ladoMaximo: Int = LADO_DECODIFICACION): Bitmap? {
        val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
        opciones.inJustDecodeBounds = false
        opciones.inSampleSize = muestraPara(opciones.outWidth, opciones.outHeight, ladoMaximo)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones)
    }

    private fun muestraPara(ancho: Int, alto: Int, ladoMaximo: Int): Int {
        if (ancho <= 0 || alto <= 0 || ladoMaximo <= 0) return 1

        var muestra = 1
        while (maxOf(ancho / muestra, alto / muestra) / 2 >= ladoMaximo) {
            muestra *= 2
        }
        return muestra
    }

    /** Devuelve un bitmap circular de [lado]×[lado], o null si no hay origen. */
    fun recortarCircular(origen: Bitmap?, lado: Int): Bitmap? {
        if (origen == null || origen.isRecycled || lado <= 0) return null
        if (origen.width <= 0 || origen.height <= 0) return null

        val ladoOrigen = minOf(origen.width, origen.height)
        if (ladoOrigen <= 0) return null

        // 1. Recorte centrado del cuadrado mayor posible (sin deformar).
        val x = (origen.width - ladoOrigen) / 2
        val y = (origen.height - ladoOrigen) / 2
        val cuadrado = Bitmap.createBitmap(origen, x, y, ladoOrigen, ladoOrigen)

        // 2. Escala al lado pedido: cuadrado a cuadrado, proporción intacta.
        val escalado = if (cuadrado.width == lado && cuadrado.height == lado) {
            cuadrado
        } else {
            Bitmap.createScaledBitmap(cuadrado, lado, lado, true)
        }

        // 3. Círculo real: lo de fuera del círculo queda transparente.
        val salida = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(salida)
        val pintor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(escalado, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val radio = lado / 2f
        lienzo.drawCircle(radio, radio, radio, pintor)

        return salida
    }
}
