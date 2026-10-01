package com.example.bookstore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    companion object {
        private const val CLAVE_VENTANA_MOSTRADA = "ventana_vencimiento_mostrada"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tvBienvenida: TextView
    private lateinit var tvAvatar: TextView
    private lateinit var tvBadge: TextView
    private lateinit var tvChipLogros: TextView

    private var avisos = listOf<AvisoPrestamo>()
    private var turnos = listOf<AvisoTurno>()

    // La ventanita sale una vez por cada entrada al Home, no al volver de otras pantallas
    private var ventanaYaMostrada = false

    private val colorTurno by lazy { Color.parseColor("#2E8B57") }
    private val colorTurnoUrgente by lazy { Color.parseColor("#C27C0E") }

    // Respuesta del usuario al permiso de notificaciones
    private val pedirPermisoNotificaciones = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (!concedido) {
            Toast.makeText(
                this,
                "No recibirás recordatorios de devolución. Puedes activarlos en los ajustes del celular.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Si solo se giró la pantalla, no se vuelve a mostrar
        ventanaYaMostrada = savedInstanceState?.getBoolean(CLAVE_VENTANA_MOSTRADA) ?: false

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvBienvenida = findViewById(R.id.tvBienvenida)
        tvAvatar = findViewById(R.id.tvAvatarHome)
        tvBadge = findViewById(R.id.tvBadgeNotificaciones)
        tvChipLogros = findViewById(R.id.tvChipLogros)

        // Recordatorios de devolución
        Recordatorios.crearCanal(this)
        Recordatorios.programar(this)
        solicitarPermisoNotificaciones()

        tvAvatar.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        tvChipLogros.setOnClickListener {
            startActivity(Intent(this, LogrosActivity::class.java))
        }

        findViewById<View>(R.id.btnNotificaciones).setOnClickListener {
            mostrarPanelAvisos()
        }

        findViewById<MaterialButton>(R.id.btnRegistrarLibro).setOnClickListener {
            startActivity(Intent(this, RegistrarLibroActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnMisLibros).setOnClickListener {
            startActivity(Intent(this, MisLibrosActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnBuscarLibros).setOnClickListener {
            startActivity(Intent(this, BuscarLibrosActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnMisPrestamos).setOnClickListener {
            abrirMisPrestamos()
        }

        findViewById<MaterialButton>(R.id.btnCerrarSesion).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Seguro que quieres cerrar sesión?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión") { _, _ ->
                    auth.signOut()
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CLAVE_VENTANA_MOSTRADA, ventanaYaMostrada)
    }

    override fun onResume() {
        super.onResume()
        cargarUsuario()
        cargarLogros()

        // Primero se pasan los turnos vencidos, luego se cargan los avisos
        val uid = auth.currentUser?.uid ?: return
        Reservas.revisarFilasDe(uid) { cargarAvisos() }
    }

    private fun abrirMisPrestamos() {
        startActivity(Intent(this, MisPrestamosActivity::class.java))
    }

    // Abre Buscar libro con el título ya escrito
    private fun abrirLibro(titulo: String) {
        val intent = Intent(this, BuscarLibrosActivity::class.java)
        intent.putExtra(BuscarLibrosActivity.EXTRA_BUSCAR, titulo)
        startActivity(intent)
    }

    // En Android 13 o superior hay que pedir permiso para mostrar notificaciones
    private fun solicitarPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pedirPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Saludo e iniciales del ícono
    private fun cargarUsuario() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("usuarios").document(uid)
            .get()
            .addOnSuccessListener { documento ->
                val nombre = documento.getString("nombre")?.trim().orEmpty()
                val apellidos = documento.getString("apellidos")?.trim().orEmpty()

                tvBienvenida.text = if (nombre.isNotEmpty()) {
                    "¡Hola, ${nombre.split(" ").first()}!"
                } else {
                    "¡Bienvenido a BookStore!"
                }

                val iniciales = (nombre.take(1) + apellidos.take(1)).uppercase()
                tvAvatar.text = iniciales.ifEmpty { "👤" }
            }
    }

    // ⭐ Pastilla de puntos: late si hay una insignia nueva sin ver
    private fun cargarLogros() {
        val uid = auth.currentUser?.uid ?: return
        Logros.sincronizar(uid) { e ->
            if (e == null || isFinishing || isDestroyed) return@sincronizar

            val nuevas = Logros.insigniasNuevas(this, uid, Logros.insignias(e))
            if (nuevas.isNotEmpty()) {
                tvChipLogros.text = "🎉 ¡Nueva insignia! Toca para verla"
                tvChipLogros.animate().scaleX(1.1f).scaleY(1.1f).setDuration(300)
                    .withEndAction {
                        tvChipLogros.animate().scaleX(1f).scaleY(1f).setDuration(300).start()
                    }.start()
            } else {
                val puntos = Logros.puntos(e)
                val nivel = Logros.nivel(puntos)
                tvChipLogros.text = "⭐ $puntos pts · ${nivel.emoji} ${nivel.nombre}  ›"
            }
        }
    }

    // Avisos de préstamos + turnos de reservas
    private fun cargarAvisos() {
        val uid = auth.currentUser?.uid ?: return

        AvisosPrestamo.cargar(uid) { listaAvisos ->
            avisos = listaAvisos
            Reservas.cargarTurnos(uid) { listaTurnos ->
                turnos = listaTurnos
                if (isFinishing || isDestroyed) return@cargarTurnos

                val total = avisos.size + turnos.size
                if (total == 0) {
                    tvBadge.visibility = View.GONE
                } else {
                    tvBadge.text = if (total > 9) "9+" else total.toString()
                    tvBadge.visibility = View.VISIBLE

                    if (!ventanaYaMostrada) {
                        ventanaYaMostrada = true
                        mostrarVentanaAvisos()
                    }
                }
            }
        }
    }

    private fun colorDe(turno: AvisoTurno) = if (turno.urgente) colorTurnoUrgente else colorTurno

    // Crea un recuadro de aviso (el mismo diseño en la campanita y en la ventanita)
    private fun agregarItem(
        contenedor: LinearLayout,
        titulo: String,
        mensaje: String,
        color: Int,
        alTocar: (() -> Unit)?
    ) {
        val item = layoutInflater.inflate(R.layout.item_aviso, contenedor, false) as MaterialCardView
        item.strokeColor = color

        val tvTitulo = item.findViewById<TextView>(R.id.tvAvisoTitulo)
        tvTitulo.text = titulo
        tvTitulo.setTextColor(color)
        item.findViewById<TextView>(R.id.tvAvisoMensaje).text = mensaje

        if (alTocar != null) {
            item.setOnClickListener { alTocar() }
        } else {
            item.isClickable = false
        }
        contenedor.addView(item)
    }

    // Ventanita al entrar: turnos y libros que vencen pronto
    private fun mostrarVentanaAvisos() {
        val vista = layoutInflater.inflate(R.layout.dialog_aviso_vencimiento, null)

        val hayVencido = avisos.any { it.dias < 0 }
        val colorPrincipal = if (avisos.isNotEmpty()) avisos.first().color else colorTurno

        vista.findViewById<MaterialCardView>(R.id.cardAvisoVencimiento).strokeColor = colorPrincipal

        vista.findViewById<TextView>(R.id.tvIconoAviso).text = when {
            hayVencido -> "❗"
            avisos.isNotEmpty() -> "⏰"
            else -> "🎉"
        }

        val tvTitulo = vista.findViewById<TextView>(R.id.tvTituloAviso)
        tvTitulo.setTextColor(colorPrincipal)
        tvTitulo.text = when {
            hayVencido && avisos.size == 1 -> "Tienes un libro vencido"
            hayVencido -> "Tienes libros vencidos"
            avisos.isNotEmpty() && turnos.isNotEmpty() -> "Tienes novedades de tus libros"
            turnos.size == 1 -> "¡Es tu turno!"
            turnos.isNotEmpty() -> "¡Tienes ${turnos.size} turnos!"
            avisos.size == 1 -> "Tienes un libro por devolver"
            else -> "Tienes ${avisos.size} libros por devolver"
        }

        vista.findViewById<TextView>(R.id.tvSubtituloAviso).text = when {
            avisos.isEmpty() -> "Un libro que reservaste ya está disponible. ¡Obtenlo antes de que pase al siguiente!"
            turnos.isEmpty() -> "Devuélvelos a tiempo para que otros usuarios también puedan leerlos."
            else -> "Revisa tus turnos y devuelve a tiempo tus préstamos."
        }

        val llLibros = vista.findViewById<LinearLayout>(R.id.llLibrosPorVencer)
        for (turno in turnos) agregarItem(llLibros, turno.titulo, turno.mensaje, colorDe(turno), null)
        for (aviso in avisos) agregarItem(llLibros, aviso.titulo, aviso.mensaje, aviso.color, null)

        val dialogo = AlertDialog.Builder(this).setView(vista).create()
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        vista.findViewById<MaterialButton>(R.id.btnAvisoVerPrestamos).setOnClickListener {
            dialogo.dismiss()
            abrirMisPrestamos()
        }
        vista.findViewById<MaterialButton>(R.id.btnAvisoEntendido).setOnClickListener {
            dialogo.dismiss()
        }

        dialogo.show()
    }

    // 🔔 Panel de la campanita
    private fun mostrarPanelAvisos() {
        val hoja = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.bottom_sheet_avisos, null)

        val llLista = vista.findViewById<LinearLayout>(R.id.llListaAvisos)
        val tvSinAvisos = vista.findViewById<TextView>(R.id.tvSinAvisos)

        if (avisos.isEmpty() && turnos.isEmpty()) {
            tvSinAvisos.visibility = View.VISIBLE
        } else {
            // Primero los turnos (en verde); al tocarlos se abre el libro
            for (turno in turnos) {
                agregarItem(llLista, turno.titulo, turno.mensaje, colorDe(turno)) {
                    hoja.dismiss()
                    abrirLibro(turno.reserva.titulo)
                }
            }
            // Luego los préstamos por vencer
            for (aviso in avisos) {
                agregarItem(llLista, aviso.titulo, aviso.mensaje, aviso.color) {
                    hoja.dismiss()
                    abrirMisPrestamos()
                }
            }
        }

        vista.findViewById<MaterialButton>(R.id.btnVerPrestamosAvisos).setOnClickListener {
            hoja.dismiss()
            abrirMisPrestamos()
        }

        hoja.setContentView(vista)
        hoja.show()
    }
}