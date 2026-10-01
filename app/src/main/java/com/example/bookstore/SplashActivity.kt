package com.example.bookstore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    companion object {
        private const val DURACION_SPLASH = 1800L // 1,8 segundos
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nuestro único splash: fondo blanco y logo nítido
        setContentView(R.layout.activity_splash)

        // El logo aparece suavemente
        val logo = findViewById<ImageView>(R.id.splashLogo)
        logo.alpha = 0f
        logo.scaleX = 0.85f
        logo.scaleY = 0.85f
        logo.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // "Mantener la sesión iniciada"
        val auth = FirebaseAuth.getInstance()
        val mantenerSesion = Sesion.debeMantener(this)
        if (!mantenerSesion) {
            auth.signOut()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val destino = if (mantenerSesion && auth.currentUser != null) {
                HomeActivity::class.java
            } else {
                MainActivity::class.java
            }
            startActivity(Intent(this, destino))
            // Transición suave hacia el login o el Home
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }, DURACION_SPLASH)
    }
}