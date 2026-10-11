package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import com.example.totaldiaria.sesion.UsoDiario
import org.json.JSONArray
import org.json.JSONObject

/**
 * Endpoints de facturas de DiariX (Fase 4).
 *
 * Contrato (ver `facturas.controller.ts`):
 *  - `POST /facturas/sincronizar` → `{resultados[], resumen{}}` donde cada
 *    resultado es `{clientUid, estado, id?, numero, diaContable, total}` o,
 *    si fue rechazada, `{clientUid, estado: "RECHAZADA", codigo, mensaje}`.
 *  - `GET /facturas/uso-diario` → `{dia, plan, planVigente, limite, usadas,
 *    restantes}`. El límite siempre viene del servidor.
 *
 * Es `open` para que las pruebas puedan sustituirla por un doble en memoria.
 */
open class FacturasApi(api: ApiCliente) {

    private val api = api

    /**
     * Envía un lote de facturas locales. La idempotencia la garantiza
     * `clientUid`: repetir el envío de la misma factura produce
     * `YA_EXISTIA` y NUNCA duplica en el servidor.
     */
    open fun sincronizar(
        accessToken: String,
        facturas: List<Factura>,
        diaService: com.example.totaldiaria.modulos.facturacion.servicio.DiaService,
    ): RespuestaSincronizacion {
        val lote = JSONArray()

        facturas.forEach { factura ->
            val json = JSONObject().apply {
                put("clientUid", factura.clientUid)
                put("numero", factura.numeroFactura)
                put("efectivo", factura.efectivo)
                put("transferencia", factura.transferencia)
                // La fecha que el servidor trata como autoridad es `ocurrioEn`
                // (UTC). Debe ser la fecha REAL de la factura —cuando se
                // registró, quizá sin conexión—, no la del envío: si no, una
                // factura de ayer subida hoy contaría en el día de hoy.
                // `fechaLocal` se conserva tal cual para referencia.
                put("ocurrioEn", diaService.utcIsoDeFechaLocal(factura.fecha) ?: diaService.ahoraUtcIso())
                put("fechaLocal", factura.fecha)
            }
            lote.put(json)
        }

        val cuerpo = JSONObject().apply { put("facturas", lote) }

        val respuesta = api.postJson(
            RUTA_SINCRONIZAR,
            token = accessToken,
            cuerpo = cuerpo,
        )
        val json = JSONObject(respuesta)

        val resultados = mutableListOf<ResultadoDeSincronizacion>()
        json.optJSONArray("resultados")?.let { array ->
            for (i in 0 until array.length()) {
                val r = array.optJSONObject(i) ?: continue
                resultados.add(
                    ResultadoDeSincronizacion(
                        clientUid = r.optString("clientUid"),
                        estado = r.optString("estado"),
                        servidorId = r.optString("id").takeIf { it.isNotBlank() },
                        codigo = r.optString("codigo").takeIf { it.isNotBlank() },
                        mensaje = r.optString("mensaje").takeIf { it.isNotBlank() },
                    )
                )
            }
        }

        val resumenJson = json.optJSONObject("resumen")

        return RespuestaSincronizacion(
            resultados = resultados,
            resumen = resumenJson?.let {
                ResumenDeSincronizacion(
                    recibidas = it.optInt("recibidas", 0),
                    nuevas = it.optInt("nuevas", 0),
                    repetidas = it.optInt("repetidas", 0),
                    rechazadas = it.optInt("rechazadas", 0),
                    limiteDiario = it.optInt("limiteDiario", 0),
                    limiteAlcanzado = it.optBoolean("limiteAlcanzado", false),
                )
            },
        )
    }

    /** Uso del día contable actual, según el servidor (America/Bogota). */
    open fun usoDiario(accessToken: String): UsoDiario {
        val respuesta = api.getJson(RUTA_USO_DIARIO, token = accessToken)
        val json = JSONObject(respuesta)

        return UsoDiario(
            dia = json.optString("dia"),
            plan = json.optString("plan"),
            planVigente = json.optBoolean("planVigente", false),
            limite = json.optInt("limite", 0),
            usadas = json.optInt("usadas", 0),
            restantes = json.optInt("restantes", 0),
        )
    }

    private companion object {
        const val RUTA_SINCRONIZAR = "/facturas/sincronizar"
        const val RUTA_USO_DIARIO = "/facturas/uso-diario"
    }
}

/** Resultado por factura que devuelve el servidor. */
data class ResultadoDeSincronizacion(
    val clientUid: String,
    val estado: String,
    val servidorId: String?,
    val codigo: String?,
    val mensaje: String?,
) {
    val sincronizada: Boolean get() = estado == SINCRONIZADA || estado == YA_EXISTIA
    val rechazada: Boolean get() = estado == RECHAZADA

    companion object {
        const val SINCRONIZADA = "SINCRONIZADA"
        const val YA_EXISTIA = "YA_EXISTIA"
        const val RECHAZADA = "RECHAZADA"
    }
}

/** Resumen del lote: cuántas entraron, cuántas se repitieron y el límite. */
data class ResumenDeSincronizacion(
    val recibidas: Int,
    val nuevas: Int,
    val repetidas: Int,
    val rechazadas: Int,
    val limiteDiario: Int,
    val limiteAlcanzado: Boolean,
)

data class RespuestaSincronizacion(
    val resultados: List<ResultadoDeSincronizacion>,
    val resumen: ResumenDeSincronizacion?,
)