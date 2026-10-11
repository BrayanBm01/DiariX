package com.example.totaldiaria.network

import com.example.totaldiaria.sesion.PerfilEditable
import okhttp3.OkHttpClient
import okhttp3.Protocol
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
 * DIARIX · Fase 0.1 · El contrato real de `GET`/`PATCH /usuarios/yo`.
 *
 * ## Por qué un test de red contra el contrato del backend
 *
 * El riesgo de este par de llamadas no es que fallen: es que **funcionen a
 * medias**. El backend responde al PATCH con los nombres de la COLUMNA
 * (`fullName`, `phone`) y no con los del DTO (`nombreCompleto`, `telefono`),
 * porque es lo que devuelve Prisma con su `select`. Si la app reconstruyera la
 * ficha desde esa respuesta, el nombre volvería vacío y aparecería como que la
 * persona se quedó sin nombre.
 *
 * Estos tests fijan ese comportamiento: el PATCH se manda, y lo que se lee
 * después es el `GET`. No es una manía: es la única forma de que "lo que ves es
 * lo que el servidor tiene" siga siendo verdad.
 *
 * Se levanta un servidor de verdad (`MockWebServer`) en vez de doblar
 * `ApiCliente`, porque lo que hay que comprobar aquí son las RUTAS y el
 * VERBO, no la lógica de JSON.
 *
 * Corre bajo Robolectric y no como un test de JVM puro por un motivo concreto:
 * `ApiCliente` usa `org.json`, y en un test de JVM normal los métodos de
 * `JSONObject` vienen de un stub del SDK que **lanza excepción** en cuanto se
 * llama a `put`, `optString` o `toString`. Sin Robolectric, este archivo
 * entero fallaría con "Method toString in org.json.JSONObject not mocked",
 * que dice más del andamiaje de pruebas que del código que se quiere probar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsuariosApiTest {

    private lateinit var servidor: MockWebServer
    private lateinit var api: UsuariosApi

    @Before
    fun setUp() {
        servidor = MockWebServer()
        servidor.start()

        // Sin red real: el cliente va al 127.0.0.1 del servidor de arriba.
        api = UsuariosApi(ApiCliente(servidor.url("/api/v1").toString()))
    }

    @After
    fun tearDown() {
        servidor.shutdown()
    }

    private fun RecordedRequest.json(): JSONObject =
        JSONObject(body.readUtf8())

    private fun primera(): RecordedRequest = servidor.takeRequest()

    // ----------------------------------------------------------------
    // GET /usuarios/yo
    // ----------------------------------------------------------------

    @Test
    fun `leer el perfil devuelve los campos reales del backend`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": "usr_1",
                  "email": "ana@ejemplo.co",
                  "nombreCompleto": "Ana María Torres",
                  "telefono": "3001234567",
                  "rol": "ACCOUNTANT",
                  "estado": "ACTIVE",
                  "correoVerificado": true,
                  "ultimoAcceso": "2026-02-01T10:00:00.000Z",
                  "creadoEn": "2025-11-04T09:00:00.000Z",
                  "empresa": { "id": "emp_1", "nit": "901234567", "nombre": "Contabilidad Torres" },
                  "conductor": null,
                  "plan": { "plan": "PRO", "planVigente": true },
                  "capacidades": { "facturasPorDia": 200 }
                }
                """.trimIndent()
            )
        )

        val perfil = api.miPerfil("token-de-prueba")

        assertEquals("usr_1", perfil.id)
        assertEquals("ana@ejemplo.co", perfil.email)
        assertEquals("Ana María Torres", perfil.nombreCompleto)
        assertEquals("3001234567", perfil.telefonoEditable)
        assertEquals("ACCOUNTANT", perfil.rol)
        assertEquals("ACTIVE", perfil.estado)
        assertTrue(perfil.correoVerificado)
        assertEquals("Contabilidad Torres", perfil.empresaNombre)
        assertEquals("PRO", perfil.plan)
    }

    /**
     * El backend devuelve `empresa: null` y `plan: null` para un usuario
     * individual, que es el caso de quien entra con Google.
     *
     * Un `null` mal tratado se convierte en la palabra "null" o "NullPointer",
     * y eso es lo que vería la persona en su propia ficha.
     */
    @Test
    fun `un usuario sin empresa ni plan se lee con esos campos vacios`() {

        servidor.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": "usr_2",
                  "email": "individual@ejemplo.co",
                  "nombreCompleto": "Luis Solo",
                  "telefono": null,
                  "rol": "USER",
                  "estado": "ACTIVE",
                  "correoVerificado": true,
                  "empresa": null,
                  "conductor": null,
                  "plan": null
                }
                """.trimIndent()
            )
        )

        val perfil = api.miPerfil("token")

        assertNull(perfil.empresaNombre)
        assertNull(perfil.plan)
        assertNull(perfil.telefono)
        assertEquals("", perfil.telefonoEditable)
    }

    @Test
    fun `leer el perfil va a la ruta correcta con el token en la cabecera`() {

        servidor.enqueue(MockResponse().setBody("""{"id":"usr_1"}"""))

        api.miPerfil("mi-token")

        val peticion = primera()

        assertEquals("GET", peticion.method)
        assertEquals("/api/v1/usuarios/yo", peticion.path)
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))
    }

    // ----------------------------------------------------------------
    // PATCH /usuarios/yo
    // ----------------------------------------------------------------

    @Test
    fun `actualizar el perfil usa PATCH y manda solo lo que se cambia`() {

        servidor.enqueue(MockResponse().setBody("""{"id":"usr_1"}"""))
        servidor.enqueue(
            MockResponse().setBody(
                """{"id":"usr_1","email":"ana@ejemplo.co","nombreCompleto":"Ana Torres","telefono":null}"""
            )
        )

        api.actualizarMiPerfil(
            accessToken = "mi-token",
            nombreCompleto = "Ana Torres",
            telefono = null,
        )

        val peticion = primera()

        assertEquals(
            "El backend actualiza de forma parcial: tiene que ser PATCH",
            "PATCH",
            peticion.method
        )

        val cuerpo = peticion.json()

        assertEquals("Ana Torres", cuerpo.getString("nombreCompleto"))

        // `null` = "no lo toques". Mandarlo como `""` sería vaciar el teléfono
        // sin que nadie lo hubiera pedido.
        assertTrue(
            "Un campo null no debe viajar en el cuerpo del PATCH",
            !cuerpo.has("telefono")
        )
    }

    /**
     * Vaciar un campo y no tocarlo son dos peticiones DISTINTAS.
     *
     * Es la razón de que `telefono` sea nullable en la API: si la persona
     * borra el teléfono a propósito, viaja `""` (que el backend guarda como
     * vacío), no la ausencia de la clave.
     */
    @Test
    fun `vaciar un campo se manda como texto vacio y no como ausencia`() {

        servidor.enqueue(MockResponse().setBody("""{"id":"usr_1"}"""))
        servidor.enqueue(MockResponse().setBody("""{"id":"usr_1","nombreCompleto":"Ana Torres"}"""))

        api.actualizarMiPerfil(
            accessToken = "mi-token",
            nombreCompleto = null,
            telefono = "",
        )

        // Solo el PATCH tiene cuerpo: el `GET` que va detrás viene vacío y
        // no es el que hay que mirar.
        val cuerpo = primera().json()

        assertTrue(cuerpo.has("telefono"))
        assertEquals("", cuerpo.getString("telefono"))
        assertTrue(
            "Un campo null no debe viajar en el cuerpo del PATCH",
            !cuerpo.has("nombreCompleto")
        )
    }

    /**
     * El correo NO se manda nunca, porque el backend no lo acepta.
     *
     * `ActualizarPerfilDto` no tiene ese campo, así que mandarlo lo ignoraría
     * en silencio. Es mejor que no mandarlo: si algún día el backend lo acepta,
     * que el fallo sea visible en las pruebas y no un cambio aparente que el
     * servidor revierte a los cinco segundos.
     */
    @Test
    fun `el correo nunca se manda en el PATCH`() {

        servidor.enqueue(MockResponse().setBody("""{"id":"usr_1"}"""))
        servidor.enqueue(MockResponse().setBody("""{"id":"usr_1","nombreCompleto":"Ana Torres"}"""))

        api.actualizarMiPerfil(
            accessToken = "mi-token",
            nombreCompleto = "Ana Torres",
            telefono = null,
        )

        val cuerpo = primera().json()

        assertTrue(
            "El correo no es un campo editable: no debe mandarse nunca",
            !cuerpo.has("email")
        )
    }

    /**
     * Tras guardar, lo que se lee es un `GET` nuevo, no la respuesta del PATCH.
     *
     * El PATCH devuelve `{id, email, fullName, phone}` —los nombres de la
     * columna— y no `{nombreCompleto, telefono}`. Reconstruir la ficha desde ahí
     * dejaría el nombre vacío. La lectura nueva es lo que hace que la pantalla
     * enseñe lo que el servidor tiene de verdad.
     */
    @Test
    fun `despues de guardar se vuelve a leer el perfil entero`() {

        // 1) el PATCH
        servidor.enqueue(
            MockResponse().setBody(
                """{"id":"usr_1","email":"ana@ejemplo.co","fullName":"Ana Torres","phone":"300"}"""
            )
        )

        // 2) el GET, con los nombres del DTO
        servidor.enqueue(
            MockResponse().setBody(
                """{"id":"usr_1","email":"ana@ejemplo.co","nombreCompleto":"Ana Torres","telefono":"300"}"""
            )
        )

        val perfil = api.actualizarMiPerfil(
            accessToken = "mi-token",
            nombreCompleto = "Ana Torres",
            telefono = "300",
        )

        val patch = primera()
        val get = primera()

        assertEquals("PATCH", patch.method)
        assertEquals("GET", get.method)

        assertEquals(
            "Lo que se pinta tiene que ser lo del GET, no lo del PATCH",
            "Ana Torres",
            perfil.nombreCompleto
        )
        assertEquals("300", perfil.telefonoEditable)
    }

    /**
     * Un PATCH que falla NO se come el error.
     *
     * Si el backend rechaza el nombre por largo, la app tiene que enterarse y
     * poder avisar, no guardar un "guardado" que no ocurrió.
     */
    @Test
    fun `un PATCH rechazado se propaga como ErrorDiarix`() {

        servidor.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "codigo": "VALIDACION",
                      "mensaje": "El nombre es demasiado largo."
                    }
                    """.trimIndent()
                )
        )

        val fallo = try {
            api.actualizarMiPerfil("token", "x".repeat(500), null)
            null
        } catch (e: ErrorDiarix) {
            e
        }

        assertTrue(
            "Un PATCH rechazado debe lanzar ErrorDiarix, no devolverse en silencio",
            fallo is ErrorDiarix.DeBackend
        )

        assertEquals("VALIDACION", (fallo as ErrorDiarix.DeBackend).codigo)
    }

    // ----------------------------------------------------------------
    // POST /auth/logout-todos
    // ----------------------------------------------------------------

    @Test
    fun `cerrar la sesion en otros dispositivos va a la ruta correcta`() {

        servidor.enqueue(MockResponse().setBody("""{"sesionesRevocadas":3}"""))

        val cerradas = api.cerrarSesionEnTodosLosDispositivos("mi-token")

        val peticion = primera()

        assertEquals(3, cerradas)
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/auth/logout-todos", peticion.path)
        assertEquals("Bearer mi-token", peticion.getHeader("Authorization"))
    }

    @Test
    fun `sin sesiones que revocar el numero es cero y no un fallo`() {

        servidor.enqueue(MockResponse().setBody("""{"sesionesRevocadas":0}"""))

        assertEquals(0, api.cerrarSesionEnTodosLosDispositivos("token"))
    }

    /**
     * El número de sesiones se lee del nombre que devuelve el backend.
     *
     * Si el campo no viniera con ese nombre, `optInt` daría 0 y la app
     * diría "se cerraron 0 sesiones" aunque hubiera cerrado cinco, que es peor
     * que un error: el usuario cree que ha funcionado.
     */
    @Test
    fun `el numero de sesiones revocadas viene del backend y no se inventa`() {

        servidor.enqueue(MockResponse().setBody("""{"sesionesRevocadas":1}"""))

        assertEquals(1, api.cerrarSesionEnTodosLosDispositivos("token"))
    }

    // ----------------------------------------------------------------
    // El perfil se reconstruye bien a partir de la respuesta
    // ----------------------------------------------------------------

    @Test
    fun `un perfil sin ningun campo reconocible sale vacio y no con datos inventados`() {

        servidor.enqueue(MockResponse().setBody("""{}"""))

        val perfil = api.miPerfil("token")

        assertEquals("", perfil.email)
        assertEquals("", perfil.nombreCompleto)
        assertEquals("", perfil.telefonoEditable)
        assertNull(perfil.empresaNombre)
        assertNull(PerfilEditable.vacio().telefono)
    }
}