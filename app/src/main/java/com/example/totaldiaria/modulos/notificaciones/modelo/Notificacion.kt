package com.example.totaldiaria.modulos.notificaciones.modelo

/**
 * DIARIX · Notificación de la app
 *
 * Los avisos que recibe el conductor (o el usuario individual) cuando la web
 * CONFIRMA un pago o la recepción de un comprobante. La app no los genera ni
 * los deduce: el servidor es la única fuente y manda ya el título y el
 * mensaje exactos, de modo que un cambio de texto no obliga a publicar una
 * versión nueva.
 *
 * [leidaEn] nulo significa pendiente. El historial nunca se borra al leer:
 * la fila sigue existiendo y solo cambia esa fecha.
 */
data class Notificacion(
    val id: String,
    val tipo: String,
    val titulo: String,
    val mensaje: String,
    val facturaId: String,
    val numero: String,
    val total: Double,
    val modalidad: String,
    val conductorNombre: String?,
    val diaContable: String,
    val leidaEn: String?,
    val creadaEn: String,
) {

    val leida: Boolean get() = leidaEn != null

    /** `Pago confirmado` (tipo `PAGO_RECIBIDO`): la empresa verificó la transferencia. */
    val esPago: Boolean get() = tipo == TIPO_PAGO

    companion object {
        const val TIPO_PAGO = "PAGO_RECIBIDO"
        const val TIPO_COMPROBANTE = "COMPROBANTE_RECIBIDO"
    }
}

/** Sobre paginado que devuelve `GET /notificaciones/mias`. */
data class PaginaDeNotificaciones(
    val datos: List<Notificacion>,
    val total: Int,
    val pagina: Int,
    val porPagina: Int,
    val totalPaginas: Int,
    /** Pendientes en TODA la bandeja, no solo en esta página. */
    val pendientes: Int,
)

/** Respuesta de `GET /notificaciones/mias/resumen`, para la campanita. */
data class ResumenDeNotificaciones(
    val pendientes: Int,
    val total: Int,
)
