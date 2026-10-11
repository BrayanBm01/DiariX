package com.example.totaldiaria.sesion

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado simétrico respaldado por el Android Keystore (Fase 4).
 *
 * La clave AES-256 vive en el Keystore (TEE/strongbox cuando el hardware lo
 * permite) y nunca sale de él: la app cifra/descifra por referencia. El
 * material cifrado (IV + texto cifrado) se guarda en un archivo privado de
 * la app.
 *
 * Los tokens de sesión NUNCA se escriben en claro: ni SharedPreferences,
 * ni SQLite, ni logs.
 *
 * Robolectric soporta AndroidKeyStore; además, la clase acepta un
 * [Cifrador] inyectable para poder probar la lógica de archivo sin Keystore.
 */
class AlmacenSeguro(
    context: Context,
    private val cifrador: Cifrador = CifradorKeystore(KEYSTORE_ALIAS),
) {

    private val archivo = File(context.filesDir, NOMBRE_ARCHIVO)

    /** Guarda [blob] cifrado en disco (sobreescribe lo anterior). */
    fun guardar(blob: ByteArray): Boolean =
        try {
            val cifrado = cifrador.cifrar(blob)
            archivo.parentFile?.mkdirs()
            archivo.writeBytes(cifrado)
            true
        } catch (_: Exception) {
            false
        }

    /** Lee y descifra, o `null` si no hay datos o el descifrado falla. */
    fun leer(): ByteArray? {
        if (!archivo.exists()) return null

        return try {
            cifrador.descifrar(archivo.readBytes())
        } catch (_: Exception) {
            // Material corrupto o clave inaccesible: se trata como sesión
            // inexistente (el flujo re-loguea). Nunca se deja un token a
            // medias.
            null
        }
    }

    fun borrar(): Boolean =
        try {
            archivo.delete()
        } catch (_: Exception) {
            false
        }

    companion object {
        private const val NOMBRE_ARCHIVO = "diarix_credenciales.bin"
        private const val KEYSTORE_ALIAS = "diarix_tokens_v1"
    }
}

/** Cifrado AES-GCM con la clave del Keystore, o un doble para pruebas. */
interface Cifrador {
    fun cifrar(plano: ByteArray): ByteArray
    fun descifrar(cifrado: ByteArray): ByteArray
}

class CifradorKeystore(private val alias: String) : Cifrador {

    override fun cifrar(plano: ByteArray): ByteArray {
        val cifra = Cipher.getInstance(TRANSFORMACION)
        cifra.init(Cipher.ENCRYPT_MODE, clave())
        // IV (12 bytes GCM) delante del texto cifrado.
        return cifra.iv + cifra.doFinal(plano)
    }

    override fun descifrar(cifrado: ByteArray): ByteArray {
        val cifra = Cipher.getInstance(TRANSFORMACION)
        cifra.init(
            Cipher.DECRYPT_MODE,
            clave(),
            GCMParameterSpec(TAMANIO_TAG_BITS, cifrado, 0, IV_BYTES),
        )
        return cifra.doFinal(cifrado, IV_BYTES, cifrado.size - IV_BYTES)
    }

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)
            ?.let { return it.secretKey }

        val generador = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generador.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generador.generateKey()
    }

    private companion object {
        const val TRANSFORMACION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAMANIO_TAG_BITS = 128
    }
}