package com.example.totaldiaria.modulos.prestamos.modelo

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * DIARIX · Fase 3 · Préstamos — modelo del lado de la app
 * ============================================================================
 * Lo que llega de `GET/POST /prestamos`. El dinero es [BigDecimal] SIEMPRE
 * en este módulo: el backend es la única fuente de las cifras (las calcula
 * con Decimal y las envía desde NUMERIC(14,2)), y aquí se repiten sin tocar
 * coma flotante. `Double` queda para los módulos viejos; un préstamo es un
 * acuerdo financiero y sus centavos importan (una cuota de 336.666,66 que se
 * convirtiera en 336.666,6599999 dejaría un saldo que no cuadra).
 *
 * `cliente` es `null` SOLO para un préstamo cerrado cuyo cliente se eliminó
 * (la matriz de borrado quita la referencia pero conserva el registro): la
 * ficha debe seguir leyéndose y decir "Cliente eliminado".
 */
data class ClienteDePrestamo(
    val id: String,
    val nombreCompleto: String,
    val tipoDocumento: String,
    val numeroDocumento: String,
) {
    /** `CC 1020304050`, con la etiqueta del tipo. Misma idea que `Cliente`. */
    fun documentoConEtiqueta(): String =
        "${ClienteDePrestamo.etiquetaDeDocumento(tipoDocumento)} $numeroDocumento"

    companion object {
        private val ETIQUETAS = mapOf(
            "CC" to "Cédula de ciudadanía",
            "CE" to "Cédula de extranjería",
            "NIT" to "NIT",
            "TI" to "Tarjeta de identidad",
            "PASAPORTE" to "Pasaporte",
            "RUT" to "RUT",
        )

        fun etiquetaDeDocumento(tipo: String): String = ETIQUETAS[tipo] ?: tipo
    }
}

/** Fila del listado y de la ficha: el acuerdo + el estado derivado. */
data class Prestamo(
    val id: String,
    val empresaId: String?,
    val cliente: ClienteDePrestamo?,
    val modalidad: String,
    val tipoCalculo: String,
    /** `null` = acuerdo por TOTAL (no hay porcentaje), nunca 0. */
    val porcentaje: BigDecimal?,
    val fechaInicio: String,
    val montoPrestado: BigDecimal,
    val totalAPagar: BigDecimal,
    val valorCuota: BigDecimal,
    val numeroDeCuotas: Int,
    val estado: String,
    val totalPagado: BigDecimal,
    val saldoPendiente: BigDecimal,
    val cuotasPagadas: Int,
    val cuotasPendientes: Int,
    val creadoEn: String,
    val actualizadoEn: String,
)

/** Una cuota del calendario (RF-07), tal como la generó el servidor. */
data class CuotaDePrestamo(
    val numero: Int,
    val fechaVencimiento: String,
    val valorEsperado: BigDecimal,
    val valorPagado: BigDecimal,
    val saldo: BigDecimal,
    val estado: String,
)

/** Detalle de un préstamo: la ficha + el atraso + el calendario completo. */
data class DetalleDePrestamo(
    val prestamo: Prestamo,
    val nota: String?,
    val diasDeAtraso: Int,
    val numeroDeCuotasAtrasadas: Int,
    val valorAtrasado: BigDecimal,
    val cuotas: List<CuotaDePrestamo>,
)

/** Sobre paginado del listado, como lo envía el servidor. */
data class PaginaDePrestamos(
    val datos: List<Prestamo>,
    val total: Int,
    val pagina: Int,
    val porPagina: Int,
    val totalPaginas: Int,
)

/**
 * Cuerpo del alta (RF-05/RF-06). Los campos de dinero van en [BigDecimal];
 * quién los convierte a JSON es [PrestamosApi], no la pantalla.
 */
data class DatosDePrestamoNuevo(
    val clienteId: String,
    val fechaInicio: String,
    val monto: BigDecimal,
    val modalidad: String,
    val numeroDeCuotas: Int,
    val tipoCalculo: String,
    /** Obligatorio con PORCENTAJE; prohibido con TOTAL. */
    val porcentaje: BigDecimal? = null,
    /** Obligatorio con TOTAL; prohibido con PORCENTAJE. */
    val totalAPagar: BigDecimal? = null,
)

/**
 * Vista previa LOCAL del acuerdo, solo para acompañar al formulario.
 *
 * El cálculo AUTORITATIVO (y el calendario) lo hace el backend al registrar:
 * esto no es más que la ayuda que permite ver "1000000 al 20 % en 12 cuotas
 * = $1.200.000 en cuotas de $100.000" antes de mandar nada.
 */
data class CalculoDePrestamo(
    val totalAPagar: BigDecimal,
    val valorCuota: BigDecimal,
    /** Los centavos que la última cuota absorbe por el redondeo (puede ser 0). */
    val residuoUltimaCuota: BigDecimal,
)

/** Catálogos cerrados y presentación de estados. Nada de esto se inventa aquí. */
object EstadosDePrestamo {

    // Estados del PRÉSTAMO (RF-09). CANCELADO nace en la Fase 4 (anulación);
    // en esta fase nunca se verá, pero el catálogo completo llega del backend.
    const val PENDIENTE_DE_INICIO = "PENDIENTE_DE_INICIO"
    const val ACTIVO = "ACTIVO"
    const val PAGADO = "PAGADO"
    const val VENCIDO = "VENCIDO"
    const val CANCELADO = "CANCELADO"

    // Estados de la CUOTA (RF-07).
    const val PENDIENTE = "PENDIENTE"
    const val PAGADA = "PAGADA"
    const val PAGO_PARCIAL = "PAGO_PARCIAL"
    const val VENCIDA = "VENCIDA"

    /** Filtros del listado, en el orden de la pantalla. */
    val FILTROS = listOf(
        "TODOS" to "Todos",
        ACTIVO to "Activos",
        VENCIDO to "Vencidos",
        PENDIENTE_DE_INICIO to "Por iniciar",
        PAGADO to "Pagados",
        CANCELADO to "Cancelados",
    )

    val ETIQUETAS = mapOf(
        PENDIENTE_DE_INICIO to "Por iniciar",
        ACTIVO to "Activo",
        PAGADO to "Pagado",
        VENCIDO to "Vencido",
        CANCELADO to "Cancelado",
        PENDIENTE to "Pendiente",
        PAGADA to "Pagada",
        PAGO_PARCIAL to "Pago parcial",
        VENCIDA to "Vencida",
    )

    fun etiqueta(estado: String): String = ETIQUETAS[estado] ?: estado

    /** Color de texto para el chip de estado. Colores del área: azul, verde y rojo. */
    fun color(estado: String): String = when (estado) {
        ACTIVO -> "#2B6ED4"
        PAGADO, PAGADA -> "#0AA87A"
        VENCIDO, VENCIDA -> "#D43D3D"
        PENDIENTE_DE_INICIO, PENDIENTE, CANCELADO -> "#8A8FA3"
        PAGO_PARCIAL -> "#C97B10"
        else -> "#8A8FA3"
    }
}

object ModalidadesDePrestamo {
    const val DIARIO = "DIARIO"
    const val SEMANAL = "SEMANAL"
    const val QUINCENAL = "QUINCENAL"
    const val MENSUAL = "MENSUAL"

    /** Mismo orden que `modalidad` en `prestamos.dto.ts` del backend. */
    val LISTA = listOf(DIARIO, SEMANAL, QUINCENAL, MENSUAL)

    val ETIQUETAS = mapOf(
        DIARIO to "Diario",
        SEMANAL to "Semanal",
        QUINCENAL to "Quincenal",
        MENSUAL to "Mensual",
    )

    fun etiqueta(modalidad: String): String = ETIQUETAS[modalidad] ?: modalidad
}

object TiposDeCalculoPrestamo {
    const val PORCENTAJE = "PORCENTAJE"
    const val TOTAL = "TOTAL"
    val LISTA = listOf(PORCENTAJE, TOTAL)
}

/**
 * Cálculo de la vista previa, con BigDecimal y las MISMAS reglas que el
 * backend (`financiero.util.ts`): total desde porcentaje, cuota uniforme
 * truncada a centavos y el residuo que absorbe la última cuota.
 *
 * Devuelve [CalculoDePrestamo] (el tipo) y no es el tipo en sí, para que el
 * formulario pueda pintar un resultado y el test pueda comparar centavos.
 */
object CalculoDePrestamoUtil {

    const val MAXIMO_NUMERO_DE_CUOTAS = 60

    fun totalDesdePorcentaje(monto: BigDecimal, porcentaje: BigDecimal): BigDecimal {
        val factor = BigDecimal.ONE.add(
            porcentaje.divide(BigDecimal(100), 6, RoundingMode.HALF_UP),
        )
        return monto.multiply(factor).setScale(2, RoundingMode.HALF_UP)
    }

    /**
     * Reparte un total en `numeroDeCuotas`. `valorCuota` se trunca a centavos
     * (ROUND_DOWN, igual que el backend) y la última cuota suma el residuo.
     */
    fun repartir(total: BigDecimal, numeroDeCuotas: Int): CalculoDePrestamo {
        val porCuota = total.divide(
            BigDecimal.valueOf(numeroDeCuotas.toLong()),
            2,
            RoundingMode.DOWN,
        )
        val resto = total.subtract(
            porCuota.multiply(BigDecimal.valueOf((numeroDeCuotas - 1).toLong())),
        )
        return CalculoDePrestamo(
            totalAPagar = total,
            valorCuota = porCuota,
            residuoUltimaCuota = resto.subtract(porCuota),
        )
    }
}

// ============================================================================
// Fase 4 · Pagos, cobros y resumen
// ============================================================================

/** Una cuota que recibió parte de un pago (la repartición la hizo el servidor). */
data class AsignacionDePago(
    val cuotaId: String,
    val numeroCuota: Int,
    val montoAplicado: BigDecimal,
)

/** Fila del historial de pagos: el pago + su distribución + el cliente. */
data class PagoDePrestamo(
    val id: String,
    val prestamoId: String,
    val cliente: ClienteDePrestamo?,
    val fechaPago: String,
    val metodoPago: String,
    val monto: BigDecimal,
    /**
     * Ruta RELATIVA (`prestamos/pagos/:id/comprobante`): la pantalla antepone
     * su base. La app nunca ve la ruta local del archivo en el servidor.
     */
    val comprobanteUrl: String?,
    val comprobanteMime: String?,
    val revertido: Boolean,
    val motivoReversion: String?,
    val revertidoEn: String?,
    val creadoEn: String,
    val asignaciones: List<AsignacionDePago>,
)

/** Sobre paginado del historial de pagos, como lo envía el servidor. */
data class PaginaDePagos(
    val datos: List<PagoDePrestamo>,
    val total: Int,
    val pagina: Int,
    val porPagina: Int,
    val totalPaginas: Int,
)

/** Fila de la pantalla de cobros: una cuota con su saldo y su atraso. */
data class Cobro(
    val id: String,
    val prestamoId: String,
    val modalidad: String,
    val cliente: ClienteDePrestamo?,
    val numeroCuota: Int,
    val fechaVencimiento: String,
    val valorEsperado: BigDecimal,
    val valorPagado: BigDecimal,
    val saldo: BigDecimal,
    val diasDeAtraso: Int,
    val estado: String,
)

/** Sobre paginado de los cobros, como lo envía el servidor. */
data class PaginaDeCobros(
    val datos: List<Cobro>,
    val total: Int,
    val pagina: Int,
    val porPagina: Int,
    val totalPaginas: Int,
)

/** Indicador simple (cantidad + valor) de un bloque del resumen. */
data class IndicadorDeCobros(
    val cantidad: Int,
    val valor: BigDecimal,
)

/** Indicador de cuotas vencidas: además de cantidad y valor suma días máximos. */
data class IndicadorDeVencidas(
    val cantidad: Int,
    val valor: BigDecimal,
    val diasMaximosDeAtraso: Int,
)

/**
 * Resumen del área (Inicio) calculado por el servidor a partir de los datos
 * reales: préstamos con saldo, cobros del día, vencidos, próximos y recaudado
 * de hoy. Ningún número se calcula en la app.
 */
data class ResumenDePrestamos(
    val prestamosActivos: Int,
    val saldoPendienteTotal: BigDecimal,
    val cobrosDeHoy: IndicadorDeCobros,
    val cuotasVencidas: IndicadorDeVencidas,
    val proximosCobros: IndicadorDeCobros,
    val recaudadoHoy: IndicadorDeCobros,
)

/**
 * Cuerpo del registro de un pago (RF-10/RF-11). El monto viaja en
 * [BigDecimal]; quién lo convierte a JSON es [PrestamosApi]. `claveUnica` la
 * genera la app (UUID) para idempotencia: si la pantalla llega a repetir el
 * envío, el servidor devuelve el MISMO pago en vez de registrar dos.
 */
data class DatosDePagoNuevo(
    val monto: BigDecimal,
    val metodoPago: String,
    val claveUnica: String,
    /** `null` = hoy. El servidor valida que no sea una fecha futura. */
    val fechaPago: String? = null,
)

/** Cuerpo de la reversión: el motivo ES obligatorio (auditoría). */
data class DatosDeReversion(
    val motivo: String,
)

/**
 * Sobre de una operación que escribe un pago (registrar o revertir): el pago
 * servido y la ficha del préstamo RECIÉN actualizada (saldos, estados y
 * calendario) en la misma respuesta. Nadie en la app recalcula nada.
 */
data class RespuestaDeOperacionDePago(
    val pago: PagoDePrestamo,
    val prestamo: DetalleDePrestamo,
)

/** Comprobante elegido en el dispositivo para adjuntar a un pago por
 * transferencia. El móvil nunca le cuenta a nadie la ruta local del archivo. */
data class ComprobanteElegido(
    val bytes: ByteArray,
    val nombre: String,
    val mime: String,
)

/** Catálogo de métodos de pago (enum `MetodoDePagoPrestamo` del backend). */
object MetodosDePagoDePrestamo {
    const val EFECTIVO = "EFECTIVO"
    const val TRANSFERENCIA = "TRANSFERENCIA"

    val LISTA = listOf(EFECTIVO, TRANSFERENCIA)

    val ETIQUETAS = mapOf(
        EFECTIVO to "Efectivo",
        TRANSFERENCIA to "Transferencia",
    )

    fun etiqueta(metodo: String): String = ETIQUETAS[metodo] ?: metodo

    /** Solo las transferencias llevan comprobante (regla del backend). */
    fun admiteComprobante(metodo: String): Boolean = metodo == TRANSFERENCIA
}

/** Categorías de la pantalla de Cobros; el SERVIDOR las filtra (la app no). */
object CategoriasDeCobro {
    const val HOY = "HOY"
    const val VENCIDAS = "VENCIDAS"
    const val PROXIMAS = "PROXIMAS"

    val LISTA = listOf(HOY, VENCIDAS, PROXIMAS)

    val ETIQUETAS = mapOf(
        HOY to "Hoy",
        VENCIDAS to "Vencidas",
        PROXIMAS to "Próximas",
    )

    fun etiqueta(categoria: String): String = ETIQUETAS[categoria] ?: categoria
}

/** Estados que el servidor deriva de una cuota para la pantalla de cobros. */
object EstadosDeCobro {
    const val PENDIENTE = "PENDIENTE"
    const val PAGADA = "PAGADA"
    const val PAGO_PARCIAL = "PAGO_PARCIAL"
    const val VENCIDA = "VENCIDA"

    val ETIQUETAS = mapOf(
        PENDIENTE to "Pendiente",
        PAGADA to "Pagada",
        PAGO_PARCIAL to "Pago parcial",
        VENCIDA to "Vencida",
    )

    fun etiqueta(estado: String): String = ETIQUETAS[estado] ?: estado

    fun color(estado: String): String = when (estado) {
        PAGADA -> "#0AA87A"
        PAGO_PARCIAL -> "#C97B10"
        VENCIDA -> "#D43D3D"
        else -> "#8A8FA3"
    }
}

/**
 * Reglas de escritura de las pantallas de pagos, separadas del Activity para
 * poder probarse: el formulario valida aquí ANTES de mandar (mismo motivo por
 * el que existe `CalculoDePrestamoUtil`) y el servidor vuelve a imponerlas.
 */
object ReglasDeEscrituraDePagos {

    fun montoValido(monto: BigDecimal, saldo: BigDecimal): String? = when {
        monto <= BigDecimal.ZERO -> "Escribe el monto que se recibió."
        monto > saldo -> "El pago no puede ser mayor que el saldo pendiente."
        else -> null
    }

    fun motivoDeReversionValido(motivo: String): String? {
        val limpio = motivo.trim()
        return when {
            limpio.isEmpty() -> "Escribe el motivo de la reversión."
            limpio.length < 5 -> "El motivo debe explicar la reversión (mínimo 5 caracteres)."
            else -> null
        }
    }
}