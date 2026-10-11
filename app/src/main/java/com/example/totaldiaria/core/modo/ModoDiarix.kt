package com.example.totaldiaria.core.modo

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import com.example.totaldiaria.R
import com.example.totaldiaria.modulos.facturacion.ui.MainActivity
import com.example.totaldiaria.modulos.prestamos.ui.PrestamosInicioActivity

/**
 * DIARIX · Fase 0 · Áreas de trabajo (modos).
 *
 * DiariX son DOS sistemas que viven en la MISMA aplicación, y nunca se
 * mezclan:
 *
 *  - [FACTURACION] — el DiariX actual: registrar la factura del día, historial,
 *    registros, estadísticas y configuración. No cambia en esta fase.
 *  - [PRESTAMOS] — el sistema nuevo: clientes, préstamos, cuotas, cobros,
 *    pagos y dashboard. En la Fase 0 solo existe su pantalla de inicio.
 *
 * Este enum es la ÚNICA fuente de verdad de en qué área se está trabajando:
 * de aquí salen el menú inferior que se infla, la etiqueta que se pinta en el
 * selector y la pantalla a la que se entra. No hay un segundo sitio donde se
 * decida el modo, porque dos sitios se desincronizan.
 *
 * La lista es cerrada y corta a propósito: es el mapa de la aplicación, no un
 * registro dinámico. Cuando llegue un módulo nuevo se AGREGA AQUÍ su área, y
 * en ningún otro.
 *
 * [pantallaDeInicio] es el único punto donde `core` conoce clases concretas de
 * los módulos. Es el precio de mantener el ruteo en un único lugar; el resto de
 * la app pregunta "¿en qué área estoy?" y nunca "¿a qué Activity voy?".
 */
enum class ModoDiarix(
    /** Clave estable que se persiste; nunca se renombra (habría que migrar). */
    val clave: String,
    @param:StringRes val titulo: Int,
    @param:StringRes val resumen: Int,
    val pantallaDeInicio: Class<out AppCompatActivity>,
) {

    FACTURACION(
        clave = "facturacion",
        titulo = R.string.modo_facturacion,
        resumen = R.string.modo_facturacion_resumen,
        pantallaDeInicio = MainActivity::class.java,
    ),

    PRESTAMOS(
        clave = "prestamos",
        titulo = R.string.modo_prestamos,
        resumen = R.string.modo_prestamos_resumen,
        pantallaDeInicio = PrestamosInicioActivity::class.java,
    ),

    ;

    companion object {

        /**
         * Área en la que entra alguien que nunca eligió.
         *
         * FACTURACIÓN a propósito: DiariX hasta hoy ES facturación, así que
         * quien actualice la app (y quien ya la usaba) sigue viendo
         * exactamente lo mismo, sin elegir nada. PRÉSTAMOS se elige
         * conscientemente desde el selector.
         */
        val INICIAL: ModoDiarix = FACTURACION

        /** De una clave persistida a su área; `null` si no se reconoce. */
        fun desdeClave(clave: String?): ModoDiarix? =
            entries.firstOrNull { it.clave == clave }

        /**
         * Igual que [desdeClave] pero nunca falla: una clave desconocida o
         * corrupta devuelve [INICIAL]. Un modo ilegible no puede dejar la app
         * sin pantalla de inicio.
         */
        fun desdeClaveOInicial(clave: String?): ModoDiarix =
            desdeClave(clave) ?: INICIAL
    }
}