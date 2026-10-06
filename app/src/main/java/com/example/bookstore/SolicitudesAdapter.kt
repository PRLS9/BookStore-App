package com.example.bookstore

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class SolicitudesAdapter(
    private val onAceptar: (Solicitud) -> Unit,
    private val onRechazar: (Solicitud) -> Unit
) : RecyclerView.Adapter<SolicitudesAdapter.SolicitudViewHolder>() {

    private var lista: List<Solicitud> = emptyList()

    fun actualizar(nuevaLista: List<Solicitud>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    inner class SolicitudViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLetra: TextView = itemView.findViewById(R.id.tvLetraSolicitud)
        val tvSolicitante: TextView = itemView.findViewById(R.id.tvSolicitante)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvTituloSolicitud)
        val tvFecha: TextView = itemView.findViewById(R.id.tvFechaSolicitud)
        val btnAceptar: MaterialButton = itemView.findViewById(R.id.btnAceptarSolicitud)
        val btnRechazar: MaterialButton = itemView.findViewById(R.id.btnRechazarSolicitud)

        init {
            btnAceptar.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onAceptar(lista[posicion])
            }
            btnRechazar.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onRechazar(lista[posicion])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SolicitudViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_solicitud, parent, false)
        return SolicitudViewHolder(vista)
    }

    override fun onBindViewHolder(holder: SolicitudViewHolder, position: Int) {
        val solicitud = lista[position]

        holder.tvLetra.text = solicitud.titulo.take(1).uppercase()
        holder.tvLetra.background.mutate().setTint(LibroAdapter.colorPorGenero(solicitud.genero))

        val quien = solicitud.solicitanteNombre.ifEmpty { "Un estudiante" }
        holder.tvSolicitante.text = "📩 $quien quiere tu libro"
        holder.tvTitulo.text = solicitud.titulo
        holder.tvFecha.text =
            "Solicitado el ${ReglasPrestamo.formatear(solicitud.fechaSolicitud?.toDate())}"
    }

    override fun getItemCount(): Int = lista.size
}