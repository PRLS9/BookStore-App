package com.example.bookstore

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val etUsuario = findViewById<TextInputEditText>(R.id.etUsuario)
        val etContrasena = findViewById<TextInputEditText>(R.id.etContrasena)
        val cbMantenerSesion = findViewById<MaterialCheckBox>(R.id.cbMantenerSesion)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnRegistro = findViewById<MaterialButton>(R.id.btnRegistro)
        val tvOlvidaste = findViewById<TextView>(R.id.tvOlvidasteContrasena)

        // La casilla recuerda tu última elección
        cbMantenerSesion.isChecked = Sesion.debeMantener(this)

        btnLogin.setOnClickListener {
            val correo = etUsuario.text.toString().trim()
            val password = etContrasena.text.toString().trim()

            if (correo.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Evita que se presione varias veces mientras carga
            btnLogin.isEnabled = false
            btnLogin.text = "Ingresando..."

            auth.signInWithEmailAndPassword(correo, password)
                .addOnSuccessListener {
                    // Se guarda si el estudiante quiere mantener la sesión
                    Sesion.guardar(this, cbMantenerSesion.isChecked)

                    Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                .addOnFailureListener { error ->
                    btnLogin.isEnabled = true
                    btnLogin.text = "Ingresar"

                    val mensaje = when (error) {
                        is FirebaseAuthInvalidUserException,
                        is FirebaseAuthInvalidCredentialsException -> "Correo o contraseña incorrectos"
                        is FirebaseNetworkException -> "Sin conexión a internet"
                        else -> "No se pudo iniciar sesión. Inténtalo de nuevo"
                    }
                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                }
        }

        btnRegistro.setOnClickListener {
            startActivity(Intent(this, RegistroActivity::class.java))
        }

        tvOlvidaste.setOnClickListener {
            val intent = Intent(this, RecuperarContrasenaActivity::class.java)
            intent.putExtra(RecuperarContrasenaActivity.EXTRA_CORREO, etUsuario.text.toString().trim())
            startActivity(intent)
        }
    }
}