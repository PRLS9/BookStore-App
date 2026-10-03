package com.example.bookstore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Date

/**
 * CR-01 (DIP): implementación con Cloud Firestore.
 * Es la única clase del módulo de préstamos que conoce a Firebase.
 */
class FirestorePrestamoRepository(
    private val db: FirebaseFirestore
) : PrestamoRepository {

    companion object {
        private const val COLECCION_LIBROS = "libros"
        private const val COLECCION_PRESTAMOS = "prestamos"
        private const val COLECCION_RESERVAS = "reservas"
    }

    override fun contarPrestamosActivos(uid: String, alTerminar: (Result<Int>) -> Unit) {
        db.collection(COLECCION_PRESTAMOS)
            .whereEqualTo("usuarioId", uid)
            .whereEqualTo("estado", ReglasPrestamo.ESTADO_ACTIVO)
            .get()
            .addOnSuccessListener { activos -> alTerminar(Result.success(activos.size())) }
            .addOnFailureListener { error -> alTerminar(Result.failure(error)) }
    }

    // Transacción: respeta la prioridad de las reservas y evita que dos personas lo obtengan a la vez
    override fun registrarPrestamo(
        libro: Libro,
        uid: String,
        fechaPrestamo: Date,
        fechaLimite: Date,
        alTerminar: (ResultadoPrestamo) -> Unit
    ) {
        val libroRef = db.collection(COLECCION_LIBROS).document(libro.id)
        val reservaRef = db.collection(COLECCION_RESERVAS).document(Reservas.idPara(libro.id, uid))
        val prestamoRef = db.collection(COLECCION_PRESTAMOS).document()

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
                    "fechaPrestamo" to Timestamp(fechaPrestamo),
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