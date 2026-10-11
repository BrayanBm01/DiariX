package com.example.totaldiaria.modulos.prestamos.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * DIARIX · Fase 4 · Las reglas que la pantalla valida ANTES de mandar un pago
 * o una reversión.
 *
 * Lo mismo que `PrestamosCalculoTest`: la app se adelanta al error para que la
 * persona corrija aquí, pero la AUTORIDAD sigue siendo el servidor (él vuelve
 * a validar el monto contra el saldo y la existencia del último pago en su
 * transacción). Estas reglas existen para poder probarse sin abrir un
 * Activity, no para suplantar al backend.
 */
class ReglasDeEscrituraDePagosTest {

    // ----------------------------------------------------------------
    // montoValido
    // ----------------------------------------------------------------

    @Test
    fun `un monto en cero o negativo no es un pago`() {
        assertEquals(
            "Escribe el monto que se recibió.",
            ReglasDeEscrituraDePagos.montoValido(BigDecimal.ZERO, BigDecimal("100000")),
        )
        assertEquals(
            "Escribe el monto que se recibió.",
            ReglasDeEscrituraDePagos.montoValido(BigDecimal("-5000"), BigDecimal("100000")),
        )
    }

    @Test
    fun `un monto mayor que el saldo se rechaza aqui y el servidor lo revalida`() {
        assertEquals(
            "El pago no puede ser mayor que el saldo pendiente.",
            ReglasDeEscrituraDePagos.montoValido(BigDecimal("100001"), BigDecimal("100000")),
        )
    }

    @Test
    fun `un pago parcial o total dentro del saldo pasa`() {
        assertNull(ReglasDeEscrituraDePagos.montoValido(BigDecimal("40000"), BigDecimal("100000")))
        assertNull(ReglasDeEscrituraDePagos.montoValido(BigDecimal("100000"), BigDecimal("100000")))
    }

    // ----------------------------------------------------------------
    // motivoDeReversionValido
    // ----------------------------------------------------------------

    @Test
    fun `una reversion sin motivo se rechaza`() {
        assertEquals(
            "Escribe el motivo de la reversión.",
            ReglasDeEscrituraDePagos.motivoDeReversionValido(""),
        )
        assertEquals(
            "Escribe el motivo de la reversión.",
            ReglasDeEscrituraDePagos.motivoDeReversionValido("   "),
        )
    }

    @Test
    fun `un motivo demasiado corto no explica la reversion`() {
        assertEquals(
            "El motivo debe explicar la reversión (mínimo 5 caracteres).",
            ReglasDeEscrituraDePagos.motivoDeReversionValido("Dup"),
        )
    }

    @Test
    fun `un motivo que explica la reversion pasa`() {
        assertNull(
            ReglasDeEscrituraDePagos.motivoDeReversionValido("Cobro duplicado"),
        )
    }
}