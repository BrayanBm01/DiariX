package com.example.totaldiaria.modulos.facturacion.modelo

/**
 * DIARIX · Fase 2 · Cliente (RF-01 a RF-04)
 *
 * Ficha de un cliente tal como llega del backend (`GET /clientes/:id` y el
 * objeto `datos[]` del listado). Es un `data class` inmutable: la pantalla
 * lo pinta, nunca lo modifica. Quien rellena campos es [DatosDeCliente],
 * que es el cuerpo que se manda al crear y al editar.
 *
 * Los campos opcionales son `String?` y no `String` vacío a propósito: un
 * cliente sin teléfono tiene `null`, y `null` distingue "no tiene" de "me lo
 * dejó en blanco" en la ficha y en el formulario.
 *
 * No hay relación con facturas ni con préstamos: el backend tampoco la tiene
 * todavía. Ver `docs/FASE-2-CLIENTES.md`.
 */
data class Cliente(
    val id: String,
    val nombres: String,
    val apellidos: String,
    /** `nombres + " " + apellidos`, calculado en el servidor (una sola fuente). */
    val nombreCompleto: String,
    val tipoDocumento: String,
    val numeroDocumento: String,
    val email: String?,
    val telefono: String?,
    val direccion: String?,
    val referencias: String?,
    val observaciones: String?,
    val creadoEn: String,
    val actualizadoEn: String,
) {
    /**
     * Documento con su etiqueta, para la tarjeta del listado:
     * `CC 1020304050`.
     *
     * Si el tipo llegara desconocido (el backend añadió uno antes que
     * la app se actualizara) se muestra el código tal cual en vez de dejar
     * la línea en blanco.
     */
    fun documentoConEtiqueta(): String =
        "${etiquetaDeDocumento(tipoDocumento)} $numeroDocumento"

    companion object {
        /**
         * Catálogo cerrado de tipos de documento, en el MISMO orden que lo
         * declara `clientes.dto.ts` en el backend.
         *
         * Es una lista y no un `enum` para poder añadir un tipo sin que
         * `when` exhaustivos repartidos por la app dejen de compilar.
         */
        val TIPOS_DE_DOCUMENTO = listOf("CC", "CE", "NIT", "TI", "PASAPORTE", "RUT")

        private val ETIQUETAS = mapOf(
            "CC" to "Cédula de ciudadanía",
            "CE" to "Cédula de extranjería",
            "NIT" to "NIT",
            "TI" to "Tarjeta de identidad",
            "PASAPORTE" to "Pasaporte",
            "RUT" to "RUT",
        )

        /** Etiqueta de un tipo, tal como la mostrará el formulario. */
        fun etiquetaDeDocumento(tipo: String): String = ETIQUETAS[tipo] ?: tipo
    }
}

/**
 * Cuerpo de un alta o de una actualización de cliente.
 *
 * Vive aquí y no en la pantalla porque el JSON lo arma el repositorio: la UI
 * pasa datos tipados y nunca toca `org.json` ni elige qué campos van al
 * servidor.
 *
 * Los opcionales son `String?`; `null` significa "sin dato" y se envía como
 * `null` en el cuerpo. Quien decide qué hace el backend con eso es
 * [ClientesApi], que manda siempre la ficha entera.
 */
data class DatosDeCliente(
    val nombres: String,
    val apellidos: String,
    val tipoDocumento: String,
    val numeroDocumento: String,
    val email: String? = null,
    val telefono: String? = null,
    val direccion: String? = null,
    val referencias: String? = null,
    val observaciones: String? = null,
)

/** Sobre del listado, con la paginación que calcula el servidor. */
data class PaginaDeClientes(
    val datos: List<Cliente>,
    val total: Int,
    val pagina: Int,
    val porPagina: Int,
    val totalPaginas: Int,
)
