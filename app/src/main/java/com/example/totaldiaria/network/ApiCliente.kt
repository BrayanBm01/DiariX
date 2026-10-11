package com.example.totaldiaria.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP mínimo de DiariX (Fase 4).
 *
 * Usa OkHttp y el JSON de la plataforma (org.json). Interpreta la forma de
 * error del backend — `{codigo, mensaje, detalles}` con un status no-2xx —
 * y la convierte en [ErrorDiarix] para que NINGUNA capa superior vea
 * excepciones HTTP crudas ni detalles internos.
 */
class ApiCliente(
    private val baseUrl: String,
    private val cliente: OkHttpClient = crearClientePorDefecto(),
) {

    fun postJson(
        ruta: String,
        token: String? = null,
        cuerpo: JSONObject,
    ): String {
        val peticion = Request.Builder()
            .url("$baseUrl$ruta")
            .post(cuerpo.toString().toRequestBody(TIPO_JSON))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        return ejecutar(peticion)
    }

    fun getJson(
        ruta: String,
        token: String? = null,
    ): String {
        val peticion = Request.Builder()
            .url("$baseUrl$ruta")
            .get()
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        return ejecutar(peticion)
    }

    /**
     * PATCH con cuerpo.
     *
     * Existe para `PATCH /usuarios/yo`, que es la actualización parcial de un
     * recurso: se manda solo lo que cambia. Se escribió un método propio en vez
     * de reutilizar `postJson` porque hacer un PATCH con una verbo POST
     * escondido dentro del cliente es la forma de que nadie sepa qué está
     * haciendo la app contra el backend.
     */
    fun patchJson(
        ruta: String,
        token: String? = null,
        cuerpo: JSONObject,
    ): String {
        val peticion = Request.Builder()
            .url("$baseUrl$ruta")
            .patch(cuerpo.toString().toRequestBody(TIPO_JSON))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        return ejecutar(peticion)
    }

    /**
     * DELETE sin cuerpo.
     *
     * Existe para `DELETE /clientes/:id`, que elimina un registro. Como el
     * borrado no admite cuerpo, no hay variante con `cuerpo` y no se montó
     * sobre `postJson`: el verbo a la vista en el cliente, igual que en el
     * resto de los métodos.
     */
    fun deleteJson(
        ruta: String,
        token: String? = null,
    ): String {
        val peticion = Request.Builder()
            .url("$baseUrl$ruta")
            .delete()
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        return ejecutar(peticion)
    }

    private fun ejecutar(peticion: Request): String {
        val respuesta = try {
            cliente.newCall(peticion).execute()
        } catch (e: IOException) {
            // Sin red, timeout o servidor que no responde: por diseño un solo
            // error amigable, nunca la excepción.
            throw ErrorDiarix.Red(e.message ?: "")
        }

        respuesta.use { r ->
            val cuerpo = r.body?.string().orEmpty()

            if (!r.isSuccessful) {
                throw ErrorDiarix.DeBackend(
                    codigo = codigoDe(r.code, cuerpo),
                    mensaje = mensajeDe(cuerpo),
                    detalles = detallesDe(cuerpo),
                )
            }

            return cuerpo
        }
    }

    /**
     * POST multipart con el archivo del comprobante (campo `archivo`), tal y
     * como lo recibe `AlmacenComprobantesService`. El archivo viaja EN MEMORIA,
     * nunca por ruta: la app no puede filtrar una ruta local porque no la manda.
     */
    fun postMultipart(
        ruta: String,
        token: String? = null,
        nombreArchivo: String,
        mime: String,
        archivo: ByteArray,
    ): String {
        val cuerpo = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("archivo", nombreArchivo, archivo.toRequestBody(mime.toMediaType()))
            .build()

        val peticion = Request.Builder()
            .url("$baseUrl$ruta")
            .post(cuerpo)
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        return ejecutar(peticion)
    }

    /**
     * GET que devuelve BYTES (la descarga de un comprobante). No pasa por
     * `getJson`: el éxito no es texto, es el archivo. Un fallo del servidor
     * TIENE cuerpo JSON (`{codigo, mensaje}`) y se interpreta como el resto.
     */
    fun getBytes(
        ruta: String,
        token: String? = null,
    ): ByteArray {
        val peticion = Request.Builder()
            .url("$baseUrl$ruta")
            .get()
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()

        val respuesta = try {
            cliente.newCall(peticion).execute()
        } catch (e: IOException) {
            throw ErrorDiarix.Red(e.message ?: "")
        }

        respuesta.use { r ->
            val cuerpo = try {
                r.body?.bytes() ?: ByteArray(0)
            } catch (e: IOException) {
                throw ErrorDiarix.Red(e.message ?: "")
            }

            if (!r.isSuccessful) {
                val texto = String(cuerpo, Charsets.UTF_8)
                throw ErrorDiarix.DeBackend(
                    codigo = codigoDe(r.code, texto),
                    mensaje = mensajeDe(texto),
                    detalles = detallesDe(texto),
                )
            }

            return cuerpo
        }
    }

    private fun codigoDe(status: Int, cuerpo: String): String {
        val json = cuerpo.tryComoJson() ?: return "HTTP_$status"
        return json.optString("codigo").ifBlank { "HTTP_$status" }
    }

    private fun mensajeDe(cuerpo: String): String {
        val json = cuerpo.tryComoJson() ?: return ""
        return json.optString("mensaje")
    }

    private fun detallesDe(cuerpo: String): List<String> {
        val json = cuerpo.tryComoJson() ?: return emptyList()
        val detalles = json.optJSONArray("detalles") ?: return emptyList()

        return buildList {
            for (i in 0 until detalles.length()) {
                val item = detalles.optString(i).ifBlank { continue }
                add(item)
            }
        }
    }

    private fun String.tryComoJson(): JSONObject? =
        try {
            JSONObject(this)
        } catch (_: Exception) {
            null
        }

    companion object {
        private val TIPO_JSON = "application/json; charset=utf-8".toMediaType()

        /** Tiempos razonables para una app que graba facturas al paso. */
        fun crearClientePorDefecto(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()
    }
}