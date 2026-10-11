package com.example.totaldiaria.modulos.facturacion.ui

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.BuildConfig
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.LoginActivity
import com.example.totaldiaria.R
import com.example.totaldiaria.core.cuenta.AcercaDeActivity
import com.example.totaldiaria.core.cuenta.AyudaActivity
import com.example.totaldiaria.core.cuenta.EditarPerfilActivity
import com.example.totaldiaria.core.modo.ModoDiarix
import com.example.totaldiaria.core.modo.SelectorDeModo
import com.example.totaldiaria.core.modo.modoVigente
import com.example.totaldiaria.core.navegacion.BottomNavigator
import com.example.totaldiaria.modulos.facturacion.preferencias.QrManager
import com.example.totaldiaria.modulos.facturacion.servicio.RespaldoService
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.sesion.PerfilEditable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DIARIX · Fase 0.1 · Configuración como pantalla de verdad.
 *
 * ## Qué cambió respecto a antes
 *
 * Antes eran cuatro botones del mismo tamaño apilados en tarjetas sin jerarquía:
 * "Seleccionar QR", "Exportar respaldo", "Importar respaldo" y "Cerrar sesión".
 * Cuatro acciones idénticas visualmente, sin ninguna pista de cuál se usa a
 * diario y cuál deshace el trabajo del usuario. Ahora la pantalla está
 * organizada por SECCIONES y cada sección tiene un peso:
 *
 *  - **Perfil** — quién eres y qué datos tuyos se pueden cambiar.
 *  - **Cuenta** — el área de trabajo de DiariX (el cambio de área) y lo que
 *    tiene que ver con la sesión.
 *  - **Comprobantes** — el QR que se imprime en las facturas.
 *  - **Tus datos** — el respaldo de la información local.
 *  - **Información** — Ayuda, Acerca de y la versión real instalada.
 *  - **Cerrar sesión** — sola, al final, con el color del error.
 *
 * ## Lo que NO aparece, y por qué
 *
 * No hay sección de Apariencia ni de Notificaciones. DiariX no tiene sistema de
 * temas ni notificaciones, así que un interruptor de "tema oscuro" o de "avisar
 * por cierres" sería un control que se puede tocar y no hace nada. Es peor que
 * no ponerlo: entrena al usuario a desconfiar de los controles que sí funcionan.
 * Cuando existan, se añaden (ver `docs/FASE-0-SEPARACION-DE-MODOS.md`).
 *
 * ## El cambio de área vive AQUÍ
 *
 * Y solo aquí. La tira de área que se veía en todas las pantallas se retiró en
 * la Fase 0.1 precisamente por eso: cambiar de área era un gesto disponible en
 * cualquier sitio, lo que hace que el área vigente cambie sin querer y que el
 * "atrás" devuelva a una pantalla del área que ya se abandonó.
 *
 * Al cambiar desde aquí el diálogo llama a [SelectorDeModo.mostrarComoDialogo],
 * que acaba en [SelectorDeModo.elegirYEntrar]: se guarda el área, se abre su
 * inicio con `CLEAR_TASK` y la pantalla de Configuración desaparece de la pila.
 * Por eso el "atrás" no puede devolver a la pantalla anterior, que era del área
 * antigua.
 *
 * ## Alcance de los datos
 *
 * Se pinta primero lo que la sesión ya tiene en memoria (para que la pantalla
 * no aparezca vacía ni un instante) y después se sustituye por lo que devuelve
 * `GET /usuarios/yo`. Si la red falla se queda con lo de la sesión: es mejor
 * mostrar el nombre que se conoce que dejar la ficha en blanco.
 */
class ConfiguracionActivity : AppCompatActivity() {

    private lateinit var qrManager: QrManager
    private lateinit var respaldoService: RespaldoService

    private lateinit var txtIniciales: TextView
    private lateinit var txtNombrePerfil: TextView
    private lateinit var txtCorreoPerfil: TextView
    private lateinit var txtTelefonoPerfil: TextView
    private lateinit var etiquetaGoogle: TextView
    private lateinit var txtModoActual: TextView
    private lateinit var barraModo: View
    private lateinit var txtEstadoSesion: TextView
    private lateinit var txtEstadoQr: TextView
    private lateinit var txtVersion: TextView

    private var dialogoProgreso: AlertDialog? = null

    /**
     * La ficha detallada se pide UNA vez por apertura de la pantalla.
     *
     * Sin esta marca, `onResume` pediría el perfil cada vez que se vuelve de
     * editarlo o de ayuda, que es una llamada de red inútil y un parpadeo.
     */
    private var perfilSolicitado = false

    private val seleccionarQr =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->

            uri?.let {

                val guardado =
                    qrManager.guardarQr(it)

                mostrarEstadoQr(guardado)
            }
        }

    private val elegirDestinoExportacion =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri: Uri? ->

            if (uri != null) {
                exportarRespaldo(uri)
            }
        }

    private val elegirOrigenImportacion =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->

            if (uri != null) {
                prepararImportacion(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_configuracion)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        Diarix.iniciar(this)

        qrManager = QrManager(this)

        respaldoService = RespaldoService(this)

        txtIniciales = findViewById(R.id.txtIniciales)
        txtNombrePerfil = findViewById(R.id.txtNombrePerfil)
        txtCorreoPerfil = findViewById(R.id.txtCorreoPerfil)
        txtTelefonoPerfil = findViewById(R.id.txtTelefonoPerfil)
        etiquetaGoogle = findViewById(R.id.etiquetaGoogle)
        txtModoActual = findViewById(R.id.txtModoActual)
        barraModo = findViewById(R.id.barraModo)
        txtEstadoSesion = findViewById(R.id.txtEstadoSesion)
        txtEstadoQr = findViewById(R.id.txtEstadoQr)
        txtVersion = findViewById(R.id.txtVersion)

        mostrarEstadoQr(qrManager.existeQr())
        mostrarModo()
        mostrarSesion()
        mostrarVersion()
        pintarPerfilDesdeLaSesion()

        // ------------------------------------------------------------------
        // PERFIL
        // ------------------------------------------------------------------

        findViewById<View>(R.id.filaEditarPerfil).setOnClickListener {
            startActivity(Intent(this, EditarPerfilActivity::class.java))
        }

        // ------------------------------------------------------------------
        // CUENTA
        // ------------------------------------------------------------------

        findViewById<View>(R.id.filaModo).setOnClickListener {
            SelectorDeModo.mostrarComoDialogo(this)
        }

        findViewById<View>(R.id.filaCerrarSesionTodos).setOnClickListener {
            pedirConfirmacionCerrarSesionTodos()
        }

        // ------------------------------------------------------------------
        // COMPROBANTES Y RESPALDO
        // ------------------------------------------------------------------

        findViewById<View>(R.id.filaQr).setOnClickListener {
            seleccionarQr.launch("image/*")
        }

        findViewById<View>(R.id.filaExportar).setOnClickListener {
            iniciarExportacion()
        }

        findViewById<View>(R.id.filaImportar).setOnClickListener {
            iniciarImportacion()
        }

        // ------------------------------------------------------------------
        // INFORMACIÓN
        // ------------------------------------------------------------------

        findViewById<View>(R.id.filaAyuda).setOnClickListener {
            startActivity(Intent(this, AyudaActivity::class.java))
        }

        findViewById<View>(R.id.filaAcercaDe).setOnClickListener {
            startActivity(Intent(this, AcercaDeActivity::class.java))
        }

        // ------------------------------------------------------------------
        // SALIR
        // ------------------------------------------------------------------

        findViewById<View>(R.id.btnCerrarSesion).setOnClickListener {
            pedirConfirmacionCerrarSesion()
        }

        BottomNavigator.configurar(this, R.id.nav_configuracion)
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_configuracion)
        mostrarEstadoQr(qrManager.existeQr())
        pedirPerfilSiFalta()
    }

    // =======================================================================
    // Perfil
    // =======================================================================

    /**
     * Pinta la ficha con lo que la sesión YA sabe, sin esperar a la red.
     *
     * La sesión solo conoce el nombre, el correo y el rol; el teléfono no está
     * en `/auth/yo`. Se pinta con eso y se sustituye en cuanto llegue la
     * llamada, para que Configuración no abra con un hueco.
     */
    private fun pintarPerfilDesdeLaSesion() {

        val perfil = Diarix.sesion.perfilActual

        if (perfil == null) {

            pintarPerfil(PerfilEditable.vacio())
            return
        }

        pintarPerfil(PerfilEditable.desde(perfil))
    }

    /** Pide `GET /usuarios/yo` una sola vez y repinta cuando llegue. */
    private fun pedirPerfilSiFalta() {

        if (perfilSolicitado) return
        if (!Diarix.tieneSesion()) return

        perfilSolicitado = true

        Diarix.enHiloDeFondo {

            try {

                val perfil = Diarix.sesion.miPerfil()

                runOnUiThread { pintarPerfil(perfil) }

            } catch (_: ErrorDiarix) {

                // Sin red o sesión caducada: se queda lo que ya había, que es
                // el nombre y el correo de la sesión. Un fallo al leer el
                // perfil NO es motivo para enseñarle un error a alguien que
                // solo ha venido a cambiar el QR.
            }
        }
    }

    /**
     * Dibuja la ficha.
     *
     * Todos los campos muestran lo que el servidor tiene. Lo que no existe se
     * dice que no existe ("Sin teléfono"), nunca se rellena con un texto de
     * ejemplo: un dato inventado en una pantalla de perfil es el sitio donde
     * más fácil se cuela una mentira.
     */
    private fun pintarPerfil(perfil: PerfilEditable) {

        val nombre = perfil.nombreCompleto.trim().ifEmpty {
            getString(R.string.perfil_sin_nombre)
        }

        val iniciales = PerfilEditable.iniciales(perfil.nombreCompleto)

        txtIniciales.text =
            iniciales.ifEmpty { getString(R.string.perfil_inicial_vacio) }

        txtNombrePerfil.text = nombre

        txtCorreoPerfil.text =
            perfil.email.ifEmpty { getString(R.string.perfil_sin_nombre) }

        // El teléfono solo se muestra si existe. No se pone un campo vacío con
        // un hint que parezca un dato.
        txtTelefonoPerfil.visibility =
            if (perfil.telefonoEditable.isBlank()) View.GONE
            else View.VISIBLE

        txtTelefonoPerfil.text = perfil.telefonoEditable

        // "Cuenta de Google" solo si la cuenta ES de Google. La marca viene
        // del propio inicio de sesión (ver `SessionManager.esCuentaDeGoogle`):
        // el backend no expone si una cuenta está vinculada, y suponerlo por el
        // dominio del correo sería inventar.
        etiquetaGoogle.visibility =
            if (Diarix.sesion.esCuentaDeGoogle()) View.VISIBLE
            else View.GONE
    }

    // =======================================================================
    // Cuenta
    // =======================================================================

    /**
     * El área en la que se está trabajando.
     *
     * El texto va con el color del área y la barra de al lado repite ese
     * color, que es el mismo lenguaje que usa el selector. Así se sabe en qué
     * área se está sin tener que leer, y el cambio de área queda marcado como
     * algo distinto de leer un dato.
     */
    private fun mostrarModo() {

        when (modoVigente(this)) {

            ModoDiarix.FACTURACION -> {
                txtModoActual.text = getString(R.string.modo_facturacion)
                txtModoActual.setTextColor(color("#0AA87A"))
                barraModo.setBackgroundResource(R.drawable.bg_modo_facturacion)
            }

            ModoDiarix.PRESTAMOS -> {
                txtModoActual.text = getString(R.string.modo_prestamos)
                txtModoActual.setTextColor(color("#2B6ED4"))
                barraModo.setBackgroundResource(R.drawable.bg_modo_prestamos)
            }
        }
    }

    /**
     * El estado de la sesión, sin adornarlo.
     *
     * Si no hay sesión se dice, en vez de fingir que la hay: Configuración
     * puede quedar abierta un rato y para entonces el refresh puede haber
     * caducado. Esta fila NO tiene chevron justamente porque no lleva a ninguna
     * parte de la app donde arreglarlo.
     */
    private fun mostrarSesion() {

        val perfil = Diarix.sesion.perfilActual

        txtEstadoSesion.text = when {
            !Diarix.tieneSesion() -> getString(R.string.configuracion_sesion_sin_datos)
            perfil == null -> getString(R.string.configuracion_sesion_activa)
            else -> getString(
                R.string.configuracion_sesion_con_rol,
                nombreDelRol(perfil.rol)
            )
        }
    }

    /**
     * Traduce el rol del backend a cómo se llama esa persona.
     *
     * Un rol desconocido se devuelve tal cual, en vez de esconderse: si el
     * servidor añade un quinto rol y la app no lo conoce, hay que VERLO en la
     * pantalla (así se nota en las pruebas) y no leer "usuario", que haría creer
     * que todo el mundo es un usuario normal.
     */
    private fun nombreDelRol(rol: String): String {

        val recurso = when (rol.uppercase(Locale.ROOT)) {
            "ADMIN" -> R.string.configuracion_rol_admin
            "ACCOUNTANT" -> R.string.configuracion_rol_accountant
            "DRIVER" -> R.string.configuracion_rol_driver
            "USER" -> R.string.configuracion_rol_user
            else -> return rol
        }

        return getString(recurso)
    }

    /**
     * Confirmar antes de revocar las otras sesiones.
     *
     * Avisa de lo que va a pasar y de lo que NO va a pasar (este dispositivo
     * sigue dentro), porque quien toca esto desde un teléfono que ha perdido
     * otro está resolviendo un susto y necesita saber si va a perder también el
     * acceso desde aquí.
     */
    private fun pedirConfirmacionCerrarSesionTodos() {

        AlertDialog.Builder(this)
            .setTitle(R.string.configuracion_cerrar_todos)
            .setMessage(R.string.configuracion_cerrar_todos_confirmacion_detalle)
            .setPositiveButton(R.string.confirmar) { _, _ ->
                ejecutarCerrarSesionEnTodos()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun ejecutarCerrarSesionEnTodos() {

        Diarix.enHiloDeFondo {

            try {

                val cerradas = Diarix.sesion.cerrarSesionEnTodosLosDispositivos()

                runOnUiThread {
                    Toast.makeText(
                        this,
                        getString(R.string.configuracion_cerrar_todos_lista, cerradas),
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (_: ErrorDiarix) {

                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.configuracion_cerrar_todos_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // =======================================================================
    // Cerrar sesión
    // =======================================================================

    /**
     * Confirmar antes de cerrar sesión.
     *
     * Es la única acción de la pantalla que cambia el estado de la cuenta, y por
     * eso lleva su propio diálogo con el texto exacto que se pidió: se dice que
     * hay que volver a entrar, porque quien confirme tiene que saber que la
     * próxima vez verá el login y no el menú de siempre.
     */
    private fun pedirConfirmacionCerrarSesion() {

        AlertDialog.Builder(this)
            .setTitle(R.string.configuracion_cerrar_sesion_titulo)
            .setMessage(R.string.configuracion_cerrar_sesion_detalle)
            .setPositiveButton(R.string.configuracion_cerrar_sesion_confirmar) { _, _ ->
                ejecutarCierreDeSesion()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /**
     * Fase 4 · Cierre de sesión: llama a /auth/logout (revoca el refresh en
     * el servidor) y, pase lo que pase con la red, borra los tokens locales
     * y vuelve al login.
     */
    private fun ejecutarCierreDeSesion() {

        Diarix.enHiloDeFondo {
            // Server + local, incluso sin internet (borra local igual).
            Diarix.sesion.cerrarSesion()

            runOnUiThread {
                val intent =
                    Intent(this, LoginActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }
                startActivity(intent)
            }
        }
    }

    // =======================================================================
    // Información
    // =======================================================================

    /**
     * La versión sale de [BuildConfig].
     *
     * Nunca escrita a mano en un texto: un "1.0.1" puesto a mano es una
     * mentira en cuanto se publica la 1.0.2, y quien llega a soporte "¿qué
     * versión tiene?" recibe un número que no corresponde con el APK que
     * tiene instalado. `BuildConfig` es lo mismo que el APK de verdad.
     */
    private fun mostrarVersion() {

        txtVersion.text = getString(
            R.string.configuracion_version_numero,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE
        )
    }

    private fun color(hex: String): Int = Color.parseColor(hex)

    // =======================================================================
    // Exportación
    // =======================================================================

    private fun iniciarExportacion() {

        val fecha = SimpleDateFormat(
            "dd-MM-yyyy",
            Locale.getDefault()
        ).format(Date())

        try {

            elegirDestinoExportacion.launch(
                "DiariX_respaldo_$fecha.zip"
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                RespaldoService.MENSAJE_ERROR_EXPORTAR,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun exportarRespaldo(uri: Uri) {

        mostrarProgreso("Generando respaldo…")

        Thread {

            try {

                val salida =
                    contentResolver.openOutputStream(uri)

                    ?: throw RespaldoService.RespaldoException(
                        RespaldoService.MENSAJE_ERROR_EXPORTAR
                    )

                val resumen = salida.use {
                    respaldoService.exportar(it)
                }

                runOnUiThread {

                    ocultarProgreso()

                    Toast.makeText(
                        this,
                        "Exportación completada correctamente.\n" +
                                "Facturas: ${resumen.facturas} · " +
                                "Comprobantes: ${resumen.comprobantes}",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {

                runOnUiThread {

                    ocultarProgreso()

                    Toast.makeText(
                        this,
                        mensajeDeError(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // =======================================================================
    // Importación
    // =======================================================================

    private fun iniciarImportacion() {

        try {

            elegirOrigenImportacion.launch(
                arrayOf(
                    "application/zip",
                    "application/x-zip-compressed",
                    "application/octet-stream"
                )
            )

        } catch (e: Exception) {

            Toast.makeText(
                this,
                RespaldoService.MENSAJE_ARCHIVO_INVALIDO,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Valida el respaldo elegido antes de modificar nada y pide
     * confirmación mostrando un resumen del contenido.
     */
    private fun prepararImportacion(uri: Uri) {

        mostrarProgreso("Validando respaldo…")

        Thread {

            val validacion = respaldoService.validar {

                contentResolver.openInputStream(uri)
            }

            runOnUiThread {

                ocultarProgreso()

                when (validacion) {

                    is RespaldoService.Validacion.Ok ->
                        pedirConfirmacion(uri, validacion.resumen)

                    else ->

                        Toast.makeText(
                            this,
                            validacion.mensaje,
                            Toast.LENGTH_LONG
                        ).show()
                }
            }
        }.start()
    }

    private fun pedirConfirmacion(
        uri: Uri,
        resumen: RespaldoService.ResumenBackup
    ) {

        val detalle = StringBuilder()

        detalle.append("Facturas: ")
            .append(resumen.facturas)
            .append('\n')

        detalle.append("Cierres diarios: ")
            .append(resumen.registros)
            .append('\n')

        detalle.append("Días en papelera: ")
            .append(resumen.papelera)
            .append('\n')

        detalle.append("Comprobantes incluidos: ")
            .append(resumen.comprobantes)

        if (resumen.fechaExportacion.isNotEmpty()) {

            detalle.append('\n')
                .append("Fecha del respaldo: ")
                .append(resumen.fechaExportacion)
        }

        AlertDialog.Builder(this)
            .setTitle("Respaldo encontrado")
            .setMessage(detalle)
            .setPositiveButton("Importar") { _, _ ->
                importarRespaldo(uri)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun importarRespaldo(uri: Uri) {

        mostrarProgreso("Importando respaldo…")

        Thread {

            try {

                val resultado = respaldoService.importar {

                    contentResolver.openInputStream(uri)
                }

                runOnUiThread {

                    ocultarProgreso()

                    val mensaje = StringBuilder()

                    mensaje.append(
                        "Importación completada correctamente.\n"
                    )

                    mensaje.append("Nuevas: ")
                        .append(resultado.insertadas)
                        .append('\n')

                    mensaje.append("Actualizadas: ")
                        .append(resultado.actualizadas)
                        .append('\n')

                    mensaje.append("Comprobantes restaurados: ")
                        .append(resultado.comprobantesRestaurados)

                    if (resultado.qrRestaurado) {

                        mensaje.append('\n')
                            .append("Código QR restaurado.")
                    }

                    Toast.makeText(
                        this,
                        mensaje,
                        Toast.LENGTH_LONG
                    ).show()

                    mostrarEstadoQr(qrManager.existeQr())
                }

            } catch (e: Exception) {

                runOnUiThread {

                    ocultarProgreso()

                    Toast.makeText(
                        this,
                        mensajeDeError(e),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }.start()
    }

    // =======================================================================
    // Utilidades de interfaz
    // =======================================================================

    private fun mostrarProgreso(mensaje: String) {

        ocultarProgreso()

        dialogoProgreso = AlertDialog.Builder(this)
            .setMessage(mensaje)
            .setCancelable(false)
            .create()

        dialogoProgreso?.show()
    }

    private fun ocultarProgreso() {

        dialogoProgreso?.dismiss()

        dialogoProgreso = null
    }

    private fun mensajeDeError(e: Exception): String =

        (e as? RespaldoService.RespaldoException)?.mensajeUsuario
            ?: RespaldoService.MENSAJE_ERROR_IMPORTAR

    private fun mostrarEstadoQr(configurado: Boolean) {

        txtEstadoQr.text = getString(
            if (configurado) R.string.configuracion_qr_configurado
            else R.string.configuracion_qr_sin_configurar
        )

        txtEstadoQr.setTextColor(
            ContextCompat.getColor(
                this,
                if (configurado) android.R.color.holo_green_dark
                else android.R.color.holo_red_dark
            )
        )
    }
}