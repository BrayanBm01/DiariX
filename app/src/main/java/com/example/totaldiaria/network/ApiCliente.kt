package com.example.totaldiaria.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Cliente HTTP mínimo de DiariX (Fase 4).
 *
 * Usa OkHttp y el JSON de la plataforma (org.json). Interpreta la forma de
 * error del backend — `{codigo, mensaje, detalles}` con un status no-2xx —
 * y la convierte en [ErrorDiarix] para que NINGUNA capa superior vea
 * excepciones HTTP crudas ni detalles internos.
 *
 * Un fallo de socket NO se reduce a "sin conexión": se clasifica con
 * [MotivoRed] (sin Internet, DNS, timeout, TLS, servidor inalcanzable) para
 * que el mensaje diga la causa real. La excepción original se registra en
 * Logcat con la etiqueta [TAG], que es lo que se pide en soporte.
 */
class ApiCliente(
    private val baseUrl: String,
    private val cliente: OkHttpClient = crearClientePorDefecto(),
    /** Para distinguir "el teléfono no tiene Internet" de "el DNS falló". */
    private val hayInternet: () -> Boolean = { true },
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
            throw redDe(peticion, e)
        }

        respuesta.use { r ->
            // El timeout también puede ocurrir LEYENDO el cuerpo (Render
            // arrancó y mandó cabeceras, pero la respuesta se cortó). Antes
            // esa excepción escapaba cruda de ApiCliente; ahora se clasifica
            // igual que un fallo al conectar.
            val cuerpo = try {
                r.body?.string().orEmpty()
            } catch (e: IOException) {
                throw redDe(peticion, e)
            }

            if (!r.isSuccessful) {
                registrarHttpDeError(peticion, r.code, cuerpo)
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
            throw redDe(peticion, e)
        }

        respuesta.use { r ->
            val cuerpo = try {
                r.body?.bytes() ?: ByteArray(0)
            } catch (e: IOException) {
                throw redDe(peticion, e)
            }

            if (!r.isSuccessful) {
                val texto = String(cuerpo, Charsets.UTF_8)
                registrarHttpDeError(peticion, r.code, texto)
                throw ErrorDiarix.DeBackend(
                    codigo = codigoDe(r.code, texto),
                    mensaje = mensajeDe(texto),
                    detalles = detallesDe(texto),
                )
            }

            return cuerpo
        }
    }

    // =========================================================================
    // Diagnóstico de red
    // =========================================================================

    /**
     * Traduce la excepción ORIGINAL a un motivo y la deja en Logcat.
     *
     * La app no muestra la traza (no aporta al usuario y puede filtrar
     * detalles), pero tampoco la borra: `adb logcat -s DiariX/HTTP` conserva
     * la clase y el mensaje exactos para diagnosticar el dispositivo.
     */
    private fun redDe(peticion: Request, e: IOException): ErrorDiarix.Red {
        val motivo = motivoDe(e)

        runCatching {
            Log.w(
                TAG,
                "Fallo de red (${motivo.name}) ${peticion.method} ${peticion.url} · " +
                    "${e.javaClass.name}: ${e.message}",
                e,
            )
        }

        return ErrorDiarix.Red(
            detalle = "${e.javaClass.simpleName}: ${e.message}",
            motivo = motivo,
        )
    }

    private fun motivoDe(e: IOException): MotivoRed = when (e) {
        is SocketTimeoutException -> MotivoRed.TIMEOUT
        is InterruptedIOException -> MotivoRed.TIMEOUT
        is UnknownHostException ->
            if (!hayInternetSeguro()) MotivoRed.SIN_INTERNET else MotivoRed.DNS
        is SSLException -> MotivoRed.TLS
        is ConnectException -> MotivoRed.SERVIDOR_NO_ALCANZABLE
        is NoRouteToHostException -> MotivoRed.SERVIDOR_NO_ALCANZABLE
        else -> MotivoRed.DESCONOCIDO
    }

    private fun hayInternetSeguro(): Boolean =
        runCatching { hayInternet() }.getOrDefault(true)

    /** Un status no-2xx sí llegó del servidor: se registra, no se disfraza. */
    private fun registrarHttpDeError(peticion: Request, status: Int, cuerpo: String) {
        runCatching {
            val recorte = cuerpo.take(300).replace('\n', ' ')
            Log.w(TAG, "HTTP $status ${peticion.method} ${peticion.url} → $recorte")
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
        /** Etiqueta de Logcat: `adb logcat -s DiariX/HTTP`. */
        const val TAG = "DiariX/HTTP"

        private val TIPO_JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Tiempos pensados para Render free tier.
         *
         * La instancia de Render se duerme tras 15 minutos sin tráfico y la
         * primera petición tiene que esperar a que arranque (NestJS + Prisma +
         * Supabase), lo que puede tardar decenas de segundos. Con el `read`
         * de 20 s anterior, esa primera petición moría por timeout y la app
         * decía «no hay conexión» aunque el servidor estuviera sano.
         *
         * El `connect` sigue corto (15 s) porque el borde de Cloudflare acepta
         * la conexión TCP al instante; lo que tarda es la respuesta. El
         * `write` se queda en 30 s para no romper subidas de comprobantes.
         */
        fun crearClientePorDefecto(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
    }
}