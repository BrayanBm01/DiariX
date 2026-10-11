package com.example.totaldiaria.modulos.facturacion.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.Diarix
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.facturacion.modelo.Cliente
import com.example.totaldiaria.modulos.facturacion.modelo.DatosDeCliente
import com.example.totaldiaria.network.ErrorDiarix
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * DIARIX · Fase 2 · Alta, edición y borrado de un cliente (RF-01, RF-03, RF-04).
 *
 * UNA sola pantalla para las tres operaciones. Tener dos Activities casi
 * idénticas (Alta / Edición) es la forma más segura de que un día se añada un
 * campo a una y se olvide en la otra, y el usuario ve las dos formas del mismo
 * formulario y no entiende cuál es cuál.
 *
 * El modo se decide por [EXTRA_CLIENTE_ID]: sin id es alta, con id se trae la
 * ficha con `GET /clientes/:id` y se rellena.
 *
 * VALIDACIÓN: solo se comprueban localmente los cuatro campos obligatorios
 * con la MISMA regla que el servidor (recortar y mirar si queda algo). Todo lo
 * demás — formato de correo, longitud máxima, catálogo de tipos de documento
 * — lo decide el backend, que es quien pone las reglas: repetirlas aquí
 * produciría dos respuestas distintas para el mismo dato.
 *
 * Los errores que vienen del servidor se muestran TAL CUAL y no se reescriben.
 * Cuando el backend manda `detalles`, la lista entera va en el aviso: son
 * frases como "Los nombres deben tener al menos 3 caracteres.", que es
 * exactamente lo que el usuario necesita leer para arreglarlo.
 */
class ClienteFormularioActivity : AppCompatActivity() {

    private lateinit var campoNombres: TextInputLayout
    private lateinit var campoApellidos: TextInputLayout
    private lateinit var campoDocumento: TextInputLayout
    private lateinit var edtNombres: TextInputEditText
    private lateinit var edtApellidos: TextInputEditText
    private lateinit var edtNumeroDocumento: TextInputEditText
    private lateinit var edtEmail: TextInputEditText
    private lateinit var edtTelefono: TextInputEditText
    private lateinit var edtDireccion: TextInputEditText
    private lateinit var edtReferencias: TextInputEditText
    private lateinit var edtObservaciones: TextInputEditText
    private lateinit var spinnerTipo: Spinner
    private lateinit var aviso: TextView
    private lateinit var btnGuardar: Button
    private lateinit var btnEliminar: Button

    /** `null` = alta. Con valor, es el id de la ficha que se está editando. */
    private var clienteId: String? = null

    /** Ficha ya cargada, para poder guardar sin traerla otra vez. */
    private var clienteActual: Cliente? = null

    private var guardando = false

    /** Cada petición sube el contador; una respuesta vieja se descarta. */
    private var versionDeCarga = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cliente_formulario)

        Diarix.iniciar(this)

        window.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        clienteId = intent.getStringExtra(EXTRA_CLIENTE_ID)

        vincularVistas()
        prepararTipoDeDocumento()

        if (clienteId == null) {
            txt(R.id.txtTituloFormulario).text = "Nuevo cliente"
            btnEliminar.visibility = Button.GONE
        } else {
            txt(R.id.txtTituloFormulario).text = "Editar cliente"
            btnEliminar.visibility = Button.VISIBLE
            cargarFicha()
        }

        btnGuardar.setOnClickListener { guardar() }
        btnEliminar.setOnClickListener { confirmarEliminacion() }
    }

    // ==========================================================================
    // Carga de la ficha
    // ==========================================================================

    private fun cargarFicha() {
        val id = clienteId ?: return
        val version = ++versionDeCarga

        habilitar(false, "Cargando…")
        ocultarAviso()

        Diarix.enHiloDeFondo {
            try {
                val cliente = Diarix.clientes.obtener(id)

                runOnUiThread {
                    if (version != versionDeCarga) return@runOnUiThread
                    clienteActual = cliente
                    rellenar(cliente)
                    habilitar(true, "Guardar cliente")
                }
            } catch (e: ErrorDiarix) {
                fallar(version, e)
            } catch (e: Exception) {
                fallar(version, ErrorDiarix.Inesperado(e.message ?: ""))
            }
        }
    }

    private fun rellenar(cliente: Cliente) {
        edtNombres.setText(cliente.nombres)
        edtApellidos.setText(cliente.apellidos)
        edtNumeroDocumento.setText(cliente.numeroDocumento)
        edtEmail.setText(cliente.email.orEmpty())
        edtTelefono.setText(cliente.telefono.orEmpty())
        edtDireccion.setText(cliente.direccion.orEmpty())
        edtReferencias.setText(cliente.referencias.orEmpty())
        edtObservaciones.setText(cliente.observaciones.orEmpty())

        val posicion = Cliente.TIPOS_DE_DOCUMENTO.indexOf(cliente.tipoDocumento)
        if (posicion >= 0) spinnerTipo.setSelection(posicion)

        txt(R.id.txtSubtituloFormulario).text = cliente.documentoConEtiqueta()
    }

    // ==========================================================================
    // Guardado
    // ==========================================================================

    private fun guardar() {
        if (guardando) return

        val nombres = obligatorio(edtNombres, campoNombres, "los nombres") ?: return
        val apellidos = obligatorio(edtApellidos, campoApellidos, "los apellidos") ?: return
        val documento = obligatorio(
            edtNumeroDocumento, campoDocumento, "el número de documento"
        ) ?: return

        val tipo = Cliente.TIPOS_DE_DOCUMENTO[spinnerTipo.selectedItemPosition]

        val datos = DatosDeCliente(
            nombres = nombres,
            apellidos = apellidos,
            tipoDocumento = tipo,
            numeroDocumento = documento,
            email = edtEmail.text?.toString()?.trim()?.ifEmpty { null },
            telefono = edtTelefono.text?.toString()?.trim()?.ifEmpty { null },
            direccion = edtDireccion.text?.toString()?.trim()?.ifEmpty { null },
            referencias = edtReferencias.text?.toString()?.trim()?.ifEmpty { null },
            observaciones = edtObservaciones.text?.toString()?.trim()?.ifEmpty { null },
        )

        val id = clienteId
        val version = ++versionDeCarga

        ocultarAviso()
        habilitar(false, if (id == null) "Creando…" else "Guardando…")

        Diarix.enHiloDeFondo {
            try {
                val guardado = if (id == null) {
                    Diarix.clientes.crear(datos)
                } else {
                    Diarix.clientes.actualizar(id, datos)
                }

                runOnUiThread {
                    if (version != versionDeCarga) return@runOnUiThread

                    val mensaje = if (id == null) {
                        "Cliente creado."
                    } else {
                        "Cliente actualizado."
                    }

                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: ErrorDiarix) {
                fallar(version, e)
            } catch (e: Exception) {
                fallar(version, ErrorDiarix.Inesperado(e.message ?: ""))
            }
        }
    }

    // ==========================================================================
    // Eliminación (RF-04)
    // ==========================================================================

    /**
     * Pide confirmación con el nombre COMPLETO del cliente en pantalla.
     *
     * No se comprueba nada más antes de borrar: la única regla que existe
     * (que no tenga préstamos activos) se decide en el servidor, porque es
     * el servidor quien sabe si los hay. Reproducirla aquí sería inventar una
     * respuesta que a veces sería falsa.
     */
    private fun confirmarEliminacion() {
        val cliente = clienteActual
        val id = clienteId ?: return

        val nombre = cliente?.nombreCompleto ?: "este cliente"
        val documento = cliente?.documentoConEtiqueta() ?: ""

        AlertDialog.Builder(this)
            .setTitle("Eliminar cliente")
            .setMessage(
                if (documento.isEmpty()) {
                    "Se eliminará a $nombre. Esta acción no se puede deshacer."
                } else {
                    "Se eliminará a $nombre ($documento). " +
                        "Esta acción no se puede deshacer."
                }
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ -> eliminar(id) }
            .show()
    }

    private fun eliminar(id: String) {
        if (guardando) return

        val version = ++versionDeCarga

        ocultarAviso()
        habilitar(false, "Eliminando…")

        Diarix.enHiloDeFondo {
            try {
                Diarix.clientes.eliminar(id)

                runOnUiThread {
                    if (version != versionDeCarga) return@runOnUiThread
                    Toast.makeText(this, "Cliente eliminado.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: ErrorDiarix) {
                fallar(version, e)
            } catch (e: Exception) {
                fallar(version, ErrorDiarix.Inesperado(e.message ?: ""))
            }
        }
    }

    // ==========================================================================
    // Utilidades de UI
    // ==========================================================================

    /**
     * Comprueba un campo obligatorio con la MISMA regla que el servidor:
     * recortar los espacios y ver si queda algo.
     *
     * Devuelve `null` cuando el campo no sirve, que es lo que corta el guardado.
     */
    private fun obligatorio(
        campo: TextInputEditText,
        contenedor: TextInputLayout,
        etiqueta: String,
    ): String? {
        val valor = campo.text?.toString()?.trim().orEmpty()

        if (valor.isEmpty()) {
            contenedor.error = "Debe indicar $etiqueta."
            contenedor.requestFocus()
            return null
        }

        contenedor.error = null
        return valor
    }

    private fun fallar(version: Int, e: ErrorDiarix) {
        runOnUiThread {
            if (version != versionDeCarga) return@runOnUiThread

            habilitar(true, "Guardar cliente")

            val detalles = if (e is ErrorDiarix.DeBackend && e.detalles.isNotEmpty()) {
                "\n\n" + e.detalles.joinToString("\n")
            } else {
                ""
            }

            mostrarAviso(e.mensajeAmigable + detalles)
        }
    }

    private fun mostrarAviso(texto: String) {
        aviso.text = texto
        aviso.visibility = TextView.VISIBLE
    }

    private fun ocultarAviso() {
        aviso.visibility = TextView.GONE
    }

    /**
     * Un solo interruptor para los dos botones.
     *
     * Mientras se manda algo, lo que NO debe poder pasarse dos veces es el
     * botón de Guardar; el de Eliminar queda cortado también porque mandar un
     * DELETE mientras el POST anterior sigue en vuelo dejaría la ficha en un
     * estado imposible de explicar.
     */
    private fun habilitar(si: Boolean, textoGuardar: String) {
        guardando = !si
        btnGuardar.isEnabled = si
        btnEliminar.isEnabled = si
        btnGuardar.text = textoGuardar
    }

    private fun txt(id: Int): TextView = findViewById(id)

    private fun prepararTipoDeDocumento() {
        val etiquetas = Cliente.TIPOS_DE_DOCUMENTO.map { Cliente.etiquetaDeDocumento(it) }

        spinnerTipo.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            etiquetas,
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun vincularVistas() {
        campoNombres = findViewById(R.id.campoNombres)
        campoApellidos = findViewById(R.id.campoApellidos)
        campoDocumento = findViewById(R.id.campoNumeroDocumento)
        edtNombres = findViewById(R.id.edtNombres)
        edtApellidos = findViewById(R.id.edtApellidos)
        edtNumeroDocumento = findViewById(R.id.edtNumeroDocumento)
        edtEmail = findViewById(R.id.edtEmail)
        edtTelefono = findViewById(R.id.edtTelefono)
        edtDireccion = findViewById(R.id.edtDireccion)
        edtReferencias = findViewById(R.id.edtReferencias)
        edtObservaciones = findViewById(R.id.edtObservaciones)
        spinnerTipo = findViewById(R.id.spinnerTipoDocumento)
        aviso = findViewById(R.id.txtErrorFormulario)
        btnGuardar = findViewById(R.id.btnGuardarCliente)
        btnEliminar = findViewById(R.id.btnEliminarCliente)
    }

    companion object {

        /** Abre el formulario en blanco, para dar de alta. */
        fun nueva(context: Context): Intent =
            Intent(context, ClienteFormularioActivity::class.java)

        /** Abre el formulario con la ficha [id] cargada, para editar. */
        fun editar(context: Context, id: String): Intent =
            Intent(context, ClienteFormularioActivity::class.java)
                .putExtra(EXTRA_CLIENTE_ID, id)

        private const val EXTRA_CLIENTE_ID = "cliente_id"
    }
}
