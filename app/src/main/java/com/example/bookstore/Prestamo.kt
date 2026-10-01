package com.example.bookstore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Prestamo(
    @DocumentId val id: String = "",
    val libroId: String = "",
    val titulo: String = "",
    val autor: String = "",
    val genero: String = "",
    val usuarioId: String = "",
    val fechaPrestamo: Timestamp? = null,
    val fechaLimite: Timestamp? = null,
    val fechaDevolucion: Timestamp? = null,
    val estado: String = ReglasPrestamo.ESTADO_ACTIVO,
    val renovado: Boolean = false
)

object ReglasPrestamo {
    const val DIAS_PRESTAMO = 30
    const val MAX_PRESTAMOS_ACTIVOS = 3
    const val DIAS_RENOVACION = 7

    const val ESTADO_ACTIVO = "activo"
    const val ESTADO_DEVUELTO = "devuelto"

    private val formato = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    fun formatear(fecha: Date?): String = if (fecha == null) "—" else formato.format(fecha)

    fun sumarDias(fecha: Date, dias: Int): Date {
        val calendario = Calendar.getInstance()
        calendario.time = fecha
        calendario.add(Calendar.DAY_OF_YEAR, dias)
        return calendario.time
    }

    private fun inicioDelDia(fecha: Date): Long {
        val calendario = Calendar.getInstance()
        calendario.time = fecha
        calendario.set(Calendar.HOUR_OF_DAY, 0)
        calendario.set(Calendar.MINUTE, 0)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)
        return calendario.timeInMillis
    }

    // Positivo: días que faltan. 0: vence hoy. Negativo: días de retraso.
    fun diasRestantes(fechaLimite: Date): Int {
        val diferencia = inicioDelDia(fechaLimite) - inicioDelDia(Date())
        return Math.round(diferencia / 86_400_000.0).toInt()
    }
}