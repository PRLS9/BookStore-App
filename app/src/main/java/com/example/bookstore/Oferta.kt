package com.example.bookstore

import android.graphics.Color
import java.util.Locale

object Oferta {

    const val PRESTAMO = "Préstamo"
    const val INTERCAMBIO = "Intercambio"
    const val VENTA = "Venta"
    const val DONACION = "Donación"

    val TIPOS = listOf(PRESTAMO, INTERCAMBIO, VENTA, DONACION)

    // Precios accesibles: sin barreras para conseguir un libro
    const val PRECIO_MINIMO = 1.0
    const val PRECIO_MAXIMO = 30.0

    // Estados de un libro
    const val ESTADO_DISPONIBLE = "disponible"
    const val ESTADO_PRESTADO = "prestado"
    const val ESTADO_VENDIDO = "vendido"
    const val ESTADO_INTERCAMBIADO = "intercambiado"
    const val ESTADO_DONADO = "donado"

    val ESTADOS_FINALIZADOS = listOf(ESTADO_VENDIDO, ESTADO_INTERCAMBIADO, ESTADO_DONADO)

    // Los libros antiguos (sin tipo) se consideran de préstamo
    fun tipoDe(libro: Libro): String = libro.tipoOferta.ifEmpty { PRESTAMO }

    fun estaFinalizado(libro: Libro): Boolean = libro.estado.lowercase() in ESTADOS_FINALIZADOS

    // Estado al que pasa un libro cuando se cierra el trato
    fun estadoFinalPara(tipo: String): String? = when (tipo) {
        VENTA -> ESTADO_VENDIDO
        INTERCAMBIO -> ESTADO_INTERCAMBIADO
        DONACION -> ESTADO_DONADO
        else -> null
    }

    fun textoAccionFinal(tipo: String): String = when (tipo) {
        VENTA -> "✅ Marcar como vendido"
        INTERCAMBIO -> "✅ Marcar como intercambiado"
        DONACION -> "✅ Marcar como donado"
        else -> ""
    }

    fun etiqueta(libro: Libro): String = when (tipoDe(libro)) {
        VENTA -> "💰 Venta · ${formatearPrecio(libro.precio)}"
        INTERCAMBIO -> "🔄 Intercambio"
        DONACION -> "🎁 Donación"
        else -> "📖 Préstamo"
    }

    fun color(libro: Libro): Int = Color.parseColor(
        when (tipoDe(libro)) {
            VENTA -> "#2E8B57"
            INTERCAMBIO -> "#7A6FB5"
            DONACION -> "#D64550"
            else -> "#2C4770"
        }
    )

    // 15.0 → "S/ 15"   |   12.5 → "S/ 12.50"
    fun formatearPrecio(precio: Double): String {
        return if (precio % 1.0 == 0.0) "S/ ${precio.toInt()}"
        else "S/ " + String.format(Locale.US, "%.2f", precio)
    }
}