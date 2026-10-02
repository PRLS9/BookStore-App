package com.example.bookstore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
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
 * CR-01 (SRP): reglas y operaciones de préstamo que antes estaban dentro de
 * BuscarLibrosActivity. La Activity ahora solo muestra la interfaz y los mensajes.
 */
class PrestamoService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    // ¿El estudiante todavía puede tener otro libro prestado?
    fun verificarLimite(uid: String, alTerminar: (ResultadoLimite) -> Unit) {
        db.collection("prestamos")
            .whereEqualTo("usuarioId", uid)
            .whereEqualTo("estado", ReglasPrestamo.ESTADO_ACTIVO)
            .get()
            .addOnSuccessListener { activos ->
                if (activos.size() >= ReglasPrestamo.MAX_PRESTAMOS_ACTIVOS) {
                    alTerminar(ResultadoLimite.Alcanzado)
                } else {
                    alTerminar(ResultadoLimite.Permitido)
                }
            }
            .addOnFailureListener { error -> alTerminar(ResultadoLimite.Error(error.message)) }
    }

    // Revisa el límite y, si se puede, registra el préstamo
    fun obtenerLibro(libro: Libro, uid: String, alTerminar: (ResultadoPrestamo) -> Unit) {
        verificarLimite(uid) { limite ->
            when (limite) {
                ResultadoLimite.Alcanzado -> alTerminar(ResultadoPrestamo.LimiteAlcanzado)
                is ResultadoLimite.Error -> alTerminar(ResultadoPrestamo.ErrorConsulta(limite.mensaje))
                ResultadoLimite.Permitido -> registrarPrestamo(libro, uid, alTerminar)
            }
        }
    }

    // Operación segura: respeta la prioridad de las reservas y evita que dos personas lo obtengan a la vez
    private fun registrarPrestamo(libro: Libro, uid: String, alTerminar: (ResultadoPrestamo) -> Unit) {
        val libroRef = db.collection("libros").document(libro.id)
        val reservaRef = db.collection("reservas").document(Reservas.idPara(libro.id, uid))
        val prestamoRef = db.collection("prestamos").document()
        val ahora = Date()
        val fechaLimite = ReglasPrestamo.sumarDias(ahora, ReglasPrestamo.DIAS_PRESTAMO)

        db.runTransaction { transaccion ->
            val documento = transaccion.get(libroRef)
            val miReserva = transaccion.get(reservaRef)

            val estadoActual = documento.getString("estado") ?: ""
            if (!estadoActual.equals(Oferta.ESTADO_DISPONIBLE, ignoreCase = true)) {
                throw FirebaseFirestoreException(
                    "Este libro ya fue prestado",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }

            // Si está reservado para otra persona y su prioridad sigue vigente, no se puede
            val reservadoPara = documento.getString("reservadoPara") ?: ""
            val reservaHasta = documento.getTimestamp("reservaHasta")?.toDate()
            if (reservadoPara.isNotEmpty() && reservadoPara != uid && reservaHasta?.after(Date()) == true) {
                throw FirebaseFirestoreException(
                    "Reservado para otro estudiante",
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION
                )
            }

            transaccion.update(
                libroRef, mapOf(
                    "estado" to Oferta.ESTADO_PRESTADO,
                    "prestadoA" to uid,
                    "reservadoPara" to FieldValue.delete(),
                    "reservaHasta" to FieldValue.delete()
                )
            )
            transaccion.set(
                prestamoRef, hashMapOf(
                    "libroId" to libro.id,
                    "titulo" to libro.titulo,
                    "autor" to libro.autor,
                    "genero" to libro.genero,
                    "usuarioId" to uid,
                    "fechaPrestamo" to Timestamp(ahora),
                    "fechaLimite" to Timestamp(fechaLimite),
                    "fechaDevolucion" to null,
                    "estado" to ReglasPrestamo.ESTADO_ACTIVO,
                    "renovado" to false
                )
            )

            // Si tenías una reserva de este libro, queda cumplida
            val estadoReserva = miReserva.getString("estado")
            if (miReserva.exists() && (estadoReserva == Reservas.EN_ESPERA || estadoReserva == Reservas.TURNO)) {
                transaccion.update(reservaRef, "estado", Reservas.COMPLETADA)
            }
            null
        }.addOnSuccessListener {
            alTerminar(ResultadoPrestamo.Exito(fechaLimite))
        }.addOnFailureListener { error ->
            val codigo = (error as? FirebaseFirestoreException)?.code
            alTerminar(
                when (codigo) {
                    FirebaseFirestoreException.Code.FAILED_PRECONDITION -> ResultadoPrestamo.ReservadoParaOtro
                    FirebaseFirestoreException.Code.ABORTED -> ResultadoPrestamo.NoDisponible
                    else -> ResultadoPrestamo.ErrorRegistro(error.message)
                }
            )
        }
    }
}