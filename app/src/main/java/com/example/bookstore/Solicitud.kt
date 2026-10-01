package com.example.bookstore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Solicitud(
    @DocumentId val id: String = "",
    val libroId: String = "",
    val titulo: String = "",
    val autor: String = "",
    val genero: String = "",
    val propietarioId: String = "",
    val solicitanteId: String = "",
    val solicitanteNombre: String = "",
    val estado: String = "",
    val fechaSolicitud: Timestamp? = null,
    val fechaRespuesta: Timestamp? = null
)

object EstadoSolicitud {
    const val PENDIENTE = "pendiente"
    const val ACEPTADA = "aceptada"
    const val RECHAZADA = "rechazada"

    // Cada estudiante tiene una sola solicitud por libro: "idLibro_idEstudiante"
    fun idPara(libroId: String, solicitanteId: String) = "${libroId}_$solicitanteId"

    // "Peter Ramírez" → "Peter R."
    fun nombreCorto(nombre: String, apellidos: String): String {
        val primerNombre = nombre.trim().split(" ").first()
        val inicial = apellidos.trim().take(1).uppercase()
        return if (inicial.isNotEmpty()) "$primerNombre $inicial." else primerNombre
    }
}