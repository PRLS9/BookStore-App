package com.example.bookstore

import android.graphics.Color
import com.google.firebase.firestore.FirebaseFirestore

data class AvisoPrestamo(
    val prestamo: Prestamo,
    val dias: Int,
    val titulo: String,
    val mensaje: String,
    val color: Int
)

object AvisosPrestamo {

    // Se avisa cuando faltan estos días o menos
    const val DIAS_AVISO = 3

    private val COLOR_VENCIDO = Color.parseColor("#D64550")
    private val COLOR_PRONTO = Color.parseColor("#C27C0E")

    fun calcular(prestamos: List<Prestamo>): List<AvisoPrestamo> {
        return prestamos
            .filter { it.estado == ReglasPrestamo.ESTADO_ACTIVO }
            .mapNotNull { prestamo ->
                val fechaLimite = prestamo.fechaLimite?.toDate() ?: return@mapNotNull null
                val dias = ReglasPrestamo.diasRestantes(fechaLimite)
                if (dias > DIAS_AVISO) return@mapNotNull null

                val fecha = ReglasPrestamo.formatear(fechaLimite)
                when {
                    dias < 0 -> {
                        val retraso = -dias
                        val textoDias = if (retraso == 1) "1 día" else "$retraso días"
                        AvisoPrestamo(
                            prestamo, dias, "❗ Préstamo vencido",
                            "\"${prestamo.titulo}\" venció hace $textoDias. Devuélvelo lo antes posible.",
                            COLOR_VENCIDO
                        )
                    }
                    dias == 0 -> AvisoPrestamo(
                        prestamo, dias, "⚠️ Vence hoy",
                        "Hoy es el último día para devolver \"${prestamo.titulo}\".",
                        COLOR_PRONTO
                    )
                    dias == 1 -> AvisoPrestamo(
                        prestamo, dias, "⏰ Vence mañana",
                        "\"${prestamo.titulo}\" debe devolverse mañana ($fecha).",
                        COLOR_PRONTO
                    )
                    else -> AvisoPrestamo(
                        prestamo, dias, "📚 Quedan $dias días",
                        "Recuerda devolver \"${prestamo.titulo}\" antes del $fecha.",
                        COLOR_PRONTO
                    )
                }
            }
            .sortedBy { it.dias } // Los más urgentes primero
    }

    // Busca los préstamos activos del usuario y devuelve sus avisos
    fun cargar(uid: String, alTerminar: (List<AvisoPrestamo>) -> Unit) {
        FirebaseFirestore.getInstance().collection("prestamos")
            .whereEqualTo("usuarioId", uid)
            .whereEqualTo("estado", ReglasPrestamo.ESTADO_ACTIVO)
            .get()
            .addOnSuccessListener { resultado ->
                alTerminar(calcular(resultado.map { it.toObject(Prestamo::class.java) }))
            }
            .addOnFailureListener {
                alTerminar(emptyList())
            }
    }
}