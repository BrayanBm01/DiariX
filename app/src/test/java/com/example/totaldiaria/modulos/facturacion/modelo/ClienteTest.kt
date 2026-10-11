package com.example.totaldiaria.modulos.facturacion.modelo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * DIARIX · Fase 2 · El modelo de cliente y su catálogo de documentos.
 *
 * Lo que se fija aquí es que la app y el backend hablan el mismo idioma:
 *
 *  - El catálogo de tipos de documento es EL MISMO que declara
 *    `clientes.dto.ts` (`CC, CE, NIT, TI, PASAPORTE, RUT`). Si el backend
 *    añade o renombra un tipo y este catálogo no lo sigue, el formulario
 *    guardaría un valor que el servidor rechaza (o no ofrecería uno que
 *    acepta), y el error no aparecería en un dispositivo sino en el contrato.
 *
 *  - La etiqueta no entra en el dato: el servidor manda el CÓDIGO
 *    ("CC") y esta pantalla lo presenta ("Cédula de ciudadanía"). Si la app
 *    guardara la etiqueta, "Cédula de ciudadanía" no estaría en el catálogo
 *    del backend y sería un 400 garantizado.
 */
class ClienteTest {

    @Test
    fun `el catalogo de tipos de documento es el del backend y no se repite`() {

        assertEquals(
            listOf("CC", "CE", "NIT", "TI", "PASAPORTE", "RUT"),
            Cliente.TIPOS_DE_DOCUMENTO
        )

        assertEquals(
            Cliente.TIPOS_DE_DOCUMENTO.size,
            Cliente.TIPOS_DE_DOCUMENTO.distinct().size
        )
    }

    @Test
    fun `cada tipo de documento tiene su etiqueta legible`() {

        assertEquals("Cédula de ciudadanía", Cliente.etiquetaDeDocumento("CC"))
        assertEquals("Cédula de extranjería", Cliente.etiquetaDeDocumento("CE"))
        assertEquals("NIT", Cliente.etiquetaDeDocumento("NIT"))
        assertEquals("Tarjeta de identidad", Cliente.etiquetaDeDocumento("TI"))
        assertEquals("Pasaporte", Cliente.etiquetaDeDocumento("PASAPORTE"))
        assertEquals("RUT", Cliente.etiquetaDeDocumento("RUT"))
    }

    @Test
    fun `un tipo que el catalogo no conoce se muestra tal cual`() {

        // El backend puede añadir un tipo antes de que la app se actualice.
        // Mejor mostrar el código que dejar la línea en blanco o fallar.
        assertEquals("DNI", Cliente.etiquetaDeDocumento("DNI"))
    }

    @Test
    fun `el documento de la tarjeta junta tipo y numero con etiqueta`() {

        val cliente = Cliente(
            id = "cli_1",
            nombres = "María",
            apellidos = "Restrepo",
            nombreCompleto = "María Restrepo",
            tipoDocumento = "CC",
            numeroDocumento = "1020304050",
            email = null,
            telefono = null,
            direccion = null,
            referencias = null,
            observaciones = null,
            creadoEn = "2026-10-01T09:00:00.000Z",
            actualizadoEn = "2026-10-01T09:00:00.000Z",
        )

        assertEquals(
            "Cédula de ciudadanía 1020304050",
            cliente.documentoConEtiqueta()
        )
    }
}