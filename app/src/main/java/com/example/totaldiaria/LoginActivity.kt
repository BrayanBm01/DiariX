package com.example.totaldiaria

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import com.example.totaldiaria.core.modo.AlmacenDeModo
import com.example.totaldiaria.core.modo.SelectorDeModo
import com.example.totaldiaria.core.modo.abrirSeleccionDeModo
import com.example.totaldiaria.network.ErrorDiarix
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.SecureRandom
import java.util.Base64

/**
 * Pantalla de entrada (Fase 4) con los DOS métodos de acceso:
 *
 *  - "Entrar con Google" → alta/acceso de usuario INDIVIDUAL con Credential
 *    Manager. El backend se reserva el derecho de rechazar con un mensaje claro
 *    si el correo pertenece a una cuenta empresarial o de administración
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

    private lateinit var credentialManager: CredentialManager

    /** Nonce anti-replay del intento EN CURSO; viaja al backend junto al token. */
    private var nonceDeEsteIntento: String? = null

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

        // Render free tier se duerme: despertarlo mientras el usuario escribe
        // evita que el primer login cargue con todo el arranque en frío.
        Diarix.precalentar()

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
            // Configuración pendiente: nunca inventamos credenciales ni dejamos
            // un botón muerto. El valor por defecto ya trae el Web Client ID,
            // así que esto solo ocurre si se sobreescribe con un valor vacío.
            btnGoogle.setOnClickListener {
                mostrarError(
                    "El acceso con Google aún no está disponible en esta versión de la app. " +
                        "Usa el acceso empresarial de tu empresa."
                )
            }
            return
        }

        credentialManager = CredentialManager.create(this)

        btnGoogle.setOnClickListener {
            ocultarError()
            setOcupado(true)
            pedirCredencialGoogle(webClientId)
        }
    }

    /**
     * Credential Manager con `GetGoogleIdOption`:
     *
     *  - `setServerClientId` usa el Web Client ID (BuildConfig), que es el
     *    `aud` que el backend verifica.
     *  - `setFilterByAuthorizedAccounts(false)` para ofrecer también cuentas
     *    de Google que aún no han autorizado la app (alta de usuarios nuevos).
     *  - `setNonce` con 32 bytes aleatorios en base64url, para impedir replay.
     *
     * Se usa la variante asíncrona con callback (`getCredentialAsync`) para no
     * añadir corutinas solo para esta pantalla.
     */
    private fun pedirCredencialGoogle(webClientId: String) {
        // Nonce anti-replay: se genera POR INTENTO, se incrusta en la petición
        // a Google y se envía al backend junto al token, que exige que el claim
        // `nonce` del idToken sea exactamente este.
        val nonce = nonceAleatorio()
        nonceDeEsteIntento = nonce

        val opcionGoogle = GetGoogleIdOption.Builder()
            .setServerClientId(webClientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .setNonce(nonce)
            .build()

        val solicitud = GetCredentialRequest.Builder()
            .addCredentialOption(opcionGoogle)
            .build()

        credentialManager.getCredentialAsync(
            this,
            solicitud,
            null,
            ContextCompat.getMainExecutor(this),
            object : CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
                override fun onResult(result: GetCredentialResponse) {
                    val idToken = idTokenDeGoogle(result.credential)

                    if (idToken == null) {
                        setOcupado(false)
                        nonceDeEsteIntento = null
                        mostrarError(
                            "No se recibió una credencial de Google válida. Inténtalo nuevamente."
                        )
                        return
                    }

                    entrarConGoogle(idToken, nonce)
                }

                override fun onError(e: GetCredentialException) {
                    setOcupado(false)
                    nonceDeEsteIntento = null

                    val mensaje = when (e) {
                        // El usuario cerró la hoja de Google: no es un fallo.
                        is GetCredentialCancellationException ->
                            "Acceso con Google cancelado."
                        // No hay cuenta elegible: ni autorizada ni nueva.
                        is NoCredentialException ->
                            "No hay cuentas de Google disponibles en este dispositivo. " +
                                "Añade una cuenta de Google e inténtalo nuevamente."
                        // El dispositivo no puede dialogar con Google.
                        is GetCredentialUnsupportedException ->
                            "Este dispositivo no puede usar el acceso con Google. " +
                                "Actualiza Servicios de Google Play e inténtalo de nuevo."
                        is GetCredentialProviderConfigurationException ->
                            "No hay un proveedor de cuentas de Google disponible en este dispositivo."
                        is GetCredentialInterruptedException ->
                            "La verificación con Google se interrumpió. Inténtalo nuevamente."
                        // Fallo real de red/infraestructura de Google.
                        else ->
                            "No se pudo conectar con Google. " +
                                "Revisa tu conexión e inténtalo nuevamente."
                    }

                    mostrarError(mensaje)
                }
            },
        )
    }

    /**
     * Extrae el idToken de la credencial devuelta.
     *
     * ⚠ AQUÍ ESTABA EL FALLO: el proveedor de Google Play Services NO entrega
     * una instancia de `GoogleIdTokenCredential`, sino una `CustomCredential`
     * genérica con los datos en su bundle (`Credential.Companion.createFrom`).
     * El chequeo `is GoogleIdTokenCredential` fallaba SIEMPRE y el login moría
     * con "No se pudo completar el acceso con Google" antes de llegar al
     * backend. El patrón oficial de la documentación es comprobar el TIPO y
     * rehidratar con `GoogleIdTokenCredential.createFrom(credential.data)`.
     * Se acepta también la credencial ya tipada, por si un proveedor futuro la
     * entrega así.
     */
    private fun idTokenDeGoogle(credencial: androidx.credentials.Credential): String? {
        val idToken = when {
            credencial is GoogleIdTokenCredential -> credencial.idToken

            credencial is CustomCredential &&
                credencial.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL ->
                GoogleIdTokenCredential.createFrom(credencial.data).idToken

            else -> null
        }

        return idToken?.takeIf { it.isNotBlank() }
    }

    /** 32 bytes aleatorios, codificados como base64url sin relleno. */
    private fun nonceAleatorio(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
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

    /**
     * El idToken viaja a `POST /auth/google` con el campo `idToken`
     * (ver `AuthApi.loginGoogle`). `SessionManager` guarda access y refresh en
     * el mismo almacén seguro que el login con contraseña.
     */
    private fun entrarConGoogle(idToken: String, nonce: String?) {
        setOcupado(true)

        Diarix.enHiloDeFondo {
            val resultado = try {
                Diarix.sesion.entrarConGoogle(idToken, nonce)
                null
            } catch (e: ErrorDiarix) {
                e.mensajeAmigable
            }

            runOnUiThread {
                setOcupado(false)
                nonceDeEsteIntento = null
                if (resultado == null) {
                    abrirPrincipal()
                } else {
                    // Mensaje del backend (token inválido, cuenta empresarial,
                    // sesión activa en otro dispositivo…) en español y seguro
                    // de mostrar. La pantalla queda SIN cambiar nada.
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
