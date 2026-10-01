package com.example.bookstore

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class MisLibrosActivity : AppCompatActivity() {

    private enum class Pestana { PUBLICADOS, SOLICITUDES, FINALIZADOS }

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapterLibros: MisLibrosAdapter
    private lateinit var adapterSolicitudes: SolicitudesAdapter

    private lateinit var tvContador: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var cardVacio: MaterialCardView
    private lateinit var tvMensajeVacio: TextView
    private lateinit var btnIrRegistrar: MaterialButton
    private lateinit var btnPestanaSolicitudes: MaterialButton

    private var publicados = listOf<Libro>()
    private var finalizados = listOf<Libro>()
    private var solicitudes = listOf<Solicitud>()
    private var pestanaActual = Pestana.PUBLICADOS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mis_libros)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvContador = findViewById(R.id.tvContadorMisLibros)
        recycler = findViewById(R.id.recyclerMisLibros)
        cardVacio = findViewById(R.id.cardVacioMisLibros)
        tvMensajeVacio = findViewById(R.id.tvMensajeVacioMisLibros)
        btnIrRegistrar = findViewById(R.id.btnIrRegistrar)
        btnPestanaSolicitudes = findViewById(R.id.btnPestanaSolicitudes)

        adapterLibros = MisLibrosAdapter(
            onFinalizar = { libro -> confirmarFinalizar(libro) },
            onRepublicar = { libro -> confirmarRepublicar(libro) },
            onEditar = { libro -> mostrarEditar(libro) },
            onEliminar = { libro -> confirmarEliminar(libro) }
        )
        adapterSolicitudes = SolicitudesAdapter(
            onAceptar = { solicitud -> confirmarAceptar(solicitud) },
            onRechazar = { solicitud -> confirmarRechazar(solicitud) }
        )
        recycler.layoutManager = LinearLayoutManager(this)

        val grupo = findViewById<MaterialButtonToggleGroup>(R.id.grupoPestanasMisLibros)
        grupo.check(R.id.btnPestanaPublicados)
        grupo.addOnButtonCheckedListener { _, idBoton, marcado ->
            if (marcado) {
                pestanaActual = when (idBoton) {
                    R.id.btnPestanaSolicitudes -> Pestana.SOLICITUDES
                    R.id.btnPestanaFinalizados -> Pestana.FINALIZADOS
                    else -> Pestana.PUBLICADOS
                }
                mostrarLista()
            }
        }

        findViewById<MaterialButton>(R.id.btnRegresarMisLibros).setOnClickListener { finish() }
        btnIrRegistrar.setOnClickListener {
            startActivity(Intent(this, RegistrarLibroActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        cargarDatos()
    }

    private fun cargarDatos() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }

        // Mis libros
        db.collection("libros")
            .whereEqualTo("propietarioId", uid)
            .get()
            .addOnSuccessListener { resultado ->
                val todos = resultado.map { it.toObject(Libro::class.java) }
                publicados = todos.filter { !Oferta.estaFinalizado(it) }.sortedBy { it.titulo.lowercase() }
                finalizados = todos.filter { Oferta.estaFinalizado(it) }.sortedBy { it.titulo.lowercase() }

                tvContador.text = when (publicados.size) {
                    1 -> "Tienes 1 libro publicado"
                    else -> "Tienes ${publicados.size} libros publicados"
                }
                mostrarLista()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar tus libros: ${error.message}", Toast.LENGTH_SHORT).show()
            }

        // Solicitudes pendientes que recibí
        db.collection("solicitudes")
            .whereEqualTo("propietarioId", uid)
            .whereEqualTo("estado", EstadoSolicitud.PENDIENTE)
            .get()
            .addOnSuccessListener { resultado ->
                solicitudes = resultado.map { it.toObject(Solicitud::class.java) }
                    .sortedBy { it.fechaSolicitud }
                btnPestanaSolicitudes.text =
                    if (solicitudes.isEmpty()) "Solicitudes" else "Solicitudes (${solicitudes.size})"
                mostrarLista()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar solicitudes: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarLista() {
        val estaVacia: Boolean

        when (pestanaActual) {
            Pestana.SOLICITUDES -> {
                recycler.adapter = adapterSolicitudes
                adapterSolicitudes.actualizar(solicitudes)
                estaVacia = solicitudes.isEmpty()
                tvMensajeVacio.text = "No tienes solicitudes pendientes"
                btnIrRegistrar.visibility = View.GONE
            }
            Pestana.FINALIZADOS -> {
                recycler.adapter = adapterLibros
                adapterLibros.actualizar(finalizados)
                estaVacia = finalizados.isEmpty()
                tvMensajeVacio.text = "Aún no has vendido, intercambiado ni donado libros"
                btnIrRegistrar.visibility = View.GONE
            }
            Pestana.PUBLICADOS -> {
                recycler.adapter = adapterLibros
                adapterLibros.actualizar(publicados)
                estaVacia = publicados.isEmpty()
                tvMensajeVacio.text = "Aún no has publicado libros.\n¡Comparte uno!"
                btnIrRegistrar.visibility = View.VISIBLE
            }
        }

        recycler.visibility = if (estaVacia) View.GONE else View.VISIBLE
        cardVacio.visibility = if (estaVacia) View.VISIBLE else View.GONE
    }

    // ---------- SOLICITUDES ----------

    private fun confirmarAceptar(solicitud: Solicitud) {
        val quien = solicitud.solicitanteNombre.ifEmpty { "este estudiante" }
        val fechaLimite = ReglasPrestamo.sumarDias(Date(), ReglasPrestamo.DIAS_PRESTAMO)

        MaterialAlertDialogBuilder(this)
            .setTitle("Aceptar solicitud")
            .setMessage(
                "¿Prestar \"${solicitud.titulo}\" a $quien?\n\n" +
                        "Tendrá hasta el ${ReglasPrestamo.formatear(fechaLimite)} para devolverlo. " +
                        "Coordina con él o ella la entrega del libro."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Aceptar") { _, _ -> aceptarSolicitud(solicitud) }
            .show()
    }

    private fun aceptarSolicitud(solicitud: Solicitud) {
        val uid = auth.currentUser?.uid ?: return

        // Primero busco las otras solicitudes pendientes del mismo libro, para rechazarlas
        db.collection("solicitudes")
            .whereEqualTo("propietarioId", uid)
            .whereEqualTo("libroId", solicitud.libroId)
            .whereEqualTo("estado", EstadoSolicitud.PENDIENTE)
            .get()
            .addOnSuccessListener { pendientes ->
                val ahora = Date()
                val fechaLimite = ReglasPrestamo.sumarDias(ahora, ReglasPrestamo.DIAS_PRESTAMO)
                val lote = db.batch()

                // 1. El libro pasa a prestado
                lote.update(
                    db.collection("libros").document(solicitud.libroId),
                    mapOf(
                        "estado" to Oferta.ESTADO_PRESTADO,
                        "prestadoA" to solicitud.solicitanteId,
                        "reservadoPara" to FieldValue.delete(),
                        "reservaHasta" to FieldValue.delete()
                    )
                )

                // 2. Se crea el préstamo a nombre del estudiante que lo pidió
                lote.set(
                    db.collection("prestamos").document(),
                    hashMapOf(
                        "libroId" to solicitud.libroId,
                        "titulo" to solicitud.titulo,
                        "autor" to solicitud.autor,
                        "genero" to solicitud.genero,
                        "usuarioId" to solicitud.solicitanteId,
                        "propietarioId" to uid,
                        "fechaPrestamo" to Timestamp(ahora),
                        "fechaLimite" to Timestamp(fechaLimite),
                        "fechaDevolucion" to null,
                        "estado" to ReglasPrestamo.ESTADO_ACTIVO,
                        "renovado" to false
                    )
                )

                // 3. Esta solicitud se acepta y las demás del mismo libro se rechazan
                for (documento in pendientes) {
                    val nuevoEstado = if (documento.id == solicitud.id) EstadoSolicitud.ACEPTADA
                    else EstadoSolicitud.RECHAZADA
                    lote.update(
                        documento.reference,
                        mapOf("estado" to nuevoEstado, "fechaRespuesta" to Timestamp(ahora))
                    )
                }

                lote.commit()
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "¡Préstamo aceptado! \"${solicitud.titulo}\" ahora está prestado",
                            Toast.LENGTH_LONG
                        ).show()
                        cargarDatos()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo aceptar: ${error.message}", Toast.LENGTH_LONG).show()
                        cargarDatos()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmarRechazar(solicitud: Solicitud) {
        val quien = solicitud.solicitanteNombre.ifEmpty { "este estudiante" }

        MaterialAlertDialogBuilder(this)
            .setTitle("Rechazar solicitud")
            .setMessage("¿Rechazar la solicitud de $quien para \"${solicitud.titulo}\"?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Rechazar") { _, _ ->
                db.collection("solicitudes").document(solicitud.id)
                    .update(
                        mapOf(
                            "estado" to EstadoSolicitud.RECHAZADA,
                            "fechaRespuesta" to Timestamp.now()
                        )
                    )
                    .addOnSuccessListener {
                        Toast.makeText(this, "Solicitud rechazada", Toast.LENGTH_SHORT).show()
                        cargarDatos()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo rechazar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    // ---------- MIS LIBROS ----------

    // Marcar como vendido / intercambiado / donado
    private fun confirmarFinalizar(libro: Libro) {
        val tipo = Oferta.tipoDe(libro)
        val estadoFinal = Oferta.estadoFinalPara(tipo) ?: return
        val verbo = when (tipo) {
            Oferta.VENTA -> "vendiste"
            Oferta.INTERCAMBIO -> "intercambiaste"
            else -> "donaste"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("¿Ya lo $verbo?")
            .setMessage(
                "\"${libro.titulo}\" dejará de aparecer en la búsqueda y pasará a Finalizados.\n\n" +
                        "¡Gracias por hacer circular los libros! 📚"
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Sí, confirmar") { _, _ ->
                cambiarEstado(libro, estadoFinal, "¡Listo! \"${libro.titulo}\" pasó a Finalizados")
            }
            .show()
    }

    private fun confirmarRepublicar(libro: Libro) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Volver a publicar")
            .setMessage("\"${libro.titulo}\" volverá a aparecer en la búsqueda como disponible.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Publicar") { _, _ ->
                cambiarEstado(libro, Oferta.ESTADO_DISPONIBLE, "\"${libro.titulo}\" está publicado otra vez")
            }
            .show()
    }

    private fun cambiarEstado(libro: Libro, nuevoEstado: String, mensajeExito: String) {
        db.collection("libros").document(libro.id)
            .update("estado", nuevoEstado)
            .addOnSuccessListener {
                Toast.makeText(this, mensajeExito, Toast.LENGTH_SHORT).show()
                cargarDatos()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "No se pudo actualizar: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmarEliminar(libro: Libro) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar libro")
            .setMessage("¿Seguro que quieres eliminar \"${libro.titulo}\"? Esta acción no se puede deshacer.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                db.collection("libros").document(libro.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Libro eliminado", Toast.LENGTH_SHORT).show()
                        cargarDatos()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo eliminar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    private fun mostrarEditar(libro: Libro) {
        val tipo = Oferta.tipoDe(libro)
        val vista = layoutInflater.inflate(R.layout.dialog_editar_libro, null)

        val tilTitulo = vista.findViewById<TextInputLayout>(R.id.tilEditarTitulo)
        val tilAutor = vista.findViewById<TextInputLayout>(R.id.tilEditarAutor)
        val tilPrecio = vista.findViewById<TextInputLayout>(R.id.tilEditarPrecio)
        val tilIntercambio = vista.findViewById<TextInputLayout>(R.id.tilEditarIntercambio)
        val tilSinopsis = vista.findViewById<TextInputLayout>(R.id.tilEditarSinopsis)
        val etTitulo = vista.findViewById<TextInputEditText>(R.id.etEditarTitulo)
        val etAutor = vista.findViewById<TextInputEditText>(R.id.etEditarAutor)
        val etPrecio = vista.findViewById<TextInputEditText>(R.id.etEditarPrecio)
        val etIntercambio = vista.findViewById<TextInputEditText>(R.id.etEditarIntercambio)
        val etSinopsis = vista.findViewById<TextInputEditText>(R.id.etEditarSinopsis)
        val btnGuardar = vista.findViewById<MaterialButton>(R.id.btnGuardarEdicion)

        etTitulo.setText(libro.titulo)
        etAutor.setText(libro.autor)
        etSinopsis.setText(libro.sinopsis)

        if (tipo == Oferta.VENTA) {
            tilPrecio.visibility = View.VISIBLE
            tilPrecio.helperText = "Máximo ${Oferta.formatearPrecio(Oferta.PRECIO_MAXIMO)}"
            etPrecio.setText(Oferta.formatearPrecio(libro.precio).removePrefix("S/ "))
        }
        if (tipo == Oferta.INTERCAMBIO) {
            tilIntercambio.visibility = View.VISIBLE
            etIntercambio.setText(libro.intercambioPor)
        }

        val dialogo = AlertDialog.Builder(this).setView(vista).create()
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        vista.findViewById<MaterialButton>(R.id.btnCancelarEdicion).setOnClickListener {
            dialogo.dismiss()
        }

        btnGuardar.setOnClickListener {
            val titulo = etTitulo.text.toString().trim()
            val autor = etAutor.text.toString().trim()
            val sinopsis = etSinopsis.text.toString().trim()
            val intercambioPor = etIntercambio.text.toString().trim()

            tilTitulo.error = if (titulo.isEmpty()) "Escribe el título" else null
            tilAutor.error = if (autor.isEmpty()) "Escribe el autor" else null
            tilSinopsis.error = if (sinopsis.isEmpty()) "Escribe la sinopsis" else null
            if (titulo.isEmpty() || autor.isEmpty() || sinopsis.isEmpty()) return@setOnClickListener

            val cambios = mutableMapOf<String, Any>(
                "titulo" to titulo,
                "autor" to autor,
                "sinopsis" to sinopsis
            )

            if (tipo == Oferta.VENTA) {
                val valor = etPrecio.text.toString().replace(",", ".").toDoubleOrNull()
                tilPrecio.error = when {
                    valor == null -> "Escribe el precio"
                    valor < Oferta.PRECIO_MINIMO -> "El precio mínimo es ${Oferta.formatearPrecio(Oferta.PRECIO_MINIMO)}"
                    valor > Oferta.PRECIO_MAXIMO -> "El precio máximo es ${Oferta.formatearPrecio(Oferta.PRECIO_MAXIMO)}"
                    else -> null
                }
                if (tilPrecio.error != null || valor == null) return@setOnClickListener
                cambios["precio"] = Math.round(valor * 100) / 100.0
            }

            if (tipo == Oferta.INTERCAMBIO) {
                cambios["intercambioPor"] =
                    if (intercambioPor.isNotEmpty()) intercambioPor else FieldValue.delete()
            }

            btnGuardar.isEnabled = false

            db.collection("libros").document(libro.id)
                .update(cambios)
                .addOnSuccessListener {
                    Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
                    dialogo.dismiss()
                    cargarDatos()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "No se pudo guardar: ${error.message}", Toast.LENGTH_SHORT).show()
                    btnGuardar.isEnabled = true
                }
        }

        dialogo.show()
    }
}