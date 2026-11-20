package com.example.totaldiaria.core.cuenta

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.network.ErrorDiarix
import com.example.totaldiaria.sesion.PerfilEditable
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.ByteArrayOutputStream

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
 *  - **Foto.** Aquí es donde se gestiona: circular, grande, con ajuste de zoom
 *    y posición antes de guardar. El backend no tiene columna de avatar, así
 *    que la foto vive SOLO en el almacenamiento privado de esta cuenta
 *    ([FotoPerfilStore]): la personalizada manda sobre la de Google y nada se
 *    sobrescribe hasta que la persona confirma con «Guardar».
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

    // Foto de perfil: avatar grande y filas de gestión (solo esta pantalla).
    private lateinit var imgAvatarPerfil: ImageView
    private lateinit var txtInicialesPerfil: TextView
    private lateinit var filaCambiarFoto: View
    private lateinit var filaUsarFotoGoogle: View
    private lateinit var fotoStore: FotoPerfilStore

    /** Con lo que se abre la pantalla, para saber qué ha cambiado de verdad. */
    private var nombreOriginal = ""
    private var telefonoOriginal = ""

    private val seleccionarFoto =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { abrirAjusteDeFoto(it) }
        }

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

        imgAvatarPerfil = findViewById(R.id.imgAvatarPerfil)
        txtInicialesPerfil = findViewById(R.id.txtInicialesPerfil)
        filaCambiarFoto = findViewById(R.id.filaCambiarFoto)
        filaUsarFotoGoogle = findViewById(R.id.filaUsarFotoGoogle)
        fotoStore = FotoPerfilStore(this)

        // Se abre con lo que ya hay en memoria y se sustituye en cuanto llegue
        // el perfil detallado. Escribir los campos antes de que llegue la red
        // evita que alguien empiece a escribir su nombre encima de un campo que
        // se rellena un segundo después y se lo borre.
        pintarCon(perfilDeLaSesion())
        pintarAvatar()
        sincronizarFotoDeGoogle()
        cargarPerfil()

        btnGuardar.setOnClickListener { guardar() }

        filaCambiarFoto.setOnClickListener {
            seleccionarFoto.launch("image/*")
        }

        filaUsarFotoGoogle.setOnClickListener {
            fotoStore.usarFotoDeGoogle()
            pintarAvatar()
        }
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

        // El avatar de iniciales sigue al nombre, también al cambiarlo aquí.
        txtInicialesPerfil.text = inicialesDe(perfil)
    }

    // =======================================================================
    // Foto de perfil (solo esta pantalla)
    // =======================================================================

    /** Pinta la foto de ESTA cuenta, circular; sin foto, las iniciales. */
    private fun pintarAvatar() {
        // El ImageView es `match_parent` dentro del FrameLayout: su
        // layoutParams.width es -1, así que el lado real sale del recurso.
        val lado = resources.getDimensionPixelSize(R.dimen.avatar_perfil_tamano)

        val archivo = fotoStore.fotoActual()
        val decodada = archivo?.takeIf { it.exists() }?.let {
            runCatching { FotoCircular.decodificar(it) }.getOrNull()
        }

        val circular = FotoCircular.recortarCircular(decodada, lado)

        if (circular == null) {
            imgAvatarPerfil.setImageDrawable(null)
            imgAvatarPerfil.visibility = View.GONE
            txtInicialesPerfil.visibility = View.VISIBLE
        } else {
            imgAvatarPerfil.setImageBitmap(circular)
            imgAvatarPerfil.visibility = View.VISIBLE
            txtInicialesPerfil.visibility = View.GONE
        }

        // «Usar foto de Google» solo tiene sentido si lo que se muestra es la
        // personalizada y existe la de Google a la que volver.
        filaUsarFotoGoogle.visibility =
            if (fotoStore.usandoPersonalizada() && fotoStore.tieneFotoGoogle()) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    /**
     * La foto de Google no la guarda el backend: llega en la respuesta del
     * login y se copia UNA vez al almacenamiento privado de la cuenta, para
     * que siga disponible sin red y no se confunda con la de otra persona.
     */
    private fun sincronizarFotoDeGoogle() {
        val url = Diarix.sesion.fotoGoogleDeSesion() ?: fotoStore.urlGoogle()

        if (url == null || fotoStore.tieneFotoGoogle()) return

        Diarix.enHiloDeFondo {
            val descargada = runCatching { fotoStore.descargarGoogle(url) }
                .getOrDefault(false)

            if (descargada) runOnUiThread { pintarAvatar() }
        }
    }

    /** Galería → bytes decodificados → diálogo de ajuste. */
    private fun abrirAjusteDeFoto(uri: Uri) {
        val bytes = runCatching {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()

        val original = bytes?.takeIf { it.isNotEmpty() }?.let {
            runCatching { FotoCircular.decodificar(it) }.getOrNull()
        }

        if (original == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
            return
        }

        mostrarDialogoAjuste(original)
    }

    /**
     * Ajuste con zoom y desplazamiento. NADA se guarda hasta que la persona
     * pulse «Guardar»: cancelar cierra el diálogo y la foto anterior queda
     * intacta.
     */
    private fun mostrarDialogoAjuste(original: Bitmap) {
        val vista = layoutInflater.inflate(R.layout.dialogo_ajuste_foto, null)
        val recorte = vista.findViewById<VistaRecorteCircular>(R.id.vistaRecorteFoto)
        val vistaPrevia = vista.findViewById<ImageView>(R.id.imgVistaPreviaFoto)

        recorte.setImagen(original)

        fun refrescarVistaPrevia() {
            val previa = runCatching { recorte.recortar(LADO_VISTA_PREVIA) }.getOrNull()
            vistaPrevia.setImageBitmap(previa)
        }

        recorte.alAjustar = { refrescarVistaPrevia() }
        // La vista aún no tiene tamaño mientras se infla el diálogo: la primera
        // vista previa se calcula después del primer layout, y no antes.
        recorte.post { refrescarVistaPrevia() }

        vista.findViewById<View>(R.id.btnZoomMas).setOnClickListener {
            recorte.masZoom()
            refrescarVistaPrevia()
        }

        vista.findViewById<View>(R.id.btnZoomMenos).setOnClickListener {
            recorte.menosZoom()
            refrescarVistaPrevia()
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.perfil_ajustar_foto_titulo)
            .setView(vista)
            .setPositiveButton(R.string.perfil_foto_guardar) { _, _ ->
                guardarFotoAjustada(recorte)
            }
            .setNegativeButton(R.string.perfil_foto_cancelar, null)
            .show()
    }

    /** Recorta lo que se ve en el círculo y lo guarda para ESTA cuenta. */
    private fun guardarFotoAjustada(recorte: VistaRecorteCircular) {
        val circular = recorte.recortar(LADO_GUARDADO)

        if (circular == null) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
            return
        }

        val bytes = ByteArrayOutputStream().use { salida ->
            circular.compress(Bitmap.CompressFormat.PNG, 100, salida)
            salida.toByteArray()
        }

        if (!fotoStore.guardarPersonalizada(bytes)) {
            Toast.makeText(this, R.string.perfil_foto_error, Toast.LENGTH_SHORT).show()
            return
        }

        pintarAvatar()

        Toast.makeText(this, R.string.perfil_foto_guardada, Toast.LENGTH_SHORT).show()
    }

    /** Iniciales del avatar; el «—» cuando el nombre aún no llegó. */
    private fun inicialesDe(perfil: PerfilEditable): String =
        PerfilEditable.iniciales(perfil.nombreCompleto)
            .ifEmpty { getString(R.string.perfil_inicial_vacio) }

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

        /** Lado del PNG guardado: nítido en el avatar y ligero en disco. */
        const val LADO_GUARDADO = 512

        /** Lado de la miniatura de vista previa del diálogo de ajuste. */
        const val LADO_VISTA_PREVIA = 128
    }
}