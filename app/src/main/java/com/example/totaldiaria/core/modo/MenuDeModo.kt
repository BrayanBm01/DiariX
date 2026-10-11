package com.example.totaldiaria.core.modo

import androidx.annotation.MenuRes
import com.example.totaldiaria.R

/**
 * Qué menú inferior corresponde a cada área.
 *
 * Es el mecanismo que hace que las pantallas de un área NO aparezcan en el menú
 * de la otra: el menú se infla desde el código según el modo vigente, no desde
 * `app:menu` en el layout.
 *
 * Que el menú esté en el código y no en el XML no es un capricho: `app:menu` es
 * estático, se declara una vez por layout y es el MISMO para las seis
 * pantallas de facturación. Si el menú fuese del XML, habría que poner un
 * `app:menu="@null"` en cada layout nuevo y acordarse de inflarlo bien, y el
 * primer olvido mostraría las pestañas equivocadas en el área ajena.
 */
object MenuDeModo {

    @MenuRes
    fun de(modo: ModoDiarix): Int = when (modo) {
        ModoDiarix.FACTURACION -> R.menu.menu_facturacion
        ModoDiarix.PRESTAMOS -> R.menu.menu_prestamos
    }
}