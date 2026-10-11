package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.prestamos.modelo.AsignacionDePago
import com.example.totaldiaria.modulos.prestamos.modelo.ClienteDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.Cobro
import com.example.totaldiaria.modulos.prestamos.modelo.CuotaDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePagoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePrestamoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDeReversion
import com.example.totaldiaria.modulos.prestamos.modelo.DetalleDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.IndicadorDeCobros
import com.example.totaldiaria.modulos.prestamos.modelo.IndicadorDeVencidas
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDeCobros
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDePagos
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDePrestamos
import com.example.totaldiaria.modulos.prestamos.modelo.PagoDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.Prestamo
import com.example.totaldiaria.modulos.prestamos.modelo.ResumenDePrestamos
import com.example.totaldiaria.modulos.prestamos.modelo.RespuestaDeOperacionDePago
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.net.URLEncoder

/**
 * DIARIX · Fase 3 · Endpoints de préstamos
 *
 * Contrato (ver `prestamos.controller.ts`):
 *
 *  - `GET  /prestamos?pagina=&porPagina=[&q=][&estado=]` → sobre paginado
 *    `{datos[], total, pagina, porPagina, totalPaginas}`.
 *  - `GET  /prestamos/:id` → la ficha completa, con el calendario (RF-07). El
 *    alta (`POST`) devuelve la MISMA forma: el servidor calcula el acuerdo y
 *    monta el calendario en la misma transacción (RF-06/RF-07).
 *  - `POST /prestamos` → alta. `monto`, `totalAPagar` y `porcentaje` viajan
 *    como números JSON exactos (BigDecimal), jamás como `Double`: un préstamo
 *    es dinero y sus centavos no se redondean por el camino.
 *
 * LA PAGINACIÓN Y LOS ESTADOS VIVEN EN EL SERVIDOR: `estado` es el estado
 * DERIVADO (RF-09), que el backend calcula al leer; aquí solo se repite el
 * filtro que eligió la persona, como repite la página y el tamaño.
 *
 * Es `open` para que las pruebas puedan sustituirla por un doble en memoria,
 * igual que `ClientesApi`.
 */
open class PrestamosApi(private val api: ApiCliente) {

    /** Listado paginado, con búsqueda por el cliente y filtro por estado. */
    open fun listar(
        accessToken: String,
        pagina: Int,
        porPagina: Int,
        texto: String? = null,
        estado: String? = null,
    ): PaginaDePrestamos {
        val consulta = StringBuilder()
            .append("?pagina=").append(pagina)
            .append("&porPagina=").append(porPagina)

        if (!texto.isNullOrBlank()) {
            consulta.append("&q=").append(URLEncoder.encode(texto.trim(), "UTF-8"))
        }

        if (!estado.isNullOrBlank()) {
            consulta.append("&estado=").append(estado)
        }

        val cuerpo = JSONObject(api.getJson("/prestamos$consulta", accessToken))

        return PaginaDePrestamos(
            datos = lecturaLista(cuerpo.optJSONArray("datos")),
            total = cuerpo.optInt("total", 0),
            pagina = cuerpo.optInt("pagina", 1),
            porPagina = cuerpo.optInt("porPagina", porPagina),
            totalPaginas = cuerpo.optInt("totalPaginas", 1),
        )
    }

    open fun obtener(accessToken: String, id: String): DetalleDePrestamo =
        aDetalle(JSONObject(api.getJson("/prestamos/$id", accessToken)))

    /** Alta: el servidor devuelve la ficha YA con su calendario completo. */
    open fun crear(accessToken: String, datos: DatosDePrestamoNuevo): DetalleDePrestamo {
        val cuerpo = JSONObject(api.postJson("/prestamos", accessToken, aJson(datos)))
        return aDetalle(cuerpo)
    }

    // ==========================================================================
    // Fase 4 · Pagos, cobros, resumen y comprobantes
    // ==========================================================================

    /**
     * Registra un pago en `POST /prestamos/:id/pagos`. El servidor reparte el
     * monto entre las cuotas pendientes (la más antigua primero) en la misma
     * transacción y devuelve el pago + la ficha actualizada. `claveUnica` hace
     * que repetir la petición devuelva el mismo pago en vez de cobrar dos veces.
     */
    open fun registrarPago(
        accessToken: String,
        prestamoId: String,
        datos: DatosDePagoNuevo,
    ): RespuestaDeOperacionDePago {
        val cuerpo = JSONObject(
            api.postJson("/prestamos/$prestamoId/pagos", accessToken, aJsonDePago(datos)),
        )
        return aOperacion(cuerpo)
    }

    /** Historial de pagos de UN préstamo (`GET /prestamos/:id/pagos`). */
    open fun listarPagosDePrestamo(
        accessToken: String,
        prestamoId: String,
        pagina: Int,
        porPagina: Int,
    ): PaginaDePagos {
        val cuerpo = JSONObject(
            api.getJson(
                "/prestamos/$prestamoId/pagos?pagina=$pagina&porPagina=$porPagina",
                accessToken,
            ),
        )
        return aPaginaDePagos(cuerpo, porPagina)
    }

    /** Historial GLOBAL de pagos (`GET /prestamos/pagos`), servidor paginado. */
    open fun listarPagos(
        accessToken: String,
        pagina: Int,
        porPagina: Int,
        texto: String? = null,
        metodoPago: String? = null,
    ): PaginaDePagos {
        val consulta = StringBuilder()
            .append("?pagina=").append(pagina)
            .append("&porPagina=").append(porPagina)

        if (!texto.isNullOrBlank()) {
            consulta.append("&q=").append(URLEncoder.encode(texto.trim(), "UTF-8"))
        }
        if (!metodoPago.isNullOrBlank()) {
            consulta.append("&metodoPago=").append(metodoPago)
        }

        val cuerpo = JSONObject(api.getJson("/prestamos/pagos$consulta", accessToken))
        return aPaginaDePagos(cuerpo, porPagina)
    }

    /**
     * Reversión SOLO por el backend: `POST /prestamos/pagos/:id/revertir`, con
     * el motivo obligatorio (auditoría). El pago queda `revertido`, no se borra.
     */
    open fun revertirPago(
        accessToken: String,
        pagoId: String,
        motivo: String,
    ): RespuestaDeOperacionDePago {
        val cuerpo = JSONObject(
            api.postJson(
                "/prestamos/pagos/$pagoId/revertir",
                accessToken,
                aJsonDeReversion(DatosDeReversion(motivo)),
            ),
        )
        return aOperacion(cuerpo)
    }

    /**
     * Cobros paginado (`GET /cobros`): la categoría (HOY/VENCIDAS/PROXIMAS) la
     * filtra el SERVIDOR por fecha real y saldo; la app solo repite el filtro.
     */
    open fun listarCobros(
        accessToken: String,
        categoria: String,
        pagina: Int,
        porPagina: Int,
    ): PaginaDeCobros {
        val cuerpo = JSONObject(
            api.getJson(
                "/cobros?categoria=$categoria&pagina=$pagina&porPagina=$porPagina",
                accessToken,
            ),
        )
        return aPaginaDeCobros(cuerpo, porPagina)
    }

    /** Resumen del Inicio (`GET /prestamos/resumen`), calculado en el servidor. */
    open fun resumen(accessToken: String): ResumenDePrestamos {
        val cuerpo = JSONObject(api.getJson("/prestamos/resumen", accessToken))
        return aResumen(cuerpo)
    }

    /** Adjunta el comprobante DESPUÉS del pago (igual que en facturas). */
    open fun adjuntarComprobante(
        accessToken: String,
        pagoId: String,
        nombre: String,
        mime: String,
        archivo: ByteArray,
    ): PagoDePrestamo {
        val cuerpo = JSONObject(
            api.postMultipart(
                "/prestamos/pagos/$pagoId/comprobante",
                accessToken,
                nombreArchivo = nombre,
                mime = mime,
                archivo = archivo,
            ),
        )
        return aPago(cuerpo)
    }

    /** Quita el comprobante de un pago (DELETE). */
    open fun quitarComprobante(accessToken: String, pagoId: String): PagoDePrestamo {
        val cuerpo = JSONObject(
            api.deleteJson("/prestamos/pagos/$pagoId/comprobante", accessToken),
        )
        return aPago(cuerpo)
    }

    /** Descarga el comprobante en bytes (GET binario). */
    open fun descargarComprobante(accessToken: String, pagoId: String): ByteArray =
        api.getBytes("/prestamos/pagos/$pagoId/comprobante", accessToken)

    // ==========================================================================
    // JSON
    // ==========================================================================

    private fun aJson(datos: DatosDePrestamoNuevo): JSONObject =
        JSONObject().apply {
            put("clienteId", datos.clienteId)
            put("fechaInicio", datos.fechaInicio)
            // BigDecimal → token JSON exacto, sin pasar por Double.
            put("monto", datos.monto.setScale(2))
            put("modalidad", datos.modalidad)
            put("numeroDeCuotas", datos.numeroDeCuotas)
            put("tipoCalculo", datos.tipoCalculo)

            // La regla es EXCLUYENTE (igual que en el backend) y un campo que
            // no aplica NO viaja: PORCENTAJE no admite total y TOTAL no admite
            // porcentaje. Mandarlo en null sería un dato que el servicio
            // rechaza.
            if (datos.tipoCalculo == TiposDeCalculo.PORCENTAJE) {
                put("porcentaje", datos.porcentaje?.setScale(2))
            } else if (datos.tipoCalculo == TiposDeCalculo.TOTAL) {
                put("totalAPagar", datos.totalAPagar?.setScale(2))
            }
        }

    private fun lecturaLista(json: JSONArray?): List<Prestamo> {
        if (json == null) return emptyList()

        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                add(aPrestamo(item))
            }
        }
    }

    private fun aPrestamo(json: JSONObject): Prestamo {
        val clienteJson = json.optJSONObject("cliente")

        return Prestamo(
            id = json.optString("id"),
            empresaId = opcional(json, "empresaId"),
            cliente = clienteJson?.let { aCliente(it) },
            modalidad = json.optString("modalidad"),
            tipoCalculo = json.optString("tipoCalculo"),
            porcentaje = if (json.has("porcentaje") && !json.isNull("porcentaje")) {
                numero(json, "porcentaje")
            } else {
                null
            },
            fechaInicio = json.optString("fechaInicio"),
            montoPrestado = numero(json, "montoPrestado"),
            totalAPagar = numero(json, "totalAPagar"),
            valorCuota = numero(json, "valorCuota"),
            numeroDeCuotas = json.optInt("numeroDeCuotas", 0),
            estado = json.optString("estado"),
            totalPagado = numero(json, "totalPagado"),
            saldoPendiente = numero(json, "saldoPendiente"),
            cuotasPagadas = json.optInt("cuotasPagadas", 0),
            cuotasPendientes = json.optInt("cuotasPendientes", 0),
            creadoEn = json.optString("creadoEn"),
            actualizadoEn = json.optString("actualizadoEn"),
        )
    }

    private fun aDetalle(json: JSONObject): DetalleDePrestamo {
        val cuotas = buildList {
            val arreglo = json.optJSONArray("cuotas") ?: return@buildList
            for (i in 0 until arreglo.length()) {
                val cuota = arreglo.optJSONObject(i) ?: continue
                add(
                    CuotaDePrestamo(
                        numero = cuota.optInt("numero", 0),
                        fechaVencimiento = cuota.optString("fechaVencimiento"),
                        valorEsperado = numero(cuota, "valorEsperado"),
                        valorPagado = numero(cuota, "valorPagado"),
                        saldo = numero(cuota, "saldo"),
                        estado = cuota.optString("estado"),
                    ),
                )
            }
        }

        return DetalleDePrestamo(
            prestamo = aPrestamo(json),
            nota = opcional(json, "nota"),
            diasDeAtraso = json.optInt("diasDeAtraso", 0),
            numeroDeCuotasAtrasadas = json.optInt("numeroDeCuotasAtrasadas", 0),
            valorAtrasado = numero(json, "valorAtrasado"),
            cuotas = cuotas,
        )
    }

    private fun aCliente(json: JSONObject): ClienteDePrestamo =
        ClienteDePrestamo(
            id = json.optString("id"),
            nombreCompleto = json.optString("nombreCompleto"),
            tipoDocumento = json.optString("tipoDocumento"),
            numeroDocumento = json.optString("numeroDocumento"),
        )

    private fun aJsonDePago(datos: DatosDePagoNuevo): JSONObject =
        JSONObject().apply {
            // BigDecimal → token JSON exacto, sin pasar por Double.
            put("monto", datos.monto.setScale(2))
            put("metodoPago", datos.metodoPago)
            put("claveUnica", datos.claveUnica)
            if (!datos.fechaPago.isNullOrBlank()) {
                put("fechaPago", datos.fechaPago)
            }
        }

    private fun aJsonDeReversion(datos: DatosDeReversion): JSONObject =
        JSONObject().apply { put("motivo", datos.motivo.trim()) }

    /** `{pago, prestamo}`: el mismo sobre en registrar y en revertir. */
    private fun aOperacion(json: JSONObject): RespuestaDeOperacionDePago =
        RespuestaDeOperacionDePago(
            pago = aPago(json.getJSONObject("pago")),
            prestamo = aDetalle(json.getJSONObject("prestamo")),
        )

    private fun aPaginaDePagos(cuerpo: JSONObject, porPagina: Int): PaginaDePagos =
        PaginaDePagos(
            datos = lecturaDePagos(cuerpo.optJSONArray("datos")),
            total = cuerpo.optInt("total", 0),
            pagina = cuerpo.optInt("pagina", 1),
            porPagina = cuerpo.optInt("porPagina", porPagina),
            totalPaginas = cuerpo.optInt("totalPaginas", 1),
        )

    private fun lecturaDePagos(json: JSONArray?): List<PagoDePrestamo> {
        if (json == null) return emptyList()

        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                add(aPago(item))
            }
        }
    }

    private fun aPago(json: JSONObject): PagoDePrestamo {
        val clienteJson = json.optJSONObject("cliente")

        return PagoDePrestamo(
            id = json.optString("id"),
            prestamoId = json.optString("prestamoId"),
            cliente = clienteJson?.let { aCliente(it) },
            fechaPago = json.optString("fechaPago"),
            metodoPago = json.optString("metodoPago"),
            monto = numero(json, "monto"),
            comprobanteUrl = opcional(json, "comprobanteUrl"),
            comprobanteMime = opcional(json, "comprobanteMime"),
            revertido = json.optBoolean("revertido", false),
            motivoReversion = opcional(json, "motivoReversion"),
            revertidoEn = opcional(json, "revertidoEn"),
            creadoEn = json.optString("creadoEn"),
            asignaciones = lecturaDeAsignaciones(json.optJSONArray("asignaciones")),
        )
    }

    private fun lecturaDeAsignaciones(json: JSONArray?): List<AsignacionDePago> {
        if (json == null) return emptyList()

        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                add(
                    AsignacionDePago(
                        cuotaId = item.optString("cuotaId"),
                        numeroCuota = item.optInt("numeroCuota", 0),
                        montoAplicado = numero(item, "montoAplicado"),
                    ),
                )
            }
        }
    }

    private fun aPaginaDeCobros(cuerpo: JSONObject, porPagina: Int): PaginaDeCobros =
        PaginaDeCobros(
            datos = lecturaDeCobros(cuerpo.optJSONArray("datos")),
            total = cuerpo.optInt("total", 0),
            pagina = cuerpo.optInt("pagina", 1),
            porPagina = cuerpo.optInt("porPagina", porPagina),
            totalPaginas = cuerpo.optInt("totalPaginas", 1),
        )

    private fun lecturaDeCobros(json: JSONArray?): List<Cobro> {
        if (json == null) return emptyList()

        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                add(aCobro(item))
            }
        }
    }

    private fun aCobro(json: JSONObject): Cobro {
        val clienteJson = json.optJSONObject("cliente")

        return Cobro(
            id = json.optString("id"),
            prestamoId = json.optString("prestamoId"),
            modalidad = json.optString("modalidad"),
            cliente = clienteJson?.let { aCliente(it) },
            numeroCuota = json.optInt("numeroCuota", 0),
            fechaVencimiento = json.optString("fechaVencimiento"),
            valorEsperado = numero(json, "valorEsperado"),
            valorPagado = numero(json, "valorPagado"),
            saldo = numero(json, "saldo"),
            diasDeAtraso = json.optInt("diasDeAtraso", 0),
            estado = json.optString("estado"),
        )
    }

    private fun aResumen(json: JSONObject): ResumenDePrestamos =
        ResumenDePrestamos(
            prestamosActivos = json.optInt("prestamosActivos", 0),
            saldoPendienteTotal = numero(json, "saldoPendienteTotal"),
            cobrosDeHoy = aIndicador(json.optJSONObject("cobrosDeHoy")),
            cuotasVencidas = aIndicadorDeVencidas(json.optJSONObject("cuotasVencidas")),
            proximosCobros = aIndicador(json.optJSONObject("proximosCobros")),
            recaudadoHoy = aIndicador(json.optJSONObject("recaudadoHoy")),
        )

    private fun aIndicador(json: JSONObject?): IndicadorDeCobros =
        if (json == null) {
            IndicadorDeCobros(cantidad = 0, valor = BigDecimal.ZERO)
        } else {
            IndicadorDeCobros(
                cantidad = json.optInt("cantidad", 0),
                valor = numero(json, "valor"),
            )
        }

    private fun aIndicadorDeVencidas(json: JSONObject?): IndicadorDeVencidas =
        if (json == null) {
            IndicadorDeVencidas(cantidad = 0, valor = BigDecimal.ZERO, diasMaximosDeAtraso = 0)
        } else {
            IndicadorDeVencidas(
                cantidad = json.optInt("cantidad", 0),
                valor = numero(json, "valor"),
                diasMaximosDeAtraso = json.optInt("diasMaximosDeAtraso", 0),
            )
        }

    /** Un texto que puede faltar o ser `null`: nunca la cadena "null". */
    private fun opcional(json: JSONObject, campo: String): String? =
        if (!json.has(campo) || json.isNull(campo)) null
        else json.optString(campo).trim().ifEmpty { null }

    /**
     * Lee un importe EXACTO del JSON. `org.json` de Android traduce todo los
     * números a `Long` o `Double`, y un `Double` no puede guardar 336666.66.
     * Pero `Double.toString` imprime la representación decimal más corta que
     * vuelve a dar ese mismo `Double`: para dinero del backend (hasta 15
     * dígitos significativos, con 2 decimales) esa cadena es la del token que
     * se escribió, y `BigDecimal(cadena)` recupera ese decimal exacto. Por eso
     * se lee el valor crudo y se convierte con `BigDecimal(valor.toString())`
     * en vez de `optDouble`, que mandaría la cifra a coma flotante.
     */
    private fun numero(json: JSONObject, campo: String): BigDecimal {
        if (!json.has(campo) || json.isNull(campo)) return BigDecimal.ZERO
        val valor = json.opt(campo) ?: return BigDecimal.ZERO
        return runCatching { BigDecimal(valor.toString()) }.getOrDefault(BigDecimal.ZERO)
    }

    private object TiposDeCalculo {
        const val PORCENTAJE = "PORCENTAJE"
        const val TOTAL = "TOTAL"
    }
}