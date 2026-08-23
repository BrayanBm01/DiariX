package com.example.totaldiaria.ui

import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText

class MonedaTextWatcher(
    private val editText: EditText,
    private val alCambiar: () -> Unit = {}
) : TextWatcher {

    private var textoFormateadoActual = ""

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) {

        if (s.toString() == textoFormateadoActual) {

            alCambiar()
            return
        }

        editText.removeTextChangedListener(this)

        val digitos = s.toString()
            .replace(".", "")
            .replace(",", "")

        textoFormateadoActual =
            if (digitos.isNotEmpty()) {
                FormatoMoneda.paraEdicion(
                    (digitos.toLongOrNull() ?: 0L).toDouble()
                )
            } else {
                ""
            }

        if (digitos.isNotEmpty()) {
            editText.setText(textoFormateadoActual)
            editText.setSelection(textoFormateadoActual.length)
        }

        editText.addTextChangedListener(this)

        alCambiar()
    }
}
