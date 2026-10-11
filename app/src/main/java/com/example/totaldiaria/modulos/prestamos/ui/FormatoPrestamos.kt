package com.example.totaldiaria.modulos.prestamos.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import java.math.BigDecimal
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * DIARIX · Fase 3 · Formato de dinero y fechas del módulo de préstamos.
 *
 * Es de este módulo y no el `FormatoMoneda` de facturación a propósito: el de
 * facturación trabaja con `Double` y este módulo NO toca coma flotante para
 * el dinero. Aquí lo único que se pasa por `double` es el dibujo de la cifra
 * (`NumberFormat` de Java no sabe formatear BigDecimal sin perder centavos en
 * algunos dispositivos), nunca el cálculo: lo que se pinta sale de un
 * [BigDecimal] y el resultado de una cifra con centavos sale redondeado a 2
 * decimales en la representación.
 */
object FormatoPrestamos {

    private val locale = Locale("es", "CO")

    /**
     * `$1.000.000`. Sin centavos cuando el valor no tiene, con los dos cuando
     * sí (una cuota de 336.666,66 no puede pintarse como 336.667).
     */
    fun formatear(valor: BigDecimal): String {
        val conCentavos = valor.setScale(2).remainder(BigDecimal.ONE)
            .compareTo(BigDecimal.ZERO) != 0

        val formato = NumberFormat.getNumberInstance(locale).apply {
            if (conCentavos) {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            } else {
                minimumFractionDigits = 0
                maximumFractionDigits = 0
            }
        }

        return "$" + formato.format(valor.toDouble())
    }

    /** `2026-10-08` → `8 oct 2026`, en español. Corto para el listado. */
    fun formatearFecha(dia: String): String {
        if (dia.isBlank()) return ""

        return runCatching {
            val formato = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fecha = formato.parse(dia) ?: return@runCatching ""
            SimpleDateFormat("d MMM yyyy", locale).format(fecha)
        }.getOrDefault(dia)
    }

    /** Pone `hoy` (reloj del dispositivo) en `yyyy-MM-dd`. */
    fun hoy(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())

    /**
     * Parsea el texto de un campo de monto: solo dígitos (el watcher separa
     * los miles en pantalla), a [BigDecimal] exacto. Campos de pesos enteros:
     * los centavos se admiten, se piden y se muestran en la vista previa y en
     * el calendario, no en el teclado del monto.
     */
    fun parsear(texto: String): BigDecimal {
        val digitos = texto.replace(Regex("[^0-9]"), "")
        if (digitos.isEmpty()) return BigDecimal.ZERO
        return runCatching { BigDecimal(digitos) }.getOrDefault(BigDecimal.ZERO)
    }
}

/**
 * Watcher de montos del módulo: solo dígitos con separador de miles es-CO.
 *
 * Mismo comportamiento que `MonedaTextWatcher` de facturación, pero leyendo y
 * entregando [BigDecimal] para este módulo. La separación de miles en
 * pantalla hace imposible *escribir* un separador decimal "a mano" y que se
 * interprete mal: quien teclea `1500.5` produce `1500` y el parseo siguiente
 * es siempre limpio.
 */
class MonedaPrestamoTextWatcher(
    private val editText: EditText,
    private val alCambiar: () -> Unit = {},
) : TextWatcher {

    private var textoFormateadoActual = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {
        if (s == null) return
        if (s.toString() == textoFormateadoActual) {
            alCambiar()
            return
        }

        editText.removeTextChangedListener(this)

        val digitos = s.toString().replace(Regex("[^0-9]"), "")

        textoFormateadoActual = if (digitos.isNotEmpty()) {
            val valor = BigDecimal(digitos)
            val formato = NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
                maximumFractionDigits = 0
            }
            formato.format(valor)
        } else {
            ""
        }

        // Formatear *dentro* de afterTextChanged es frágil en el dispositivo: el
        // Editable que llega es el SpannableBuilder de emoji2 (mutarlo en el
        // sitio no llega a pintar), y `setText` mientras el IME está cometiendo
        // el tecleo pierde dígitos y puede lanzar el `IndexOutOfBoundsException`
        // conocido. Se difiere la mutación al final de la cola de mensajes (post),
        // donde ya no hay un commit en curso; y se recalcula desde el texto vivo
        // para que, aunque se encolen varios posts, el último deje el campo bien
        // formateado sin perder ninguna tecla.
        val watcher: TextWatcher = this
        editText.post {
            val actual = editText.text?.toString().orEmpty()
            val digitosActuales = actual.replace(Regex("[^0-9]"), "")
            val formateado = if (digitosActuales.isNotEmpty()) {
                val valor = BigDecimal(digitosActuales)
                NumberFormat.getNumberInstance(Locale("es", "CO")).apply {
                    maximumFractionDigits = 0
                }.format(valor)
            } else {
                ""
            }
            if (formateado != actual) {
                editText.removeTextChangedListener(watcher)
                runCatching {
                    editText.setText(formateado)
                    editText.setSelection(editText.length())
                }
                editText.addTextChangedListener(watcher)
            }
        }

        editText.addTextChangedListener(this)

        alCambiar()
    }
}