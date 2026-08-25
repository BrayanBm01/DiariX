package com.example.totaldiaria

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.totaldiaria.navigation.BottomNavigator
import com.example.totaldiaria.service.EstadisticasService
import com.example.totaldiaria.service.EstadisticasService.Periodo
import com.example.totaldiaria.ui.FormatoMoneda
import com.example.totaldiaria.ui.graficas.GraficaBarras
import com.example.totaldiaria.ui.graficas.GraficaBarrasHorizontales
import com.example.totaldiaria.ui.graficas.GraficaDona
import com.example.totaldiaria.ui.graficas.GraficaLineas

class EstadisticasActivity : AppCompatActivity() {

    private lateinit var estadisticasService: EstadisticasService

    private lateinit var txtTotalFacturas: TextView
    private lateinit var txtIngresosTotales: TextView
    private lateinit var txtPromedio: TextView
    private lateinit var txtMejorDia: TextView
    private lateinit var txtMejorDiaFecha: TextView

    private lateinit var graficaLineas: GraficaLineas
    private lateinit var graficaBarras: GraficaBarras
    private lateinit var graficaDona: GraficaDona
    private lateinit var graficaComparacion: GraficaBarrasHorizontales

    private lateinit var txtVacioLineas: TextView
    private lateinit var txtVaciasBarras: TextView
    private lateinit var txtVaciosMetodos: TextView
    private lateinit var contenedorLeyenda: LinearLayout
    private lateinit var cardComparacion: View
    private lateinit var cardFacturasPorMetodo: View
    private lateinit var txtCantSoloEfectivo: TextView
    private lateinit var txtCantSoloTransferencia: TextView
    private lateinit var txtCantAmbos: TextView

    private val botonesFiltro = mutableMapOf<Periodo, TextView>()

    private var periodoActual: Periodo = Periodo.HOY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_estadisticas)

        window.statusBarColor =
            ContextCompat.getColor(this, android.R.color.white)

        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = true

        estadisticasService = EstadisticasService(this)

        txtTotalFacturas = findViewById(R.id.txtTotalFacturas)
        txtIngresosTotales = findViewById(R.id.txtIngresosTotales)
        txtPromedio = findViewById(R.id.txtPromedio)
        txtMejorDia = findViewById(R.id.txtMejorDia)
        txtMejorDiaFecha = findViewById(R.id.txtMejorDiaFecha)

        graficaLineas = findViewById(R.id.graficaLineas)
        graficaBarras = findViewById(R.id.graficaBarras)
        graficaDona = findViewById(R.id.graficaDona)
        graficaComparacion = findViewById(R.id.graficaComparacion)

        txtVacioLineas = findViewById(R.id.txtVacioLineas)
        txtVaciasBarras = findViewById(R.id.txtVaciasBarras)
        txtVaciosMetodos = findViewById(R.id.txtVaciosMetodos)
        contenedorLeyenda = findViewById(R.id.contenedorLeyenda)
        cardComparacion = findViewById(R.id.cardComparacion)
        cardFacturasPorMetodo = findViewById(R.id.cardFacturasPorMetodo)
        txtCantSoloEfectivo = findViewById(R.id.txtCantSoloEfectivo)
        txtCantSoloTransferencia = findViewById(R.id.txtCantSoloTransferencia)
        txtCantAmbos = findViewById(R.id.txtCantAmbos)

        botonesFiltro[Periodo.HOY] = findViewById(R.id.btnFiltroHoy)
        botonesFiltro[Periodo.ULTIMOS_7] = findViewById(R.id.btnFiltro7)
        botonesFiltro[Periodo.ULTIMOS_30] = findViewById(R.id.btnFiltro30)
        botonesFiltro[Periodo.ESTE_MES] = findViewById(R.id.btnFiltroMes)

        for ((periodo, boton) in botonesFiltro) {

            boton.setOnClickListener {
                seleccionarPeriodo(periodo)
            }
        }

        BottomNavigator.configurar(this, R.id.nav_estadisticas)

        seleccionarPeriodo(Periodo.HOY)
    }

    override fun onResume() {
        super.onResume()
        BottomNavigator.sincronizarPestana(this, R.id.nav_estadisticas)
        cargarEstadisticas()
    }

    // ------------------------------------------------------------------
    // Filtros
    // ------------------------------------------------------------------

    private fun seleccionarPeriodo(periodo: Periodo) {

        periodoActual = periodo

        for ((clave, chip) in botonesFiltro) {

            val seleccionado = clave == periodo

            chip.setBackgroundResource(

                if (seleccionado) R.drawable.bg_tab_seleccionada
                else R.drawable.bg_chip_normal
            )

            chip.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (seleccionado) android.R.color.white
                    else R.color.gris_etiqueta
                )
            )
        }

        cargarEstadisticas()
    }

    // ------------------------------------------------------------------
    // Carga y presentación
    // ------------------------------------------------------------------

    private fun cargarEstadisticas() {

        val resumen =
            estadisticasService.obtenerResumen(periodoActual)

        txtTotalFacturas.text =
            resumen.totalFacturas.toString()

        txtIngresosTotales.text =
            FormatoMoneda.formatear(resumen.ingresosTotales)

        txtPromedio.text =
            FormatoMoneda.formatear(resumen.promedioPorFactura)

        txtMejorDia.text =
            FormatoMoneda.formatearCompacto(resumen.mejorDiaTotal)

        txtMejorDiaFecha.text =

            resumen.mejorDiaFecha
                ?.let { estadisticasService.etiquetaCorta(it) }
                ?: "—"

        txtCantSoloEfectivo.text = resumen.cantidadSoloEfectivo.toString()
        txtCantSoloTransferencia.text = resumen.cantidadSoloTransferencia.toString()
        txtCantAmbos.text = resumen.cantidadAmbos.toString()

        val hayFacturasPorMetodo = resumen.totalFacturas > 0
        cardFacturasPorMetodo.visibility =
            if (hayFacturasPorMetodo) View.VISIBLE else View.GONE

        pintarGraficasDiarias(resumen)

        pintarMetodosDePago(resumen)
    }

    /**
     * Una única lista agrupada por día alimenta la gráfica de líneas
     * (totales) y la de barras (cantidades).
     */
    private fun pintarGraficasDiarias(
        resumen: EstadisticasService.Resumen
    ) {

        val dias = resumen.actividadPorDia

        if (dias.isEmpty()) {

            graficaLineas.visibility = View.GONE
            txtVacioLineas.visibility = View.VISIBLE

            graficaBarras.visibility = View.GONE
            txtVaciasBarras.visibility = View.VISIBLE

            return
        }

        val etiquetas = dias.map {
            estadisticasService.etiquetaCorta(it.fecha)
        }

        graficaLineas.visibility = View.VISIBLE
        txtVacioLineas.visibility = View.GONE

        graficaLineas.setDatos(
            dias.mapIndexed { indice, dia ->
                GraficaLineas.Punto(etiquetas[indice], dia.total)
            }
        )

        graficaBarras.visibility = View.VISIBLE
        txtVaciasBarras.visibility = View.GONE

        graficaBarras.setDatos(
            dias.mapIndexed { indice, dia ->
                GraficaBarras.Barra(etiquetas[indice], dia.cantidadFacturas)
            }
        )
    }

    private fun pintarMetodosDePago(
        resumen: EstadisticasService.Resumen
    ) {

        contenedorLeyenda.removeAllViews()

        val metodos = resumen.metodosPago

        if (metodos.isEmpty()) {

            graficaDona.visibility = View.GONE
            txtVaciosMetodos.visibility = View.VISIBLE

            cardComparacion.visibility = View.GONE

            return
        }

        graficaDona.visibility = View.VISIBLE
        txtVaciosMetodos.visibility = View.GONE

        val segmentos = metodos.map { metodo ->

            val color =

                if (metodo.nombre == "Efectivo") R.color.verde_pago
                else R.color.azul_pago

            GraficaDona.Segmento(
                metodo.nombre,
                metodo.monto,
                ContextCompat.getColor(this, color)
            )
        }

        graficaDona.setDatos(segmentos)

        for (metodo in metodos) {

            contenedorLeyenda.addView(filaLeyenda(metodo))
        }

        cardComparacion.visibility = View.VISIBLE

        graficaComparacion.setDatos(
            metodos.map { metodo ->

                val color =

                    if (metodo.nombre == "Efectivo") R.color.verde_pago
                    else R.color.azul_pago

                GraficaBarrasHorizontales.Fila(
                    metodo.nombre,
                    metodo.monto,
                    ContextCompat.getColor(this, color)
                )
            }
        )
    }

    /**
     * Fila de leyenda: punto de color, nombre, monto y porcentaje.
     */
    private fun filaLeyenda(
        metodo: EstadisticasService.MetodoPago
    ): LinearLayout {

        val color =

            if (metodo.nombre == "Efectivo") R.color.verde_pago
            else R.color.azul_pago

        val fila = LinearLayout(this)

        fila.orientation = LinearLayout.HORIZONTAL

        fila.gravity = Gravity.CENTER_VERTICAL

        fila.setPadding(0, dp(6), 0, dp(6))

        val punto = View(this)

        val fondoPunto = GradientDrawable().apply {

            shape = GradientDrawable.OVAL

            setColor(ContextCompat.getColor(this@EstadisticasActivity, color))
        }

        punto.background = fondoPunto

        punto.layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))

        fila.addView(punto)

        val nombre = TextView(this)

        nombre.text = metodo.nombre

        nombre.setTextSize(13f)

        nombre.setTextColor(
            ContextCompat.getColor(this, R.color.texto_principal_dark)
        )

        nombre.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ).apply { marginStart = dp(8) }

        fila.addView(nombre)

        val monto = TextView(this)

        monto.text = FormatoMoneda.formatear(metodo.monto)

        monto.setTextSize(13f)

        monto.setTypeface(null, android.graphics.Typeface.BOLD)

        monto.setTextColor(
            ContextCompat.getColor(this, R.color.texto_principal_dark)
        )

        fila.addView(monto)

        val porcentaje = TextView(this)

        porcentaje.text = " ${metodo.porcentaje.toInt()}%"

        porcentaje.setTextSize(12f)

        porcentaje.setTextColor(
            ContextCompat.getColor(this, R.color.gris_etiqueta)
        )

        fila.addView(porcentaje)

        return fila
    }

    private fun dp(valor: Int): Int =

        (valor * resources.displayMetrics.density).toInt()
}
