package com.example.totaldiaria.modulos.facturacion.sincronizacion

import com.example.totaldiaria.modulos.facturacion.modelo.EstadoSincronizacion
import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import com.example.totaldiaria.network.ApiCliente
import com.example.totaldiaria.network.FacturasApi
import com.example.totaldiaria.network.RespuestaSincronizacion
import com.example.totaldiaria.network.ResultadoDeSincronizacion
import com.example.totaldiaria.network.ResumenDeSincronizacion
import com.example.totaldiaria.sesion.TokensDeSesion
import com.example.totaldiaria.modulos.facturacion.servicio.DiaService
import com.example.totaldiaria.modulos.facturacion.servicio.AlmacenDeComprobantes
import com.example.totaldiaria.modulos.facturacion.servicio.ComprobanteParaSubir
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.sesion.UsoDiario
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Dobles de prueba de la cola de sincronización (Fase 4).
 */

/** Red simulada: se enciende y apaga a voluntad. */
class RedFalsa(var conectada: Boolean = true) : EstadoDeRed {

    override fun hayInternet(): Boolean = conectada

    override fun observarCambios(escucha: () -> Unit): java.io.Closeable =
        java.io.Closeable { }
}

/**
 * Repositorio en memoria con las MISMAS reglas que la cola de SQLite:
 * solo se devuelven las PENDIENTE (con clientUid) y el veredicto del
 * servidor cambia su estado.
 */
class RepositorioDeSyncFalso(
    iniciales: List<Factura> = emptyList()
) : RepositorioDeSincronizacion {

    val filas = iniciales.toMutableList()

    val marcadasSincronizadas = mutableListOf<Pair<Int, String>>()
    val marcadasRechazadas = mutableListOf<Triple<Int, String, Int>>()
    val comprobantesSubidos = mutableListOf<Int>()
    val comprobantesFallidos = mutableListOf<Int>()

    override fun obtenerPendientes(limite: Int): List<Factura> =
        filas
            .filter { it.estadoSincronizacion == EstadoSincronizacion.PENDIENTE }
            .take(limite)

    override fun marcarSincronizada(idFactura: Int, servidorId: String): Boolean {
        marcadasSincronizadas += idFactura to servidorId

        filas.reemplazarSi({ it.id == idFactura }) {
            it.copy(
                estadoSincronizacion = EstadoSincronizacion.SINCRONIZADA,
                servidorId = servidorId,
                intentos = 0,
                ultimoError = null,
            )
        }
        return true
    }

    override fun marcarRechazada(
        idFactura: Int,
        mensaje: String,
        intentos: Int
    ): Boolean {
        marcadasRechazadas += Triple(idFactura, mensaje, intentos)

        filas.reemplazarSi({ it.id == idFactura }) {
            it.copy(
                estadoSincronizacion = EstadoSincronizacion.RECHAZADA,
                ultimoError = mensaje,
                intentos = intentos,
            )
        }
        return true
    }

    override fun obtenerPendientesDeComprobante(limite: Int): List<Factura> =
        filas
            .filter {
                it.estadoSincronizacion == EstadoSincronizacion.SINCRONIZADA &&
                    it.servidorId != null &&
                    it.comprobanteUri != null &&
                    !it.comprobanteSubido
            }
            .take(limite)

    override fun marcarComprobanteSubido(idFactura: Int): Boolean {
        comprobantesSubidos += idFactura

        filas.reemplazarSi({ it.id == idFactura }) {
            it.copy(comprobanteSubido = true)
        }

        return true
    }

    override fun marcarComprobanteFallido(idFactura: Int): Boolean {
        comprobantesFallidos += idFactura
        return true
    }
}

/** Sustituye in situ la primera fila que cumpla el predicado. */
private fun <T> MutableList<T>.reemplazarSi(
    predicado: (T) -> Boolean,
    transform: (T) -> T
) {
    val indice = indexOfFirst(predicado)
    if (indice >= 0) this[indice] = transform(this[indice])
}

/** FacturasApi falsa: guarda los lotes enviados y devuelve lo que se le pida. */
class FacturasApiFalsa : FacturasApi(ApiCliente("http://localhost:0/api/v1")) {

    val lotesEnviados = mutableListOf<List<Factura>>()
    val tokensRecibidos = mutableListOf<String>()

    /** Respuesta a devolver; por defecto, todo sincronizado. */
    var respuesta: RespuestaSincronizacion = respuestaPorDefecto()

    /** Si se asigna, la llamada falla con este error. */
    var error: ErrorDiarix? = null

    /** Si se asigna, la subida del comprobante falla con este error. */
    var errorAlAdjuntar: ErrorDiarix? = null

    data class ComprobanteEnviado(
        val facturaId: String,
        val nombre: String,
        val mime: String,
        val bytes: Int,
    )

    val comprobantesEnviados = mutableListOf<ComprobanteEnviado>()

    /** Si se asigna, la llamada espera a esta señal (para probar vuelo único). */
    var espera: CountDownLatch? = null

    override fun sincronizar(
        accessToken: String,
        facturas: List<Factura>,
        diaService: DiaService
    ): RespuestaSincronizacion {
        tokensRecibidos += accessToken
        lotesEnviados += facturas

        espera?.await(2, TimeUnit.SECONDS)

        error?.let { throw it }

        return respuesta
    }

    override fun usoDiario(accessToken: String): UsoDiario =
        UsoDiario(
            dia = "01/10/2026",
            plan = "FREE",
            planVigente = true,
            limite = 15,
            usadas = 3,
            restantes = 12,
        )

    override fun adjuntarComprobante(
        accessToken: String,
        facturaId: String,
        nombre: String,
        mime: String,
        archivo: ByteArray,
    ) {
        comprobantesEnviados += ComprobanteEnviado(facturaId, nombre, mime, archivo.size)

        errorAlAdjuntar?.let { throw it }
    }

    companion object {
        fun factura(
            id: Int,
            clientUid: String = "uid-$id",
            estado: String = EstadoSincronizacion.PENDIENTE,
            servidorId: String? = null,
            comprobanteUri: String? = null,
            comprobanteSubido: Boolean = false,
        ) = Factura(
            id = id,
            numeroFactura = "F-$id",
            efectivo = 10_000.0,
            transferencia = 0.0,
            fecha = "01/10/2026 10:00",
            comprobanteUri = comprobanteUri,
            clientUid = clientUid,
            estadoSincronizacion = estado,
            servidorId = servidorId,
            comprobanteSubido = comprobanteSubido,
        )

        /** Todas las facturas del lote quedan sincronizadas. */
        fun respuestaPorDefecto(
            estado: String = ResultadoDeSincronizacion.SINCRONIZADA,
            limite: Int = 15
        ) = RespuestaSincronizacion(
            resultados = emptyList(),
            resumen = ResumenDeSincronizacion(
                recibidas = 0,
                nuevas = 0,
                repetidas = 0,
                rechazadas = 0,
                limiteDiario = limite,
                limiteAlcanzado = false,
            ),
        )

        /** Construye una respuesta con un veredicto por cada clientUid. */
        fun respuestaCon(
            vararg veredictos: Triple<String, String, String?>,
            limite: Int = 15,
            limiteAlcanzado: Boolean = false
        ): RespuestaSincronizacion =
            RespuestaSincronizacion(
                resultados = veredictos.map { (uid, estado, id) ->
                    ResultadoDeSincronizacion(
                        clientUid = uid,
                        estado = estado,
                        servidorId = id,
                        codigo = if (estado == ResultadoDeSincronizacion.RECHAZADA)
                            com.example.totaldiaria.network.CodigosDelBackend.LIMITE_FACTURAS_DIARIAS
                        else null,
                        mensaje = if (estado == ResultadoDeSincronizacion.RECHAZADA)
                            "Alcanzaste el límite de $limite facturas de este día."
                        else null,
                    )
                },
                resumen = ResumenDeSincronizacion(
                    recibidas = veredictos.size,
                    nuevas = veredictos.count {
                        it.second == ResultadoDeSincronizacion.SINCRONIZADA
                    },
                    repetidas = veredictos.count {
                        it.second == ResultadoDeSincronizacion.YA_EXISTIA
                    },
                    rechazadas = veredictos.count {
                        it.second == ResultadoDeSincronizacion.RECHAZADA
                    },
                    limiteDiario = limite,
                    limiteAlcanzado = limiteAlcanzado,
                ),
            )
    }
}

/** TokenStore listo para pruebas de la sincronización. */
internal fun tokensVigentes(ahora: Long) =
    TokensDeSesion(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        accessExpiraAtMs = ahora + 15 * 60_000L,
        refreshExpiraAtMs = ahora + 30 * 24 * 60 * 60_000L,
    )

/**
 * Almacén de comprobantes simulado: devuelve un archivo fijo y registra qué
 * URI se leyó. Sirve para comprobar que la sincronización sube la foto real.
 */
class AlmacenDeComprobantesFalso(
    var contenido: ComprobanteParaSubir? =
        ComprobanteParaSubir(
            bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
            mime = "image/jpeg",
            nombre = "comprobante.jpg",
        )
) : AlmacenDeComprobantes {

    val leidos = mutableListOf<String>()

    override fun leerParaSubir(uriTexto: String): ComprobanteParaSubir? {
        leidos += uriTexto
        return contenido
    }
}
