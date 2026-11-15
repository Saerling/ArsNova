package com.arsnova

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra tokens y contraseñas antes de guardarlos en SharedPreferences.
 * La clave AES-256-GCM vive en el Android Keystore (no se puede extraer ni viaja en copias).
 * Valores antiguos sin cifrar se leen igual y se cifran la primera vez que se leen.
 * Si el Keystore no está disponible (tests en JVM), se guarda tal cual.
 */
object Secreto {
    private const val ALIAS = "secretos_app"
    private const val PREFIJO = "enc1:"

    private fun clave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun cifrar(texto: String): String = try {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, clave()) }
        val datos = c.iv + c.doFinal(texto.toByteArray(Charsets.UTF_8))
        PREFIJO + Base64.encodeToString(datos, Base64.NO_WRAP)
    } catch (_: Throwable) { texto }

    fun descifrar(valor: String): String? {
        if (!valor.startsWith(PREFIJO)) return valor
        return try {
            val datos = Base64.decode(valor.removePrefix(PREFIJO), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, clave(), GCMParameterSpec(128, datos, 0, 12))
            String(c.doFinal(datos, 12, datos.size - 12), Charsets.UTF_8)
        } catch (_: Throwable) { null } // clave perdida (p. ej. restaurado en otro equipo): hay que volver a entrar
    }

    /** Lee [k] descifrado; si estaba en claro lo cifra en el acto. */
    fun leer(p: SharedPreferences, k: String): String? {
        val v = p.getString(k, null) ?: return null
        if (!v.startsWith(PREFIJO)) {
            val c = cifrar(v)
            if (c != v) p.edit().putString(k, c).apply()
            return v
        }
        return descifrar(v)
    }

    fun poner(e: SharedPreferences.Editor, k: String, v: String?): SharedPreferences.Editor =
        if (v == null) e.remove(k) else e.putString(k, cifrar(v))
}
