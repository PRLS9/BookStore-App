package com.example.bookstore

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Date

class BuscarLibrosActivity : AppCompatActivity() {

    companion object {
        // Para abrir esta pantalla con un texto ya escrito en el buscador
        const val EXTRA_BUSCAR = "extra_buscar"
    }

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var prestamoService: PrestamoService
    private lateinit var adapter: LibroAdapter
    private lateinit var llGeneros: LinearLayout
    private lateinit var btnFiltrar: MaterialButton
    private lateinit var etBuscar: TextInputEditText
    private lateinit var recyclerView: RecyclerView

    // Filtros activos
    private var filtroTitulo = ""
    private var filtroAutor = ""
    private var filtroCurso = ""
    private var filtroGeneros = arrayListOf<String>()
    private var filtroTipo = FiltrosActivity.TODOS
    private var filtroOrden = FiltrosActivity.ORDEN_DEFECTO
    private var filtroDisponibilidad = FiltrosActivity.TODOS

    // Listas para los desplegables de la ventana de filtros
    private var listaTitulos = arrayListOf<String>()
    private var listaAutores = arrayListOf<String>()

    private val chipsGeneros = listOf(FiltrosActivity.TODOS) + FiltrosActivity.GENEROS

    private val colorAzul by lazy { Color.parseColor("#2C4770") }
    private val colorMorado by lazy { Color.parseColor("#7A6FB5") }
    private val colorVerde by lazy { Color.parseColor("#2E8B57") }

    private val lanzadorFiltros = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == RESULT_OK) {
            val datos = resultado.data ?: return@registerForActivityResult
            filtroTitulo = datos.getStringExtra(FiltrosActivity.EXTRA_TITULO) ?: ""
            filtroAutor = datos.getStringExtra(FiltrosActivity.EXTRA_AUTOR) ?: ""
            filtroCurso = datos.getStringExtra(FiltrosActivity.EXTRA_CURSO) ?: ""
            filtroGeneros = datos.getStringArrayListExtra(FiltrosActivity.EXTRA_GENEROS) ?: arrayListOf()
            filtroTipo = datos.getStringExtra(FiltrosActivity.EXTRA_TIPO) ?: FiltrosActivity.TODOS
            filtroOrden = datos.getStringExtra(FiltrosActivity.EXTRA_ORDEN) ?: FiltrosActivity.ORDEN_DEFECTO
            filtroDisponibilidad = datos.getStringExtra(FiltrosActivity.EXTRA_DISPONIBILIDAD) ?: FiltrosActivity.TODOS
            aplicarFiltros()

            if (::adapter.isInitialized && adapter.itemCount == 0) {
                Toast.makeText(this, "No hay libros con esos filtros", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_buscar_libros)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        // Inyección de dependencias: aquí se decide qué repositorio usa el servicio
        prestamoService = PrestamoService(FirestorePrestamoRepository(db))

        findViewById<MaterialButton>(R.id.btnRegresar).setOnClickListener { finish() }

        btnFiltrar = findViewById(R.id.btnFiltrar)
        btnFiltrar.setOnClickListener { abrirFiltros() }

        etBuscar = findViewById(R.id.etBuscar)
        recyclerView = findViewById(R.id.recyclerLibros)
        recyclerView.layoutManager = LinearLayoutManager(this)
        llGeneros = findViewById(R.id.llGeneros)

        etBuscar.doAfterTextChanged { texto ->
            if (::adapter.isInitialized) adapter.filtrar(texto?.toString() ?: "")
        }

        // Si llegamos desde "Ver libro" en una reserva, el título ya viene escrito
        intent.getStringExtra(EXTRA_BUSCAR)?.let { etBuscar.setText(it) }
    }

    // Se recarga cada vez que vuelves a esta pantalla
    override fun onResume() {
        super.onResume()
        cargarLibros()
    }

    private fun cargarLibros() {
        db.collection("libros")
            .get()
            .addOnSuccessListener { resultado ->
                val listaLibros = resultado.map { it.toObject(Libro::class.java) }
                    .filter { !Oferta.estaFinalizado(it) } // Los vendidos, intercambiados o donados no se muestran

                // ⏭️ Si alguien no aprovechó su turno, pasa al siguiente de la fila
                listaLibros.filter { Reservas.prioridadVencida(it) }
                    .forEach { Reservas.avanzarFila(it) }
                listaTitulos = ArrayList(
                    listaLibros.map { it.titulo }.filter { it.isNotBlank() }.distinct().sorted()
                )
                listaAutores = ArrayList(
                    listaLibros.map { it.autor }.filter { it.isNotBlank() }.distinct().sorted()
                )

                adapter = LibroAdapter(listaLibros) { libro -> mostrarDetalleLibro(libro) }
                recyclerView.adapter = adapter
                adapter.filtrar(etBuscar.text?.toString() ?: "")
                aplicarFiltros()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar libros: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun abrirFiltros() {
        if (!::adapter.isInitialized) {
            Toast.makeText(this, "Espera a que carguen los libros", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, FiltrosActivity::class.java)
        intent.putExtra(FiltrosActivity.EXTRA_TITULO, filtroTitulo)
        intent.putExtra(FiltrosActivity.EXTRA_AUTOR, filtroAutor)
        intent.putExtra(FiltrosActivity.EXTRA_CURSO, filtroCurso)
        intent.putStringArrayListExtra(FiltrosActivity.EXTRA_GENEROS, filtroGeneros)
        intent.putExtra(FiltrosActivity.EXTRA_TIPO, filtroTipo)
        intent.putExtra(FiltrosActivity.EXTRA_ORDEN, filtroOrden)
        intent.putExtra(FiltrosActivity.EXTRA_DISPONIBILIDAD, filtroDisponibilidad)
        intent.putStringArrayListExtra(FiltrosActivity.EXTRA_LISTA_TITULOS, listaTitulos)
        intent.putStringArrayListExtra(FiltrosActivity.EXTRA_LISTA_AUTORES, listaAutores)
        lanzadorFiltros.launch(intent)
    }

    private fun aplicarFiltros() {
        if (!::adapter.isInitialized) return
        adapter.aplicarFiltros(
            filtroTitulo, filtroAutor, filtroCurso, filtroGeneros.toSet(),
            filtroTipo, filtroDisponibilidad, filtroOrden
        )
        pintarChipsGeneros()
        actualizarTextoFiltrar()
    }

    private fun actualizarTextoFiltrar() {
        var activos = 0
        if (filtroTitulo.isNotEmpty()) activos++
        if (filtroAutor.isNotEmpty()) activos++
        if (filtroCurso.isNotEmpty()) activos++
        if (filtroGeneros.isNotEmpty()) activos++
        if (filtroTipo != FiltrosActivity.TODOS) activos++
        if (filtroOrden != FiltrosActivity.ORDEN_DEFECTO) activos++
        if (filtroDisponibilidad != FiltrosActivity.TODOS) activos++
        btnFiltrar.text = if (activos > 0) "Filtrar ($activos)" else "Filtrar"
    }

    // ---------- DETALLE DEL LIBRO ----------

    private fun mostrarDetalleLibro(libro: Libro) {
        val vista = layoutInflater.inflate(R.layout.dialog_detalle_libro, null)
        val uid = auth.currentUser?.uid
        val tipo = Oferta.tipoDe(libro)

        val tvLetra = vista.findViewById<TextView>(R.id.tvDetalleLetra)
        tvLetra.text = libro.titulo.take(1).uppercase()
        tvLetra.background.mutate().setTint(LibroAdapter.colorPorGenero(libro.genero))

        vista.findViewById<TextView>(R.id.tvDetalleTitulo).text = libro.titulo
        vista.findViewById<TextView>(R.id.tvDetalleAutor).text = "Autor: ${libro.autor}"
        vista.findViewById<TextView>(R.id.tvDetalleGenero).text =
            if (libro.curso.isNotEmpty()) "${libro.genero}\nCurso: ${libro.curso}" else libro.genero

        val disponible = libro.estado.equals(Oferta.ESTADO_DISPONIBLE, ignoreCase = true)
        val loTengoYo = !disponible && uid != null && libro.prestadoA == uid
        val esMio = uid != null && libro.propietarioId == uid
        val esDeEstudiante = libro.propietarioId.isNotEmpty()
        val miTurno = Reservas.esMiTurno(libro, uid)
        val reservadoParaOtro = Reservas.reservadoParaOtro(libro, uid)

        // Estado
        val tvEstado = vista.findViewById<TextView>(R.id.tvDetalleEstado)
        tvEstado.text = when {
            loTengoYo -> "Prestado a ti"
            disponible && reservadoParaOtro -> "Reservado 🔒"
            else -> libro.estado.replaceFirstChar { it.uppercase() }.ifEmpty { "Sin estado" }
        }
        tvEstado.background.mutate().setTint(
            when {
                disponible && reservadoParaOtro -> colorMorado
                disponible -> colorVerde
                else -> Color.parseColor("#D9A62E")
            }
        )

        // Tipo de oferta
        val tvOferta = vista.findViewById<TextView>(R.id.tvDetalleOferta)
        tvOferta.text = Oferta.etiqueta(libro)
        tvOferta.background.mutate().setTint(Oferta.color(libro))

        // Información extra
        val lineasExtra = mutableListOf<String>()
        if (miTurno) {
            lineasExtra.add("🎉 ¡Es tu turno! Te quedan ${Reservas.tiempoRestante(libro.reservaHasta?.toDate())} para obtenerlo.")
        }
        if (disponible && reservadoParaOtro) {
            lineasExtra.add("🔒 Reservado para otro estudiante por ${Reservas.tiempoRestante(libro.reservaHasta?.toDate())} más.")
        }
        if (tipo == Oferta.INTERCAMBIO && libro.intercambioPor.isNotEmpty()) {
            lineasExtra.add("🔄 A cambio busca: ${libro.intercambioPor}")
        }
        if (esDeEstudiante && libro.propietarioNombre.isNotEmpty()) {
            lineasExtra.add("👤 Ofrecido por: ${if (esMio) "ti" else libro.propietarioNombre}")
        }
        val tvExtra = vista.findViewById<TextView>(R.id.tvDetalleOfertaExtra)
        if (lineasExtra.isNotEmpty()) {
            tvExtra.text = lineasExtra.joinToString("\n")
            tvExtra.visibility = View.VISIBLE
        }

        vista.findViewById<TextView>(R.id.tvDetalleSinopsis).text =
            libro.sinopsis.ifEmpty { "Sinopsis no disponible." }

        val dialogo = AlertDialog.Builder(this)
            .setView(vista)
            .create()
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        vista.findViewById<MaterialButton>(R.id.btnCerrarDetalle).setOnClickListener {
            dialogo.dismiss()
        }

        // Botón principal
        val btnPrincipal = vista.findViewById<MaterialButton>(R.id.btnObtenerLibro)
        when {
            esMio -> {
                btnPrincipal.isEnabled = false
                btnPrincipal.text = "Es tu libro"
            }
            tipo == Oferta.PRESTAMO && loTengoYo -> {
                btnPrincipal.isEnabled = false
                btnPrincipal.text = "Ya lo tienes"
            }
            // Prestado a otra persona: se puede reservar
            tipo == Oferta.PRESTAMO && !disponible -> configurarBotonReserva(btnPrincipal, libro, dialogo)
            !disponible -> {
                btnPrincipal.isEnabled = false
                btnPrincipal.text = "No disponible"
            }
            // Disponible, pero con prioridad para otra persona
            tipo == Oferta.PRESTAMO && reservadoParaOtro -> {
                btnPrincipal.isEnabled = false
                btnPrincipal.text = "Reservado 🔒"
            }
            // Libro del catálogo
            tipo == Oferta.PRESTAMO && !esDeEstudiante -> {
                btnPrincipal.text = if (miTurno) "Obtener · ¡Tu turno!" else "Obtener"
                btnPrincipal.setOnClickListener {
                    dialogo.dismiss()
                    confirmarObtener(libro)
                }
            }
            // Libro de otro estudiante
            tipo == Oferta.PRESTAMO -> configurarBotonSolicitud(btnPrincipal, tvExtra, libro, dialogo, miTurno)
            // Intercambio, venta o donación
            else -> {
                btnPrincipal.text = "Contactar"
                btnPrincipal.setOnClickListener {
                    dialogo.dismiss()
                    contactarDueno(libro)
                }
            }
        }

        dialogo.show()
    }

    // ---------- RESERVAS ----------

    private fun configurarBotonReserva(boton: MaterialButton, libro: Libro, dialogo: AlertDialog) {
        val uid = auth.currentUser?.uid ?: return
        boton.isEnabled = false
        boton.text = "Consultando…"
        boton.backgroundTintList = ColorStateList.valueOf(colorMorado)

        db.collection("reservas").document(Reservas.idPara(libro.id, uid))
            .get()
            .addOnSuccessListener { documento ->
                val reserva = if (documento.exists()) documento.toObject(Reserva::class.java) else null
                val yaEnFila = reserva?.estado == Reservas.EN_ESPERA

                boton.isEnabled = true
                boton.text = if (yaEnFila) "📌 Reservado · ver fila" else "📌 Reservar"
                boton.setOnClickListener {
                    dialogo.dismiss()
                    mostrarHojaReserva(libro, if (yaEnFila) reserva else null)
                }
            }
            .addOnFailureListener {
                boton.isEnabled = true
                boton.text = "📌 Reservar"
                boton.setOnClickListener {
                    dialogo.dismiss()
                    mostrarHojaReserva(libro, null)
                }
            }
    }

    // Panel que sube desde abajo con tu lugar en la fila
    private fun mostrarHojaReserva(libro: Libro, miReserva: Reserva?) {
        val uid = auth.currentUser?.uid ?: return
        val hoja = BottomSheetDialog(this)
        val vista = layoutInflater.inflate(R.layout.bottom_sheet_reserva, null)
        hoja.setContentView(vista)

        val tvTitulo = vista.findViewById<TextView>(R.id.tvHojaTitulo)
        val tvLibro = vista.findViewById<TextView>(R.id.tvHojaLibro)
        val tvPosicion = vista.findViewById<TextView>(R.id.tvHojaPosicion)
        val tvPosicionTexto = vista.findViewById<TextView>(R.id.tvHojaPosicionTexto)
        val llFila = vista.findViewById<LinearLayout>(R.id.llHojaFila)
        val btnConfirmar = vista.findViewById<MaterialButton>(R.id.btnHojaConfirmar)
        val btnCancelarReserva = vista.findViewById<MaterialButton>(R.id.btnHojaCancelarReserva)

        tvLibro.text = "\"${libro.titulo}\" · ${libro.autor}"
        if (miReserva != null) tvTitulo.text = "📌 Tu reserva"
        btnConfirmar.isEnabled = false
        btnConfirmar.text = "Cargando fila…"

        Reservas.cargarFila(libro.id) { fila ->
            val posicion = if (miReserva != null) {
                (fila.indexOfFirst { it.usuarioId == uid } + 1).coerceAtLeast(1)
            } else {
                fila.size + 1
            }
            val antes = posicion - 1

            tvPosicion.text = "#$posicion"
            tvPosicionTexto.text = when {
                miReserva != null && antes == 0 -> "¡Eres el siguiente en la fila! 🎉"
                miReserva != null -> "Eres el $posicion.º en la fila · ${Reservas.textoPersonas(antes)} antes que tú"
                antes == 0 -> "¡Nadie más lo ha reservado! Serás el primero"
                else -> "Serás el $posicion.º en la fila · ${Reservas.textoPersonas(antes)} antes que tú"
            }

            val circuloTu = pintarFila(llFila, fila, uid, agregarTuPrevio = miReserva == null)

            if (miReserva == null) {
                btnConfirmar.isEnabled = true
                btnConfirmar.text = "Confirmar reserva"
                btnConfirmar.setOnClickListener {
                    btnConfirmar.isEnabled = false
                    btnConfirmar.text = "Reservando…"

                    reservar(libro) { exito ->
                        if (!exito) {
                            btnConfirmar.isEnabled = true
                            btnConfirmar.text = "Confirmar reserva"
                            return@reservar
                        }

                        // Celebración: vibración, botón verde y el circulito "Tú" late
                        btnConfirmar.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        btnConfirmar.text = "✓ ¡Reservado!"
                        btnConfirmar.backgroundTintList = ColorStateList.valueOf(colorVerde)
                        circuloTu?.animate()?.alpha(1f)?.scaleX(1.3f)?.scaleY(1.3f)?.setDuration(180)
                            ?.withEndAction {
                                circuloTu.animate().scaleX(1f).scaleY(1f).setDuration(180).start()
                            }?.start()

                        btnConfirmar.postDelayed({
                            hoja.dismiss()
                            Snackbar.make(
                                findViewById(R.id.main),
                                "📌 Reservaste \"${libro.titulo}\" · eres el $posicion.º",
                                Snackbar.LENGTH_LONG
                            ).setAction("Deshacer") {
                                cancelarReserva(Reservas.idPara(libro.id, uid))
                            }.show()
                        }, 1000)
                    }
                }
            } else {
                btnConfirmar.visibility = View.GONE
                btnCancelarReserva.visibility = View.VISIBLE
                btnCancelarReserva.setOnClickListener {
                    cancelarReserva(miReserva.id)
                    hoja.dismiss()
                }
            }
        }

        hoja.show()
    }

    // Dibuja la fila: 📖 › A › B › Tú, con animación de entrada
    private fun pintarFila(
        contenedor: LinearLayout,
        fila: List<Reserva>,
        uid: String,
        agregarTuPrevio: Boolean
    ): TextView? {
        contenedor.removeAllViews()
        val circulos = mutableListOf<TextView>()
        var circuloTu: TextView? = null

        // Quien tiene el libro ahora
        circulos.add(crearCirculo("📖", Color.parseColor("#D9A62E"), false))

        for (reserva in fila) {
            if (reserva.usuarioId == uid) {
                val circulo = crearCirculo("Tú", colorAzul, true)
                circuloTu = circulo
                circulos.add(circulo)
            } else {
                val inicial = reserva.usuarioNombre.take(1).uppercase().ifEmpty { "?" }
                circulos.add(crearCirculo(inicial, Color.parseColor("#B8B3D9"), false))
            }
        }

        // Vista previa de dónde quedarías si reservas
        if (agregarTuPrevio) {
            val circulo = crearCirculo("Tú", colorAzul, true)
            circuloTu = circulo
            circulos.add(circulo)
        }

        circulos.forEachIndexed { indice, circulo ->
            if (indice > 0) contenedor.addView(crearFlecha())
            contenedor.addView(circulo)

            val alphaFinal = if (agregarTuPrevio && circulo == circuloTu) 0.45f else 1f
            circulo.alpha = 0f
            circulo.scaleX = 0.4f
            circulo.scaleY = 0.4f
            circulo.animate()
                .alpha(alphaFinal).scaleX(1f).scaleY(1f)
                .setStartDelay(indice * 80L)
                .setDuration(260)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
        return circuloTu
    }

    private fun crearCirculo(texto: String, color: Int, destacado: Boolean): TextView {
        val tamano = dp(if (destacado) 48 else 40)
        val circulo = TextView(this)
        circulo.layoutParams = LinearLayout.LayoutParams(tamano, tamano)
        circulo.gravity = Gravity.CENTER
        circulo.text = texto
        circulo.textSize = if (destacado) 14f else 15f
        circulo.setTextColor(Color.WHITE)
        circulo.setTypeface(circulo.typeface, android.graphics.Typeface.BOLD)
        circulo.background = getDrawable(R.drawable.bg_circulo)?.mutate()
        circulo.background.setTint(color)
        return circulo
    }

    private fun crearFlecha(): TextView {
        val flecha = TextView(this)
        flecha.text = "›"
        flecha.textSize = 22f
        flecha.setTextColor(Color.parseColor("#9E9E9E"))
        flecha.setPadding(dp(6), 0, dp(6), 0)
        return flecha
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()

    private fun reservar(libro: Libro, alTerminar: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            alTerminar(false)
            return
        }

        // 1. Límite de reservas activas
        db.collection("reservas")
            .whereEqualTo("usuarioId", uid)
            .whereIn("estado", listOf(Reservas.EN_ESPERA, Reservas.TURNO))
            .get()
            .addOnSuccessListener { activas ->
                if (activas.size() >= Reservas.MAX_RESERVAS_ACTIVAS) {
                    Toast.makeText(
                        this,
                        "Puedes tener hasta ${Reservas.MAX_RESERVAS_ACTIVAS} reservas a la vez",
                        Toast.LENGTH_LONG
                    ).show()
                    alTerminar(false)
                    return@addOnSuccessListener
                }

                // 2. Tu nombre para mostrarlo en la fila
                db.collection("usuarios").document(uid).get()
                    .addOnSuccessListener { usuario ->
                        val nombre = EstadoSolicitud.nombreCorto(
                            usuario.getString("nombre").orEmpty(),
                            usuario.getString("apellidos").orEmpty()
                        )

                        // 3. Crea (o renueva) la reserva
                        val reserva = hashMapOf(
                            "libroId" to libro.id,
                            "titulo" to libro.titulo,
                            "autor" to libro.autor,
                            "genero" to libro.genero,
                            "usuarioId" to uid,
                            "usuarioNombre" to nombre,
                            "estado" to Reservas.EN_ESPERA,
                            "fechaReserva" to Timestamp.now(),
                            "fechaLimiteTurno" to null
                        )

                        db.collection("reservas").document(Reservas.idPara(libro.id, uid))
                            .set(reserva)
                            .addOnSuccessListener { alTerminar(true) }
                            .addOnFailureListener { error ->
                                Toast.makeText(this, "No se pudo reservar: ${error.message}", Toast.LENGTH_LONG).show()
                                alTerminar(false)
                            }
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "Error al leer tu perfil: ${error.message}", Toast.LENGTH_SHORT).show()
                        alTerminar(false)
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                alTerminar(false)
            }
    }

    private fun cancelarReserva(reservaId: String) {
        db.collection("reservas").document(reservaId)
            .update("estado", Reservas.CANCELADA)
            .addOnSuccessListener {
                Toast.makeText(this, "Reserva cancelada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "No se pudo cancelar: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- SOLICITAR PRÉSTAMO A OTRO ESTUDIANTE ----------

    private fun configurarBotonSolicitud(
        boton: MaterialButton,
        tvExtra: TextView,
        libro: Libro,
        dialogo: AlertDialog,
        miTurno: Boolean
    ) {
        val uid = auth.currentUser?.uid ?: return
        val textoBoton = if (miTurno) "Solicitar · ¡Tu turno!" else "Solicitar"
        boton.isEnabled = false
        boton.text = "Consultando…"

        db.collection("solicitudes").document(EstadoSolicitud.idPara(libro.id, uid))
            .get()
            .addOnSuccessListener { documento ->
                when (documento.getString("estado")) {
                    EstadoSolicitud.PENDIENTE -> {
                        boton.text = "Solicitud enviada ⏳"
                        return@addOnSuccessListener
                    }
                    EstadoSolicitud.RECHAZADA -> {
                        val nota = "ℹ️ Tu solicitud anterior fue rechazada. Puedes volver a intentarlo."
                        tvExtra.text = listOf(tvExtra.text.toString(), nota)
                            .filter { it.isNotBlank() }.joinToString("\n")
                        tvExtra.visibility = View.VISIBLE
                    }
                }
                boton.isEnabled = true
                boton.text = textoBoton
                boton.setOnClickListener {
                    dialogo.dismiss()
                    confirmarSolicitud(libro)
                }
            }
            .addOnFailureListener {
                boton.isEnabled = true
                boton.text = textoBoton
                boton.setOnClickListener {
                    dialogo.dismiss()
                    confirmarSolicitud(libro)
                }
            }
    }

    private fun confirmarSolicitud(libro: Libro) {
        val dueno = libro.propietarioNombre.ifEmpty { "el dueño" }

        MaterialAlertDialogBuilder(this)
            .setTitle("Solicitar préstamo")
            .setMessage(
                "Le enviaremos tu solicitud a $dueno.\n\n" +
                        "Si la acepta, tendrás ${ReglasPrestamo.DIAS_PRESTAMO} días para leer " +
                        "\"${libro.titulo}\" y aparecerá en Mis préstamos."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Enviar solicitud") { _, _ -> enviarSolicitud(libro) }
            .show()
    }

    private fun enviarSolicitud(libro: Libro) {
        val uid = auth.currentUser?.uid ?: return

        // 1. Revisa el límite de préstamos (ahora lo decide PrestamoService)
        prestamoService.verificarLimite(uid) { limite ->
            when (limite) {
                ResultadoLimite.Alcanzado -> mostrarLimiteAlcanzado()
                is ResultadoLimite.Error ->
                    Toast.makeText(this, "Error: ${limite.mensaje}", Toast.LENGTH_SHORT).show()
                ResultadoLimite.Permitido -> crearSolicitud(libro, uid)
            }
        }
    }

    private fun crearSolicitud(libro: Libro, uid: String) {
        // 2. Toma tu nombre del perfil
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { usuario ->
                val nombre = EstadoSolicitud.nombreCorto(
                    usuario.getString("nombre").orEmpty(),
                    usuario.getString("apellidos").orEmpty()
                )

                // 3. Crea (o renueva) la solicitud
                val solicitud = hashMapOf(
                    "libroId" to libro.id,
                    "titulo" to libro.titulo,
                    "autor" to libro.autor,
                    "genero" to libro.genero,
                    "propietarioId" to libro.propietarioId,
                    "solicitanteId" to uid,
                    "solicitanteNombre" to nombre,
                    "estado" to EstadoSolicitud.PENDIENTE,
                    "fechaSolicitud" to Timestamp.now(),
                    "fechaRespuesta" to null
                )

                db.collection("solicitudes")
                    .document(EstadoSolicitud.idPara(libro.id, uid))
                    .set(solicitud)
                    .addOnSuccessListener {
                        // Si era tu turno, tu reserva queda cumplida
                        if (libro.reservadoPara == uid) {
                            db.collection("reservas")
                                .document(Reservas.idPara(libro.id, uid))
                                .update("estado", Reservas.COMPLETADA)
                        }
                        Snackbar.make(
                            findViewById(R.id.main),
                            "Solicitud enviada. Si la aceptan, el libro aparecerá en Mis préstamos",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    .addOnFailureListener { error ->
                        Toast.makeText(this, "No se pudo enviar: ${error.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al leer tu perfil: ${error.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun mostrarLimiteAlcanzado() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Límite alcanzado")
            .setMessage(
                "Ya tienes ${ReglasPrestamo.MAX_PRESTAMOS_ACTIVOS} libros prestados. " +
                        "Devuelve uno para poder obtener otro."
            )
            .setNegativeButton("Entendido", null)
            .setPositiveButton("Ver mis préstamos") { _, _ ->
                startActivity(Intent(this, MisPrestamosActivity::class.java))
            }
            .show()
    }

    // ---------- CONTACTAR (intercambio, venta, donación) ----------

    private fun contactarDueno(libro: Libro) {
        val celular = libro.contactoCelular.filter { it.isDigit() }
        if (celular.length != 9) {
            Toast.makeText(this, "El dueño no tiene un celular válido registrado", Toast.LENGTH_SHORT).show()
            return
        }

        val mensaje = when (Oferta.tipoDe(libro)) {
            Oferta.VENTA -> "Hola, vi tu libro \"${libro.titulo}\" en venta a " +
                    "${Oferta.formatearPrecio(libro.precio)} en BookStore. ¿Sigue disponible?"
            Oferta.INTERCAMBIO -> "Hola, vi tu libro \"${libro.titulo}\" para intercambio " +
                    "en BookStore. ¿Podemos conversar?"
            else -> "Hola, vi que donas el libro \"${libro.titulo}\" en BookStore. " +
                    "¿Todavía está disponible? ¡Me encantaría leerlo!"
        }

        // 51 = código de Perú
        val enlace = Uri.parse("https://wa.me/51$celular?text=" + Uri.encode(mensaje))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, enlace))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No se encontró WhatsApp ni un navegador", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- OBTENER (libros del catálogo) ----------

    private fun confirmarObtener(libro: Libro) {
        val fechaLimite = ReglasPrestamo.sumarDias(Date(), ReglasPrestamo.DIAS_PRESTAMO)

        MaterialAlertDialogBuilder(this)
            .setTitle("Obtener libro")
            .setMessage(
                "¿Quieres obtener \"${libro.titulo}\"?\n\n" +
                        "Tendrás ${ReglasPrestamo.DIAS_PRESTAMO} días para leerlo. " +
                        "Deberás devolverlo antes del ${ReglasPrestamo.formatear(fechaLimite)}."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Obtener") { _, _ -> obtenerLibro(libro) }
            .show()
    }

    // La Activity solo pide el préstamo; las reglas y Firestore están en PrestamoService
    private fun obtenerLibro(libro: Libro) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }
        prestamoService.obtenerLibro(libro, uid) { resultado -> mostrarResultadoPrestamo(resultado) }
    }

    // Traduce el resultado del servicio en mensajes para el estudiante
    private fun mostrarResultadoPrestamo(resultado: ResultadoPrestamo) {
        when (resultado) {
            is ResultadoPrestamo.Exito -> {
                Snackbar.make(
                    findViewById(R.id.main),
                    "¡Listo! Devuélvelo antes del ${ReglasPrestamo.formatear(resultado.fechaLimite)}",
                    Snackbar.LENGTH_LONG
                ).setAction("Ver préstamos") {
                    startActivity(Intent(this, MisPrestamosActivity::class.java))
                }.show()
                cargarLibros()
            }
            ResultadoPrestamo.LimiteAlcanzado -> mostrarLimiteAlcanzado()
            is ResultadoPrestamo.ErrorConsulta ->
                Toast.makeText(this, "Error: ${resultado.mensaje}", Toast.LENGTH_SHORT).show()
            ResultadoPrestamo.ReservadoParaOtro -> {
                Toast.makeText(this, "Este libro está reservado para otro estudiante 🔒", Toast.LENGTH_SHORT).show()
                cargarLibros()
            }
            ResultadoPrestamo.NoDisponible -> {
                Toast.makeText(this, "Alguien acaba de obtener este libro", Toast.LENGTH_SHORT).show()
                cargarLibros()
            }
            is ResultadoPrestamo.ErrorRegistro -> {
                Toast.makeText(this, "No se pudo obtener el libro: ${resultado.mensaje}", Toast.LENGTH_SHORT).show()
                cargarLibros()
            }
        }
    }
    private fun pintarChipsGeneros() {
        llGeneros.removeAllViews()
        for (genero in chipsGeneros) {
            val seleccionado = if (genero == FiltrosActivity.TODOS) filtroGeneros.isEmpty()
            else genero in filtroGeneros

            val chip = TextView(this)
            chip.text = genero
            chip.textSize = 13f
            chip.setPadding(28, 14, 28, 14)
            chip.setTextColor(if (seleccionado) Color.WHITE else Color.parseColor("#1E1E1E"))
            chip.background = getDrawable(R.drawable.bg_cover_placeholder)
            chip.background.mutate().setTint(
                if (seleccionado) colorAzul else Color.parseColor("#EDEDED")
            )
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 10
            chip.layoutParams = params

            chip.setOnClickListener {
                filtroGeneros = if (genero == FiltrosActivity.TODOS) arrayListOf() else arrayListOf(genero)
                aplicarFiltros()
            }
            llGeneros.addView(chip)
        }
    }
}