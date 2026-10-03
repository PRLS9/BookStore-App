package com.example.bookstore

import java.util.Date

// Resultado de revisar si el estudiante puede tener otro préstamo
sealed class ResultadoLimite {
    object Permitido : ResultadoLimite()
    object Alcanzado : ResultadoLimite()
    data class Error(val mensaje: String?) : ResultadoLimite()
}

// Resultado de intentar obtener un libro del catálogo
sealed class ResultadoPrestamo {
    data class Exito(val fechaLimite: Date) : ResultadoPrestamo()
    object LimiteAlcanzado : ResultadoPrestamo()
    object NoDisponible : ResultadoPrestamo()
    object ReservadoParaOtro : ResultadoPrestamo()
    data class ErrorConsulta(val mensaje: String?) : ResultadoPrestamo()
    data class ErrorRegistro(val mensaje: String?) : ResultadoPrestamo()
}

/**
 * CR-01: reglas de préstamo.
 * SRP -> ya no están dentro de BuscarLibrosActivity.
 * DIP -> depende de la interfaz PrestamoRepository, no de Firebase.
 * DI  -> el repositorio se recibe por el constructor (inyección de dependencias).
 */
class PrestamoService(
    private val repositorio: PrestamoRepository
) {

    // ¿El estudiante todavía puede tener otro libro prestado?
    fun verificarLimite(uid: String, alTerminar: (ResultadoLimite) -> Unit) {
        repositorio.contarPrestamosActivos(uid) { resultado ->
            resultado.fold(
                onSuccess = { cantidad ->
                    if (cantidad >= ReglasPrestamo.MAX_PRESTAMOS_ACTIVOS) {
                        alTerminar(ResultadoLimite.Alcanzado)
                    } else {
                        alTerminar(ResultadoLimite.Permitido)
                    }
                },
                onFailure = { error -> alTerminar(ResultadoLimite.Error(error.message)) }
            )
        }
    }

    // Revisa el límite y, si se puede, registra el préstamo por 30 días
    fun obtenerLibro(libro: Libro, uid: String, alTerminar: (ResultadoPrestamo) -> Unit) {
        verificarLimite(uid) { limite ->
            when (limite) {
                ResultadoLimite.Alcanzado -> alTerminar(ResultadoPrestamo.LimiteAlcanzado)
                is ResultadoLimite.Error -> alTerminar(ResultadoPrestamo.ErrorConsulta(limite.mensaje))
                ResultadoLimite.Permitido -> {
                    val ahora = Date()
                    val fechaLimite = ReglasPrestamo.sumarDias(ahora, ReglasPrestamo.DIAS_PRESTAMO)
                    repositorio.registrarPrestamo(libro, uid, ahora, fechaLimite, alTerminar)
                }
            }
        }
    }
}