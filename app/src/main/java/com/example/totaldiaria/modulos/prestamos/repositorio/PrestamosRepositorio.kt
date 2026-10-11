package com.example.totaldiaria.modulos.prestamos.repositorio

import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePagoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePrestamoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.DetalleDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDeCobros
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDePagos
import com.example.totaldiaria.modulos.prestamos.modelo.PaginaDePrestamos
import com.example.totaldiaria.modulos.prestamos.modelo.PagoDePrestamo
import com.example.totaldiaria.modulos.prestamos.modelo.ResumenDePrestamos
import com.example.totaldiaria.modulos.prestamos.modelo.RespuestaDeOperacionDePago
import com.example.totaldiaria.network.PrestamosApi
import com.example.totaldiaria.sesion.SessionManager

/**
 * DIARIX · Fase 3 · Repositorio de préstamos
 *
 * Única puerta entre las pantallas de préstamos y la red, igual que
 * [com.example.totaldiaria.modulos.facturacion.repositorio.ClientesRepositorio]
 * lo es para clientes:
 *
 *  1. QUÉ TOKEN se usa. [SessionManager.tokensListos] en cada llamada
 *     (renueva por vencer); ninguna pantalla toca los tokens.
 *
 *  2. QUÉ PÁGINA y QUÉ FILTRO se piden. La paginación, la búsqueda y el
 *     estado derivado son del servidor: aquí solo se repiten.
 *
 * No guarda nada en SQLite, igual que los clientes: sin conexión el listado
 * muestra su estado de Error con reintentar, nunca una lista que parece
 * cargada. La fuente del módulo es el backend Fase 3, que sí existe (ver
 * `prestamos.controller.ts`).
 */
class PrestamosRepositorio(
    private val api: PrestamosApi,
    private val sesion: SessionManager,
) {

    fun listar(
        pagina: Int,
        porPagina: Int,
        texto: String? = null,
        estado: String? = null,
    ): PaginaDePrestamos =
        api.listar(sesion.tokensListos().accessToken, pagina, porPagina, texto, estado)

    fun obtener(id: String): DetalleDePrestamo =
        api.obtener(sesion.tokensListos().accessToken, id)

    fun crear(datos: DatosDePrestamoNuevo): DetalleDePrestamo =
        api.crear(sesion.tokensListos().accessToken, datos)

    // ==========================================================================
    // Fase 4 · Pagos, cobros y resumen
    // ==========================================================================

    fun registrarPago(prestamoId: String, datos: DatosDePagoNuevo): RespuestaDeOperacionDePago =
        api.registrarPago(sesion.tokensListos().accessToken, prestamoId, datos)

    fun pagosDePrestamo(prestamoId: String, pagina: Int, porPagina: Int): PaginaDePagos =
        api.listarPagosDePrestamo(
            sesion.tokensListos().accessToken,
            prestamoId,
            pagina,
            porPagina,
        )

    fun pagos(
        pagina: Int,
        porPagina: Int,
        texto: String? = null,
        metodoPago: String? = null,
    ): PaginaDePagos =
        api.listarPagos(sesion.tokensListos().accessToken, pagina, porPagina, texto, metodoPago)

    fun revertirPago(pagoId: String, motivo: String): RespuestaDeOperacionDePago =
        api.revertirPago(sesion.tokensListos().accessToken, pagoId, motivo)

    fun cobros(categoria: String, pagina: Int, porPagina: Int): PaginaDeCobros =
        api.listarCobros(sesion.tokensListos().accessToken, categoria, pagina, porPagina)

    fun resumen(): ResumenDePrestamos =
        api.resumen(sesion.tokensListos().accessToken)

    fun adjuntarComprobante(
        pagoId: String,
        nombre: String,
        mime: String,
        archivo: ByteArray,
    ): PagoDePrestamo =
        api.adjuntarComprobante(sesion.tokensListos().accessToken, pagoId, nombre, mime, archivo)

    fun quitarComprobante(pagoId: String): PagoDePrestamo =
        api.quitarComprobante(sesion.tokensListos().accessToken, pagoId)

    fun descargarComprobante(pagoId: String): ByteArray =
        api.descargarComprobante(sesion.tokensListos().accessToken, pagoId)
}