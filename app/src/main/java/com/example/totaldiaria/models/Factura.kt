package com.example.totaldiaria.models

data class Factura(
    val id: Int,
    val numeroFactura: String,
    val efectivo: Double,
    val transferencia: Double,
    val fecha: String,
    val comprobanteUri: String? = null
)