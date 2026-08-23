package com.example.totaldiaria.models

data class PapeleraItem(
    val fecha: String,
    val cantidadFacturas: Int,
    val efectivo: Double,
    val transferencia: Double,
    val total: Double,
    val cantidadEfectivo: Int,
    val cantidadTransferencia: Int
)