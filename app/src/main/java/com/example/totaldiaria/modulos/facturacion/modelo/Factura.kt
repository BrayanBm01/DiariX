package com.example.totaldiaria.modulos.facturacion.modelo

/**
 * Factura local (Fase 4).
 *
 * Los campos nuevos (`clientUid`, `estadoSincronizacion`, `servidorId`,
 * `intentos`, `ultimoError`) alimentan el flujo de sincronización:
 *
 *  - `clientUid` es la clave de idempotencia: lo genera la app y el servidor
 *    lo usa para que reenviar NUNCA duplique una factura.
 *  - `estadoSincronizacion`:
 *    `PENDIENTE` → falta subir; `SINCRONIZADA` → el servidor la confirmó;
 *    `RECHAZADA` → el servidor la rechazó (sigue siendo local, con el error).
 *  - `servidorId` es la id que asigna el backend tras confirmar.
 */
data class Factura(
    val id: Int,
    val numeroFactura: String,
    val efectivo: Double,
    val transferencia: Double,
    val fecha: String,
    val comprobanteUri: String? = null,
    val clientUid: String? = null,
    val estadoSincronizacion: String = EstadoSincronizacion.PENDIENTE,
    val servidorId: String? = null,
    val intentos: Int = 0,
    val ultimoError: String? = null,
)

object EstadoSincronizacion {
    const val PENDIENTE = "PENDIENTE"
    const val SINCRONIZADA = "SINCRONIZADA"
    const val RECHAZADA = "RECHAZADA"
}