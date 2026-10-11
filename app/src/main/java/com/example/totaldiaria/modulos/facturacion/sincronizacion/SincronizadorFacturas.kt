package com.example.totaldiaria.modulos.facturacion.sincronizacion

import com.example.totaldiaria.modulos.facturacion.repositorio.FacturaRepository
import com.example.totaldiaria.modulos.facturacion.modelo.Factura
import com.example.totaldiaria.network.CodigosDelBackend
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.network.FacturasApi
import com.example.totaldiaria.network.RespuestaSincronizacion
import com.example.totaldiaria.sesion.SessionManager
import com.example.totaldiaria.modulos.facturacion.servicio.DiaService

/**
 * Abstracción de las piezas de la base que el sincronizador necesita, para
 * poder probar la lógica con un doble sin SQLite.
 */
interface RepositorioDeSincronizacion {
    fun obtenerPendientes(limite: Int): List<Factura>
    fun marcarSincronizada(idFactura: Int, servidorId: String): Boolean
    fun marcarRechazada(idFactura: Int, mensaje: String, intentos: Int): Boolean
}

/** El repositorio real cumple el contrato de sincronización. */
class RepositorioSyncDeFacturas(
    private val base: FacturaRepository,
) : RepositorioDeSincronizacion {
    override fun obtenerPendientes(limite: Int): List<Factura> =
        base.obtenerPendientesDeSincronizacion(limite)

    override fun marcarSincronizada(idFactura: Int, servidorId: String): Boolean =
        base.marcarSincronizada(idFactura, servidorId)

    override fun marcarRechazada(idFactura: Int, mensaje: String, intentos: Int): Boolean =
        base.marcarRechazada(idFactura, mensaje, intentos)
}

/** Resultado de una pasada de sincronización para pintar la UI. */
sealed interface ResultadoDeSync {
    /** No había red: se queda todo en la cola. */
    data object SinConexion : ResultadoDeSync
    /** No había nada que subir. */
    data object SinPendientes : ResultadoDeSync
    /** Ya había otra pasada en curso (vuelo único). */
    data object Omitido : ResultadoDeSync
    /** La sesión murió (el SessionManager ya avisó). */
    data object SinSesion : ResultadoDeSync
    data class Exito(val resumen: com.example.totaldiaria.network.ResumenDeSincronizacion?) : ResultadoDeSync
    /** El servidor respondió errores de negocio (límite, suscripción…). */
    data class Error(val mensaje: String, val codigo: String?) : ResultadoDeSync
}

/**
 * Cola de sincronización local → servidor (Fase 4).
 *
 * - Toma las facturas PENDIENTE (con `clientUid`) y las sube en lotes.
 * - Aplica el veredicto SINCRONIZADA / YA_EXISTIA / RECHAZADA sin perder
 *   NUNCA la factura local (la fila queda marcada, no se borra).
 * - Sin internet deja todo en cola; el disparador de reconexión lo vuelve a
 *   llamar automáticamente.
 * - Un solo hilo ejecuta la pasada a la vez (no hay síncronos concurrentes).
 */
class SincronizadorFacturas(
    private val facturasApi: FacturasApi,
    private val repositorio: RepositorioDeSincronizacion,
    private val sesion: SessionManager,
    private val red: EstadoDeRed,
    private val diaService: DiaService = DiaService(),
    private val tamanoDeLote: Int = 20,
) {

    @Volatile
    var sincronizando: Boolean = false
        private set

    @Volatile
    var ultimoResultado: ResultadoDeSync? = null
        private set

    /** Avisa (en el hilo que llamó) al terminar una pasada. */
    @Volatile
    var alTerminar: ((ResultadoDeSync) -> Unit)? = null

    /** Ejecuta una pasada. Idempotente y seguro de llamar a menudo. */
    fun sincronizarPendientes(): ResultadoDeSync {
        if (sincronizando) return ResultadoDeSync.Omitido

        if (!red.hayInternet()) {
            return emitir(ResultadoDeSync.SinConexion)
        }

        var resultado: ResultadoDeSync
        sincronizando = true
        try {
            resultado = ejecutarPasada()
        } catch (e: ErrorDiarix) {
            resultado = clasificarError(e)
        } finally {
            sincronizando = false
        }

        // Solo se sobrescribe el último estado con resultados que realmente
        // dicen algo (un Omitido no informa).
        if (resultado !is ResultadoDeSync.Omitido) {
            ultimoResultado = resultado
        }
        alTerminar?.invoke(resultado)
        return resultado
    }

    private fun emitir(resultado: ResultadoDeSync): ResultadoDeSync {
        ultimoResultado = resultado
        alTerminar?.invoke(resultado)
        return resultado
    }

    private fun ejecutarPasada(): ResultadoDeSync {
        val tokens = sesion.tokensListos() // refresca si hace falta

        val pendientes = repositorio.obtenerPendientes(tamanoDeLote)
        if (pendientes.isEmpty()) return ResultadoDeSync.SinPendientes

        val respuesta =
            facturasApi.sincronizar(tokens.accessToken, pendientes, diaService)
        aplicarVeredictos(respuesta, pendientes)

        return ResultadoDeSync.Exito(respuesta.resumen)
    }

    private fun clasificarError(e: ErrorDiarix): ResultadoDeSync =
        when (e) {
            is ErrorDiarix.Red -> ResultadoDeSync.SinConexion
            is ErrorDiarix.DeBackend ->
                if (e.codigo == CodigosDelBackend.NO_AUTENTICADO ||
                    e.codigo == CodigosDelBackend.CREDENCIALES_INVALIDAS
                ) {
                    // La sesión murió: el SessionManager ya borró y avisó.
                    ResultadoDeSync.SinSesion
                } else {
                    ResultadoDeSync.Error(e.mensajeAmigable, e.codigo)
                }
            is ErrorDiarix.Inesperado -> ResultadoDeSync.Error(e.mensajeAmigable, null)
        }

    private fun aplicarVeredictos(
        respuesta: RespuestaSincronizacion,
        pendientes: List<Factura>,
    ) {
        val porClientUid = pendientes.associateBy { it.clientUid }

        respuesta.resultados.forEach { resultado ->
            val factura = porClientUid[resultado.clientUid] ?: return@forEach

            when {
                resultado.sincronizada && resultado.servidorId != null ->
                    repositorio.marcarSincronizada(factura.id, resultado.servidorId)

                resultado.rechazada -> repositorio.marcarRechazada(
                    idFactura = factura.id,
                    mensaje = resultado.mensaje
                        ?: "El servidor rechazó la factura. Consérvala y revisa el dato.",
                    intentos = factura.intentos + 1,
                )

                // YA_EXISTIA sin id (no debería pasar): se deja pendiente y se
                // reintentará; el servidor no duplica igualmente.
            }
        }
    }
}