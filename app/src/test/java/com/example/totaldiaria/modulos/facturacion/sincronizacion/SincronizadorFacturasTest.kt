package com.example.totaldiaria.modulos.facturacion.sincronizacion

import com.example.totaldiaria.modulos.facturacion.modelo.EstadoSincronizacion
import com.example.totaldiaria.network.CodigosDelBackend
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.network.ResultadoDeSincronizacion
import com.example.totaldiaria.sesion.AuthApiFalsa
import com.example.totaldiaria.sesion.SessionManager
import com.example.totaldiaria.sesion.TokenStoreEnMemoria
import com.example.totaldiaria.modulos.facturacion.servicio.DiaService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import java.util.concurrent.CountDownLatch

/**
 * Pruebas de la sincronización de facturas (Fase 4).
 *
 * Verifican el flujo LOCAL → PENDIENTE → INTERNET → API → CONFIRMACIÓN →
 * SINCRONIZADA, la idempotencia (reintentar no duplica), que la factura
 * NUNCA se pierde (ni offline ni si el servidor la rechaza) y que los
 * bloqueos del servidor se traducen en mensajes claros.
 */
class SincronizadorFacturasTest {

    private val ahora = 1_760_000_000_000L

    private val diaService = DiaService(
        reloj = { java.util.Date(ahora) },
        zonaHoraria = TimeZone.getTimeZone("UTC")
    )

    private fun sesionConTokensVigentes() =
        SessionManager(
            AuthApiFalsa({ ahora }),
            TokenStoreEnMemoria(tokensVigentes(ahora))
        ) { ahora }

    private fun sincronizador(
        api: FacturasApiFalsa,
        repositorio: RepositorioDeSyncFalso,
        red: EstadoDeRed = RedFalsa(),
        sesion: SessionManager = sesionConTokensVigentes()
    ) = SincronizadorFacturas(
        facturasApi = api,
        repositorio = repositorio,
        sesion = sesion,
        red = red,
        diaService = diaService,
    )

    // ------------------------------------------------------------------
    // Camino feliz
    // ------------------------------------------------------------------

    @Test
    fun `una factura confirmada queda sincronizada con el id del servidor`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.SINCRONIZADA, "fact_abc")
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        assertTrue(resultado is ResultadoDeSync.Exito)
        assertEquals(listOf(1 to "fact_abc"), repositorio.marcadasSincronizadas)
        assertEquals(
            EstadoSincronizacion.SINCRONIZADA,
            repositorio.filas.first().estadoSincronizacion
        )
    }

    @Test
    fun `el lote enviado lleva el clientUid de cada factura`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.SINCRONIZADA, "fact_1"),
            Triple("uid-2", ResultadoDeSincronizacion.SINCRONIZADA, "fact_2"),
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(
                FacturasApiFalsa.factura(1),
                FacturasApiFalsa.factura(2),
            )
        )

        sincronizador(api, repositorio).sincronizarPendientes()

        assertEquals(
            listOf("uid-1", "uid-2"),
            api.lotesEnviados.single().map { it.clientUid }
        )
    }

    @Test
    fun `sin facturas pendientes no se llama al servidor`() {
        val api = FacturasApiFalsa()
        val repositorio = RepositorioDeSyncFalso(
            listOf(
                FacturasApiFalsa.factura(
                    1,
                    estado = EstadoSincronizacion.SINCRONIZADA
                )
            )
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        assertEquals(ResultadoDeSync.SinPendientes, resultado)
        assertTrue(api.lotesEnviados.isEmpty())
    }

    // ------------------------------------------------------------------
    // Idempotencia: reintentar NO duplica
    // ------------------------------------------------------------------

    @Test
    fun `reenviar una factura ya existente no la duplica`() {
        val api = FacturasApiFalsa()

        // El servidor responde YA_EXISTIA con el id original.
        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.YA_EXISTIA, "fact_1")
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        sincronizador(api, repositorio).sincronizarPendientes()

        // Localmente sigue habiendo UNA sola factura, ahora sincronizada.
        assertEquals(1, repositorio.filas.size)
        assertEquals(
            EstadoSincronizacion.SINCRONIZADA,
            repositorio.filas.single().estadoSincronizacion
        )
        assertEquals(listOf(1 to "fact_1"), repositorio.marcadasSincronizadas)
    }

    @Test
    fun `tras sincronizar la factura ya no se reenvia`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.SINCRONIZADA, "fact_1")
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val s = sincronizador(api, repositorio)

        s.sincronizarPendientes()
        s.sincronizarPendientes()

        // La segunda pasada no tiene nada que enviar.
        assertEquals(1, api.lotesEnviados.size)
    }

    // ------------------------------------------------------------------
    // Rechazos del servidor: la factura NO se pierde
    // ------------------------------------------------------------------

    @Test
    fun `una factura rechazada se conserva localmente con su mensaje`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple(
                "uid-1",
                ResultadoDeSincronizacion.RECHAZADA,
                null
            ),
            limite = 15
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        sincronizador(api, repositorio).sincronizarPendientes()

        val factura = repositorio.filas.single()

        // Sigue en la base local: el usuario la ve y decide qué hacer.
        assertEquals(EstadoSincronizacion.RECHAZADA, factura.estadoSincronizacion)
        assertEquals(
            "Alcanzaste el límite de 15 facturas de este día.",
            factura.ultimoError
        )
        assertEquals(1, repositorio.marcadasRechazadas.size)
    }

    @Test
    fun `una factura rechazada no se reintenta sola`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.RECHAZADA, null)
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val s = sincronizador(api, repositorio)

        s.sincronizarPendientes()
        s.sincronizarPendientes()

        // Reintentar una factura rechazada no sirve (el día sigue lleno): la
        // cola no la vuelve a enviar sola.
        assertEquals(1, api.lotesEnviados.size)
    }

    @Test
    fun `un lote mixto aplica cada veredicto por separado`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.SINCRONIZADA, "fact_1"),
            Triple("uid-2", ResultadoDeSincronizacion.RECHAZADA, null),
            Triple("uid-3", ResultadoDeSincronizacion.YA_EXISTIA, "fact_3"),
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(
                FacturasApiFalsa.factura(1),
                FacturasApiFalsa.factura(2),
                FacturasApiFalsa.factura(3),
            )
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        val resumen = (resultado as ResultadoDeSync.Exito).resumen!!

        assertEquals(3, resumen.recibidas)
        assertEquals(1, resumen.nuevas)
        assertEquals(1, resumen.repetidas)
        assertEquals(1, resumen.rechazadas)

        // SINCRONIZADA y YA_EXISTIA quedan las dos confirmadas en el
        // servidor: el reenvío de un reintento NO crea una segunda factura.
        assertEquals(2, repositorio.marcadasSincronizadas.size)
        assertEquals(1, repositorio.marcadasRechazadas.size)
    }

    // ------------------------------------------------------------------
    // Offline
    // ------------------------------------------------------------------

    @Test
    fun `sin internet no se intenta nada y nada se pierde`() {
        val api = FacturasApiFalsa()
        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val resultado =
            sincronizador(api, repositorio, red = RedFalsa(conectada = false))
                .sincronizarPendientes()

        assertEquals(ResultadoDeSync.SinConexion, resultado)
        assertTrue(api.lotesEnviados.isEmpty())
        assertEquals(
            EstadoSincronizacion.PENDIENTE,
            repositorio.filas.single().estadoSincronizacion
        )
    }

    @Test
    fun `si se cae la red a mitad del envio la factura sigue pendiente`() {
        val api = FacturasApiFalsa()
        api.error = ErrorDiarix.Red("timeout")

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        assertEquals(ResultadoDeSync.SinConexion, resultado)
        // Nada se marcó: se reintentará al volver la conexión.
        assertEquals(
            EstadoSincronizacion.PENDIENTE,
            repositorio.filas.single().estadoSincronizacion
        )
    }

    @Test
    fun `al volver la conexion la cola se descarga`() {
        val api = FacturasApiFalsa()
        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.SINCRONIZADA, "fact_1")
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val red = RedFalsa(conectada = false)
        val s = sincronizador(api, repositorio, red = red)

        // Primera pasada offline.
        s.sincronizarPendientes()
        assertTrue(api.lotesEnviados.isEmpty())

        // Llega la conexión: el mismo sincronizador sube lo pendiente.
        red.conectada = true
        s.sincronizarPendientes()

        assertEquals(1, api.lotesEnviados.size)
        assertEquals(
            EstadoSincronizacion.SINCRONIZADA,
            repositorio.filas.single().estadoSincronizacion
        )
    }

    // ------------------------------------------------------------------
    // Sesión
    // ------------------------------------------------------------------

    @Test
    fun `con la sesion caducada la sync se detiene sin marcar nada`() {
        val api = FacturasApiFalsa()
        api.error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.NO_AUTENTICADO,
            mensaje = "No autenticado."
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        assertEquals(ResultadoDeSync.SinSesion, resultado)
        assertTrue(repositorio.marcadasSincronizadas.isEmpty())
        assertTrue(repositorio.marcadasRechazadas.isEmpty())
    }

    // ------------------------------------------------------------------
    // Bloqueos de negocio: mensaje claro, sin "error desconocido"
    // ------------------------------------------------------------------

    @Test
    fun `una suscripcion vencida muestra el mensaje del servidor`() {
        val api = FacturasApiFalsa()
        api.error = ErrorDiarix.DeBackend(
            codigo = CodigosDelBackend.SUSCRIPCION_VENCIDA,
            mensaje = "La suscripción de tu empresa está vencida. Pide su renovación."
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        assertTrue(resultado is ResultadoDeSync.Error)
        assertEquals(
            "La suscripción de tu empresa está vencida. Pide su renovación.",
            (resultado as ResultadoDeSync.Error).mensaje
        )
        // Quedan pendientes: si la empresa renueva, se subirán solas.
        assertEquals(
            EstadoSincronizacion.PENDIENTE,
            repositorio.filas.single().estadoSincronizacion
        )
    }

    @Test
    fun `un limit alcanzado se refleja en el resumen`() {
        val api = FacturasApiFalsa()

        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.RECHAZADA, null),
            limite = 15,
            limiteAlcanzado = true,
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val resultado = sincronizador(api, repositorio).sincronizarPendientes()

        val resumen = (resultado as ResultadoDeSync.Exito).resumen!!

        assertTrue(resumen.limiteAlcanzado)
        assertEquals(15, resumen.limiteDiario)
    }

    // ------------------------------------------------------------------
    // Vuelo único del sincronizador
    // ------------------------------------------------------------------

    @Test
    fun `una segunda pasada mientras corre la primera se omite`() {
        val api = FacturasApiFalsa()
        api.respuesta = FacturasApiFalsa.respuestaCon(
            Triple("uid-1", ResultadoDeSincronizacion.SINCRONIZADA, "fact_1")
        )

        val repositorio = RepositorioDeSyncFalso(
            listOf(FacturasApiFalsa.factura(1))
        )

        val dentro = CountDownLatch(1)
        api.espera = dentro

        val s = sincronizador(api, repositorio)

        val hilo = Thread { s.sincronizarPendientes() }
        hilo.start()

        // Mientras el primero está en la red, el segundo no debe salir.
        Thread.sleep(200)
        val segundo = s.sincronizarPendientes()

        dentro.countDown()
        hilo.join()

        assertEquals(ResultadoDeSync.Omitido, segundo)
        assertEquals(1, api.lotesEnviados.size)
    }
}
