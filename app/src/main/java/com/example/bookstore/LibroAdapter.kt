package com.example.bookstore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView


class LibroAdapter(
    private val listaCompleta: List<Libro>,
    private val onLibroClick: (Libro) -> Unit
) : RecyclerView.Adapter<LibroAdapter.LibroViewHolder>() {

    private var listaFiltrada: List<Libro> = listaCompleta

    private var filtroTexto = ""
    private var filtroTitulo = ""
    private var filtroAutor = ""
    private var filtroCurso = ""
    private var filtroGeneros: Set<String> = emptySet()
    private var filtroTipo = FiltrosActivity.TODOS
    private var filtroDisponibilidad = FiltrosActivity.TODOS
    private var orden = FiltrosActivity.ORDEN_DEFECTO

    companion object {
        private val paletaColores = listOf(
            "#D64550", "#7A6FB5", "#2C4770", "#2E8B57", "#D9A62E",
            "#4A6FA5", "#B5537A", "#3E8E7E", "#8E6C3E", "#5B5B8E"
        )

        fun colorPorGenero(genero: String): Int {
            val indice = ((genero.hashCode() % paletaColores.size) + paletaColores.size) % paletaColores.size
            return Color.parseColor(paletaColores[indice])
        }

        // Texto "Género · Curso" (si el libro no tiene curso, solo el género)
        fun textoGeneroYCurso(libro: Libro): String {
            return if (libro.curso.isNotEmpty()) "${libro.genero} · ${libro.curso}" else libro.genero
        }
    }

    init {
        actualizarFiltros()
    }

    inner class LibroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTitulo: TextView = itemView.findViewById(R.id.tvTitulo)
        val tvAutor: TextView = itemView.findViewById(R.id.tvAutor)
        val tvGeneroChip: TextView = itemView.findViewById(R.id.tvGeneroChip)
        val tvCoverLetter: TextView = itemView.findViewById(R.id.tvCoverLetter)
        val tvTipoOferta: TextView = itemView.findViewById(R.id.tvTipoOferta)

        init {
            itemView.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) {
                    onLibroClick(listaFiltrada[posicion])
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LibroViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_libro, parent, false)
        return LibroViewHolder(vista)
    }

    override fun onBindViewHolder(holder: LibroViewHolder, position: Int) {
        val libro = listaFiltrada[position]

        holder.tvTitulo.text = libro.titulo
        holder.tvAutor.text = "Autor: ${libro.autor}"
        holder.tvGeneroChip.text = textoGeneroYCurso(libro)

        holder.tvCoverLetter.text = libro.titulo.take(1).uppercase()
        holder.tvCoverLetter.background.mutate().setTint(colorPorGenero(libro.genero))

        holder.tvTipoOferta.text = Oferta.etiqueta(libro)
        holder.tvTipoOferta.background.mutate().setTint(Oferta.color(libro))
    }

    override fun getItemCount(): Int = listaFiltrada.size


    private fun actualizarFiltros() {
        val textoNorm = TextoUtils.normalizar(filtroTexto)
        val tituloNorm = TextoUtils.normalizar(filtroTitulo)
        val autorNorm = TextoUtils.normalizar(filtroAutor)
        val cursoNorm = TextoUtils.normalizar(filtroCurso)

        val filtrados = listaCompleta.filter { libro ->
            val coincideTexto = textoNorm.isEmpty() ||
                    TextoUtils.normalizar(libro.titulo).contains(textoNorm) ||
                    TextoUtils.normalizar(libro.autor).contains(textoNorm) ||
                    TextoUtils.normalizar(libro.genero).contains(textoNorm) ||
                    TextoUtils.normalizar(libro.curso).contains(textoNorm) ||
                    TextoUtils.normalizar(Oferta.tipoDe(libro)).contains(textoNorm)

            val coincideTitulo = tituloNorm.isEmpty() || TextoUtils.normalizar(libro.titulo).contains(tituloNorm)
            val coincideAutor = autorNorm.isEmpty() || TextoUtils.normalizar(libro.autor).contains(autorNorm)
            val coincideCurso = cursoNorm.isEmpty() || TextoUtils.normalizar(libro.curso).contains(cursoNorm)
            val coincideGenero = filtroGeneros.isEmpty() || libro.genero in filtroGeneros
            val coincideTipo = filtroTipo == FiltrosActivity.TODOS || Oferta.tipoDe(libro) == filtroTipo

            val estaDisponible = libro.estado.equals(Oferta.ESTADO_DISPONIBLE, ignoreCase = true)
            val coincideDisponibilidad = when (filtroDisponibilidad) {
                FiltrosActivity.DISPONIBLES -> estaDisponible
                FiltrosActivity.PRESTADOS -> !estaDisponible
                else -> true
            }

            coincideTexto && coincideTitulo && coincideAutor && coincideCurso &&
                    coincideGenero && coincideTipo && coincideDisponibilidad
        }

        listaFiltrada = when (orden) {
            FiltrosActivity.ORDEN_TITULO_ZA -> filtrados.sortedByDescending { TextoUtils.normalizar(it.titulo) }
            FiltrosActivity.ORDEN_AUTOR_AZ -> filtrados.sortedBy { TextoUtils.normalizar(it.autor) }
            else -> filtrados.sortedBy { TextoUtils.normalizar(it.titulo) }
        }
        notifyDataSetChanged()
    }

    fun filtrar(texto: String) {
        filtroTexto = texto
        actualizarFiltros()
    }

    fun aplicarFiltros(
        titulo: String,
        autor: String,
        curso: String,
        generos: Set<String>,
        tipo: String,
        disponibilidad: String,
        ordenElegido: String
    ) {
        filtroTitulo = titulo
        filtroAutor = autor
        filtroCurso = curso
        filtroGeneros = generos
        filtroTipo = tipo
        filtroDisponibilidad = disponibilidad
        orden = ordenElegido
        actualizarFiltros()
    }
}