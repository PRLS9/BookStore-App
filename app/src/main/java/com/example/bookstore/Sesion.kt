package com.example.bookstore

import android.content.Context

object Sesion {

    private const val PREFS = "sesion"
    private const val CLAVE_MANTENER = "mantener_sesion"

    // ¿El estudiante marcó "Mantener la sesión iniciada"?
    fun debeMantener(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(CLAVE_MANTENER, false)
    }

    fun guardar(context: Context, mantener: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(CLAVE_MANTENER, mantener)
            .apply()
    }
}