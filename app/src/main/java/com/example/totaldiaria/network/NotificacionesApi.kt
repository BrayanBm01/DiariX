package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.notificaciones.modelo.Notificacion
import com.example.totaldiaria.modulos.notificaciones.modelo.PaginaDeNotificaciones
import com.example.totaldiaria.modulos.notificaciones.modelo.ResumenDeNotificaciones
import org.json.JSONArray
import org.json.JSONObject

/**
 * DIARIX · Endpoints de las notificaciones propias de la app
 *
 * Contrato (ver `notificaciones.controller.ts`):
 *
 *  - `GET  /notificaciones/mias?pagina=&porPagina=&estado=` → sobre paginado
 *    `{datos[], total, pagina, porPagina, totalPaginas, pendientes}`.
 *  - `GET  /notificaciones/mias/resumen` → `{pendientes, total}`.
 *  - `POST /notificaciones/mias/:id/leer` → la notificación ya leída.
 *  - `POST /notificaciones/mias/leer-todas` → `{marcadas}`.
 *
 * El servidor resuelve el destinatario desde la sesión: un conductor solo ve
 * las suyas y un usuario individual solo las de su cuenta. Esta capa no manda
 * ningún id de destinatario, así que no puede pedir las de otra persona.
 *
 * Es `open` para poder sustituirla por un doble en las pruebas, igual que
 * [ClientesApi] y [FacturasApi].
 */
open class NotificacionesApi(private val api: ApiCliente) {

    open fun listar(
        accessToken: String,
        pagina: Int,
        porPagina: Int,
        estado: String = ESTADO_TODAS,
    ): PaginaDeNotificaciones {
        val consulta = "?pagina=$pagina&porPagina=$porPagina&estado=$estado"
        val cuerpo = JSONObject(
            api.getJson("/notificaciones/mias$consulta", accessToken),
        )

        return PaginaDeNotificaciones(
            datos = leerLista(cuerpo.optJSONArray("datos")),
            total = cuerpo.optInt("total", 0),
            pagina = cuerpo.optInt("pagina", pagina),
            porPagina = cuerpo.optInt("porPagina", porPagina),
            totalPaginas = cuerpo.optInt("totalPaginas", 1),
            pendientes = cuerpo.optInt("pendientes", 0),
        )
    }

    open fun resumen(accessToken: String): ResumenDeNotificaciones {
        val cuerpo = JSONObject(api.getJson("/notificaciones/mias/resumen", accessToken))

        return ResumenDeNotificaciones(
            pendientes = cuerpo.optInt("pendientes", 0),
            total = cuerpo.optInt("total", 0),
        )
    }

    open fun marcarLeida(accessToken: String, id: String): Notificacion {
        val cuerpo = JSONObject(
            api.postJson("/notificaciones/mias/$id/leer", accessToken, JSONObject()),
        )

        return aNotificacion(cuerpo)
    }

    /** Devuelve cuántas quedaron marcadas; el servidor no borra ninguna. */
    open fun marcarTodas(accessToken: String): Int {
        val cuerpo = JSONObject(
            api.postJson(
                "/notificaciones/mias/leer-todas",
                accessToken,
                JSONObject(),
            ),
        )

        return cuerpo.optInt("marcadas", 0)
    }

    // ==========================================================================
    // JSON
    // ==========================================================================

    private fun leerLista(json: JSONArray?): List<Notificacion> {
        if (json == null) return emptyList()

        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                add(aNotificacion(item))
            }
        }
    }

    private fun aNotificacion(json: JSONObject): Notificacion = Notificacion(
        id = json.optString("id"),
        tipo = json.optString("tipo"),
        titulo = json.optString("titulo"),
        mensaje = json.optString("mensaje"),
        facturaId = json.optString("facturaId"),
        numero = json.optString("numero"),
        total = json.optDouble("total", 0.0),
        modalidad = json.optString("modalidad"),
        conductorNombre = texto(json, "conductorNombre"),
        diaContable = json.optString("diaContable"),
        leidaEn = texto(json, "leidaEn"),
        creadaEn = json.optString("creadaEn"),
    )

    /**
     * `optString` devuelve la cadena `"null"` cuando el JSON trae `null`, y
     * una notificación terminaría mostrando "null" como nombre del conductor
     * o como fecha de lectura. Se comprueba `isNull` antes, igual que en
     * [ClientesApi].
     */
    private fun texto(json: JSONObject, campo: String): String? =
        if (!json.has(campo) || json.isNull(campo)) null
        else json.optString(campo).trim().ifEmpty { null }

    private companion object {
        const val ESTADO_TODAS = "todas"
    }
}
