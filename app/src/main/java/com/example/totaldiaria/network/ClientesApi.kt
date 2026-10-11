package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.facturacion.modelo.Cliente
import com.example.totaldiaria.modulos.facturacion.modelo.DatosDeCliente
import com.example.totaldiaria.modulos.facturacion.modelo.PaginaDeClientes
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * DIARIX · Fase 2 · Endpoints de clientes
 *
 * Contrato (ver `clientes.controller.ts`):
 *
 *  - `GET    /clientes?pagina=&porPagina=&q=` → sobre paginado
 *    `{datos[], total, pagina, porPagina, totalPaginas}`.
 *  - `GET    /clientes/:id` → la ficha.
 *  - `POST   /clientes` → alta.
 *  - `PATCH  /clientes/:id` → actualización parcial.
 *  - `DELETE /clientes/:id` → borrado definitivo `{... , eliminado: true}`.
 *
 * LA PAGINACIÓN VIVE EN EL SERVIDOR: la app manda página y tamaño y recibe a
 * lo sumo `porPagina` filas. Nunca pide el padrón entero, ni siquiera cuando
 * la búsqueda no encuentra nada.
 *
 * Es `open` para que las pruebas puedan sustituirla por un doble en memoria,
 * igual que [FacturasApi].
 */
open class ClientesApi(private val api: ApiCliente) {

    /** Listado paginado, con búsqueda por texto libre opcional. */
    open fun listar(
        accessToken: String,
        pagina: Int,
        porPagina: Int,
        texto: String? = null,
    ): PaginaDeClientes {
        val consulta = StringBuilder()
            .append("?pagina=").append(pagina)
            .append("&porPagina=").append(porPagina)

        if (!texto.isNullOrBlank()) {
            consulta.append("&q=").append(URLEncoder.encode(texto.trim(), "UTF-8"))
        }

        val cuerpo = JSONObject(api.getJson("/clientes$consulta", accessToken))

        return PaginaDeClientes(
            datos = lecturaLista(cuerpo.optJSONArray("datos")),
            total = cuerpo.optInt("total", 0),
            pagina = cuerpo.optInt("pagina", 1),
            porPagina = cuerpo.optInt("porPagina", porPagina),
            totalPaginas = cuerpo.optInt("totalPaginas", 1),
        )
    }

    open fun obtener(accessToken: String, id: String): Cliente =
        aCliente(JSONObject(api.getJson("/clientes/$id", accessToken)))

    open fun crear(accessToken: String, datos: DatosDeCliente): Cliente {
        val cuerpo = JSONObject(api.postJson("/clientes", accessToken, aJson(datos)))
        return aCliente(cuerpo)
    }

    /**
     * Actualización de la ficha completa, campo a campo.
     *
     * Va el mismo cuerpo que en el alta: el formulario pinta los nueve
     * campos con los datos del cliente, de modo que lo que no cambió se
     * reenvía igual y el backend lo deja idéntico. El verbo es `PATCH` (y no
     * `PUT`) porque la operación sigue siendo parcial a nivel de contrato:
     * no toca `empresaId` ni `createdBy`, que el cliente no envía.
     */
    open fun actualizar(
        accessToken: String,
        id: String,
        datos: DatosDeCliente,
    ): Cliente {
        val cuerpo = JSONObject(api.patchJson("/clientes/$id", accessToken, aJson(datos)))
        return aCliente(cuerpo)
    }

    open fun eliminar(accessToken: String, id: String) {
        api.deleteJson("/clientes/$id", accessToken)
    }

    // ==========================================================================
    // JSON
    // ==========================================================================

    /**
     * Cuerpo de alta o de edición: siempre los nueve campos.
     *
     * El formulario de edición carga la FICHA ENTERA, así que lo que el
     * usuario no tocó viaja con su valor actual y no se borra nada por el
     * camino. Un opcional en `null` se emite como `null` de JSON: en el alta
     * el backend lo trata como "no vino" y lo guarda sin dato, y en la
     * edición como "bórralo".
     *
     * Por eso mismo este método no acepta un objeto parcial: quien construya
     * [DatosDeCliente] a medias y lo mande aquí estaría limpiando campos sin
     * querer.
     */
    private fun aJson(datos: DatosDeCliente): JSONObject =
        JSONObject().apply {
            put("nombres", datos.nombres)
            put("apellidos", datos.apellidos)
            put("tipoDocumento", datos.tipoDocumento)
            put("numeroDocumento", datos.numeroDocumento)
            putNullable("email", datos.email)
            putNullable("telefono", datos.telefono)
            putNullable("direccion", datos.direccion)
            putNullable("referencias", datos.referencias)
            putNullable("observaciones", datos.observaciones)
        }

    private fun JSONObject.putNullable(campo: String, valor: String?) {
        if (valor == null) put(campo, JSONObject.NULL) else put(campo, valor)
    }

    private fun lecturaLista(json: JSONArray?): List<Cliente> {
        if (json == null) return emptyList()

        return buildList {
            for (i in 0 until json.length()) {
                val item = json.optJSONObject(i) ?: continue
                add(aCliente(item))
            }
        }
    }

    /**
     * Lee un campo de texto que puede ser `null` o faltar.
     *
     * `optString` NO sirve: con un `null` de JSON devuelve la cadena
     * `"null"` en Android, y la ficha terminaría mostrando "null" como
     * teléfono. Por eso se comprueba `isNull` antes.
     */
    private fun texto(json: JSONObject, campo: String): String? =
        if (!json.has(campo) || json.isNull(campo)) null
        else json.optString(campo).trim().ifEmpty { null }

    private fun aCliente(json: JSONObject): Cliente = Cliente(
        id = json.optString("id"),
        nombres = json.optString("nombres"),
        apellidos = json.optString("apellidos"),
        nombreCompleto = json.optString("nombreCompleto"),
        tipoDocumento = json.optString("tipoDocumento"),
        numeroDocumento = json.optString("numeroDocumento"),
        email = texto(json, "email"),
        telefono = texto(json, "telefono"),
        direccion = texto(json, "direccion"),
        referencias = texto(json, "referencias"),
        observaciones = texto(json, "observaciones"),
        creadoEn = json.optString("creadoEn"),
        actualizadoEn = json.optString("actualizadoEn"),
    )
}
