package com.example.bookstore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

data class Reserva(
    @DocumentId val id: String = "",
    val libroId: String = "",
    val titulo: String = "",
    val autor: String = "",
    val genero: String = "",
    val usuarioId: String = "",
    val usuarioNombre: String = "",
    val estado: String = "",
    val fechaReserva: Timestamp? = null,
    val fechaLimiteTurno: Timestamp? = null
)

// Aviso de "¡Es tu turno!" para la campanita y la ventanita del Home
data class AvisoTurno(
    val reserva: Reserva,
    val titulo: String,
    val mensaje: String,
    val urgente: Boolean
)

object Reservas {

    const val EN_ESPERA = "en_espera"
    const val TURNO = "turno"
    const val COMPLETADA = "completada"
    const val CANCELADA = "cancelada"
    const val EXPIRADA = "expirada"

    const val HORAS_PRIORIDAD = 48
    const val HORAS_URGENTE = 12
    const val MAX_RESERVAS_ACTIVAS = 5

    // Cada estudiante tiene una sola reserva por libro: "idLibro_idEstudiante"
    fun idPara(libroId: String, usuarioId: String) = "${libroId}_$usuarioId"

    fun limiteTurno(): Date = Date(System.currentTimeMillis() + HORAS_PRIORIDAD * 3_600_000L)

    // El libro está reservado para OTRA persona y su prioridad sigue vigente
    fun reservadoParaOtro(libro: Libro, uid: String?): Boolean {
        if (libro.reservadoPara.isEmpty() || libro.reservadoPara == uid) return false
        return libro.reservaHasta?.toDate()?.after(Date()) == true
    }

    // Es mi turno y la prioridad sigue vigente
    fun esMiTurno(libro: Libro, uid: String?): Boolean {
        if (uid == null || libro.reservadoPara != uid) return false
        return libro.reservaHasta?.toDate()?.after(Date()) == true
    }

    // Alguien tenía el turno, pero ya pasaron sus 48 horas sin obtenerlo
    fun prioridadVencida(libro: Libro): Boolean {
        if (!libro.estado.equals(Oferta.ESTADO_DISPONIBLE, ignoreCase = true)) return false
        if (libro.reservadoPara.isEmpty()) return false
        val hasta = libro.reservaHasta?.toDate() ?: return true
        return hasta.before(Date())
    }

    // "1 d 5 h", "5 h 20 min" o "12 min"
    fun tiempoRestante(hasta: Date?): String {
        if (hasta == null) return ""
        val minutos = (hasta.time - System.currentTimeMillis()) / 60_000
        if (minutos <= 0) return "0 min"
        val dias = minutos / 1440
        val horas = (minutos % 1440) / 60
        val mins = minutos % 60
        return when {
            dias > 0 -> "$dias d $horas h"
            horas > 0 -> "$horas h $mins min"
            else -> "$mins min"
        }
    }

    // Porcentaje de las 48 horas que aún queda (para la barra de tiempo)
    fun porcentajeRestante(hasta: Date?): Int {
        if (hasta == null) return 0
        val total = HORAS_PRIORIDAD * 3_600_000L
        val restante = hasta.time - System.currentTimeMillis()
        return ((restante * 100) / total).toInt().coerceIn(0, 100)
    }

    fun textoPersonas(cantidad: Int) = if (cantidad == 1) "1 persona" else "$cantidad personas"

    // Fila de espera de un libro, en orden de llegada
    fun cargarFila(libroId: String, alTerminar: (List<Reserva>) -> Unit) {
        FirebaseFirestore.getInstance().collection("reservas")
            .whereEqualTo("libroId", libroId)
            .whereIn("estado", listOf(EN_ESPERA, TURNO))
            .get()
            .addOnSuccessListener { resultado ->
                alTerminar(
                    resultado.map { it.toObject(Reserva::class.java) }
                        .sortedBy { it.fechaReserva }
                )
            }
            .addOnFailureListener { alTerminar(emptyList()) }
    }

    // ⏭️ Pasa el turno al siguiente cuando alguien no aprovechó sus 48 horas
    fun avanzarFila(libro: Libro, alTerminar: () -> Unit = {}) {
        if (!prioridadVencida(libro)) {
            alTerminar()
            return
        }
        val db = FirebaseFirestore.getInstance()

        cargarFila(libro.id) { fila ->
            val vencida = fila.firstOrNull { it.usuarioId == libro.reservadoPara && it.estado == TURNO }
            val siguiente = fila.firstOrNull { it.estado == EN_ESPERA }
            val lote = db.batch()

            // 1. Quien no lo aprovechó queda como "expirada"
            if (vencida != null) {
                lote.update(db.collection("reservas").document(vencida.id), "estado", EXPIRADA)
            }

            // 2. El turno pasa al siguiente (o el libro queda libre si no hay nadie más)
            val camposLibro = mutableMapOf<String, Any>()
            if (siguiente != null) {
                val limite = Timestamp(limiteTurno())
                camposLibro["reservadoPara"] = siguiente.usuarioId
                camposLibro["reservaHasta"] = limite
                lote.update(
                    db.collection("reservas").document(siguiente.id),
                    mapOf("estado" to TURNO, "fechaLimiteTurno" to limite)
                )
            } else {
                camposLibro["reservadoPara"] = FieldValue.delete()
                camposLibro["reservaHasta"] = FieldValue.delete()
            }
            lote.update(db.collection("libros").document(libro.id), camposLibro)

            lote.commit().addOnCompleteListener { alTerminar() }
        }
    }

    // Revisa los libros donde estoy en la fila, por si a alguien se le venció el turno
    fun revisarFilasDe(uid: String, alTerminar: () -> Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("reservas")
            .whereEqualTo("usuarioId", uid)
            .whereEqualTo("estado", EN_ESPERA)
            .get()
            .addOnSuccessListener { resultado ->
                val libroIds = resultado.map { it.getString("libroId").orEmpty() }
                    .filter { it.isNotEmpty() }
                    .distinct()

                if (libroIds.isEmpty()) {
                    alTerminar()
                    return@addOnSuccessListener
                }

                var pendientes = libroIds.size
                val terminarUno = {
                    pendientes--
                    if (pendientes == 0) alTerminar()
                }

                for (libroId in libroIds) {
                    db.collection("libros").document(libroId).get()
                        .addOnCompleteListener { tarea ->
                            val libro = if (tarea.isSuccessful) {
                                tarea.result?.toObject(Libro::class.java)
                            } else null

                            if (libro != null && prioridadVencida(libro)) {
                                avanzarFila(libro) { terminarUno() }
                            } else {
                                terminarUno()
                            }
                        }
                }
            }
            .addOnFailureListener { alTerminar() }
    }

    // Mis turnos vigentes, convertidos en avisos
    fun cargarTurnos(uid: String, alTerminar: (List<AvisoTurno>) -> Unit) {
        FirebaseFirestore.getInstance().collection("reservas")
            .whereEqualTo("usuarioId", uid)
            .whereEqualTo("estado", TURNO)
            .get()
            .addOnSuccessListener { resultado ->
                val ahora = System.currentTimeMillis()
                val avisos = resultado.map { it.toObject(Reserva::class.java) }
                    .filter { (it.fechaLimiteTurno?.toDate()?.time ?: 0L) > ahora }
                    .sortedBy { it.fechaLimiteTurno }
                    .map { reserva ->
                        val limite = reserva.fechaLimiteTurno?.toDate()
                        val restanteMs = (limite?.time ?: ahora) - ahora
                        val urgente = restanteMs < HORAS_URGENTE * 3_600_000L
                        val tiempo = tiempoRestante(limite)

                        AvisoTurno(
                            reserva = reserva,
                            titulo = if (urgente) "⏰ ¡No pierdas tu turno!" else "🎉 ¡Es tu turno!",
                            mensaje = "\"${reserva.titulo}\" está disponible para ti. " +
                                    "Te quedan $tiempo para obtenerlo.",
                            urgente = urgente
                        )
                    }
                alTerminar(avisos)
            }
            .addOnFailureListener { alTerminar(emptyList()) }
    }
}