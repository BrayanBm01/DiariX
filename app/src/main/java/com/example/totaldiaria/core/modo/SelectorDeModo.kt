package com.example.totaldiaria.core.modo

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.totaldiaria.R

/**
 * DIARIX · El interruptor entre las dos áreas.
 *
 * Un solo componente para las dos mitades, y UN solo sitio donde se decide:
 *
 *  - [haceFaltaElegirModo] responde si hay que preguntar el área al entrar.
 *  - [mostrarComoDialogo] abre "¿Qué quieres gestionar?" con las dos áreas,
 *    para quien ya está dentro de la app (Configuración).
 *  - [SeleccionDeModoActivity] es la pantalla completa de esa misma pregunta,
 *    la que aparece al entrar cuando todavía no se eligió área.
 *  - [cambiarA] deja el área elegida como vigente y entra a su inicio.
 *
 * Se pone en el código y no en un PreferenceFragment porque el cambio de área
 * NO es un ajuste más: es un cambio de contexto de trabajo, y para que sea
 * coherente hay que limpiar la pila (abrir con `CLEAR_TASK`). Si se dejara la
 * pantalla anterior en el historial, el botón "atrás" devolvería al usuario a
 * una pantalla del área que acaba de abandonar, con el menú del área nueva.
 *
 * Fase 0.1: la tira de área que abría este diálogo en cada pantalla se retiró
 * de la interfaz, y con ella el método que la pintaba. Este objeto **no se partió
 * ni se duplicó** al retirarla: sigue siendo el único que sabe guardar un modo y
 * entrar a su pantalla.
 *
 * Y hay una consecuencia que conviene tener presente cada vez que se añada una
 * pantalla nueva: [mostrarComoDialogo] se llama desde UN solo sitio,
 * `ConfiguracionActivity`. El cambio de área tiene que ser un acto deliberado en
 * un sitio concreto; por eso el botón "Ir a Facturación" de Préstamos también se
 * retiró, en lugar de dejarse como una segunda puerta.
 */
object SelectorDeModo {

    /**
     * ¿Hay que pedirle el área a esta persona?
     *
     * `true` solo cuando nunca se eligió un área ([AlmacenDeModo.modoGuardado]
     * devuelve `null`), que incluye el caso de una clave ilegible. En cuanto
     * hay un área guardada se entra directo a ella y no se vuelve a preguntar.
     *
     * Es una función PURA a propósito: la regla "¿preguntar o no?" es la regla
     * de negocio de la Fase 0.1 y tiene que poder comprobarse sin montar
     * pantallas ni activities.
     */
    fun haceFaltaElegirModo(modoGuardado: ModoDiarix?): Boolean =
        modoGuardado == null

    /** Abre "¿Qué quieres gestionar?" como diálogo, para usar dentro de la app. */
    fun mostrarComoDialogo(activity: AppCompatActivity) {

        val vigente = modoVigente(activity)

        val contenido = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_selector_modo, null, false)

        // El área actual se marca en la propia opción, no solo con el título:
        // el usuario tiene que ver DÓNDE está antes de decidir a dónde va.
        contenido.findViewById<View>(R.id.opcionFacturacion)
            ?.let { pintarEstado(it, vigente == ModoDiarix.FACTURACION) }

        contenido.findViewById<View>(R.id.opcionPrestamos)
            ?.let { pintarEstado(it, vigente == ModoDiarix.PRESTAMOS) }

        contenido.findViewById<View>(R.id.opcionFacturacion)
            ?.setOnClickListener { cambiarA(activity, ModoDiarix.FACTURACION) }

        contenido.findViewById<View>(R.id.opcionPrestamos)
            ?.setOnClickListener { cambiarA(activity, ModoDiarix.PRESTAMOS) }

        AlertDialog.Builder(activity)
            .setTitle(R.string.selector_modo_titulo)
            .setView(contenido)
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    /**
     * Cambia el área desde DENTRO de la app (Configuración).
     *
     * Si [modo] es el área en la que ya se está, no se hace nada: se está
     * reconstruyendo la pila para acabar en la misma pantalla de la que se
     * partió. Por eso la pantalla de selección inicial NO usa este método, sino
     * [elegirYEntrar]: allí elegir el área por defecto (Facturación) SÍ tiene
     * que guardar, porque es la primera vez y nadie lo ha escrito nunca.
     */
    fun cambiarA(activity: AppCompatActivity, modo: ModoDiarix) {

        if (modo == modoVigente(activity)) {
            // Ya se está en ese área: no se reconstruye la pila de nuevo.
            return
        }

        elegirYEntrar(activity, modo)
    }

    /**
     * Guarda [modo] como área elegida y entra a su inicio. SIEMPRE.
     *
     * Es el camino de la pantalla "¿Qué quieres gestionar?" del primer arranque:
     * se guarda aunque [modo] coincida con [ModoDiarix.INICIAL], porque ahí el
     * valor por defecto todavía no está guardado en ninguna parte y sin esta
     * escritura la app volvería a preguntar en el siguiente inicio.
     */
    fun elegirYEntrar(activity: Activity, modo: ModoDiarix) {

        AlmacenDeModo(activity).guardar(modo)

        abrir(activity, modo)
    }

    /**
     * Entra a la pantalla de inicio del área vigente.
     *
     * Se usa al abrir la app, para que DiariX arranque donde el usuario lo dejó
     * la última vez.
     */
    fun abrirInicio(activity: Activity) {

        abrir(activity, modoVigente(activity))
    }

    /**
     * Entra a la pantalla de inicio de [modo].
     *
     * `NEW_TASK | CLEAR_TASK` reemplaza la pila completa: la pantalla desde la
     * que se cambió de área y todas las anteriores desaparecen, así que el
     * botón "atrás" nunca devuelve a un área ajena.
     */
    private fun abrir(activity: Activity, modo: ModoDiarix) {

        activity.startActivity(
            Intent(activity, modo.pantallaDeInicio).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
            }
        )
    }

    /** El tipo de letra de la opción activa del selector. */
    private fun pintarEstado(opcion: View, activa: Boolean) {

        // Cada fila tiene su propia marca "VIGENTE": las dos viven en el mismo
        // layout y por eso sus ids son únicos (`txtMarca` y `txtMarcaPrestamos`).
        // Aquí se elige la de la fila que se está pintando por el id de la fila,
        // que sí es único — una fila para cada área.
        val idMarca = if (opcion.id == R.id.opcionFacturacion) {
            R.id.txtMarca
        } else {
            R.id.txtMarcaPrestamos
        }

        opcion.findViewById<TextView>(idMarca)
            ?.visibility = if (activa) View.VISIBLE else View.INVISIBLE

        opcion.setBackgroundResource(
            if (activa) R.drawable.bg_opcion_modo_activa
            else R.drawable.bg_opcion_modo
        )
    }
}