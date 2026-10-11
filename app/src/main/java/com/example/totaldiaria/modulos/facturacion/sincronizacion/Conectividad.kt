package com.example.totaldiaria.modulos.facturacion.sincronizacion

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.io.Closeable

/**
 * Estado de la red (Fase 4). Detrás de una interfaz para que la lógica de
 * sincronización se pruebe sin Android.
 */
interface EstadoDeRed {
    /** ¿Hay internet AHORA? */
    fun hayInternet(): Boolean

    /** Reacciona a los cambios de conectividad (permite el auto-sync). */
    fun observarCambios(escucha: () -> Unit): Closeable
}

class EstadoDeRedDeAndroid(context: Context) : EstadoDeRed {

    private val cm =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun hayInternet(): Boolean {
        val red = cm.activeNetwork ?: return false
        val capacidades = cm.getNetworkCapabilities(red) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun observarCambios(escucha: () -> Unit): Closeable {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = escucha()
            override fun onLost(network: Network) = escucha()
        }
        cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
        return Closeable { cm.unregisterNetworkCallback(callback) }
    }
}