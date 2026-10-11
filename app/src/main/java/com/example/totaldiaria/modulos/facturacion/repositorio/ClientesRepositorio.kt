package com.example.totaldiaria.modulos.facturacion.repositorio

import com.example.totaldiaria.modulos.facturacion.modelo.Cliente
import com.example.totaldiaria.modulos.facturacion.modelo.DatosDeCliente
import com.example.totaldiaria.modulos.facturacion.modelo.PaginaDeClientes
import com.example.totaldiaria.network.ClientesApi
import com.example.totaldiaria.sesion.SessionManager

/**
 * DIARIX · Fase 2 · Repositorio de clientes
 *
 * Única puerta entre las pantallas de clientes y la red. Concentra dos
 * decisiones que la UI no debería tomar:
 *
 *  1. QUÉ TOKEN se usa. Se pide a [SessionManager.tokensListos] en cada
 *     llamada, que además renueva el token por vencer. Nada de esto se
 *     reproduce en la pantalla: si un día cambia la renovación, cambia aquí
 *     y no en diez Activities.
 *
 *  2. QUÉ PÁGINA se pide. La paginación es del servidor: la app solo repite
 *     "página N, con M por página" y consume el sobre que regresa. Toda la
 *     lógica de límites, búsqueda y orden vive en el backend.
 *
 * No guarda nada en SQLite. Un padrón de clientes sin caché local significa
 * que abrir la lista sin conexión muestra el estado de Error con reintentar,
 * que es lo que ocurre y lo que hay que decir. No se inventa una arquitectura
 * offline a medias: ver `docs/FASE-2-CLIENTES.md`.
 */
class ClientesRepositorio(
    private val api: ClientesApi,
    private val sesion: SessionManager,
) {

    fun listar(pagina: Int, porPagina: Int, texto: String? = null): PaginaDeClientes =
        api.listar(sesion.tokensListos().accessToken, pagina, porPagina, texto)

    fun obtener(id: String): Cliente =
        api.obtener(sesion.tokensListos().accessToken, id)

    fun crear(datos: DatosDeCliente): Cliente =
        api.crear(sesion.tokensListos().accessToken, datos)

    fun actualizar(id: String, datos: DatosDeCliente): Cliente =
        api.actualizar(sesion.tokensListos().accessToken, id, datos)

    fun eliminar(id: String): Unit =
        api.eliminar(sesion.tokensListos().accessToken, id)
}
