package com.example.totaldiaria.core.cuenta

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.sesion.PerfilEditable
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

/**
 * DIARIX · Fase 0.1 · Editar perfil.
 *
 * ## Lo único que se puede cambiar
 *
 * Dos campos: el nombre completo y el teléfono. Son exactamente los que acepta
 * `PATCH /usuarios/yo` (`ActualizarPerfilDto`: `nombreCompleto` con máximo 160 y
 * `telefono` con máximo 32). No hay un campo más porque no hay un endpoint más.
 *
 * ## Lo que NO se edita, y por qué
 *
 *  - **Correo.** Es la identidad de la cuenta. Con Google es además la que
 *    Google firmó, y el backend la reescribe desde Google si cambia allí: pedirla
 *    en la app produciría un cambio aparente que el servidor revierte a los
 *    cinco segundos. Se enseña arriba, en lectura, con la explicación al lado.
 *  - **Rol, empresa y plan.** Los fija el servidor (invitación de empresa, CLI
 *    del primer administrador). Nadie se cambia su propio rol.
 *  - **Foto.** No hay columna de avatar en `users` ni el backend conserva la de
 *    Google, así que no hay nada que editar. El avatar de Configuración son las
 *    iniciales del nombre real, y en cuanto exista la columna se sustituye el
 *    dibujo por la imagen.
 *
 * ## Guardado
 *
 * Solo se manda lo que ha CAMBIADO de verdad: un campo intacto viaja como `null`
 * ("no lo toques") y uno que la persona ha dejado vacío viaja como `""` ("vacíalo").
 * Es la diferencia entre no querer cambiar el teléfono y querer quitarlo, y por eso
 * se comparan los valores antes de decidir qué enviar.
 *
 * No es una edición de interfaz: lo que se ve al volver a Configuración es lo
 * que el servidor tiene, porque al guardar se vuelve a leer `GET /usuarios/yo`
 * en lugar de suponer que el cambio entró.
 */
class EditarPerfilActivity : AppCompatActivity() {

    private lateinit var edtNombre: TextInputEditText
    private lateinit var edtTelefono: TextInputEditText
    private lateinit var txtCorreo: TextView
    private lateinit var etiquetaGoogle: TextView
    private lateinit var txtError: TextView
    private lateinit var btnGuardar: MaterialButton
    private lateinit var progreso: ProgressBar

    /** Con lo que se abre la pantalla, para saber qué ha cambiado de verdad. */
    private var nombreOriginal = ""
    private var telefonoOriginal = ""

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_editar_perfil)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        Diarix.iniciar(this)

        edtNombre = findViewById(R.id.edtNombreCompleto)
        edtTelefono = findViewById(R.id.edtTelefono)
        txtCorreo = findViewById(R.id.txtCorreoSoloLectura)
        etiquetaGoogle = findViewById(R.id.etiquetaGoogleEdicion)
        txtError = findViewById(R.id.txtErrorEdicion)
        btnGuardar = findViewById(R.id.btnGuardarPerfil)
        progreso = findViewById(R.id.progresoGuardar)

        // Se abre con lo que ya hay en memoria y se sustituye en cuanto llegue
        // el perfil detallado. Escribir los campos antes de que llegue la red
        // evita que alguien empiece a escribir su nombre encima de un campo que
        // se rellena un segundo después y se lo borre.
        pintarCon(perfilDeLaSesion())
        cargarPerfil()

        btnGuardar.setOnClickListener { guardar() }
    }

    private fun perfilDeLaSesion(): PerfilEditable {

        val perfil = Diarix.sesion.perfilActual

        return if (perfil == null) PerfilEditable.vacio()
        else PerfilEditable.desde(perfil)
    }

    /** `GET /usuarios/yo`: trae el teléfono, que `/auth/yo` no trae. */
    private fun cargarPerfil() {

        if (!Diarix.tieneSesion()) return

        Diarix.enHiloDeFondo {

            try {

                val perfil = Diarix.sesion.miPerfil()

                runOnUiThread { pintarCon(perfil) }

            } catch (_: ErrorDiarix) {

                // Sin red: se queda lo de la sesión. Se puede abrir la pantalla
                // y leer el correo, que es lo que se viene a hacer cuando se
                // entra con Google y no hay cobertura.
            }
        }
    }

    private fun pintarCon(perfil: PerfilEditable) {

        val nombre = perfil.nombreCompleto.trim()
        val telefono = perfil.telefonoEditable

        nombreOriginal = nombre
        telefonoOriginal = telefono

        edtNombre.setText(nombre)
        edtTelefono.setText(telefono)

        txtCorreo.text =
            perfil.email.ifEmpty { getString(R.string.perfil_sin_nombre) }

        etiquetaGoogle.visibility =
            if (Diarix.sesion.esCuentaDeGoogle()) View.VISIBLE
            else View.GONE
    }

    /**
     * Valida y guarda.
     *
     * Los límites (160 y 32) se comprueban aquí aunque el campo ya traiga un
     * `maxLength`: el `maxLength` evita escribir de más, pero un valor pegado
     * desde el portapapeles puede venir más largo y sin ese control la petición
     * saldría y el backend la rechazaría con un error que no se entiende.
     */
    private fun guardar() {

        val nombre = edtNombre.text?.toString()?.trim().orEmpty()
        val telefono = edtTelefono.text?.toString()?.trim().orEmpty()

        ocultarError()

        when {
            nombre.isEmpty() -> {
                mostrarError(R.string.perfil_nombre_vacio)
                edtNombre.requestFocus()
                return
            }

            nombre.length > MAX_NOMBRE -> {
                mostrarError(R.string.perfil_nombre_muy_largo)
                edtNombre.requestFocus()
                return
            }

            telefono.length > MAX_TELEFONO -> {
                mostrarError(R.string.perfil_telefono_muy_largo)
                edtTelefono.requestFocus()
                return
            }
        }

        // `null` = no lo toques. Solo se manda lo que de verdad cambió.
        val nombreAMandar = if (nombre == nombreOriginal) null else nombre
        val telefonoAMandar =
            if (telefono == telefonoOriginal) null else telefono

        if (nombreAMandar == null && telefonoAMandar == null) {
            // No hay nada que guardar: avisar y no gastar una llamada inútil.
            finish()
            return
        }

        setOcupado(true)

        Diarix.enHiloDeFondo {

            try {

                Diarix.sesion.actualizarMiPerfil(
                    nombreCompleto = nombreAMandar,
                    telefono = telefonoAMandar,
                )

                runOnUiThread {
                    Toast.makeText(
                        this,
                        R.string.perfil_guardado,
                        Toast.LENGTH_SHORT
                    ).show()

                    // Se vuelve atrás a Configuración, que ya se recarga con
                    // `onResume` y repinta con lo que el servidor tiene.
                    finish()
                }

            } catch (_: ErrorDiarix) {

                runOnUiThread {
                    setOcupado(false)
                    mostrarError(R.string.perfil_no_se_puede_guardar)
                }
            }
        }
    }

    private fun setOcupado(ocupado: Boolean) {

        btnGuardar.isEnabled = !ocupado
        edtNombre.isEnabled = !ocupado
        edtTelefono.isEnabled = !ocupado

        progreso.visibility = if (ocupado) View.VISIBLE else View.GONE
    }

    private fun mostrarError(texto: Int) {
        txtError.setText(texto)
        txtError.visibility = View.VISIBLE
    }

    private fun ocultarError() {
        txtError.visibility = View.GONE
    }

    private companion object {
        /** Los mismos topes que `ActualizarPerfilDto` del backend. */
        const val MAX_NOMBRE = 160
        const val MAX_TELEFONO = 32
    }
}