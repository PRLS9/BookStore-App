package com.example.bookstore

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistrarLibroActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var tipoSeleccionado = Oferta.PRESTAMO
    private val regexCelular = Regex("^\\d{9}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registrar_libro)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        val etTitulo = findViewById<TextInputEditText>(R.id.etTitulo)
        val etAutor = findViewById<TextInputEditText>(R.id.etAutor)
        val etSinopsis = findViewById<TextInputEditText>(R.id.etSinopsis)
        val tilGenero = findViewById<TextInputLayout>(R.id.tilGenero)
        val actvGenero = findViewById<MaterialAutoCompleteTextView>(R.id.actvGeneroRegistro)
        val tilCurso = findViewById<TextInputLayout>(R.id.tilCurso)
        val actvCurso = findViewById<MaterialAutoCompleteTextView>(R.id.actvCursoRegistro)
        val chipGroupTipo = findViewById<ChipGroup>(R.id.chipGroupTipoOferta)
        val tilPrecio = findViewById<TextInputLayout>(R.id.tilPrecio)
        val etPrecio = findViewById<TextInputEditText>(R.id.etPrecio)
        val tilIntercambio = findViewById<TextInputLayout>(R.id.tilIntercambio)
        val etIntercambio = findViewById<TextInputEditText>(R.id.etIntercambio)
        val tvAvisoContacto = findViewById<TextView>(R.id.tvAvisoContacto)
        val btnGuardar = findViewById<MaterialButton>(R.id.btnGuardarLibro)

        findViewById<MaterialButton>(R.id.btnRegresar).setOnClickListener { finish() }

        // Listas oficiales (las mismas que usan los filtros)
        actvGenero.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, FiltrosActivity.GENEROS)
        )
        actvGenero.setOnItemClickListener { _, _, _, _ -> tilGenero.error = null }

        actvCurso.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, FiltrosActivity.CURSOS)
        )
        actvCurso.setOnItemClickListener { _, _, _, _ -> tilCurso.error = null }

        tilPrecio.helperText = "Máximo ${Oferta.formatearPrecio(Oferta.PRECIO_MAXIMO)}. " +
                "¡Un precio bajo ayuda a que más estudiantes puedan leer!"

        // Muestra u oculta campos según el tipo de oferta
        fun actualizarCampos() {
            tilPrecio.visibility = if (tipoSeleccionado == Oferta.VENTA) View.VISIBLE else View.GONE
            tilIntercambio.visibility = if (tipoSeleccionado == Oferta.INTERCAMBIO) View.VISIBLE else View.GONE
            tvAvisoContacto.visibility = if (tipoSeleccionado != Oferta.PRESTAMO) View.VISIBLE else View.GONE
            tilPrecio.error = null
        }

        chipGroupTipo.setOnCheckedStateChangeListener { _, idsMarcados ->
            tipoSeleccionado = when (idsMarcados.firstOrNull()) {
                R.id.chipIntercambio -> Oferta.INTERCAMBIO
                R.id.chipVenta -> Oferta.VENTA
                R.id.chipDonacion -> Oferta.DONACION
                else -> Oferta.PRESTAMO
            }
            actualizarCampos()
        }
        actualizarCampos()

        btnGuardar.setOnClickListener {
            val titulo = etTitulo.text.toString().trim()
            val autor = etAutor.text.toString().trim()
            val genero = actvGenero.text.toString().trim()
            val curso = actvCurso.text.toString().trim()
            val sinopsis = etSinopsis.text.toString().trim()
            val intercambioPor = etIntercambio.text.toString().trim()

            if (titulo.isEmpty() || autor.isEmpty() || sinopsis.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (genero !in FiltrosActivity.GENEROS) {
                tilGenero.error = "Elige un género"
                return@setOnClickListener
            }
            if (curso !in FiltrosActivity.CURSOS) {
                tilCurso.error = "Elige un curso (o \"${FiltrosActivity.CURSO_LIBRE}\")"
                return@setOnClickListener
            }

            // Precio accesible (solo en Venta)
            var precio = 0.0
            if (tipoSeleccionado == Oferta.VENTA) {
                val valor = etPrecio.text.toString().replace(",", ".").toDoubleOrNull()
                tilPrecio.error = when {
                    valor == null -> "Escribe el precio"
                    valor < Oferta.PRECIO_MINIMO -> "El precio mínimo es ${Oferta.formatearPrecio(Oferta.PRECIO_MINIMO)}"
                    valor > Oferta.PRECIO_MAXIMO -> "El precio máximo es ${Oferta.formatearPrecio(Oferta.PRECIO_MAXIMO)}"
                    else -> null
                }
                if (tilPrecio.error != null || valor == null) return@setOnClickListener
                precio = Math.round(valor * 100) / 100.0
            }

            val uid = auth.currentUser?.uid
            if (uid == null) {
                Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnGuardar.isEnabled = false

            // Se toman el nombre y el celular del perfil del usuario
            db.collection("usuarios").document(uid).get()
                .addOnSuccessListener { documento ->
                    val nombre = documento.getString("nombre")?.trim().orEmpty()
                    val apellidos = documento.getString("apellidos")?.trim().orEmpty()
                    val celular = documento.getString("celular")?.trim().orEmpty()

                    if (tipoSeleccionado != Oferta.PRESTAMO && !Validaciones.esCelularValido(celular)) {
                        btnGuardar.isEnabled = true
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Falta tu celular")
                            .setMessage(
                                "Para ofrecer libros en intercambio, venta o donación necesitas " +
                                        "un celular de 9 dígitos en tu perfil, para que puedan contactarte."
                            )
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Ir a mi perfil") { _, _ ->
                                startActivity(Intent(this, PerfilActivity::class.java))
                            }
                            .show()
                        return@addOnSuccessListener
                    }

                    // CR-02: se reutiliza la regla que ya existía (DRY): "Peter R."
                    val nombreVisible = EstadoSolicitud.nombreCorto(nombre, apellidos)

                    val libro = hashMapOf<String, Any>(
                        "titulo" to titulo,
                        "autor" to autor,
                        "genero" to genero,
                        "curso" to curso,
                        "sinopsis" to sinopsis,
                        "estado" to "disponible",
                        "propietarioId" to uid,
                        "propietarioNombre" to nombreVisible,
                        "tipoOferta" to tipoSeleccionado
                    )
                    if (tipoSeleccionado == Oferta.VENTA) libro["precio"] = precio
                    if (tipoSeleccionado == Oferta.INTERCAMBIO && intercambioPor.isNotEmpty()) {
                        libro["intercambioPor"] = intercambioPor
                    }
                    if (tipoSeleccionado != Oferta.PRESTAMO) libro["contactoCelular"] = celular

                    db.collection("libros")
                        .add(libro)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Libro registrado con éxito", Toast.LENGTH_SHORT).show()
                            etTitulo.text?.clear()
                            etAutor.text?.clear()
                            etSinopsis.text?.clear()
                            etPrecio.text?.clear()
                            etIntercambio.text?.clear()
                            actvGenero.setText("", false)
                            actvCurso.setText("", false)
                            chipGroupTipo.check(R.id.chipPrestamo)
                            btnGuardar.isEnabled = true
                        }
                        .addOnFailureListener { error ->
                            Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                            btnGuardar.isEnabled = true
                        }
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Error al leer tu perfil: ${error.message}", Toast.LENGTH_SHORT).show()
                    btnGuardar.isEnabled = true
                }
        }
    }
}