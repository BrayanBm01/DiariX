package com.example.totaldiaria

import android.content.Context
import com.example.totaldiaria.modulos.facturacion.repositorio.ClientesRepositorio
import com.example.totaldiaria.modulos.facturacion.repositorio.FacturaRepository
import com.example.totaldiaria.modulos.prestamos.repositorio.PrestamosRepositorio
import com.example.totaldiaria.network.ApiCliente
import com.example.totaldiaria.network.AuthApi
import com.example.totaldiaria.network.ClientesApi
import com.example.totaldiaria.network.FacturasApi
import com.example.totaldiaria.network.PrestamosApi
import com.example.totaldiaria.network.UsuariosApi
import com.example.totaldiaria.sesion.AlmacenSeguro
import com.example.totaldiaria.sesion.SessionManager
import com.example.totaldiaria.sesion.TokenStoreSeguro
import com.example.totaldiaria.modulos.facturacion.sincronizacion.EstadoDeRed
import com.example.totaldiaria.modulos.facturacion.sincronizacion.EstadoDeRedDeAndroid
import com.example.totaldiaria.modulos.facturacion.sincronizacion.RepositorioSyncDeFacturas
import com.example.totaldiaria.modulos.facturacion.sincronizacion.SincronizadorFacturas
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Punto único de construcción de la capa DiariX (Fase 4).
 *
 * La app no tiene Application class: las actividades se reparten entre el
 * login, la pantalla de inicio de facturación y las del navegador inferior;
 * aquí se crea una sola vez el grafo de objetos y el hilo de fondo que
 * ejecuta red/sesión/sincronización fuera del hilo de UI.
 *
 * Fase 0: este grafo es solo de la capa de DATOS y cubre hoy únicamente el
 * área de facturación (facturas, cola de sincronización, estadísticas). El
 * área de préstamos, cuando tenga repositorios propios, se registrará aquí
 * como un bloque más, con sus servicios y repositorios, sin tocar lo que ya
 * funciona. El área en la que está la persona no se guarda aquí: eso vive en
 * `AlmacenDeModo`, porque es una decisión de navegación y no de datos.
 */
object Diarix {

    @Volatile
    private var inicializado = false

    lateinit var sesion: SessionManager
        private set

    lateinit var sincronizador: SincronizadorFacturas
        private set

    lateinit var red: EstadoDeRed
        private set

    /**
     * Fase 2 · Padrón de clientes.
     *
     * Se construye en `iniciar` junto con el resto de la capa de datos y se
     * expone solo a las pantallas: nadie crea su propia instancia de
     * [ClientesApi], así que toda la red de clientes pasa por el mismo
     * `OkHttp` (mismos timeouts, mismo manejo de errores) que el resto.
     */
    lateinit var clientes: ClientesRepositorio
        private set

    /**
     * Fase 3 · Préstamos.
     *
     * Misma regla que `clientes`: se construye en `iniciar` junto con el
     * resto de la capa de datos y se expone a las pantallas. Toda la red de
     * préstamos pasa por el mismo `OkHttp` (mismo manejo de timeouts y de
     * errores) y por la misma sesión que el resto de la app.
     */
    lateinit var prestamos: PrestamosRepositorio
        private set

    private lateinit var ejecutor: ExecutorService

    val enHiloDeFondo: (() -> Unit) -> Unit
        get() = { bloque -> ejecutor.execute(bloque) }

    @Synchronized
    fun iniciar(context: Context) {
        if (inicializado) return

        val app = context.applicationContext

        val almacen = AlmacenSeguro(app)
        val api = ApiCliente(BuildConfig.DIARIX_BASE_URL)

        val authApi = AuthApi(api)
        red = EstadoDeRedDeAndroid(app)

        sesion = SessionManager(
            authApi = authApi,
            almacen = TokenStoreSeguro(almacen),
            // Fase 0.1: la ficha de "Mi perfil" (GET/PATCH /usuarios/yo) y el
            // cierre de sesión en todos los dispositivos. Se construyen aquí y
            // no dentro de SessionManager para que la sesión siga siendo
            // testeable con un doble, igual que ya lo era con AuthApi.
            usuariosApi = UsuariosApi(api),
        )

        // Fase 2 · Clientes. Va después de `sesion` porque el repositorio
        // saca el token de ahí en cada llamada.
        clientes = ClientesRepositorio(ClientesApi(api), sesion)

        // Fase 3 · Préstamos: mismo patrón; el repositorio toma el token de la
        // sesión en cada llamada y comparte el OkHttp de la app.
        prestamos = PrestamosRepositorio(PrestamosApi(api), sesion)

        val facturasApi = FacturasApi(api)

        sincronizador = SincronizadorFacturas(
            facturasApi = facturasApi,
            repositorio = RepositorioSyncDeFacturas(FacturaRepository(app)),
            sesion = sesion,
            red = red,
        )

        ejecutor = Executors.newSingleThreadExecutor()

        // Auto-sincronización al recuperar o perder la conexión: el
        // sincronizador decide si hay algo que subir y si vale la pena.
        red.observarCambios {
            if (::sincronizador.isInitialized) {
                enHiloDeFondo { sincronizador.sincronizarPendientes() }
            }
        }

        inicializado = true
    }

    fun tieneSesion(): Boolean =
        ::sesion.isInitialized && sesion.tieneSesion()
}