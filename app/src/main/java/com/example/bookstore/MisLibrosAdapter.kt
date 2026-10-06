package com.example.bookstore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class MisLibrosAdapter(
    private val onFinalizar: (Libro) -> Unit,
    private val onRepublicar: (Libro) -> Unit,
    private val onEditar: (Libro) -> Unit,
    private val onEliminar: (Libro) -> Unit
) : RecyclerView.Adapter<MisLibrosAdapter.MiLibroViewHolder>() {

    private var lista: List<Libro> = emptyList()

    fun actualizar(nuevaLista: List<Libro>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    inner class MiLibroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLetra: TextView = itemView.findViewById(R.id.tvLetraMiLibro)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvTituloMiLibro)
        val tvAutor: TextView = itemView.findViewById(R.id.tvAutorMiLibro)
        val tvInfo: TextView = itemView.findViewById(R.id.tvInfoMiLibro)
        val tvTipo: TextView = itemView.findViewById(R.id.tvTipoMiLibro)
        val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoMiLibro)
        val tvNota: TextView = itemView.findViewById(R.id.tvNotaMiLibro)
        val btnPrincipal: MaterialButton = itemView.findViewById(R.id.btnPrincipalMiLibro)
        val llAcciones: LinearLayout = itemView.findViewById(R.id.llAccionesMiLibro)
        val btnEditar: MaterialButton = itemView.findViewById(R.id.btnEditarMiLibro)
        val btnEliminar: MaterialButton = itemView.findViewById(R.id.btnEliminarMiLibro)

        init {
            btnPrincipal.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion == RecyclerView.NO_POSITION) return@setOnClickListener
                val libro = lista[posicion]
                if (Oferta.estaFinalizado(libro)) onRepublicar(libro) else onFinalizar(libro)
            }
            btnEditar.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onEditar(lista[posicion])
            }
            btnEliminar.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onEliminar(lista[posicion])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MiLibroViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mi_libro, parent, false)
        return MiLibroViewHolder(vista)
    }

    override fun onBindViewHolder(holder: MiLibroViewHolder, position: Int) {
        val libro = lista[position]
        val tipo = Oferta.tipoDe(libro)
        val estado = libro.estado.lowercase()

        holder.tvLetra.text = libro.titulo.take(1).uppercase()
        holder.tvLetra.background.mutate().setTint(LibroAdapter.colorPorGenero(libro.genero))
        holder.tvTitulo.text = libro.titulo
        holder.tvAutor.text = "Autor: ${libro.autor}"

        // Información extra: curso y lo que busca a cambio
        val lineas = mutableListOf<String>()
        if (libro.curso.isNotEmpty()) lineas.add("Curso: ${libro.curso}")
        if (tipo == Oferta.INTERCAMBIO && libro.intercambioPor.isNotEmpty()) {
            lineas.add("A cambio busca: ${libro.intercambioPor}")
        }
        holder.tvInfo.text = lineas.joinToString("\n")
        holder.tvInfo.visibility = if (lineas.isEmpty()) View.GONE else View.VISIBLE

        // Etiqueta del tipo de oferta
        holder.tvTipo.text = Oferta.etiqueta(libro)
        holder.tvTipo.background.mutate().setTint(Oferta.color(libro))

        // Etiqueta del estado
        val (textoEstado, colorEstado) = when (estado) {
            Oferta.ESTADO_PRESTADO -> "Prestado" to "#D9A62E"
            Oferta.ESTADO_VENDIDO -> "Vendido" to "#8E8E8E"
            Oferta.ESTADO_INTERCAMBIADO -> "Intercambiado" to "#8E8E8E"
            Oferta.ESTADO_DONADO -> "Donado" to "#8E8E8E"
            else -> "Disponible" to "#2E8B57"
        }
        holder.tvEstado.text = textoEstado
        holder.tvEstado.background.mutate().setTint(Color.parseColor(colorEstado))

        // Botones según la situación del libro
        when {
            estado == Oferta.ESTADO_PRESTADO -> {
                holder.tvNota.visibility = View.VISIBLE
                holder.btnPrincipal.visibility = View.GONE
                holder.llAcciones.visibility = View.GONE
            }
            Oferta.estaFinalizado(libro) -> {
                holder.tvNota.visibility = View.GONE
                holder.btnPrincipal.visibility = View.VISIBLE
                holder.btnPrincipal.text = "🔁 Volver a publicar"
                holder.btnPrincipal.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2C4770"))
                holder.llAcciones.visibility = View.VISIBLE
                holder.btnEditar.visibility = View.GONE
                holder.btnEliminar.visibility = View.VISIBLE
            }
            else -> {
                holder.tvNota.visibility = View.GONE
                holder.llAcciones.visibility = View.VISIBLE
                holder.btnEditar.visibility = View.VISIBLE
                holder.btnEliminar.visibility = View.VISIBLE

                // Los libros de préstamo no se "venden": no llevan botón de cierre
                if (tipo == Oferta.PRESTAMO) {
                    holder.btnPrincipal.visibility = View.GONE
                } else {
                    holder.btnPrincipal.visibility = View.VISIBLE
                    holder.btnPrincipal.text = Oferta.textoAccionFinal(tipo)
                    holder.btnPrincipal.backgroundTintList =
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#2E8B57"))
                }
            }
        }
    }

    override fun getItemCount(): Int = lista.size
}