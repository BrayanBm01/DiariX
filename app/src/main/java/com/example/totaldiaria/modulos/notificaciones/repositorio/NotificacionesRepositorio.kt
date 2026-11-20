package com.example.totaldiaria.modulos.notificaciones.repositorio

import com.example.totaldiaria.modulos.notificaciones.modelo.Notificacion
import com.example.totaldiaria.modulos.notificaciones.modelo.PaginaDeNotificaciones
import com.example.totaldiaria.modulos.notificaciones.modelo.ResumenDeNotificaciones
import com.example.totaldiaria.network.NotificacionesApi
import com.example.totaldiaria.sesion.SessionManager

/**
 * DIARIX · Puerta única a la bandeja de notificaciones
 *
 * Igual que [com.example.totaldiaria.modulos.facturacion.repositorio.ClientesRepositorio]:
 * la pantalla no conoce tokens ni rutas. El access token se pide en cada
 * llamada a [SessionManager.tokensListos], que lo renueva si está por vencer.
 *
 * Sin caché local a propósito. Los avisos son de confirmaciones hechas en el
 * servidor y una copia en SQLite podría mostrar como pendiente algo que ya se
 * leyó en otro dispositivo. Sin red se muestra el estado de error con
 * reintentar, que es lo que ocurre.
 */
class NotificacionesRepositorio(
    private val api: NotificacionesApi,
    private val sesion: SessionManager,
) {

    fun listar(
        pagina: Int,
        porPagina: Int,
        estado: String = "todas",
    ): PaginaDeNotificaciones =
        api.listar(sesion.tokensListos().accessToken, pagina, porPagina, estado)

    fun resumen(): ResumenDeNotificaciones =
        api.resumen(sesion.tokensListos().accessToken)

    fun marcarLeida(id: String): Notificacion =
        api.marcarLeida(sesion.tokensListos().accessToken, id)

    fun marcarTodas(): Int =
        api.marcarTodas(sesion.tokensListos().accessToken)
}
