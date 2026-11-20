package com.example.totaldiaria.network

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
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

/**
 * DIARIX · Fase G · El contrato real de `POST /auth/login` (y compañía).
 *
 * Mismo método que `ClientesApiTest`: se habla con un `MockWebServer` de
 * verdad porque lo que hay que fijar son la RUTA, el VERBO, los CAMPOS del
 * cuerpo y la LECTURA de la respuesta — exactamente lo que se rompió en
 * producción cuando la app apuntaba a un backend que no existía.
 *
 * Cubre los tres fallos que la app debe saber distinguir en el acceso:
 *  - 401 CREDENCIALES_INVALIDAS (credenciales malas),
 *  - 409 SESION_ACTIVA_EXISTENTE (contraseña buena pero ya hay sesión viva,
 *    p. ej. la sesión de la web abierta hace un minuto),
 *  - una respuesta que NO es de la API (HTML de un proxy/SPA con 405), que
 *    ApiCliente convierte en `HTTP_405` en vez de inventar una causa.
 *
 * Ninguna prueba escribe contra un backend real ni usa credenciales reales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthApiTest {

    private lateinit var servidor: MockWebServer
    private lateinit var api: AuthApi

    @Before
    fun setUp() {
        servidor = MockWebServer()
        servidor.start()
        api = AuthApi(ApiCliente(servidor.url("/api/v1").toString()))
    }

    @After
    fun tearDown() {
        servidor.shutdown()
    }

    private fun primera(): RecordedRequest = servidor.takeRequest()

    private fun RecordedRequest.json(): JSONObject =
        JSONObject(body.readUtf8())

    /** Respuesta 200 de `/auth/login` con la forma real del backend. */
    private fun sesionValida(): MockResponse = MockResponse().setResponseCode(200).setBody(
        """
        {
          "tokens": {
            "accessToken": "$ACCESS_DE_PRUEBA",
            "refreshToken": "$REFRESH_DE_PRUEBA",
            "expiraEnSegundos": 900,
            "tipo": "Bearer"
          },
          "usuario": {
            "id": "usr_1",
            "email": "ana@comercialnorte.co",
            "nombre": "Ana Restrepo",
            "rol": "DRIVER",
            "empresaId": "emp_1",
            "conductorId": "drv_1"
          },
          "empresa": {
            "id": "emp_1",
            "nit": "900123456",
            "nombre": "Comercializadora del Norte S.A.S.",
            "plan": "BUSINESS",
            "cupoConductores": 5
          },
          "cuentaNueva": false
        }
        """.trimIndent()
    )

    // =========================================================================
    // Ruta, verbo y cuerpo
    // =========================================================================

    @Test
    fun `el login empresarial va a auth login con email password y nit`() {
        servidor.enqueue(sesionValida())

        api.loginEmpresarial(
            nitEmpresa = "900123456",
            email = "ana@comercialnorte.co",
            password = "ComercialNorte2026"
        )

        val peticion = primera()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/auth/login", peticion.requestUrl!!.encodedPath)

        val cuerpo = peticion.json()
        assertEquals("ana@comercialnorte.co", cuerpo.getString("email"))
        assertEquals("ComercialNorte2026", cuerpo.getString("password"))
        assertEquals("900123456", cuerpo.getString("nitEmpresa"))
    }

    @Test
    fun `sin NIT no se manda el campo nitEmpresa`() {
        servidor.enqueue(sesionValida())

        api.loginEmpresarial(
            nitEmpresa = null,
            email = "ana@comercialnorte.co",
            password = "ComercialNorte2026"
        )

        assertFalse(primera().json().has("nitEmpresa"))
    }

    @Test
    fun `el login con Google va a auth google solo con idToken cuando no hay nonce`() {
        servidor.enqueue(sesionValida())

        api.loginGoogle("id-token-de-prueba")

        val peticion = primera()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/auth/google", peticion.requestUrl!!.encodedPath)

        val cuerpo = peticion.json()
        assertEquals("id-token-de-prueba", cuerpo.getString("idToken"))
        assertFalse("La web no manda nonce; no se debe inventar uno", cuerpo.has("nonce"))
    }

    @Test
    fun `el login con Google manda el nonce anti-replay cuando la pantalla lo genera`() {
        servidor.enqueue(sesionValida())

        api.loginGoogle("id-token-de-prueba", "nonce-seguro-123")

        val cuerpo = primera().json()
        assertEquals("id-token-de-prueba", cuerpo.getString("idToken"))
        assertEquals("nonce-seguro-123", cuerpo.getString("nonce"))
    }

    @Test
    fun `un nonce en blanco no se manda al backend`() {
        servidor.enqueue(sesionValida())

        api.loginGoogle("id-token-de-prueba", "   ")

        assertFalse(primera().json().has("nonce"))
    }

    // =========================================================================
    // Lectura de la respuesta (el backend es la autoridad)
    // =========================================================================

    @Test
    fun `lee rol empresa y tokens de la respuesta del backend`() {
        servidor.enqueue(sesionValida())

        val resuelto = api.loginEmpresarial(
            nitEmpresa = null,
            email = "ana@comercialnorte.co",
            password = "ComercialNorte2026"
        )

        assertEquals("DRIVER", resuelto.rol)
        assertEquals("ana@comercialnorte.co", resuelto.email)
        assertEquals(ACCESS_DE_PRUEBA, resuelto.tokens.accessToken)
        assertEquals(REFRESH_DE_PRUEBA, resuelto.tokens.refreshToken)
        assertEquals("emp_1", resuelto.empresa?.id)
        assertEquals("900123456", resuelto.empresa?.nit)
        assertEquals("BUSINESS", resuelto.empresa?.plan)
        assertEquals(5, resuelto.empresa?.cupoConductores)
        assertFalse(resuelto.cuentaNueva)
    }

    @Test
    fun `el refresh va a auth refresh con el refreshToken`() {
        servidor.enqueue(sesionValida())

        api.refrescar(REFRESH_DE_PRUEBA)

        val peticion = primera()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/auth/refresh", peticion.requestUrl!!.encodedPath)
        assertEquals(REFRESH_DE_PRUEBA, peticion.json().getString("refreshToken"))
    }

    @Test
    fun `el perfil va a auth yo con bearer token`() {
        servidor.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "usuario": {
                    "id": "usr_1",
                    "email": "ana@comercialnorte.co",
                    "nombre": "Ana Restrepo",
                    "rol": "DRIVER",
                    "emailVerificado": true
                  },
                  "empresa": { "id": "emp_1", "nit": "900123456", "nombre": "Comercial Norte" },
                  "conductorId": "drv_1",
                  "plan": {
                    "rol": "EMPRESA",
                    "plan": "BUSINESS",
                    "planVigente": true,
                    "facturasPorDia": 15,
                    "graficasAvanzadas": true
                  }
                }
                """.trimIndent()
            )
        )

        val perfil = api.yo(ACCESS_DE_PRUEBA)

        val peticion = primera()
        assertEquals("GET", peticion.method)
        assertEquals("/api/v1/auth/yo", peticion.requestUrl!!.encodedPath)
        assertEquals("Bearer $ACCESS_DE_PRUEBA", peticion.getHeader("Authorization"))

        assertEquals("DRIVER", perfil.rol)
        assertEquals("drv_1", perfil.conductorId)
        assertEquals(true, perfil.plan?.planVigente)
        assertEquals(15, perfil.plan?.facturasPorDia)
    }

    // =========================================================================
    // Errores: la app debe distinguirlos, no ocultarlos tras un genérico
    // =========================================================================

    @Test
    fun `credenciales invalidas se convierte en el 401 del backend`() {
        servidor.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """
                {"codigo":"CREDENCIALES_INVALIDAS","mensaje":"El correo o la contraseña no son correctos.","trazabilidad":null}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "mala") }

        assertTrue(error is ErrorDiarix.DeBackend)
        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.CREDENCIALES_INVALIDAS, error.codigo)
        assertEquals("El correo o la contraseña no son correctos.", error.mensajeAmigable)
    }

    @Test
    fun `sesion activa en otro dispositivo llega como 409 con mensaje legible`() {
        servidor.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """
                {"codigo":"SESION_ACTIVA_EXISTENTE","mensaje":"Tu cuenta ya tiene una sesión activa en otro dispositivo. Ciérrala desde ese dispositivo o espera unos minutos sin usarla antes de entrar aquí.","trazabilidad":null}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.SESION_ACTIVA_EXISTENTE, error.codigo)
        assertTrue(error.mensajeAmigable.contains("sesión activa en otro dispositivo"))
    }

    @Test
    fun `conductor sin suscripcion conserva el codigo y el mensaje del backend`() {
        servidor.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """
                {"codigo":"SUSCRIPCION_VENCIDA","mensaje":"La suscripción de tu empresa no está activa. Avísale a tu contador.","trazabilidad":null}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "conductor@comercialnorte.co", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.SUSCRIPCION_VENCIDA, error.codigo)
        assertEquals(
            "La suscripción de tu empresa no está activa. Avísale a tu contador.",
            error.mensajeAmigable
        )
    }

    @Test
    fun `un HTML en vez de JSON se trata como respuesta ajena con su estado`() {
        // Es lo que devuelve hoy un dominio que NO es la API (p. ej. la web
        // con su SPA): el cuerpo no es JSON, así que solo queda el estado.
        servidor.enqueue(
            MockResponse().setResponseCode(405).setBody("<!doctype html><html>…</html>")
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals("HTTP_405", error.codigo)
        assertTrue(error.mensajeAmigable.contains("HTTP_405"))
    }

    @Test
    fun `un 500 del servidor se nombra como fallo interno`() {
        servidor.enqueue(
            MockResponse().setResponseCode(500).setBody(
                """
                {"codigo":"ERROR_INTERNO","mensaje":"Ocurrió un error inesperado. Intenta de nuevo.","trazabilidad":"abc"}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.ERROR_INTERNO, error.codigo)
        assertTrue(error.mensajeAmigable.contains("problema interno"))
    }

    @Test
    fun `sin respuesta del servidor el error es de red y nunca una excepcion cruda`() {
        servidor.shutdown()

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        assertTrue(error is ErrorDiarix.Red)
        assertNull((error as ErrorDiarix).codigo)
    }

    // =========================================================================
    // Diagnóstico de conectividad: cada causa se distingue de las demás
    // =========================================================================

    @Test
    fun `un host que no resuelve por DNS es un fallo de red y nunca un 404`() {
        // UnknownHostException: el dominio no existe o el DNS no responde.
        // Es justo la causa raíz del incidente: la app apuntaba a un host
        // inalcanzable, así que la petición NUNCA llegaba al backend.
        val apiAlVacio = AuthApi(ApiCliente("https://host-que-no-existe.invalid/api/v1"))

        val error = falla { apiAlVacio.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        assertTrue(error is ErrorDiarix.Red)
        assertNull(error.codigo)
    }

    @Test
    fun `un puerto cerrado se reporta como red y no como error del backend`() {
        // ConnectException: el host resuelve pero nadie escucha. Se prueba con
        // un servidor levantado justo para conocer un puerto libre y luego
        // apagado, para no depender de un puerto fijo del entorno.
        servidor.shutdown()
        val puertoCerrado = servidor.port

        val apiCerrada = AuthApi(ApiCliente("http://127.0.0.1:$puertoCerrado/api/v1"))

        val error = falla { apiCerrada.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        assertTrue(error is ErrorDiarix.Red)
        assertNull(error.codigo)
    }

    @Test
    fun `el handshake TLS roto no se confunde con una API caida`() {
        // Un servidor que responde texto plano donde el cliente espera TLS
        // produce un fallo de seguridad (SSLException). La capa de transporte
        // lo agrupa como red, pero lo que se fija aquí es que NUNCA se
        // inventa un código de backend ni un mensaje de negocio.
        servidor.shutdown()
        val puertoPlano = servidor.port
        val servidorPlano = MockWebServer()
        servidorPlano.start(puertoPlano)

        try {
            val apiTls = AuthApi(ApiCliente("https://127.0.0.1:$puertoPlano/api/v1"))

            val error = falla { apiTls.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

            assertTrue(error is ErrorDiarix.Red)
        } finally {
            servidorPlano.shutdown()
        }
    }

    @Test
    fun `un 400 de validacion conserva el codigo y el mensaje del backend`() {
        // EntradaInvalidaError → 400. La app debe enseñar la razón real
        // (por ejemplo, un campo mal formado) y no un "sin conexión".
        servidor.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """
                {"codigo":"ENTRADA_INVALIDA","mensaje":"El correo no tiene un formato válido.","detalles":["email"]}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "correo-malo", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.ENTRADA_INVALIDA, error.codigo)
        assertEquals("El correo no tiene un formato válido.", error.mensajeAmigable)
        assertEquals(listOf("email"), error.detalles)
    }

    @Test
    fun `un 429 de demasiados intentos explica el limite y no la red`() {
        servidor.enqueue(
            MockResponse().setResponseCode(429).setBody(
                """
                {"codigo":"DEMASIADOS_INTENTOS","mensaje":"Demasiados intentos fallidos. Espera unos minutos."}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "mala") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.DEMASIADOS_INTENTOS, error.codigo)
        assertEquals("Demasiados intentos fallidos. Espera unos minutos.", error.mensajeAmigable)
    }

    @Test
    fun `una sesion activa en otro dispositivo se distingue de las credenciales malas`() {
        servidor.enqueue(
            MockResponse().setResponseCode(409).setBody(
                """
                {"codigo":"SESION_ACTIVA_EXISTENTE","mensaje":"Tu cuenta ya tiene una sesión abierta en otro dispositivo."}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.SESION_ACTIVA_EXISTENTE, error.codigo)
        assertTrue(error.mensajeAmigable.contains("sesión"))
    }

    @Test
    fun `un 403 por rol no autorizado conserva la causa del backend`() {
        servidor.enqueue(
            MockResponse().setResponseCode(403).setBody(
                """
                {"codigo":"NO_AUTORIZADO","mensaje":"Tu rol no tiene permiso para esta operación."}
                """.trimIndent()
            )
        )

        val error = falla { api.loginEmpresarial(null, "ana@comercialnorte.co", "buena") }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.NO_AUTORIZADO, error.codigo)
        assertEquals("Tu rol no tiene permiso para esta operación.", error.mensajeAmigable)
    }

    @Test
    fun `un JSON malformado en la respuesta de exito se reporta como inesperado y nunca como red`() {
        // El servidor respondió 200 pero con un cuerpo que no es JSON válido:
        // la app no puede leer los tokens. Ese caso NO es falta de internet y
        // quien capture la excepción debe poder saberlo.
        servidor.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("{\"tokens\": {\"accessToken\": \"a\", ")
        )

        var inesperado = false
        try {
            api.loginEmpresarial(null, "ana@comercialnorte.co", "buena")
        } catch (e: ErrorDiarix) {
            inesperado = e !is ErrorDiarix.Red
        } catch (e: org.json.JSONException) {
            inesperado = true
        }

        assertTrue("Un JSON roto no debe reportarse como fallo de red", inesperado)
    }

    // =========================================================================
    // Interno
    // =========================================================================

    /** Ejecuta y devuelve el [ErrorDiarix] lanzado; falla la prueba si no lanza. */
    private fun falla(bloque: () -> Any): ErrorDiarix {
        try {
            bloque()
        } catch (e: ErrorDiarix) {
            return e
        }
        throw AssertionError("Se esperaba un ErrorDiarix y no se lanzó ninguno.")
    }

    private companion object {
        /**
         * JWTs de pruebas: solo hace falta que `JwtUtil` pueda leer `exp`, y
         * para eso basta un payload con la reclamación. NINGÚN token real.
         */
        val ACCESS_DE_PRUEBA: String = jwtDePrueba("access")
        val REFRESH_DE_PRUEBA: String = jwtDePrueba("refresh")

        fun jwtDePrueba(tipo: String): String {
            val cabecera = JSONObject().put("alg", "HS256").toString().toByteArray()
            val expiraEnMs = System.currentTimeMillis() + 15 * 60 * 1000
            val payload = JSONObject()
                .put("sub", "usr_1")
                .put("typ", tipo)
                .put("exp", expiraEnMs / 1000)
                .toString()
                .toByteArray()

            fun base64(bytes: ByteArray) = android.util.Base64.encodeToString(
                bytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or
                    android.util.Base64.NO_PADDING
            )

            return "${base64(cabecera)}.${base64(payload)}.firma-de-pruebas"
        }
    }
}
