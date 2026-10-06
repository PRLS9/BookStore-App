package com.example.bookstore

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
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
            // Mantenimiento adaptativo: la transición suave viaja junto con startActivity.
            // Reemplaza a overridePendingTransition, obsoleto desde Android 14 (API 34),
            // y funciona igual en todas las versiones desde minSdk 24, sin código obsoleto.
            val transicion = ActivityOptionsCompat.makeCustomAnimation(
                this, android.R.anim.fade_in, android.R.anim.fade_out
            )
            startActivity(Intent(this, destino), transicion.toBundle())
            finish()
        }, DURACION_SPLASH)
    }
}