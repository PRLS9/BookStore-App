package com.example.bookstore

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot

// Lo que el estudiante ha hecho en la app
data class Estadisticas(
    val registrados: Int = 0,
    val prestadosAOtros: Int = 0,
    val donados: Int = 0,
    val intercambiados: Int = 0,
    val vendidos: Int = 0,
    val leidos: Int = 0,
    val aTiempo: Int = 0,
    val tardios: Int = 0
) {
    // Estudiantes que leyeron gracias a ti sin comprar un libro nuevo
    val impacto: Int get() = prestadosAOtros + donados + intercambiados + vendidos
}

data class Nivel(val nombre: String, val emoji: String, val desde: Int)

data class Insignia(
    val id: String,
    val emoji: String,
    val nombre: String,
    val descripcion: String,
    val progreso: Int,
    val meta: Int
) {
    val desbloqueada: Boolean get() = progreso >= meta
}

data class ReglaPuntos(val emoji: String, val accion: String, val puntos: Int, val veces: Int) {
    val total: Int get() = puntos * veces
}

data class PuestoRanking(
    @DocumentId val uid: String = "",
    val nombre: String = "",
    val puntos: Int = 0,
    val nivel: String = "",
    val insignias: Int = 0
)

object Logros {

    const val PTS_REGISTRAR = 10
    const val PTS_PRESTAR = 20
    const val PTS_DONAR = 30
    const val PTS_INTERCAMBIAR = 20
    const val PTS_VENDER = 10
    const val PTS_LEER = 5
    const val PTS_A_TIEMPO = 15
    const val PTS_TARDE = -10

    const val TAMANO_RANKING = 20
    private const val PREFS = "logros"

    val NIVELES = listOf(
        Nivel("Semilla", "🌱", 0),
        Nivel("Lector", "📗", 50),
        Nivel("Compartidor", "🤝", 150),
        Nivel("Embajador", "🌟", 300),
        Nivel("Leyenda", "👑", 600)
    )

    fun reglas(e: Estadisticas) = listOf(
        ReglaPuntos("📚", "Registrar un libro", PTS_REGISTRAR, e.registrados),
        ReglaPuntos("🤝", "Prestar tu libro a otro estudiante", PTS_PRESTAR, e.prestadosAOtros),
        ReglaPuntos("🎁", "Donar un libro", PTS_DONAR, e.donados),
        ReglaPuntos("🔄", "Intercambiar un libro", PTS_INTERCAMBIAR, e.intercambiados),
        ReglaPuntos("💰", "Vender a precio justo", PTS_VENDER, e.vendidos),
        ReglaPuntos("📖", "Leer un libro prestado", PTS_LEER, e.leidos),
        ReglaPuntos("✅", "Devolver a tiempo", PTS_A_TIEMPO, e.aTiempo),
        ReglaPuntos("⚠️", "Devolver tarde", PTS_TARDE, e.tardios)
    )

    fun puntos(e: Estadisticas): Int = reglas(e).sumOf { it.total }.coerceAtLeast(0)

    fun nivel(puntos: Int): Nivel = NIVELES.last { puntos >= it.desde }

    fun siguienteNivel(puntos: Int): Nivel? = NIVELES.firstOrNull { it.desde > puntos }

    // Porcentaje de avance dentro del nivel actual
    fun progresoNivel(puntos: Int): Int {
        val actual = nivel(puntos)
        val siguiente = siguienteNivel(puntos) ?: return 100
        return (puntos - actual.desde) * 100 / (siguiente.desde - actual.desde)
    }

    fun insignias(e: Estadisticas) = listOf(
        Insignia("primer_paso", "🌱", "Primer paso", "Registra tu primer libro", e.registrados, 1),
        Insignia("buen_vecino", "🤝", "Buen vecino", "Presta un libro a otro estudiante", e.prestadosAOtros, 1),
        Insignia("donador", "🎁", "Donador generoso", "Dona un libro", e.donados, 1),
        Insignia("trueque", "🔄", "Rey del trueque", "Intercambia 2 libros", e.intercambiados, 2),
        Insignia("lector", "📖", "Lector frecuente", "Lee 5 libros prestados", e.leidos, 5),
        Insignia("puntual", "⏱️", "Siempre puntual", "Devuelve 3 libros a tiempo, sin retrasos",
            if (e.tardios == 0) e.aTiempo else 0, 3),
        Insignia("biblioteca", "🏛️", "Biblioteca andante", "Registra 5 libros", e.registrados, 5),
        Insignia("impacto", "💚", "Gran impacto", "Ayuda a 5 estudiantes a leer sin comprar", e.impacto, 5)
    )

    // Calcula las estadísticas a partir de tus libros y préstamos reales
    fun calcular(uid: String, alTerminar: (Estadisticas?) -> Unit) {
        val db = FirebaseFirestore.getInstance()
        val tareaLibros = db.collection("libros").whereEqualTo("propietarioId", uid).get()
        val tareaMisPrestamos = db.collection("prestamos").whereEqualTo("usuarioId", uid).get()
        val tareaPrestadosAOtros = db.collection("prestamos").whereEqualTo("propietarioId", uid).get()

        Tasks.whenAllSuccess<QuerySnapshot>(tareaLibros, tareaMisPrestamos, tareaPrestadosAOtros)
            .addOnSuccessListener { resultados ->
                val libros = resultados[0]
                val misPrestamos = resultados[1]
                val prestadosAOtros = resultados[2]

                val estadosLibros = libros.documents.map { it.getString("estado").orEmpty() }

                var aTiempo = 0
                var tardios = 0
                for (documento in misPrestamos.documents) {
                    val estado = documento.getString("estado")
                    val limite = documento.getTimestamp("fechaLimite") ?: continue
                    val devolucion = documento.getTimestamp("fechaDevolucion")

                    when {
                        // Devuelto hasta el final del último día: a tiempo
                        estado == ReglasPrestamo.ESTADO_DEVUELTO && devolucion != null &&
                                devolucion.seconds <= limite.seconds + 86_400 -> aTiempo++
                        estado == ReglasPrestamo.ESTADO_DEVUELTO -> tardios++
                        estado == ReglasPrestamo.ESTADO_ACTIVO &&
                                ReglasPrestamo.diasRestantes(limite.toDate()) < 0 -> tardios++
                    }
                }

                alTerminar(
                    Estadisticas(
                        registrados = libros.size(),
                        prestadosAOtros = prestadosAOtros.size(),
                        donados = estadosLibros.count { it == Oferta.ESTADO_DONADO },
                        intercambiados = estadosLibros.count { it == Oferta.ESTADO_INTERCAMBIADO },
                        vendidos = estadosLibros.count { it == Oferta.ESTADO_VENDIDO },
                        leidos = misPrestamos.size(),
                        aTiempo = aTiempo,
                        tardios = tardios
                    )
                )
            }
            .addOnFailureListener { alTerminar(null) }
    }

    // Calcula y publica tus puntos en el ranking
    fun sincronizar(uid: String, alTerminar: (Estadisticas?) -> Unit) {
        calcular(uid) { estadisticas ->
            if (estadisticas == null) {
                alTerminar(null)
                return@calcular
            }
            val db = FirebaseFirestore.getInstance()
            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { usuario ->
                    val nombre = EstadoSolicitud.nombreCorto(
                        usuario.getString("nombre").orEmpty(),
                        usuario.getString("apellidos").orEmpty()
                    ).ifEmpty { "Estudiante" }
                    val puntos = puntos(estadisticas)
                    val nivel = nivel(puntos)

                    db.collection("ranking").document(uid)
                        .set(
                            hashMapOf(
                                "nombre" to nombre,
                                "puntos" to puntos,
                                "nivel" to "${nivel.emoji} ${nivel.nombre}",
                                "insignias" to insignias(estadisticas).count { it.desbloqueada },
                                "actualizado" to Timestamp.now()
                            )
                        )
                        .addOnCompleteListener { alTerminar(estadisticas) }
                }
                .addOnFailureListener { alTerminar(estadisticas) }
        }
    }

    fun cargarRanking(alTerminar: (List<PuestoRanking>) -> Unit) {
        FirebaseFirestore.getInstance().collection("ranking")
            .orderBy("puntos", Query.Direction.DESCENDING)
            .limit(TAMANO_RANKING.toLong())
            .get()
            .addOnSuccessListener { resultado ->
                alTerminar(resultado.map { it.toObject(PuestoRanking::class.java) })
            }
            .addOnFailureListener { alTerminar(emptyList()) }
    }

    // Insignias desbloqueadas que el estudiante todavía no ha visto celebrar
    fun insigniasNuevas(context: Context, uid: String, insignias: List<Insignia>): List<Insignia> {
        val vistas = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet("vistas_$uid", emptySet()) ?: emptySet()
        return insignias.filter { it.desbloqueada && it.id !in vistas }
    }

    fun marcarVistas(context: Context, uid: String, insignias: List<Insignia>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putStringSet("vistas_$uid", insignias.filter { it.desbloqueada }.map { it.id }.toSet())
            .apply()
    }
}