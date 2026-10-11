package com.example.totaldiaria

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import com.example.totaldiaria.core.modo.AlmacenDeModo
import com.example.totaldiaria.core.modo.SelectorDeModo
import com.example.totaldiaria.core.modo.abrirSeleccionDeModo
import com.example.totaldiaria.network.ErrorDiarix
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

/**
 * Pantalla de entrada (Fase 4) con los DOS métodos de acceso:
 *
 *  - "Registrarme con Google" → alta/acceso de usuario INDIVIDUAL. El backend
 *    se reserva el derecho de rechazar con un mensaje claro si el correo
 *    pertenece a una cuenta empresarial o de administración
 *    (CUENTA_CON_ROL_RESERVADO).
 *  - "INGRESAR" (NIT + usuario + contraseña) → contadores y conductores de una
 *    empresa.
 *
 * Tras un login 200 se entra a [MainActivity]. La sesión viva evita volver
 * aquí hasta que caduque o el usuario cierre sesión.
 *
 * Aquí solo hay presentación: la autenticación vive en `SessionManager` /
 * `AuthApi` y el ciclo de vida de la sesión en `Diarix`.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var edtNit: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnGoogle: Button
    private lateinit var btnEntrar: Button
    private lateinit var txtError: TextView
    private lateinit var progreso: ProgressBar

    private var clienteGoogle: GoogleSignInClient? = null

    private val resultadoGoogle =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { resultado ->
            val cuenta = try {
                GoogleSignIn.getSignedInAccountFromIntent(resultado.data)
                    .getResult(ApiException::class.java)
            } catch (_: ApiException) {
                // El usuario canceló o el intent falló: no es un error de la
                // app, se vuelve a mostrar la pantalla en silencio.
                null
            }

            val idToken = cuenta?.idToken
            if (idToken == null) {
                setOcupado(false)
                mostrarError("No se pudo completar el acceso con Google. Intenta de nuevo.")
                return@registerForActivityResult
            }

            entrarConGoogle(idToken, cuenta)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Siempre inicializar primero: MainActivity y LoginActivity comparten
        // el mismo grafo de objetos de la capa DiariX.
        Diarix.iniciar(this)

        // Si ya hay una sesión (app reabierta, gemelo, expiración recuperada),
        // se va directo al registro de facturas.
        if (Diarix.tieneSesion()) {
            abrirPrincipal()
            return
        }

        setContentView(R.layout.activity_login)

        aplicarEspacioDelTeclado()

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        vincularVistas()
        configurarGoogle()
        configurarAcciones()
    }

    private fun vincularVistas() {
        edtNit = findViewById(R.id.edtNit)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        btnGoogle = findViewById(R.id.btnGoogle)
        btnEntrar = findViewById(R.id.btnEntrar)
        txtError = findViewById(R.id.txtError)
        progreso = findViewById(R.id.progreso)
    }

    /**
     * En Android 15+ la app ocupa toda la pantalla y `adjustResize` ya no
     * achica la ventana: el teclado se dibuja ENCIMA. Aquí se convierte la
     * barra de estado y el alto del teclado en espacio dentro del contenido
     * desplazable, de modo que el logo nunca quede bajo la barra y los campos
     * y los botones ("INGRESAR", Google) siempre queden por encima del teclado.
     *
     * Es solo presentación: no interviene la autenticación.
     */
    private fun aplicarEspacioDelTeclado() {
        val contenido = findViewById<View>(R.id.loginContenido)
        val margenSuperior = contenido.paddingTop
        val margenInferior = contenido.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(contenido) { vista, insets ->
            val barras = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val teclado = insets.getInsets(WindowInsetsCompat.Type.ime())
            vista.updatePadding(
                top = margenSuperior + barras.top,
                bottom = margenInferior + maxOf(barras.bottom, teclado.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(contenido)
    }

    private fun configurarGoogle() {
        val webClientId = BuildConfig.DIARIX_GOOGLE_WEB_CLIENT_ID

        if (webClientId.isBlank()) {
            // La versión no trae el Web Client ID (configuración pendiente):
            // el botón explica cómo acceder mientras tanto. Nunca inventamos
            // credenciales ni dejamos un botón muerto.
            btnGoogle.setOnClickListener {
                mostrarError(
                    "El acceso con Google aún no está disponible en esta versión de la app. " +
                        "Usa el acceso empresarial de tu empresa."
                )
            }
            return
        }

        val opciones = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()

        clienteGoogle = GoogleSignIn.getClient(this, opciones)

        btnGoogle.setOnClickListener {
            ocultarError()
            clienteGoogle?.let { cliente ->
                setOcupado(true)
                resultadoGoogle.launch(cliente.signInIntent)
            }
        }
    }

    private fun configurarAcciones() {
        btnEntrar.setOnClickListener {
            ocultarError()
            entrarEmpresarial()
        }

        // El teclado del campo Contraseña muestra "Listo": se entra por ahí
        // exactamente igual que por el botón INGRESAR. No cambia la
        // autenticación, solo evita tener que soltar el botón con una mano.
        edtPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (btnEntrar.isEnabled) {
                    ocultarError()
                    entrarEmpresarial()
                }
                // `false` deja que el sistema cierre el teclado como siempre.
                false
            } else {
                false
            }
        }
    }

    // =========================================================================
    // Acceso empresarial
    // =========================================================================

    private fun entrarEmpresarial() {
        val nit = edtNit.text.toString().trim()
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()

        if (email.isEmpty() || password.isEmpty()) {
            mostrarError("Ingresa tu usuario y contraseña.")
            return
        }

        setOcupado(true)

        Diarix.enHiloDeFondo {
            val resultado = try {
                Diarix.sesion.entrarEmpresa(
                    nitEmpresa = nit.ifBlank { null },
                    email = email,
                    password = password,
                )
                null
            } catch (e: ErrorDiarix) {
                e.mensajeAmigable
            }

            runOnUiThread {
                setOcupado(false)
                if (resultado == null) {
                    abrirPrincipal()
                } else {
                    mostrarError(resultado)
                }
            }
        }
    }

    // =========================================================================
    // Acceso con Google
    // =========================================================================

    private fun entrarConGoogle(idToken: String, cuenta: GoogleSignInAccount) {
        setOcupado(true)

        Diarix.enHiloDeFondo {
            // La cuenta seleccionada en Google puede NO ser la última usada:
            // se firma la real para que Google la ofrezca de nuevo la próxima
            // vez, sin afectar a la sesión de DiariX.
            val resultado = try {
                Diarix.sesion.entrarConGoogle(idToken)
                null
            } catch (e: ErrorDiarix) {
                e.mensajeAmigable
            }

            runOnUiThread {
                setOcupado(false)
                if (resultado == null) {
                    abrirPrincipal()
                } else {
                    // El correo pertenece a una cuenta empresarial o el token
                    // no fue válido: se muestra el mensaje del backend y se
                    // queda en la pantalla SIN cambiar nada.
                    mostrarError(resultado)
                }
            }
        }
    }

    // =========================================================================
    // UI
    // =========================================================================

    /**
     * Entra a DiariX por donde el usuario lo dejó.
     *
     * Fase 0.1 — aquí es donde nace la diferencia entre "primera vez" y "ya me
     *known":
     *
     *  - Sin área elegida → [abrirSeleccionDeModo]: "¿Qué quieres gestionar?",
     *    una sola vez. Se elige, se guarda y se entra al área.
     *  - Con área elegida → se entra DIRECTO a ella. Quien ya eligió no vuelve
     *    a ver la pregunta ni al reentrar, ni al cerrar y abrir la app, ni al
     *    iniciar sesión más adelante.
     *
     * La comprobación es [AlmacenDeModo.modoGuardado], no "el modo actual":
     * son cosas distintas justamente cuando nadie ha elegido todavía.
     */
    private fun abrirPrincipal() {

        if (SelectorDeModo.haceFaltaElegirModo(AlmacenDeModo(this).modoGuardado())) {

            abrirSeleccionDeModo(this)

        } else {

            SelectorDeModo.abrirInicio(this)
        }

        finish()
    }

    private fun setOcupado(ocupado: Boolean) {
        progreso.visibility = if (ocupado) View.VISIBLE else View.GONE
        btnEntrar.isEnabled = !ocupado
        btnGoogle.isEnabled = !ocupado
    }

    private fun ocultarError() {
        txtError.visibility = View.GONE
    }

    private fun mostrarError(mensaje: String) {
        txtError.text = mensaje
        txtError.visibility = View.VISIBLE
    }
}