package com.antimaling.permanen.util

import java.security.SecureRandom

/**
 * Generator kode pairing acak 8 karakter.
 * Alfabet tanpa I/O/0/1 supaya tidak salah ketik saat dibaca dari layar HP.
 */
object CodeGen {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val rnd = SecureRandom()

    fun random(): String {
        val sb = StringBuilder(8)
        repeat(8) { sb.append(ALPHABET[rnd.nextInt(ALPHABET.length)]) }
        return sb.toString()
    }
}
