package com.example.totaldiaria.modulos.prestamos.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * DIARIX · Fase 3 · El cálculo local es UN espejo del backend.
 *
 * Estas cuentas no inventan un acuerdo: REPITEN las reglas de
 * `financiero.util.ts` (total desde porcentaje con factor Decimal, cuota
 * uniforme truncada a centavos y residuo a la última cuota). El servidor
 * sigue siendo la autoridad; este test fija que la vista previa del
 * formulario NO se desvíe del ejemplo RF-06 ni de los redondeos.
 */
class PrestamosCalculoTest {

    @Test
    fun `RF06 - un millon al 20 por ciento da 1 200 000 en 12 cuotas de 100 000`() {
        val total = CalculoDePrestamoUtil.totalDesdePorcentaje(
            BigDecimal("1000000"),
            BigDecimal("20"),
        )

        assertEquals(0, BigDecimal("1200000.00").compareTo(total))

        val reparto = CalculoDePrestamoUtil.repartir(total, 12)

        assertEquals(0, BigDecimal("100000").compareTo(reparto.valorCuota))
        assertEquals(0, BigDecimal.ZERO.compareTo(reparto.residuoUltimaCuota))

        // La suma de las doce cuotas debe ser exactamente el total.
        val suma = reparto.valorCuota.multiply(BigDecimal.valueOf(11))
            .add(reparto.valorCuota.add(reparto.residuoUltimaCuota))
        assertEquals(0, total.compareTo(suma))
    }

    @Test
    fun `total desde porcentaje admite porcentajes decimales`() {
        val total = CalculoDePrestamoUtil.totalDesdePorcentaje(
            BigDecimal("100000"),
            BigDecimal("12.5"),
        )

        assertEquals(0, BigDecimal("112500.00").compareTo(total))
    }

    @Test
    fun `un reparto que no divide exacto trunca la cuota y deja centavos a la ultima`() {
        val reparto = CalculoDePrestamoUtil.repartir(BigDecimal("1000000.00"), 3)

        // 1.000.000 / 3 = 333.333,33... -> la cuota se trunca a 333.333,33
        assertEquals(0, BigDecimal("333333.33").compareTo(reparto.valorCuota))
        // ... y la última absorbe 333.333,34: el residuo en la última, nunca
        // una cuota decimal infinita.
        assertEquals(0, BigDecimal("0.01").compareTo(reparto.residuoUltimaCuota))

        val suma = reparto.valorCuota.multiply(BigDecimal.valueOf(2))
            .add(reparto.valorCuota.add(reparto.residuoUltimaCuota))
        assertEquals(0, BigDecimal("1000000.00").compareTo(suma))
    }

    @Test
    fun `un reparto exacto no inventa residuo`() {
        val reparto = CalculoDePrestamoUtil.repartir(BigDecimal("5000000"), 5)

        assertEquals(0, BigDecimal("1000000").compareTo(reparto.valorCuota))
        assertEquals(0, BigDecimal.ZERO.compareTo(reparto.residuoUltimaCuota))
    }

    @Test
    fun `los extremos permitidos no se salen del contrato`() {
        // 0,01 % (el mínimo del backend) sobre un millón.
        val minimo = CalculoDePrestamoUtil.totalDesdePorcentaje(
            BigDecimal("1000000"),
            BigDecimal("0.01"),
        )
        assertEquals(0, BigDecimal("1000100.00").compareTo(minimo))

        // 1.000 % (el máximo del backend) sobre 100.
        val maximo = CalculoDePrestamoUtil.totalDesdePorcentaje(
            BigDecimal("100"),
            BigDecimal("1000"),
        )
        assertEquals(0, BigDecimal("1100.00").compareTo(maximo))

        assertTrue(CalculoDePrestamoUtil.MAXIMO_NUMERO_DE_CUOTAS == 60)
    }
}