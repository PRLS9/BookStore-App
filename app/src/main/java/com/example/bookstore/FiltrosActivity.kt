package com.example.bookstore

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class FiltrosActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TITULO = "extra_titulo"
        const val EXTRA_AUTOR = "extra_autor"
        const val EXTRA_CURSO = "extra_curso"
        const val EXTRA_GENEROS = "extra_generos"
        const val EXTRA_TIPO = "extra_tipo"
        const val EXTRA_ORDEN = "extra_orden"
        const val EXTRA_DISPONIBILIDAD = "extra_disponibilidad"
        const val EXTRA_LISTA_TITULOS = "extra_lista_titulos"
        const val EXTRA_LISTA_AUTORES = "extra_lista_autores"

        const val TODOS = "Todos"
        const val ORDEN_DEFECTO = "Título A-Z"
        const val CURSO_LIBRE = "Lectura libre"

        val OPCIONES_ORDEN = listOf("Título A-Z", "Título Z-A", "Autor A-Z")
        val OPCIONES_DISPONIBILIDAD = listOf("Todos", "Disponibles", "Prestados")
        val OPCIONES_TIPO = listOf(TODOS) + Oferta.TIPOS
        val GENEROS = listOf(
            "Romance", "Fantasía", "Terror", "Misterio", "Thriller",
            "Ciencia ficción", "Acción y aventura", "Finanzas", "Historia", "Tecnología"
        )
        val CURSOS = listOf(
            "Comunicación y Literatura", "Matemática", "Ciencias Naturales",
            "Historia y Humanidades", "Economía y Finanzas", "Programación",
            "Tecnología y Sociedad", "Arte y Cultura", CURSO_LIBRE
        )
    }

    private var titulo = ""
    private var autor = ""
    private var curso = ""
    private val generosSeleccionados = mutableSetOf<String>()
    private var tipo = TODOS
    private var orden = ORDEN_DEFECTO
    private var disponibilidad = TODOS

    private var listaTitulos = listOf<String>()
    private var listaAutores = listOf<String>()

    private lateinit var actvTitulo: MaterialAutoCompleteTextView
    private lateinit var actvAutor: MaterialAutoCompleteTextView
    private lateinit var actvCurso: MaterialAutoCompleteTextView
    private lateinit var actvGenero: MaterialAutoCompleteTextView
    private lateinit var chipGroupGeneros: ChipGroup
    private lateinit var grupoTipo: RadioGroup
    private lateinit var grupoOrden: RadioGroup
    private lateinit var grupoDisponibilidad: RadioGroup
    private lateinit var tvResumenTipo: TextView
    private lateinit var tvResumenOrden: TextView
    private lateinit var tvResumenDisponibilidad: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filtros)

        // Filtros actuales que envía Buscar libro
        titulo = intent.getStringExtra(EXTRA_TITULO) ?: ""
        autor = intent.getStringExtra(EXTRA_AUTOR) ?: ""
        curso = intent.getStringExtra(EXTRA_CURSO) ?: ""
        generosSeleccionados.addAll(intent.getStringArrayListExtra(EXTRA_GENEROS) ?: arrayListOf())
        tipo = intent.getStringExtra(EXTRA_TIPO) ?: TODOS
        orden = intent.getStringExtra(EXTRA_ORDEN) ?: ORDEN_DEFECTO
        disponibilidad = intent.getStringExtra(EXTRA_DISPONIBILIDAD) ?: TODOS
        listaTitulos = intent.getStringArrayListExtra(EXTRA_LISTA_TITULOS) ?: arrayListOf()
        listaAutores = intent.getStringArrayListExtra(EXTRA_LISTA_AUTORES) ?: arrayListOf()

        actvTitulo = findViewById(R.id.actvTitulo)
        actvAutor = findViewById(R.id.actvAutor)
        actvCurso = findViewById(R.id.actvCurso)
        actvGenero = findViewById(R.id.actvGenero)
        chipGroupGeneros = findViewById(R.id.chipGroupGeneros)
        grupoTipo = findViewById(R.id.grupoTipo)
        grupoOrden = findViewById(R.id.grupoOrden)
        grupoDisponibilidad = findViewById(R.id.grupoDisponibilidad)
        tvResumenTipo = findViewById(R.id.tvResumenTipo)
        tvResumenOrden = findViewById(R.id.tvResumenOrden)
        tvResumenDisponibilidad = findViewById(R.id.tvResumenDisponibilidad)

        configurarCampoConSugerencias(actvTitulo, listaTitulos) { titulo = it }
        configurarCampoConSugerencias(actvAutor, listaAutores) { autor = it }
        configurarCampoConSugerencias(actvCurso, CURSOS) { curso = it }
        configurarCampoGeneros()

        configurarDesplegable(R.id.headerTipo, grupoTipo, R.id.ivFlechaTipo)
        configurarDesplegable(R.id.headerOrden, grupoOrden, R.id.ivFlechaOrden)
        configurarDesplegable(R.id.headerDisponibilidad, grupoDisponibilidad, R.id.ivFlechaDisponibilidad)

        llenarOpciones()

        findViewById<MaterialButton>(R.id.btnRegresarFiltros).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnLimpiarFiltros).setOnClickListener {
            titulo = ""
            autor = ""
            curso = ""
            generosSeleccionados.clear()
            tipo = TODOS
            orden = ORDEN_DEFECTO
            disponibilidad = TODOS
            llenarOpciones()
        }

        findViewById<MaterialButton>(R.id.btnAplicarFiltros).setOnClickListener {
            val resultado = Intent()
            resultado.putExtra(EXTRA_TITULO, titulo)
            resultado.putExtra(EXTRA_AUTOR, autor)
            resultado.putExtra(EXTRA_CURSO, curso)
            resultado.putStringArrayListExtra(EXTRA_GENEROS, ArrayList(generosSeleccionados))
            resultado.putExtra(EXTRA_TIPO, tipo)
            resultado.putExtra(EXTRA_ORDEN, orden)
            resultado.putExtra(EXTRA_DISPONIBILIDAD, disponibilidad)
            setResult(RESULT_OK, resultado)
            finish()
        }
    }

    // Campo para escribir con lista desplegable que se recorta al escribir
    private fun configurarCampoConSugerencias(
        campo: MaterialAutoCompleteTextView,
        opciones: List<String>,
        alCambiar: (String) -> Unit
    ) {
        val adaptador = AdaptadorSugerencias(this, opciones)
        campo.setAdapter(adaptador)
        campo.threshold = 1
        campo.setOnClickListener { campo.showDropDown() }
        campo.doAfterTextChanged { texto ->
            val valor = texto?.toString() ?: ""
            alCambiar(valor.trim())
            if (valor.isEmpty()) adaptador.filter.filter(null)
        }
    }

    // Géneros: al elegir uno se agrega como etiqueta, se pueden elegir varios
    private fun configurarCampoGeneros() {
        actvGenero.threshold = 1
        actvGenero.setOnClickListener { actvGenero.showDropDown() }
        actvGenero.setOnItemClickListener { parent, _, position, _ ->
            val genero = parent.getItemAtPosition(position) as String
            generosSeleccionados.add(genero)
            actvGenero.setText("", false)
            actualizarGeneros()
        }
        actvGenero.doAfterTextChanged { texto ->
            if (texto.isNullOrEmpty()) {
                (actvGenero.adapter as? AdaptadorSugerencias)?.filter?.filter(null)
            }
        }
    }

    private fun actualizarGeneros() {
        actvGenero.setAdapter(AdaptadorSugerencias(this, GENEROS.filter { it !in generosSeleccionados }))

        chipGroupGeneros.removeAllViews()
        for (genero in generosSeleccionados) {
            val chip = Chip(this)
            chip.text = genero
            chip.isCloseIconVisible = true
            chip.setTextColor(Color.WHITE)
            chip.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#2C4770"))
            chip.closeIconTint = ColorStateList.valueOf(Color.WHITE)
            chip.setOnCloseIconClickListener {
                generosSeleccionados.remove(genero)
                actualizarGeneros()
            }
            chipGroupGeneros.addView(chip)
        }
    }

    // Abre y cierra una sección, girando la flecha
    private fun configurarDesplegable(idHeader: Int, contenido: View, idFlecha: Int) {
        val flecha = findViewById<ImageView>(idFlecha)
        findViewById<View>(idHeader).setOnClickListener {
            val abrir = contenido.visibility != View.VISIBLE
            contenido.visibility = if (abrir) View.VISIBLE else View.GONE
            flecha.animate().rotation(if (abrir) 90f else 0f).setDuration(200).start()
        }
    }

    private fun llenarOpciones() {
        actvTitulo.setText(titulo, false)
        actvAutor.setText(autor, false)
        actvCurso.setText(curso, false)
        actvGenero.setText("", false)
        actualizarGeneros()

        llenarOpcionUnica(grupoTipo, OPCIONES_TIPO, tipo) {
            tipo = it
            actualizarResumenes()
        }
        llenarOpcionUnica(grupoOrden, OPCIONES_ORDEN, orden) {
            orden = it
            actualizarResumenes()
        }
        llenarOpcionUnica(grupoDisponibilidad, OPCIONES_DISPONIBILIDAD, disponibilidad) {
            disponibilidad = it
            actualizarResumenes()
        }
        actualizarResumenes()
    }

    private fun llenarOpcionUnica(
        grupo: RadioGroup,
        opciones: List<String>,
        seleccionada: String,
        alElegir: (String) -> Unit
    ) {
        grupo.setOnCheckedChangeListener(null)
        grupo.removeAllViews()
        for (opcion in opciones) {
            val radio = RadioButton(this)
            radio.id = View.generateViewId()
            radio.text = opcion
            radio.textSize = 15f
            radio.setTextColor(Color.parseColor("#1E1E1E"))
            radio.buttonTintList = ColorStateList.valueOf(Color.parseColor("#2C4770"))
            grupo.addView(radio)
            if (opcion == seleccionada) radio.isChecked = true
        }
        grupo.setOnCheckedChangeListener { g, idMarcado ->
            val radio = g.findViewById<RadioButton>(idMarcado)
            if (radio != null) alElegir(radio.text.toString())
        }
    }

    private fun actualizarResumenes() {
        tvResumenTipo.text = tipo
        tvResumenOrden.text = orden
        tvResumenDisponibilidad.text = disponibilidad
    }
}