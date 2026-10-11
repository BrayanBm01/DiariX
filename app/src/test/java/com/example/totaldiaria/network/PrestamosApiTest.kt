package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.prestamos.modelo.DatosDePrestamoNuevo
import com.example.totaldiaria.modulos.prestamos.modelo.EstadosDePrestamo
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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
 * DIARIX · Fase 3 · El contrato real de `GET/GET/POST /prestamos`.
 *
 * Mismo método que `ClientesApiTest`: se levanta un `MockWebServer` de verdad
 * y se habla con él, porque lo que hay que fijar son RUTAS, VERBO y CAMPOS —
 * y, en este módulo, que el DINERO viaja exacto (BigDecimal), no como Double.
 *
 * Dos riesgos propios de préstamos que aquí se pinchan a la primera:
 *
 *  1. Los montos deben llegar al servidor como tokens JSON exactos
 *     (`"monto":1000000.00`), nunca `1.0E6` ni `1000000.0` producto de
 *     redondear por el camino. Se comprueba sobre el CUERPO ENVIADO en
 *     bruto, no sobre el parseo (que volvería a pasar por Double).
 *
 *  2. `porcentaje` y `totalAPagar` son EXCLUYENTES (igual que en el backend):
 *     si el acuerdo es por TOTAL, el cuerpo no lleva porcentaje, y al revés.
 *     Mandar un `porcentaje: 0` "por si acaso" convertiría un préstamo por
 *     total en uno a 0 %, que el servicio rechaza.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrestamosApiTest {

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

    private fun cuerpoBruto(): String = primera().body.readUtf8()

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
          "modalidad": "MENSUAL",
          "tipoCalculo": "PORCENTAJE",
          "porcentaje": null,
          "fechaInicio": "2026-10-08",
          "montoPrestado": 1000000,
          "totalAPagar": 1200000,
          "valorCuota": 100000,
          "numeroDeCuotas": 12,
          "estado": "ACTIVO",
          "totalPagado": 0,
          "saldoPendiente": 1200000,
          "cuotasPagadas": 0,
          "cuotasPendientes": 12,
          "creadoEn": "2026-10-08T09:00:00.000Z",
          "actualizadoEn": "2026-10-08T09:00:00.000Z"
        }
    """.trimIndent()

    // ----------------------------------------------------------------
    // GET /prestamos (listado paginado)
    // ----------------------------------------------------------------

    @Test
    fun `el listado lee el sobre paginado y la ficha resumida`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${fichaDeBackend("pre_1")}],
                  "total": 127,
                  "pagina": 3,
                  "porPagina": 25,
                  "totalPaginas": 6
                }
                """.trimIndent()
            )
        )

        val pagina = api.listar("mi-token", pagina = 3, porPagina = 25)

        assertEquals(127, pagina.total)
        assertEquals(3, pagina.pagina)
        assertEquals(25, pagina.porPagina)
        assertEquals(6, pagina.totalPaginas)
        assertEquals(1, pagina.datos.size)

        val prestamo = pagina.datos.first()
        assertEquals("pre_1", prestamo.id)
        assertEquals("María Fernanda Restrepo Villa", prestamo.cliente?.nombreCompleto)
        assertEquals("CC", prestamo.cliente?.tipoDocumento)
        assertEquals("MENSUAL", prestamo.modalidad)
        assertEquals("PORCENTAJE", prestamo.tipoCalculo)
        assertEquals("2026-10-08", prestamo.fechaInicio)
        assertEquals(12, prestamo.numeroDeCuotas)
        assertEquals(EstadosDePrestamo.ACTIVO, prestamo.estado)
        assertEquals(12, prestamo.cuotasPendientes)

        // Dinero exacto, sin pasar por Double.
        assertEquals(0, BigDecimal("1000000").compareTo(prestamo.montoPrestado))
        assertEquals(0, BigDecimal("1200000").compareTo(prestamo.totalAPagar))
        assertEquals(0, BigDecimal("100000").compareTo(prestamo.valorCuota))

        // "Porcentaje: null" significa acuerdo por TOTAL o... solo el que
        // venga; aquí el fixture lo trae null y null debe leerse null, nunca 0.
        assertNull(prestamo.porcentaje)
    }

    @Test
    fun `un porcentaje que llega se lee exacto y con sus decimales`() {

        val conPorcentaje = fichaDeBackend("pre_2")
            .replace("\"porcentaje\": null,", "\"porcentaje\": 20,")

        servidor.enqueue(
            MockResponse().setBody(
                """
                {"datos": [$conPorcentaje], "total": 1, "pagina": 1, "porPagina": 25, "totalPaginas": 1}
                """.trimIndent()
            )
        )

        val porcentaje = api.listar("t", 1, 25).datos.first().porcentaje

        assertEquals(0, BigDecimal("20").compareTo(porcentaje))
    }

    @Test
    fun `el listado pide pagina tamano busqueda y estado derivado`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"datos":[],"total":0,"pagina":1,"porPagina":25,"totalPaginas":0}"""
            )
        )

        api.listar("mi-token", pagina = 2, porPagina = 50, texto = "Restrepo", estado = EstadosDePrestamo.VENCIDO)

        val peticion = primera()

        assertEquals("GET", peticion.method)
        assertEquals(
            "/api/v1/prestamos?pagina=2&porPagina=50&q=Restrepo&estado=VENCIDO",
            peticion.path
        )
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))
    }

    @Test
    fun `sin busqueda ni filtro el listado no manda campos vacios`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"datos":[],"total":0,"pagina":1,"porPagina":25,"totalPaginas":0}"""
            )
        )

        api.listar("t", 1, 25)

        assertEquals(
            "/api/v1/prestamos?pagina=1&porPagina=25",
            primera().path
        )
    }

    @Test
    fun `una pagina sin datos devuelve lista vacia no un error`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"datos":[],"total":0,"pagina":5,"porPagina":25,"totalPaginas":0}"""
            )
        )

        val pagina = api.listar("t", 5, 25)

        assertTrue(pagina.datos.isEmpty())
        assertEquals(0, pagina.total)
    }

    // ----------------------------------------------------------------
    // GET /prestamos/:id
    // ----------------------------------------------------------------

    @Test
    fun `la ficha se lee por su id y va a la ruta correcta`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                ${fichaDeBackend("pre_9")}
                """.trimIndent()
            )
        )

        val detalle = api.obtener("mi-token", "pre_9")

        assertEquals("/api/v1/prestamos/pre_9", primera().path)
        assertEquals("pre_9", detalle.prestamo.id)
        assertEquals(0, detalle.diasDeAtraso)
        assertEquals(0, detalle.numeroDeCuotasAtrasadas)
    }

    @Test
    fun `la ficha lee el calendario completo y el atraso derivado`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": "pre_v",
                  "empresaId": "emp_1",
                  "cliente": {
                    "id": "cli_1",
                    "nombreCompleto": "María Fernanda Restrepo Villa",
                    "tipoDocumento": "CC",
                    "numeroDocumento": "1020304050"
                  },
                  "modalidad": "MENSUAL",
                  "tipoCalculo": "PORCENTAJE",
                  "porcentaje": 20,
                  "fechaInicio": "2026-09-08",
                  "montoPrestado": 1000000,
                  "totalAPagar": 1200000,
                  "valorCuota": 100000,
                  "numeroDeCuotas": 12,
                  "estado": "VENCIDO",
                  "totalPagado": 0,
                  "saldoPendiente": 1200000,
                  "cuotasPagadas": 0,
                  "cuotasPendientes": 12,
                  "creadoEn": "2026-09-08T09:00:00.000Z",
                  "actualizadoEn": "2026-10-08T09:00:00.000Z",
                  "nota": null,
                  "diasDeAtraso": 30,
                  "numeroDeCuotasAtrasadas": 1,
                  "valorAtrasado": 100000,
                  "cuotas": [
                    {
                      "numero": 1,
                      "fechaVencimiento": "2026-10-08",
                      "valorEsperado": 100000,
                      "valorPagado": 0,
                      "saldo": 100000,
                      "estado": "VENCIDA"
                    },
                    {
                      "numero": 2,
                      "fechaVencimiento": "2026-11-08",
                      "valorEsperado": 100000,
                      "valorPagado": 0,
                      "saldo": 100000,
                      "estado": "PENDIENTE"
                    }
                  ]
                }
                """.trimIndent()
            )
        )

        val detalle = api.obtener("t", "pre_v")

        assertEquals(30, detalle.diasDeAtraso)
        assertEquals(1, detalle.numeroDeCuotasAtrasadas)
        assertEquals(0, BigDecimal("100000").compareTo(detalle.valorAtrasado))
        assertNull(detalle.nota)

        val cuotas = detalle.cuotas
        assertEquals(2, cuotas.size)

        val primeraCuota = cuotas[0]
        assertEquals(1, primeraCuota.numero)
        assertEquals("2026-10-08", primeraCuota.fechaVencimiento)
        assertEquals(EstadosDePrestamo.VENCIDA, primeraCuota.estado)
        assertEquals(0, BigDecimal("100000").compareTo(primeraCuota.valorEsperado))
        assertEquals(0, BigDecimal.ZERO.compareTo(primeraCuota.valorPagado))
    }

    // ----------------------------------------------------------------
    // POST /prestamos (alta)
    // ----------------------------------------------------------------

    @Test
    fun `crear por porcentaje manda el cuerpo excluyente y omite el total`() {

        servidor.enqueue(MockResponse().setBody(fichaDeBackend("pre_nuevo")))

        api.crear(
            "mi-token",
            DatosDePrestamoNuevo(
                clienteId = "cli_1",
                fechaInicio = "2026-10-08",
                monto = BigDecimal("1000000"),
                modalidad = "MENSUAL",
                numeroDeCuotas = 12,
                tipoCalculo = "PORCENTAJE",
                porcentaje = BigDecimal("20"),
            ),
        )

        val peticion = primera()
        val cuerpo = peticion.body.readUtf8()

        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/prestamos", peticion.path)
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))

        // El dinero viaja EXACTO en el cuerpo bruto. Un importe sin centavos
        // lo serializa `org.json` como Long ("1000000", que el backend lee
        // como 1000000.00); los centavos viajan como decimal (otro test lo
        // comprueba). Lo que NUNCA puede aparecer es 1.0E6 ni un redondeo.
        assertTrue(cuerpo.contains("\"monto\":1000000"))
        assertTrue(cuerpo.contains("\"porcentaje\":20"))

        assertTrue(cuerpo.contains("\"clienteId\":\"cli_1\""))
        assertTrue(cuerpo.contains("\"fechaInicio\":\"2026-10-08\""))
        assertTrue(cuerpo.contains("\"modalidad\":\"MENSUAL\""))
        assertTrue(cuerpo.contains("\"numeroDeCuotas\":12"))
        assertTrue(cuerpo.contains("\"tipoCalculo\":\"PORCENTAJE\""))
        assertFalse(cuerpo.contains("totalAPagar"))
    }

    @Test
    fun `crear por total manda el total y omite el porcentaje`() {

        servidor.enqueue(MockResponse().setBody(fichaDeBackend("pre_total")))

        api.crear(
            "t",
            DatosDePrestamoNuevo(
                clienteId = "cli_1",
                fechaInicio = "2026-10-08",
                monto = BigDecimal("1000000"),
                modalidad = "DIARIO",
                numeroDeCuotas = 20,
                tipoCalculo = "TOTAL",
                totalAPagar = BigDecimal("1200000"),
            ),
        )

        val cuerpo = cuerpoBruto()

        assertTrue(cuerpo.contains("\"monto\":1000000"))
        assertTrue(cuerpo.contains("\"totalAPagar\":1200000"))
        assertTrue(cuerpo.contains("\"modalidad\":\"DIARIO\""))
        assertTrue(cuerpo.contains("\"numeroDeCuotas\":20"))
        assertTrue(cuerpo.contains("\"tipoCalculo\":\"TOTAL\""))
        assertFalse(cuerpo.contains("porcentaje"))
    }

    @Test
    fun `los centavos viajan exactos sin pasar por coma flotante`() {

        servidor.enqueue(MockResponse().setBody(fichaDeBackend("pre_centavos")))

        api.crear(
            "t",
            DatosDePrestamoNuevo(
                clienteId = "cli_1",
                fechaInicio = "2026-10-08",
                monto = BigDecimal("1000000.55"),
                modalidad = "QUINCENAL",
                numeroDeCuotas = 3,
                tipoCalculo = "PORCENTAJE",
                porcentaje = BigDecimal("20.5"),
            ),
        )

        val cuerpo = cuerpoBruto()

        // 1000000.55 no es un Long: `org.json` lo emite como decimal, y ese
        // decimal tiene que ser el mismo del BigDecimal, no 1000000.549999...
        assertTrue(cuerpo.contains("\"monto\":1000000.55"))
        assertTrue(cuerpo.contains("\"porcentaje\":20.5"))
    }
}