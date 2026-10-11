package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.prestamos.modelo.CategoriasDeCobro
import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePagoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.MetodosDePagoDePrestamo
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal

/**
 * DIARIX · Fase 4 · El contrato real de pagos, cobros, resumen y comprobantes.
 *
 * Mismo método que `PrestamosApiTest`: se levanta un `MockWebServer` de verdad
 * y se habla con él para fijar RUTAS, VERBO y CAMPOS. Lo propio de esta fase:
 *
 *  - El MONTÓN viaja exacto en el cuerpo (BigDecimal, `100000.00`), no como
 *    Double.
 *  - `claveUnica` es idempotente: el cuerpo del alta la lleva siempre.
 *  - El comprobante va en MULTIPART bajo el campo `archivo` (igual que en
 *    facturas), la app jamás manda una ruta local.
 *  - La categoría de cobros viaja como PARÁMETRO al servidor: la app no
 *    filtra ni inventa la cartera.
 *  - Un rechazo del servidor (422 con `{codigo, mensaje}` en español) llega
 *    como [ErrorDiarix.DeBackend] con ese mismo mensaje para los códigos
 *    conocidos de pagos y comprobantes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrestamosPagosCobrosApiTest {

    private lateinit var servidor: MockWebServer
    private lateinit var api: PrestamosApi

    @Before
    fun setUp() {
        servidor = MockWebServer()
        servidor.start()
        api = PrestamosApi(ApiCliente(servidor.url("/api/v1").toString()))
    }

    @After
    fun tearDown() {
        servidor.shutdown()
    }

    private fun primera(): RecordedRequest = servidor.takeRequest()

    private fun fichaDeBackend(id: String): String = """
        {
          "id": "$id",
          "empresaId": "emp_1",
          "cliente": {
            "id": "cli_1",
            "nombreCompleto": "María Fernanda Restrepo Villa",
            "tipoDocumento": "CC",
            "numeroDocumento": "1020304050"
          },
          "modalidad": "DIARIA",
          "tipoCalculo": "PORCENTAJE",
          "porcentaje": null,
          "fechaInicio": "2026-10-01",
          "montoPrestado": 1000000,
          "totalAPagar": 1234567,
          "valorCuota": 102880.58,
          "numeroDeCuotas": 12,
          "estado": "ACTIVO",
          "totalPagado": 100000,
          "saldoPendiente": 1134567,
          "cuotasPagadas": 1,
          "cuotasPendientes": 11,
          "creadoEn": "2026-10-01T09:00:00.000Z",
          "actualizadoEn": "2026-10-09T10:00:00.000Z"
        }
    """.trimIndent()

    private fun pagoDeBackend(
        id: String = "pag_1",
        metodo: String = MetodosDePagoDePrestamo.EFECTIVO,
        monto: String = "100000",
        revertido: Boolean = false,
        conComprobante: Boolean = false,
    ): String = """
        {
          "id": "$id",
          "prestamoId": "pre_1",
          "cliente": {
            "id": "cli_1",
            "nombreCompleto": "María Fernanda Restrepo Villa",
            "tipoDocumento": "CC",
            "numeroDocumento": "1020304050"
          },
          "fechaPago": "2026-10-09",
          "metodoPago": "$metodo",
          "monto": $monto,
          "comprobanteUrl": ${if (conComprobante) "\"prestamos/pagos/$id/comprobante\"" else "null"},
          "comprobanteMime": ${if (conComprobante) "\"image/jpeg\"" else "null"},
          "revertido": $revertido,
          "motivoReversion": ${if (revertido) "\"Cobro duplicado\"" else "null"},
          "revertidoEn": ${if (revertido) "\"2026-10-09T11:00:00.000Z\"" else "null"},
          "creadoEn": "2026-10-09T10:00:00.000Z",
          "asignaciones": [
            {"cuotaId": "cuo_1", "numeroCuota": 1, "montoAplicado": 80000},
            {"cuotaId": "cuo_2", "numeroCuota": 2, "montoAplicado": 20000}
          ]
        }
    """.trimIndent()

    private fun cobroDeBackend(
        estado: String = "PENDIENTE",
        diasDeAtraso: Int = 0,
    ): String = """
        {
          "id": "cuo_1",
          "prestamoId": "pre_1",
          "modalidad": "DIARIA",
          "cliente": {
            "id": "cli_1",
            "nombreCompleto": "María Fernanda Restrepo Villa",
            "tipoDocumento": "CC",
            "numeroDocumento": "1020304050"
          },
          "numeroCuota": 1,
          "fechaVencimiento": "2026-10-09",
          "valorEsperado": 102880.58,
          "valorPagado": 0,
          "saldo": 102880.58,
          "diasDeAtraso": $diasDeAtraso,
          "estado": "$estado"
        }
    """.trimIndent()

    // ----------------------------------------------------------------
    // POST /prestamos/:id/pagos
    // ----------------------------------------------------------------

    @Test
    fun `registrarPago manda la ruta el verbo y el cuerpo exacto`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"pago": ${pagoDeBackend()}, "prestamo": ${fichaDeBackend("pre_1")}}"""
            )
        )

        val resultado = api.registrarPago(
            accessToken = "mi-token",
            prestamoId = "pre_1",
            datos = DatosDePagoNuevo(
                monto = BigDecimal("100000.00"),
                metodoPago = MetodosDePagoDePrestamo.EFECTIVO,
                claveUnica = "clave-unica-1",
                fechaPago = "2026-10-09",
            ),
        )

        // Verbo y ruta exactos: el servidor decide la repartición.
        val peticion = primera()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/prestamos/pre_1/pagos", peticion.path)
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))

        // El dinero viaja EXACTO (BigDecimal), no `1.0E5` ni `100000.0` de un
        // Double que ya no puede guardar centavos. Sin centavos el token es
        // `100000` (lo que no se escribe no se inventa); `PrestamosApiTest`
        // ya fija que `1000000.55` viaja con sus dos decimales.
        val cuerpo = peticion.body.readUtf8()
        assertTrue(
            "El monto tiene que ser un token exacto. Cuerpo: $cuerpo",
            cuerpo.contains("\"monto\":100000"),
        )
        assertTrue(cuerpo.contains("\"metodoPago\":\"EFECTIVO\""))
        assertTrue(cuerpo.contains("\"claveUnica\":\"clave-unica-1\""))
        assertTrue(cuerpo.contains("\"fechaPago\":\"2026-10-09\""))

        // Y la respuesta trae pago + ficha actualizada en el mismo sobre.
        assertEquals("pag_1", resultado.pago.id)
        assertEquals(BigDecimal("100000").compareTo(resultado.pago.monto), 0)
        assertEquals("pre_1", resultado.prestamo.prestamo.id)
        assertEquals(2, resultado.pago.asignaciones.size)
        assertEquals(1, resultado.pago.asignaciones[0].numeroCuota)
        assertEquals(BigDecimal("80000").compareTo(resultado.pago.asignaciones[0].montoAplicado), 0)
    }

    @Test
    fun `un pago sin fecha no manda el campo fechaPago`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"pago": ${pagoDeBackend()}, "prestamo": ${fichaDeBackend("pre_1")}}"""
            )
        )

        api.registrarPago(
            accessToken = "t",
            prestamoId = "pre_1",
            datos = DatosDePagoNuevo(
                monto = BigDecimal("100000"),
                metodoPago = MetodosDePagoDePrestamo.TRANSFERENCIA,
                claveUnica = "clave-unica-2",
            ),
        )

        val cuerpo = primera().body.readUtf8()
        assertFalse("Sin fecha el servidor usa hoy; no se inventa el campo", cuerpo.contains("fechaPago"))
    }

    @Test
    fun `un pago con centavos viaja con sus dos decimales`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"pago": ${pagoDeBackend(monto = "102880.58")}, "prestamo": ${fichaDeBackend("pre_1")}}"""
            )
        )

        api.registrarPago(
            accessToken = "t",
            prestamoId = "pre_1",
            datos = DatosDePagoNuevo(
                monto = BigDecimal("102880.58"),
                metodoPago = MetodosDePagoDePrestamo.EFECTIVO,
                claveUnica = "clave-unica-decimal",
            ),
        )

        val cuerpo = primera().body.readUtf8()
        assertTrue(
            "Los centavos no pueden perderse en el viaje. Cuerpo: $cuerpo",
            cuerpo.contains("\"monto\":102880.58"),
        )
    }

    // ----------------------------------------------------------------
    // GET /prestamos/:id/pagos y GET /prestamos/pagos
    // ----------------------------------------------------------------

    @Test
    fun `listarPagosDePrestamo lee el historial paginado de UN prestamo`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${pagoDeBackend()}],
                  "total": 3,
                  "pagina": 1,
                  "porPagina": 25,
                  "totalPaginas": 1
                }
                """.trimIndent()
            )
        )

        val pagina = api.listarPagosDePrestamo("t", prestamoId = "pre_1", pagina = 1, porPagina = 25)

        assertEquals("/api/v1/prestamos/pre_1/pagos?pagina=1&porPagina=25", primera().path)
        assertEquals(3, pagina.total)
        assertEquals("pag_1", pagina.datos.first().id)
        assertEquals("EFECTIVO", pagina.datos.first().metodoPago)
        assertFalse(pagina.datos.first().revertido)
    }

    @Test
    fun `listarPagos global lleva la busqueda y el filtro de metodo al servidor`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${pagoDeBackend()}],
                  "total": 1,
                  "pagina": 1,
                  "porPagina": 25,
                  "totalPaginas": 1
                }
                """.trimIndent()
            )
        )

        api.listarPagos(
            accessToken = "t",
            pagina = 1,
            porPagina = 25,
            texto = "ana",
            metodoPago = MetodosDePagoDePrestamo.EFECTIVO,
        )

        assertEquals(
            "/api/v1/prestamos/pagos?pagina=1&porPagina=25&q=ana&metodoPago=EFECTIVO",
            primera().path,
        )
    }

    @Test
    fun `un pago revertido se lee con su motivo sin borrarse`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${pagoDeBackend(revertido = true)}],
                  "total": 1,
                  "pagina": 1,
                  "porPagina": 25,
                  "totalPaginas": 1
                }
                """.trimIndent()
            )
        )

        val pago = api.listarPagosDePrestamo("t", "pre_1", 1, 25).datos.first()

        assertTrue(pago.revertido)
        assertEquals("Cobro duplicado", pago.motivoReversion)
        assertEquals("2026-10-09T11:00:00.000Z", pago.revertidoEn)
    }

    // ----------------------------------------------------------------
    // POST /prestamos/pagos/:id/revertir
    // ----------------------------------------------------------------

    @Test
    fun `revertirPago manda el motivo por POST a la ruta del pago`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"pago": ${pagoDeBackend(revertido = true)}, "prestamo": ${fichaDeBackend("pre_1")}}"""
            )
        )

        val resultado = api.revertirPago("t", pagoId = "pag_1", motivo = "Cobro duplicado")

        val peticion = primera()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/prestamos/pagos/pag_1/revertir", peticion.path)
        assertTrue(peticion.body.readUtf8().contains("\"motivo\":\"Cobro duplicado\""))

        assertTrue(resultado.pago.revertido)
        assertEquals("Cobro duplicado", resultado.pago.motivoReversion)
    }

    // ----------------------------------------------------------------
    // GET /cobros
    // ----------------------------------------------------------------

    @Test
    fun `listarCobros pide la categoria al servidor y lee el atraso`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${cobroDeBackend(diasDeAtraso = 4)}],
                  "total": 7,
                  "pagina": 1,
                  "porPagina": 25,
                  "totalPaginas": 1
                }
                """.trimIndent()
            )
        )

        val pagina = api.listarCobros("t", CategoriasDeCobro.HOY, 1, 25)

        assertEquals("/api/v1/cobros?categoria=HOY&pagina=1&porPagina=25", primera().path)
        assertEquals(7, pagina.total)

        val cobro = pagina.datos.first()
        assertEquals("cuo_1", cobro.id)
        assertEquals("pre_1", cobro.prestamoId)
        assertEquals("DIARIA", cobro.modalidad)
        assertEquals(1, cobro.numeroCuota)
        assertEquals(4, cobro.diasDeAtraso)
        assertEquals("PENDIENTE", cobro.estado)
        assertEquals(0, BigDecimal("102880.58").compareTo(cobro.valorEsperado))
        assertEquals(0, BigDecimal("102880.58").compareTo(cobro.saldo))
        assertEquals("María Fernanda Restrepo Villa", cobro.cliente?.nombreCompleto)
    }

    // ----------------------------------------------------------------
    // GET /prestamos/resumen
    // ----------------------------------------------------------------

    @Test
    fun `resumen lee los seis indicadores que calcula el servidor`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "prestamosActivos": 3,
                  "saldoPendienteTotal": 3700000.00,
                  "cobrosDeHoy": {"cantidad": 2, "valor": 200000.00},
                  "cuotasVencidas": {"cantidad": 1, "valor": 102880.58, "diasMaximosDeAtraso": 4},
                  "proximosCobros": {"cantidad": 5, "valor": 500000.00},
                  "recaudadoHoy": {"cantidad": 1, "valor": 100000.00}
                }
                """.trimIndent()
            )
        )

        val resumen = api.resumen("t")

        assertEquals("/api/v1/prestamos/resumen", primera().path)
        assertEquals(3, resumen.prestamosActivos)
        assertEquals(0, BigDecimal("3700000").compareTo(resumen.saldoPendienteTotal))
        assertEquals(2, resumen.cobrosDeHoy.cantidad)
        assertEquals(0, BigDecimal("200000").compareTo(resumen.cobrosDeHoy.valor))
        assertEquals(4, resumen.cuotasVencidas.diasMaximosDeAtraso)
        assertEquals(0, BigDecimal("102880.58").compareTo(resumen.cuotasVencidas.valor))
        assertEquals(5, resumen.proximosCobros.cantidad)
        assertEquals(1, resumen.recaudadoHoy.cantidad)
    }

    // ----------------------------------------------------------------
    // Comprobantes
    // ----------------------------------------------------------------

    @Test
    fun `adjuntarComprobante manda multipart con el campo archivo`() {

        servidor.enqueue(
            MockResponse().setBody(pagoDeBackend(conComprobante = true))
        )

        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        val pago = api.adjuntarComprobante(
            accessToken = "t",
            pagoId = "pag_1",
            nombre = "comprobante.jpg",
            mime = "image/jpeg",
            archivo = bytes,
        )

        val peticion = primera()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/prestamos/pagos/pag_1/comprobante", peticion.path)
        assertTrue(
            "El comprobante tiene que viajar como multipart, no como una ruta",
            peticion.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"),
        )

        val cuerpo = peticion.body.readUtf8()
        assertTrue("El campo se llama archivo", cuerpo.contains("name=\"archivo\""))
        assertTrue("Lleva el nombre del archivo", cuerpo.contains("comprobante.jpg"))

        assertEquals("pag_1", pago.id)
        assertEquals("prestamos/pagos/pag_1/comprobante", pago.comprobanteUrl)
        assertEquals("image/jpeg", pago.comprobanteMime)
    }

    @Test
    fun `descargarComprobante devuelve los bytes del archivo`() {

        val bytes = byteArrayOf(9, 8, 7, 6, 5)
        servidor.enqueue(MockResponse().setBody(Buffer().write(bytes)))

        val bajados = api.descargarComprobante("t", "pag_1")

        assertEquals("/api/v1/prestamos/pagos/pag_1/comprobante", primera().path)
        assertTrue(bytes.contentEquals(bajados))
    }

    @Test
    fun `quitarComprobante hace DELETE y devuelve el pago sin soporte`() {

        servidor.enqueue(MockResponse().setBody(pagoDeBackend()))

        val pago = api.quitarComprobante("t", "pag_1")

        val peticion = primera()
        assertEquals("DELETE", peticion.method)
        assertEquals("/api/v1/prestamos/pagos/pag_1/comprobante", peticion.path)
        assertNull(pago.comprobanteUrl)
    }

    // ----------------------------------------------------------------
    // Rechazos del servidor
    // ----------------------------------------------------------------

    @Test
    fun `un rechazo de pagos llega como ErrorDiarix con el mensaje del backend`() {

        servidor.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "codigo": "PAGO_SUPERA_SALDO",
                      "mensaje": "El pago no puede superar el saldo pendiente.",
                      "detalles": []
                    }
                    """.trimIndent()
                )
        )

        try {
            api.registrarPago(
                accessToken = "t",
                prestamoId = "pre_1",
                datos = DatosDePagoNuevo(
                    monto = BigDecimal("99999999"),
                    metodoPago = MetodosDePagoDePrestamo.EFECTIVO,
                    claveUnica = "clave-unica-3",
                ),
            )
            assertFail("Debió lanzar ErrorDiarix.DeBackend")
        } catch (e: ErrorDiarix.DeBackend) {
            assertEquals("PAGO_SUPERA_SALDO", e.codigo)
            assertEquals("El pago no puede superar el saldo pendiente.", e.mensajeAmigable)
        }
    }

    private fun assertFail(mensaje: String): Nothing {
        throw AssertionError(mensaje)
    }
}