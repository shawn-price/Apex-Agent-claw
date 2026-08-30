package com.example.storage

import android.content.Context
import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.crypto.CryptoManager
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object EncryptedDatabaseFactory {
    private const val TAG = "EncryptedDbFactory"

    /**
     * Creates a SQLCipher encrypted SupportSQLiteOpenHelper.Factory using a hardware-derived
     * key from Android KeyStore.
     */
    fun createFactory(context: Context): SupportSQLiteOpenHelper.Factory? {
        return try {
            // Load SQLCipher native library if available
            try {
                System.loadLibrary("sqlcipher")
            } catch (_: Throwable) {}

            // Derive 256-bit passphrase bytes using CryptoManager hardware keystore key
            val passphraseBytes = getDatabasePassphrase()

            Log.i(TAG, "SQLCipher encrypted open helper factory initialized with hardware KeyStore key.")
            SupportOpenHelperFactory(passphraseBytes)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize SQLCipher encrypted open helper factory, using framework fallback", e)
            null
        }
    }

    private fun getDatabasePassphrase(): ByteArray {
        // Derive passphrase deterministically from Hardware KeyStore
        val sampleSeed = "openclaw_db_passphrase_seed_v1"
        val encryptedSeed = CryptoManager.encrypt(sampleSeed)
        // Take first 32 bytes (256 bits) as key
        val passphrase = ByteArray(32)
        for (i in 0 until 32) {
            passphrase[i] = encryptedSeed[i % encryptedSeed.size]
        }
        return passphrase
    }
}
