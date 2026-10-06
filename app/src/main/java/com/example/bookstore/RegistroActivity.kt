package com.example.bookstore

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegistroActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registro)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val tilNombre = findViewById<TextInputLayout>(R.id.tilNombre)
        val tilApellidos = findViewById<TextInputLayout>(R.id.tilApellidos)
        val tilCelular = findViewById<TextInputLayout>(R.id.tilCelular)
        val tilCorreo = findViewById<TextInputLayout>(R.id.tilCorreo)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirmarPassword = findViewById<TextInputLayout>(R.id.tilConfirmarPassword)

        val etNombre = findViewById<TextInputEditText>(R.id.etNombre)
        val etApellidos = findViewById<TextInputEditText>(R.id.etApellidos)
        val etCelular = findViewById<TextInputEditText>(R.id.etCelular)
        val etCorreo = findViewById<TextInputEditText>(R.id.etCorreo)
        val etPassword = findViewById<TextInputEditText>(R.id.etPasswordRegistro)
        val etConfirmarPassword = findViewById<TextInputEditText>(R.id.etConfirmarPassword)
        val btnRegistrar = findViewById<MaterialButton>(R.id.btnRegistrarUsuario)

        // Validación en tiempo real: al salir de cada campo (perder el foco)
        etNombre.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validarSoloTexto(tilNombre, etNombre, "Ingrese solo texto, sin números")
        }
        etApellidos.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validarSoloTexto(tilApellidos, etApellidos, "Ingrese solo texto, sin números")
        }
        etCelular.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validarCelular(tilCelular, etCelular)
        }
        etCorreo.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validarCorreo(tilCorreo, etCorreo)
        }
        etPassword.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validarPassword(tilPassword, etPassword)
        }
        etConfirmarPassword.setOnFocusChangeListener { _, tieneFoco ->
            if (!tieneFoco) validarConfirmacion(tilConfirmarPassword, etPassword, etConfirmarPassword)
        }

        btnRegistrar.setOnClickListener {
            val nombreValido = validarSoloTexto(tilNombre, etNombre, "Ingrese solo texto, sin números")
            val apellidosValido = validarSoloTexto(tilApellidos, etApellidos, "Ingrese solo texto, sin números")
            val celularValido = validarCelular(tilCelular, etCelular)
            val correoValido = validarCorreo(tilCorreo, etCorreo)
            val passwordValido = validarPassword(tilPassword, etPassword)
            val confirmacionValida = validarConfirmacion(tilConfirmarPassword, etPassword, etConfirmarPassword)

            if (!nombreValido || !apellidosValido || !celularValido ||
                !correoValido || !passwordValido || !confirmacionValida) {
                Toast.makeText(this, "Revisa los campos marcados en rojo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nombre = etNombre.text.toString().trim()
            val apellidos = etApellidos.text.toString().trim()
            val celular = etCelular.text.toString().trim()
            val correo = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()

            auth.createUserWithEmailAndPassword(correo, password)
                .addOnSuccessListener { resultado ->
                    val userId = resultado.user?.uid

                    val usuario = hashMapOf(
                        "nombre" to nombre,
                        "apellidos" to apellidos,
                        "celular" to celular,
                        "correo" to correo
                    )

                    if (userId != null) {
                        db.collection("usuarios").document(userId)
                            .set(usuario)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registro exitoso", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al guardar datos: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // CR-02: las reglas y los mensajes ahora vienen de Validaciones (DRY)
    private fun validarSoloTexto(til: TextInputLayout, et: TextInputEditText, mensaje: String): Boolean {
        val error = Validaciones.errorSoloTexto(et.text.toString().trim(), mensaje)
        til.error = error
        return error == null
    }

    // Antes solo contaba 9 caracteres y aceptaba "98765-432"
    private fun validarCelular(til: TextInputLayout, et: TextInputEditText): Boolean {
        val error = Validaciones.errorCelular(et.text.toString().trim())
        til.error = error
        return error == null
    }

    private fun validarCorreo(til: TextInputLayout, et: TextInputEditText): Boolean {
        val error = Validaciones.errorCorreo(et.text.toString().trim())
        til.error = error
        return error == null
    }

    private fun validarPassword(til: TextInputLayout, et: TextInputEditText): Boolean {
        val error = Validaciones.errorPassword(et.text.toString().trim())
        til.error = error
        return error == null
    }

    private fun validarConfirmacion(til: TextInputLayout, etPassword: TextInputEditText, etConfirmar: TextInputEditText): Boolean {
        val password = etPassword.text.toString().trim()
        val confirmar = etConfirmar.text.toString().trim()
        return if (confirmar.isEmpty()) {
            til.error = Validaciones.CAMPO_OBLIGATORIO
            false
        } else if (password != confirmar) {
            til.error = "Las contraseñas no coinciden"
            false
        } else {
            til.error = null
            true
        }
    }
}