package com.example.totaldiaria.network

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.UnknownHostException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/**
 * DIARIX · Fase G · El cliente HTTP tiene que decir la VERDAD.
 *
 * El fallo real que motivó esta batería: en el teléfono, cualquier
 * `IOException` se mostraba como «No hay conexión con el servidor», sin
 * importar si era DNS, TLS, timeout o el servidor apagado. Estas pruebas
 * fijan que:
 *
 *  - un HTTP 4xx/5xx NUNCA se convierte en error de red (llegó respuesta);
 *  - un 503 con cuerpo de negocio conserva el código del backend;
 *  - un 503/502/504 sin cuerpo se explica como servidor iniciándose;
 *  - DNS, timeout, TLS y conexión rechazada producen motivos distintos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApiClienteTest {

    private lateinit var servidor: MockWebServer

    @Before
    fun setUp() {
        servidor = MockWebServer()
        servidor.start()
    }

    @After
    fun tearDown() {
        servidor.shutdown()
    }

    private fun api(
        base: String = servidor.url("/api/v1").toString(),
        cliente: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build(),
        hayInternet: () -> Boolean = { true },
    ) = ApiCliente(baseUrl = base, cliente = cliente, hayInternet = hayInternet)

    @Test
    fun `el registro envia el idToken a la ruta correcta con JSON y Bearer opcional`() {
        servidor.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))

        val cuerpo = JSONObject().put("idToken", "token-de-google")
        val respuesta = api().postJson("/auth/google", cuerpo = cuerpo)

        assertEquals("""{"ok":true}""", respuesta)

        val peticion = servidor.takeRequest()
        assertEquals("POST", peticion.method)
        assertEquals("/api/v1/auth/google", peticion.path)
        assertEquals("application/json; charset=utf-8", peticion.getHeader("Content-Type"))
        assertEquals("token-de-google", JSONObject(peticion.body.readUtf8()).getString("idToken"))
    }

    @Test
    fun `un 400 de validacion es error del backend y conserva sus detalles`() {
        servidor.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """{"codigo":"VALIDACION","mensaje":"Revisa los datos enviados.","detalles":["El idToken es obligatorio."]}"""
            )
        )

        val error = capturar { api().postJson("/auth/google", cuerpo = JSONObject()) }

        assertTrue(error is ErrorDiarix.DeBackend)
        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.VALIDACION, error.codigo)
        assertEquals(listOf("El idToken es obligatorio."), error.detalles)
    }

    @Test
    fun `un 401 de autenticacion es error del backend, no error de red`() {
        servidor.enqueue(
            MockResponse().setResponseCode(401).setBody(
                """{"codigo":"NO_AUTENTICADO","mensaje":"La sesión se cerró. Vuelve a iniciar sesión."}"""
            )
        )

        val error = capturar { api().getJson("/auth/yo", token = "caducado") }

        assertTrue(error is ErrorDiarix.DeBackend)
        assertEquals(CodigosDelBackend.NO_AUTENTICADO, (error as ErrorDiarix.DeBackend).codigo)
    }

    @Test
    fun `un 503 con GOOGLE_NO_DISPONIBLE conserva el mensaje del backend`() {
        servidor.enqueue(
            MockResponse().setResponseCode(503).setBody(
                """{"codigo":"GOOGLE_NO_DISPONIBLE","mensaje":"El acceso con Google no está configurado en el servidor: falta GOOGLE_CLIENT_ID."}"""
            )
        )

        val error = capturar { api().postJson("/auth/google", cuerpo = JSONObject().put("idToken", "x")) }

        error as ErrorDiarix.DeBackend
        assertEquals(CodigosDelBackend.GOOGLE_NO_DISPONIBLE, error.codigo)
        assertTrue(error.mensajeAmigable.contains("GOOGLE_CLIENT_ID"))
    }

    @Test
    fun `un 503 sin cuerpo de negocio explica que el servidor esta iniciando`() {
        servidor.enqueue(MockResponse().setResponseCode(503).setBody("<html>Service Unavailable</html>"))

        val error = capturar { api().getJson("/planes") }

        error as ErrorDiarix.DeBackend
        assertEquals("HTTP_503", error.codigo)
        assertTrue(error.mensajeAmigable.contains("iniciando"))
    }

    @Test
    fun `un timeout se reporta como timeout, no como falta de internet`() {
        servidor.enqueue(MockResponse().setBody("""{"ok":true}""").setBodyDelay(2, TimeUnit.SECONDS))

        val cliente = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(200, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()

        val error = capturar { api(cliente = cliente).getJson("/planes") }

        error as ErrorDiarix.Red
        assertEquals(MotivoRed.TIMEOUT, error.motivo)
        assertTrue(error.mensajeAmigable.contains("tardó demasiado"))
    }

    @Test
    fun `un DNS roto se distingue de un telefono sin internet`() {
        val sinDns = OkHttpClient.Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> =
                    throw UnknownHostException(hostname)
            })
            .connectTimeout(1, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.SECONDS)
            .build()

        val conInternet = capturar { api(cliente = sinDns, hayInternet = { true }).getJson("/planes") }
        val sinInternet = capturar { api(cliente = sinDns, hayInternet = { false }).getJson("/planes") }

        assertEquals(MotivoRed.DNS, (conInternet as ErrorDiarix.Red).motivo)
        assertEquals(MotivoRed.SIN_INTERNET, (sinInternet as ErrorDiarix.Red).motivo)
    }

    @Test
    fun `un certificado rechazado se reporta como problema de TLS`() {
        // Un SSLSocketFactory que falla el handshake de forma determinista:
        // no depende de la red ni de un certificado real.
        val fabrica = object : SSLSocketFactory() {
            override fun getDefaultCipherSuites(): Array<String> = emptyArray()

            override fun getSupportedCipherSuites(): Array<String> = emptyArray()

            override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket =
                throw SSLHandshakeException("certificado rechazado por la prueba")

            override fun createSocket(host: String?, port: Int): Socket =
                throw SSLHandshakeException("certificado rechazado por la prueba")

            override fun createSocket(
                host: String?,
                port: Int,
                localHost: InetAddress?,
                localPort: Int,
            ): Socket = throw SSLHandshakeException("certificado rechazado por la prueba")

            override fun createSocket(host: InetAddress?, port: Int): Socket =
                throw SSLHandshakeException("certificado rechazado por la prueba")

            override fun createSocket(
                address: InetAddress?,
                port: Int,
                localAddress: InetAddress?,
                localPort: Int,
            ): Socket = throw SSLHandshakeException("certificado rechazado por la prueba")
        }

        val confianza = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val cliente = OkHttpClient.Builder()
            .sslSocketFactory(fabrica, confianza)
            .connectTimeout(1, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()

        val error = capturar {
            api(base = "https://127.0.0.1:${servidor.port}/api/v1", cliente = cliente).getJson("/planes")
        }

        error as ErrorDiarix.Red
        assertEquals(MotivoRed.TLS, error.motivo)
        assertTrue(error.mensajeAmigable.contains("conexión segura"))
    }

    @Test
    fun `un puerto cerrado se reporta como servidor inalcanzable`() {
        val puerto = ServerSocket(0).use { it.localPort }
        val cliente = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        val error = capturar {
            api(base = "http://127.0.0.1:$puerto/api/v1", cliente = cliente).getJson("/planes")
        }

        error as ErrorDiarix.Red
        assertEquals(MotivoRed.SERVIDOR_NO_ALCANZABLE, error.motivo)
    }

    private fun capturar(bloque: () -> Unit): Throwable =
        try {
            bloque()
            throw AssertionError("Se esperaba un ErrorDiarix")
        } catch (e: ErrorDiarix) {
            e
        }
}
