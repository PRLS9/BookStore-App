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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class PerfilActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tvIniciales: TextView
    private lateinit var tvNombreCompleto: TextView
    private lateinit var tvCorreoCabecera: TextView
    private lateinit var tvDatoNombre: TextView
    private lateinit var tvDatoApellidos: TextView
    private lateinit var tvDatoCelular: TextView
    private lateinit var tvDatoCorreo: TextView
    private lateinit var tvStatActivos: TextView
    private lateinit var tvStatLeidos: TextView
    private lateinit var tvStatRegistrados: TextView

    private lateinit var cardAnuncio: MaterialCardView
    private lateinit var tvAnuncioTitulo: TextView
    private lateinit var tvAnuncioMensaje: TextView

    private lateinit var tvEmojiNivelPerfil: TextView
    private lateinit var tvNivelPerfil: TextView
    private lateinit var tvInsigniasPerfil: TextView

    private var nombre = ""
    private var apellidos = ""
    private var celular = ""
    private var correo = ""

    // Mismas reglas que en RegistroActivity
    private val regexSoloTexto = Regex("^[a-zA-ZÀ-ÿñÑ\\s]+$")
    private val regexCelular = Regex("^\\d{9}$")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        tvIniciales = findViewById(R.id.tvIniciales)
        tvNombreCompleto = findViewById(R.id.tvNombreCompleto)
        tvCorreoCabecera = findViewById(R.id.tvCorreoCabecera)
        tvDatoNombre = findViewById(R.id.tvDatoNombre)
        tvDatoApellidos = findViewById(R.id.tvDatoApellidos)
        tvDatoCelular = findViewById(R.id.tvDatoCelular)
        tvDatoCorreo = findViewById(R.id.tvDatoCorreo)
        tvStatActivos = findViewById(R.id.tvStatActivos)
        tvStatLeidos = findViewById(R.id.tvStatLeidos)
        tvStatRegistrados = findViewById(R.id.tvStatRegistrados)

        cardAnuncio = findViewById(R.id.cardAnuncio)
        tvAnuncioTitulo = findViewById(R.id.tvAnuncioTitulo)
        tvAnuncioMensaje = findViewById(R.id.tvAnuncioMensaje)

        tvEmojiNivelPerfil = findViewById(R.id.tvEmojiNivelPerfil)
        tvNivelPerfil = findViewById(R.id.tvNivelPerfil)
        tvInsigniasPerfil = findViewById(R.id.tvInsigniasPerfil)

        findViewById<MaterialButton>(R.id.btnRegresarPerfil).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnEditarDatos).setOnClickListener { mostrarEditarDatos() }
        findViewById<MaterialButton>(R.id.btnCambiarContrasena).setOnClickListener { confirmarCambioContrasena() }
        findViewById<MaterialButton>(R.id.btnCerrarSesionPerfil).setOnClickListener { confirmarCerrarSesion() }
        findViewById<MaterialButton>(R.id.btnAnuncioVer).setOnClickListener {
            startActivity(Intent(this, MisPrestamosActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardLogrosPerfil).setOnClickListener {
            startActivity(Intent(this, LogrosActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        cargarDatos()
        cargarEstadisticas()
        cargarAnuncio()
        cargarLogros()
    }

    private fun cargarDatos() {
        val usuario = auth.currentUser
        if (usuario == null) {
            irAlLogin()
            return
        }
        correo = usuario.email ?: ""

        db.collection("usuarios").document(usuario.uid)
            .get()
            .addOnSuccessListener { documento ->
                nombre = documento.getString("nombre") ?: ""
                apellidos = documento.getString("apellidos") ?: ""
                celular = documento.getString("celular") ?: ""
                mostrarDatos()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar tus datos: ${error.message}", Toast.LENGTH_SHORT).show()
                mostrarDatos()
            }
    }

    private fun mostrarDatos() {
        val nombreCompleto = "$nombre $apellidos".trim()
        tvNombreCompleto.text = nombreCompleto.ifEmpty { "Usuario" }
        tvCorreoCabecera.text = correo

        val iniciales = (nombre.take(1) + apellidos.take(1)).uppercase()
        tvIniciales.text = iniciales.ifEmpty { "?" }

        tvDatoNombre.text = nombre.ifEmpty { "—" }
        tvDatoApellidos.text = apellidos.ifEmpty { "—" }
        tvDatoCelular.text = celular.ifEmpty { "—" }
        tvDatoCorreo.text = correo.ifEmpty { "—" }
    }

    private fun cargarEstadisticas() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("prestamos")
            .whereEqualTo("usuarioId", uid)
            .get()
            .addOnSuccessListener { resultado ->
                val estados = resultado.documents.map { it.getString("estado") }
                tvStatActivos.text = estados.count { it == ReglasPrestamo.ESTADO_ACTIVO }.toString()
                tvStatLeidos.text = estados.count { it == ReglasPrestamo.ESTADO_DEVUELTO }.toString()
            }

        db.collection("libros")
            .whereEqualTo("propietarioId", uid)
            .get()
            .addOnSuccessListener { resultado ->
                tvStatRegistrados.text = resultado.size().toString()
            }
    }

    // Anuncio: solo si algún préstamo vence en 3 días o menos, o ya venció
    private fun cargarAnuncio() {
        val uid = auth.currentUser?.uid ?: return

        AvisosPrestamo.cargar(uid) { avisos ->
            if (isFinishing || isDestroyed) return@cargar
            if (avisos.isEmpty()) {
                cardAnuncio.visibility = View.GONE
                return@cargar
            }

            val masUrgente = avisos.first()
            val estaVencido = masUrgente.dias < 0

            cardAnuncio.setCardBackgroundColor(
                Color.parseColor(if (estaVencido) "#FDECEE" else "#FFF4E0")
            )
            cardAnuncio.strokeColor = masUrgente.color

            tvAnuncioTitulo.text = masUrgente.titulo
            tvAnuncioTitulo.setTextColor(masUrgente.color)

            val otros = avisos.size - 1
            val textoOtros = when (otros) {
                0 -> ""
                1 -> "\nAdemás, tienes 1 préstamo más por vencer."
                else -> "\nAdemás, tienes $otros préstamos más por vencer."
            }
            tvAnuncioMensaje.text = masUrgente.mensaje + textoOtros

            cardAnuncio.visibility = View.VISIBLE
        }
    }

    // 🏆 Tarjeta de logros: nivel, puntos e insignias
    private fun cargarLogros() {
        val uid = auth.currentUser?.uid ?: return
        Logros.calcular(uid) { e ->
            if (e == null || isFinishing || isDestroyed) return@calcular
            val puntos = Logros.puntos(e)
            val nivel = Logros.nivel(puntos)
            val insignias = Logros.insignias(e)

            tvEmojiNivelPerfil.text = nivel.emoji
            tvNivelPerfil.text = "Nivel ${nivel.nombre} · $puntos pts"
            tvInsigniasPerfil.text =
                "🏅 ${insignias.count { it.desbloqueada }} de ${insignias.size} insignias · Ver mis logros"
        }
    }

    private fun mostrarEditarDatos() {
        val uid = auth.currentUser?.uid ?: return
        val vista = layoutInflater.inflate(R.layout.dialog_editar_perfil, null)

        val tilNombre = vista.findViewById<TextInputLayout>(R.id.tilEditarNombre)
        val tilApellidos = vista.findViewById<TextInputLayout>(R.id.tilEditarApellidos)
        val tilCelular = vista.findViewById<TextInputLayout>(R.id.tilEditarCelular)
        val etNombre = vista.findViewById<TextInputEditText>(R.id.etEditarNombre)
        val etApellidos = vista.findViewById<TextInputEditText>(R.id.etEditarApellidos)
        val etCelular = vista.findViewById<TextInputEditText>(R.id.etEditarCelular)
        val btnGuardar = vista.findViewById<MaterialButton>(R.id.btnGuardarPerfil)

        etNombre.setText(nombre)
        etApellidos.setText(apellidos)
        etCelular.setText(celular)

        val dialogo = AlertDialog.Builder(this).setView(vista).create()
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        vista.findViewById<MaterialButton>(R.id.btnCancelarPerfil).setOnClickListener {
            dialogo.dismiss()
        }

        btnGuardar.setOnClickListener {
            val nuevoNombre = etNombre.text.toString().trim()
            val nuevosApellidos = etApellidos.text.toString().trim()
            val nuevoCelular = etCelular.text.toString().trim()

            var todoValido = true

            tilNombre.error = when {
                nuevoNombre.isEmpty() -> "Este campo es obligatorio"
                !regexSoloTexto.matches(nuevoNombre) -> "Ingrese solo texto, sin números"
                else -> null
            }
            if (tilNombre.error != null) todoValido = false

            tilApellidos.error = when {
                nuevosApellidos.isEmpty() -> "Este campo es obligatorio"
                !regexSoloTexto.matches(nuevosApellidos) -> "Ingrese solo texto, sin números"
                else -> null
            }
            if (tilApellidos.error != null) todoValido = false

            tilCelular.error = when {
                nuevoCelular.isEmpty() -> "Este campo es obligatorio"
                !regexCelular.matches(nuevoCelular) -> "El celular debe tener 9 dígitos"
                else -> null
            }
            if (tilCelular.error != null) todoValido = false

            if (!todoValido) return@setOnClickListener

            btnGuardar.isEnabled = false

            val cambios = mapOf(
                "nombre" to nuevoNombre,
                "apellidos" to nuevosApellidos,
                "celular" to nuevoCelular,
                "correo" to correo
            )

            db.collection("usuarios").document(uid)
                .set(cambios, SetOptions.merge())
                .addOnSuccessListener {
                    nombre = nuevoNombre
                    apellidos = nuevosApellidos
                    celular = nuevoCelular
                    mostrarDatos()
                    Toast.makeText(this, "Datos actualizados", Toast.LENGTH_SHORT).show()
                    dialogo.dismiss()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "No se pudo guardar: ${error.message}", Toast.LENGTH_SHORT).show()
                    btnGuardar.isEnabled = true
                }
        }

        dialogo.show()
    }

    private fun confirmarCambioContrasena() {
        if (correo.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle("Cambiar contraseña")
            .setMessage("Te enviaremos un enlace a $correo para que crees una nueva contraseña.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar") { _, _ ->
                auth.setLanguageCode("es")
                auth.sendPasswordResetEmail(correo)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Revisa tu correo (y la carpeta de spam)", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo enviar: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    private fun confirmarCerrarSesion() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Cerrar sesión")
            .setMessage("¿Seguro que quieres cerrar sesión?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Cerrar sesión") { _, _ ->
                auth.signOut()
                irAlLogin()
            }
            .show()
    }

    // Vuelve al login y borra el historial de pantallas
    private fun irAlLogin() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}