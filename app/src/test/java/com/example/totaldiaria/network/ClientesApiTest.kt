package com.example.totaldiaria.network

import com.example.totaldiaria.modulos.facturacion.modelo.DatosDeCliente
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DIARIX · Fase 2 · El contrato real de `GET/POST/PATCH/DELETE /clientes`.
 *
 * Mismo método que `UsuariosApiTest`: se levanta un `MockWebServer` de verdad
 * y se habla con él, porque lo que hay que fijar son las RUTAS, el VERBO y los
 * CAMPOS, no la lógica de JSON. Así, si el backend cambia de nombre un campo,
 * la app lo nota aquí antes que en un dispositivo.
 *
 * Dos riesgos propios de este módulo y de por qué importa probarlos:
 *
 *  1. Los campos opcionales (`email`, `telefono`, ...) llegan como `null` de
 *     JSON. `optString` de Android convierte ese `null` en la CADENA "null",
 *     y la ficha mostraría "teléfono: null" como un dato real. Aquí se
 *     comprueba que un `null` se lee como `null`.
 *
 *  2. El borrado existe como verbo `DELETE` que `ApiCliente` no tenía. Se
 *     prueba que el método va solo (sin cuerpo), porque un DELETE con cuerpo
 *     es una inversión de la semántica que OkHttp acepta pero el backend no.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClientesApiTest {

    private lateinit var servidor: MockWebServer
    private lateinit var api: ClientesApi

    @Before
    fun setUp() {
        servidor = MockWebServer()
        servidor.start()
        api = ClientesApi(ApiCliente(servidor.url("/api/v1").toString()))
    }

    @After
    fun tearDown() {
        servidor.shutdown()
    }

    private fun primera(): RecordedRequest = servidor.takeRequest()

    private fun RecordedRequest.json(): JSONObject =
        JSONObject(body.readUtf8())

    private fun fichaDeBackend(id: String): String = """
        {
          "id": "$id",
          "empresaId": "emp_1",
          "nombres": "María Fernanda",
          "apellidos": "Restrepo Villa",
          "nombreCompleto": "María Fernanda Restrepo Villa",
          "tipoDocumento": "CC",
          "numeroDocumento": "1020304050",
          "email": "maria@correo.co",
          "telefono": null,
          "direccion": "Cra 10 # 20-30",
          "referencias": null,
          "observaciones": "Prefiere llamadas en la mañana.",
          "creadoEn": "2026-10-01T09:00:00.000Z",
          "actualizadoEn": "2026-10-01T09:00:00.000Z"
        }
    """.trimIndent()

    // ----------------------------------------------------------------
    // GET /clientes (listado paginado)
    // ----------------------------------------------------------------

    @Test
    fun `el listado lee el sobre paginado del servidor`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${fichaDeBackend("cli_1")}],
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

        val cliente = pagina.datos.first()
        assertEquals("cli_1", cliente.id)
        assertEquals("María Fernanda", cliente.nombres)
        assertEquals("Restrepo Villa", cliente.apellidos)
        assertEquals("María Fernanda Restrepo Villa", cliente.nombreCompleto)
        assertEquals("CC", cliente.tipoDocumento)
        assertEquals("1020304050", cliente.numeroDocumento)
        assertEquals("maria@correo.co", cliente.email)
        assertEquals("Cra 10 # 20-30", cliente.direccion)
        assertEquals("Prefiere llamadas en la mañana.", cliente.observaciones)
    }

    /**
     * El peor fallo de este módulo: un `null` de JSON que se lee como la
     * palabra "null". Aquí se fija que `telefono` y `referencias`, que vienen
     * como `null`, lleguen como `null` y no ensucien la tarjeta del listado.
     */
    @Test
    fun `un campo opcional null se lee como null y no como la cadena null`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "datos": [${fichaDeBackend("cli_1")}],
                  "total": 1, "pagina": 1, "porPagina": 25, "totalPaginas": 1
                }
                """.trimIndent()
            )
        )

        val cliente = api.listar("t", 1, 25).datos.first()

        assertNull(cliente.telefono)
        assertNull(cliente.referencias)
        assertEquals("maria@correo.co", cliente.email)
    }

    @Test
    fun `el listado pide la pagina el tamano y la busqueda`() {

        servidor.enqueue(
            MockResponse().setBody(
                """{"datos":[],"total":0,"pagina":1,"porPagina":25,"totalPaginas":0}"""
            )
        )

        api.listar("mi-token", pagina = 2, porPagina = 50, texto = "Restrepo  V")

        val peticion = primera()

        assertEquals("GET", peticion.method)
        assertEquals(
            // `URLEncoder` escribe los espacios como `+` (codificación
            // application/x-www-form-urlencoded), y Express los decodifica
            // como espacios: "Restrepo  V" vuelve íntegro al servidor.
            "/api/v1/clientes?pagina=2&porPagina=50&q=Restrepo++V",
            peticion.path
        )
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))
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
    // GET /clientes/:id
    // ----------------------------------------------------------------

    @Test
    fun `la ficha se lee por su id y va a la ruta correcta`() {

        servidor.enqueue(MockResponse().setBody(fichaDeBackend("cli_9")))

        val cliente = api.obtener("mi-token", "cli_9")

        assertEquals("/api/v1/clientes/cli_9", primera().path)
        assertEquals("cli_9", cliente.id)
    }

    // ----------------------------------------------------------------
    // POST /clientes
    // ----------------------------------------------------------------

    @Test
    fun `el alta usa POST y manda los nueve campos con null en los vacios`() {

        servidor.enqueue(MockResponse().setBody(fichaDeBackend("cli_nuevo")))

        val datos = DatosDeCliente(
            nombres = "Mario",
            apellidos = "Gómez",
            tipoDocumento = "CC",
            numeroDocumento = "798132",
            email = null,
            telefono = "3001112233",
            direccion = null,
            referencias = "Margarita, su hermana",
            observaciones = null,
        )

        api.crear("mi-token", datos)

        val peticion = primera()
        val cuerpo = peticion.json()

        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/clientes", peticion.path)
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))

        assertEquals("Mario", cuerpo.getString("nombres"))
        assertEquals("CC", cuerpo.getString("tipoDocumento"))
        assertEquals("3001112233", cuerpo.getString("telefono"))
        assertEquals("Margarita, su hermana", cuerpo.getString("referencias"))

        // Una casilla vacía no viaja como "" ni falta: viaja como null, que
        // es lo que el backend entiende como "sin dato". (String(null) por
        // comparar contra `null` sin tocar el stub de org.json.)
        assertTrue(cuerpo.isNull("email"))
        assertTrue(cuerpo.isNull("direccion"))
        assertTrue(cuerpo.isNull("observaciones"))
    }

    // ----------------------------------------------------------------
    // PATCH /clientes/:id
    // ----------------------------------------------------------------

    @Test
    fun `la edicion usa PATCH hacia la ruta del cliente y manda la ficha`() {

        servidor.enqueue(MockResponse().setBody(fichaDeBackend("cli_1")))

        api.actualizar(
            accessToken = "mi-token",
            id = "cli_1",
            datos = DatosDeCliente(
                nombres = "María Fernanda",
                apellidos = "Restrepo Villa",
                tipoDocumento = "CC",
                numeroDocumento = "1020304050",
                email = "maria@correo.co",
                telefono = null,
                direccion = "Cra 10 # 20-30",
                referencias = null,
                observaciones = "Prefiere llamadas en la mañana.",
            ),
        )

        val peticion = primera()
        val cuerpo = peticion.json()

        assertEquals("PATCH", peticion.method)
        assertEquals("/api/v1/clientes/cli_1", peticion.path)
        assertEquals("maria@correo.co", cuerpo.getString("email"))
        assertTrue(cuerpo.isNull("telefono"))
    }

    // ----------------------------------------------------------------
    // DELETE /clientes/:id
    // ----------------------------------------------------------------

    @Test
    fun `el borrado usa DELETE sin cuerpo`() {

        servidor.enqueue(MockResponse().setBody("""{"eliminado":true}"""))

        api.eliminar("mi-token", "cli_1")

        val peticion = primera()

        assertEquals("DELETE", peticion.method)
        assertEquals("/api/v1/clientes/cli_1", peticion.path)
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))
        assertEquals(
            "Un DELETE va sin cuerpo",
            "",
            peticion.body.readUtf8()
        )
    }

    // ----------------------------------------------------------------
    // Los errores del backend llegan con código y detalles
    // ----------------------------------------------------------------

    @Test
    fun `un 404 se propaga como ErrorDiarix con codigo NO_ENCONTRADO`() {

        servidor.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"codigo":"NO_ENCONTRADO","mensaje":"El cliente no existe."}""")
        )

        val fallo = try {
            api.obtener("t", "cli_perdido")
            null
        } catch (e: ErrorDiarix) {
            e
        }

        assertEquals("NO_ENCONTRADO", (fallo as ErrorDiarix.DeBackend).codigo)
        assertEquals("El cliente no existe.", fallo.mensaje)
    }

    @Test
    fun `un 400 de validacion conserva los detalles para el formulario`() {

        servidor.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """
                    {
                      "codigo": "VALIDACION",
                      "mensaje": "Revisa los datos enviados.",
                      "detalles": [
                        "El correo no tiene una forma válida.",
                        "Los nombres deben tener al menos 3 caracteres."
                      ]
                    }
                    """.trimIndent()
                )
        )

        val fallo = try {
            api.crear(
                "t",
                DatosDeCliente(
                    nombres = "A",
                    apellidos = "B",
                    tipoDocumento = "CC",
                    numeroDocumento = "1",
                    email = "no-es-correo",
                ),
            )
            null
        } catch (e: ErrorDiarix) {
            e
        }

        val deBackend = fallo as ErrorDiarix.DeBackend

        assertEquals("VALIDACION", deBackend.codigo)
        assertEquals(2, deBackend.detalles.size)
        assertTrue(
            "El formulario muestra el detalle, no solo el resumen",
            deBackend.detalles.first().contains("correo")
        )
    }
}