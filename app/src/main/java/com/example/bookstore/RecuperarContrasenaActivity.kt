package com.example.bookstore

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class RecuperarContrasenaActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CORREO = "extra_correo"
    }

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recuperar_contrasena)

        auth = FirebaseAuth.getInstance()
        auth.setLanguageCode("es") // El correo llega en español

        val tilCorreo = findViewById<TextInputLayout>(R.id.tilCorreoRecuperar)
        val etCorreo = findViewById<TextInputEditText>(R.id.etCorreoRecuperar)
        val btnEnviar = findViewById<MaterialButton>(R.id.btnEnviarEnlace)
        val llFormulario = findViewById<LinearLayout>(R.id.llFormulario)
        val llConfirmacion = findViewById<LinearLayout>(R.id.llConfirmacion)
        val tvMensaje = findViewById<TextView>(R.id.tvMensajeConfirmacion)

        // Si el usuario ya había escrito su correo en el login, lo copiamos
        intent.getStringExtra(EXTRA_CORREO)?.let { etCorreo.setText(it) }

        findViewById<MaterialButton>(R.id.btnRegresarRecuperar).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnVolverLogin).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnReintentar).setOnClickListener {
            llConfirmacion.visibility = View.GONE
            llFormulario.visibility = View.VISIBLE
            etCorreo.text?.clear()
            etCorreo.requestFocus()
        }

        btnEnviar.setOnClickListener {
            val correo = etCorreo.text.toString().trim()

            if (correo.isEmpty()) {
                tilCorreo.error = "Escribe tu correo"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                tilCorreo.error = "Correo no válido"
                return@setOnClickListener
            }
            tilCorreo.error = null

            btnEnviar.isEnabled = false
            btnEnviar.text = "Enviando..."

            auth.sendPasswordResetEmail(correo)
                .addOnCompleteListener { tarea ->
                    btnEnviar.isEnabled = true
                    btnEnviar.text = "Enviar enlace"

                    if (tarea.isSuccessful) {
                        tvMensaje.text = "Si $correo está registrado en BookStore, recibirás un " +
                                "enlace para crear una nueva contraseña. Revisa también la carpeta de spam."
                        llFormulario.visibility = View.GONE
                        llConfirmacion.visibility = View.VISIBLE
                    } else {
                        tilCorreo.error = when (tarea.exception) {
                            is FirebaseAuthInvalidUserException -> "No existe una cuenta con ese correo"
                            is FirebaseNetworkException -> "Sin conexión a internet"
                            else -> "No se pudo enviar. Inténtalo de nuevo"
                        }
                    }
                }
        }
    }
}