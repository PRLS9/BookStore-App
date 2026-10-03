package com.example.bookstore

import java.util.Date

/**
 * CR-01 (DIP): abstracción del almacenamiento de préstamos.
 * PrestamoService depende de esta interfaz y no de Firebase.
 */
interface PrestamoRepository {

    // Cuántos préstamos activos tiene el estudiante
    fun contarPrestamosActivos(uid: String, alTerminar: (Result<Int>) -> Unit)

    // Registra el préstamo de forma segura (libro, préstamo y reserva a la vez)
    fun registrarPrestamo(
        libro: Libro,
        uid: String,
        fechaPrestamo: Date,
        fechaLimite: Date,
        alTerminar: (ResultadoPrestamo) -> Unit
    )
}