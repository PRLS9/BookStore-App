package com.example.bookstore

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class MisPrestamosActivity : AppCompatActivity() {

    private enum class Pestana { ACTIVOS, RESERVAS, HISTORIAL }

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapterPrestamos: PrestamoAdapter
    private lateinit var adapterReservas: ReservaAdapter

    private lateinit var tvContador: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var cardVacio: MaterialCardView
    private lateinit var tvMensajeVacio: TextView
    private lateinit var btnIrBuscar: MaterialButton
    private lateinit var btnPestanaReservas: MaterialButton

    private var prestamosActivos = listOf<Prestamo>()
    private var prestamosHistorial = listOf<Prestamo>()
    private var reservas = listOf<Reserva>()
    private var posiciones = mapOf<String, Int>()
    private var pestanaActual = Pestana.ACTIVOS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mis_prestamos)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvContador = findViewById(R.id.tvContadorPrestamos)
        recycler = findViewById(R.id.recyclerPrestamos)
        cardVacio = findViewById(R.id.cardVacio)
        tvMensajeVacio = findViewById(R.id.tvMensajeVacio)
        btnIrBuscar = findViewById(R.id.btnIrBuscar)
        btnPestanaReservas = findViewById(R.id.btnPestanaReservas)

        adapterPrestamos = PrestamoAdapter(
            onDevolver = { prestamo -> confirmarDevolucion(prestamo) },
            onRenovar = { prestamo -> confirmarRenovacion(prestamo) }
        )
        adapterReservas = ReservaAdapter(
            onIrAlLibro = { reserva -> irAlLibro(reserva) },
            onCancelar = { reserva -> confirmarCancelarReserva(reserva) }
        )
        recycler.layoutManager = LinearLayoutManager(this)

        val grupoPestanas = findViewById<MaterialButtonToggleGroup>(R.id.grupoPestanas)
        grupoPestanas.check(R.id.btnPestanaActivos)
        grupoPestanas.addOnButtonCheckedListener { _, idBoton, marcado ->
            if (marcado) {
                pestanaActual = when (idBoton) {
                    R.id.btnPestanaReservas -> Pestana.RESERVAS
                    R.id.btnPestanaHistorial -> Pestana.HISTORIAL
                    else -> Pestana.ACTIVOS
                }
                mostrarLista()
            }
        }

        findViewById<MaterialButton>(R.id.btnRegresarPrestamos).setOnClickListener { finish() }
        btnIrBuscar.setOnClickListener {
            startActivity(Intent(this, BuscarLibrosActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        cargarPrestamos()
        cargarReservas()
    }

    private fun cargarPrestamos() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("prestamos")
            .whereEqualTo("usuarioId", uid)
            .get()
            .addOnSuccessListener { resultado ->
                val todos = resultado.map { it.toObject(Prestamo::class.java) }

                prestamosActivos = todos
                    .filter { it.estado == ReglasPrestamo.ESTADO_ACTIVO }
                    .sortedBy { it.fechaLimite }
                prestamosHistorial = todos
                    .filter { it.estado == ReglasPrestamo.ESTADO_DEVUELTO }
                    .sortedByDescending { it.fechaDevolucion }

                tvContador.text = "Tienes ${prestamosActivos.size} de " +
                        "${ReglasPrestamo.MAX_PRESTAMOS_ACTIVOS} préstamos activos"
                mostrarLista()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar préstamos: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun cargarReservas() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("reservas")
            .whereEqualTo("usuarioId", uid)
            .whereIn("estado", listOf(Reservas.EN_ESPERA, Reservas.TURNO, Reservas.EXPIRADA))
            .get()
            .addOnSuccessListener { resultado ->
                // Primero las que ya tienen turno, luego las más antiguas
                reservas = resultado.map { it.toObject(Reserva::class.java) }
                    .sortedWith(compareBy(
                        { when (it.estado) { Reservas.TURNO -> 0; Reservas.EN_ESPERA -> 1; else -> 2 } },
                        { it.fechaReserva }
                    ))

                btnPestanaReservas.text =
                    if (reservas.isEmpty()) "Reservas" else "Reservas (${reservas.size})"
                calcularPosiciones()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar reservas: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Calcula en qué lugar de la fila estás para cada libro
    private fun calcularPosiciones() {
        val enEspera = reservas.filter { it.estado == Reservas.EN_ESPERA }
        if (enEspera.isEmpty()) {
            posiciones = emptyMap()
            mostrarLista()
            return
        }

        val resultado = mutableMapOf<String, Int>()
        var pendientes = enEspera.size
        for (reserva in enEspera) {
            Reservas.cargarFila(reserva.libroId) { fila ->
                val indice = fila.indexOfFirst { it.id == reserva.id }
                resultado[reserva.id] = if (indice >= 0) indice + 1 else 1
                pendientes--
                if (pendientes == 0) {
                    posiciones = resultado
                    mostrarLista()
                }
            }
        }
    }

    private fun mostrarLista() {
        val estaVacia: Boolean

        when (pestanaActual) {
            Pestana.ACTIVOS -> {
                recycler.adapter = adapterPrestamos
                adapterPrestamos.actualizar(prestamosActivos)
                estaVacia = prestamosActivos.isEmpty()
                tvMensajeVacio.text = "Aún no tienes libros.\n¡Busca uno!"
                btnIrBuscar.visibility = View.VISIBLE
            }
            Pestana.RESERVAS -> {
                recycler.adapter = adapterReservas
                adapterReservas.actualizar(reservas, posiciones)
                estaVacia = reservas.isEmpty()
                tvMensajeVacio.text = "No tienes reservas.\nCuando un libro esté prestado, podrás reservarlo 📌"
                btnIrBuscar.visibility = View.VISIBLE
            }
            Pestana.HISTORIAL -> {
                recycler.adapter = adapterPrestamos
                adapterPrestamos.actualizar(prestamosHistorial)
                estaVacia = prestamosHistorial.isEmpty()
                tvMensajeVacio.text = "Aún no has devuelto ningún libro"
                btnIrBuscar.visibility = View.GONE
            }
        }

        recycler.visibility = if (estaVacia) View.GONE else View.VISIBLE
        cardVacio.visibility = if (estaVacia) View.VISIBLE else View.GONE
    }

    // ---------- RESERVAS ----------

    // Abre Buscar libro con el título ya escrito
    private fun irAlLibro(reserva: Reserva) {
        val intent = Intent(this, BuscarLibrosActivity::class.java)
        intent.putExtra(BuscarLibrosActivity.EXTRA_BUSCAR, reserva.titulo)
        startActivity(intent)
    }

    private fun confirmarCancelarReserva(reserva: Reserva) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cancelar reserva")
            .setMessage("¿Quieres salir de la fila de \"${reserva.titulo}\"?")
            .setNegativeButton("No", null)
            .setPositiveButton("Sí, cancelar") { _, _ ->
                db.collection("reservas").document(reserva.id)
                    .update("estado", Reservas.CANCELADA)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Reserva cancelada", Toast.LENGTH_SHORT).show()
                        cargarReservas()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo cancelar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    // ---------- PRÉSTAMOS ----------

    private fun confirmarDevolucion(prestamo: Prestamo) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Devolver libro")
            .setMessage("¿Confirmas que devuelves \"${prestamo.titulo}\"?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Devolver") { _, _ -> devolverLibro(prestamo) }
            .show()
    }

    private fun devolverLibro(prestamo: Prestamo) {
        val uid = auth.currentUser?.uid ?: return

        // Primero se revisa si alguien está en la fila de este libro
        Reservas.cargarFila(prestamo.libroId) { fila ->
            val siguiente = fila.firstOrNull { it.estado == Reservas.EN_ESPERA && it.usuarioId != uid }
            val lote = db.batch()

            // 1. El préstamo pasa al historial
            lote.update(
                db.collection("prestamos").document(prestamo.id),
                mapOf(
                    "estado" to ReglasPrestamo.ESTADO_DEVUELTO,
                    "fechaDevolucion" to Timestamp.now()
                )
            )

            // 2. El libro vuelve a estar disponible (con prioridad para el siguiente, si hay)
            val camposLibro = mutableMapOf<String, Any>(
                "estado" to Oferta.ESTADO_DISPONIBLE,
                "prestadoA" to FieldValue.delete()
            )
            if (siguiente != null) {
                val limite = Timestamp(Reservas.limiteTurno())
                camposLibro["reservadoPara"] = siguiente.usuarioId
                camposLibro["reservaHasta"] = limite

                // 3. El siguiente de la fila recibe su turno
                lote.update(
                    db.collection("reservas").document(siguiente.id),
                    mapOf("estado" to Reservas.TURNO, "fechaLimiteTurno" to limite)
                )
            } else {
                camposLibro["reservadoPara"] = FieldValue.delete()
                camposLibro["reservaHasta"] = FieldValue.delete()
            }
            lote.update(db.collection("libros").document(prestamo.libroId), camposLibro)

            lote.commit()
                .addOnSuccessListener {
                    val mensaje = if (siguiente != null) {
                        val quien = siguiente.usuarioNombre.ifEmpty { "El siguiente estudiante" }
                        "¡Gracias por devolverlo! $quien era el siguiente en la fila 📌"
                    } else {
                        "Gracias por devolver \"${prestamo.titulo}\""
                    }
                    Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
                    cargarPrestamos()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "No se pudo devolver: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun confirmarRenovacion(prestamo: Prestamo) {
        if (prestamo.renovado) {
            Toast.makeText(this, "Este préstamo ya fue renovado", Toast.LENGTH_SHORT).show()
            return
        }
        val fechaActual = prestamo.fechaLimite?.toDate() ?: return
        val nuevaFecha = ReglasPrestamo.sumarDias(fechaActual, ReglasPrestamo.DIAS_RENOVACION)

        MaterialAlertDialogBuilder(this)
            .setTitle("Renovar préstamo")
            .setMessage(
                "Tendrás ${ReglasPrestamo.DIAS_RENOVACION} días más para leer \"${prestamo.titulo}\".\n\n" +
                        "Nueva fecha límite: ${ReglasPrestamo.formatear(nuevaFecha)}\n\n" +
                        "Solo puedes renovar una vez."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Renovar") { _, _ ->
                db.collection("prestamos").document(prestamo.id)
                    .update(mapOf("fechaLimite" to Timestamp(nuevaFecha), "renovado" to true))
                    .addOnSuccessListener {
                        Toast.makeText(this, "Renovado hasta el ${ReglasPrestamo.formatear(nuevaFecha)}", Toast.LENGTH_SHORT).show()
                        cargarPrestamos()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo renovar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }
}