package com.example.bookstore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Libro(
    @DocumentId val id: String = "",
    val titulo: String = "",
    val autor: String = "",
    val genero: String = "",
    val curso: String = "",
    val estado: String = "",
    val sinopsis: String = "",
    val propietarioId: String = "",
    val prestadoA: String = "",
    val tipoOferta: String = "",
    val precio: Double = 0.0,
    val intercambioPor: String = "",
    val propietarioNombre: String = "",
    val contactoCelular: String = "",
    val reservadoPara: String = "",
    val reservaHasta: Timestamp? = null
)