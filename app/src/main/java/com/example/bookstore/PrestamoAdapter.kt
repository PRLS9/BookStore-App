package com.example.bookstore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class PrestamoAdapter(
    private val onDevolver: (Prestamo) -> Unit,
    private val onRenovar: (Prestamo) -> Unit
) : RecyclerView.Adapter<PrestamoAdapter.PrestamoViewHolder>() {

    private var lista: List<Prestamo> = emptyList()

    fun actualizar(nuevaLista: List<Prestamo>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    inner class PrestamoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLetra: TextView = itemView.findViewById(R.id.tvLetraPrestamo)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvTituloPrestamo)
        val tvAutor: TextView = itemView.findViewById(R.id.tvAutorPrestamo)
        val tvFechaPrestamo: TextView = itemView.findViewById(R.id.tvFechaPrestamo)
        val tvFechaLimite: TextView = itemView.findViewById(R.id.tvFechaLimite)
        val tvDiasRestantes: TextView = itemView.findViewById(R.id.tvDiasRestantes)
        val llAcciones: LinearLayout = itemView.findViewById(R.id.llAccionesPrestamo)
        val btnRenovar: MaterialButton = itemView.findViewById(R.id.btnRenovar)
        val btnDevolver: MaterialButton = itemView.findViewById(R.id.btnDevolver)

        init {
            btnDevolver.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onDevolver(lista[posicion])
            }
            btnRenovar.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onRenovar(lista[posicion])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrestamoViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_prestamo, parent, false)
        return PrestamoViewHolder(vista)
    }

    override fun onBindViewHolder(holder: PrestamoViewHolder, position: Int) {
        val prestamo = lista[position]

        holder.tvLetra.text = prestamo.titulo.take(1).uppercase()
        holder.tvLetra.background.mutate().setTint(LibroAdapter.colorPorGenero(prestamo.genero))
        holder.tvTitulo.text = prestamo.titulo
        holder.tvAutor.text = "Autor: ${prestamo.autor}"
        holder.tvFechaPrestamo.text =
            "Obtenido: ${ReglasPrestamo.formatear(prestamo.fechaPrestamo?.toDate())}"

        if (prestamo.estado == ReglasPrestamo.ESTADO_DEVUELTO) {
            // Historial
            holder.tvFechaLimite.text =
                "Devuelto: ${ReglasPrestamo.formatear(prestamo.fechaDevolucion?.toDate())}"
            holder.tvDiasRestantes.text = "Devuelto"
            holder.tvDiasRestantes.background.mutate().setTint(Color.parseColor("#8E8E8E"))
            holder.llAcciones.visibility = View.GONE
            return
        }

        // Préstamo activo
        val fechaLimite = prestamo.fechaLimite?.toDate()
        val textoRenovado = if (prestamo.renovado) " (renovado)" else ""
        holder.tvFechaLimite.text =
            "Devolver antes del: ${ReglasPrestamo.formatear(fechaLimite)}$textoRenovado"

        val dias = if (fechaLimite != null) ReglasPrestamo.diasRestantes(fechaLimite) else 0
        val (texto, color) = when {
            dias < 0 -> {
                val retraso = -dias
                (if (retraso == 1) "Vencido hace 1 día" else "Vencido hace $retraso días") to "#D64550"
            }
            dias == 0 -> "Vence hoy" to "#D9A62E"
            dias <= 3 -> (if (dias == 1) "Queda 1 día" else "Quedan $dias días") to "#D9A62E"
            else -> "Quedan $dias días" to "#2E8B57"
        }
        holder.tvDiasRestantes.text = texto
        holder.tvDiasRestantes.background.mutate().setTint(Color.parseColor(color))

        holder.llAcciones.visibility = View.VISIBLE
        holder.btnRenovar.isEnabled = !prestamo.renovado
        holder.btnRenovar.text = if (prestamo.renovado) "Ya renovado" else "Renovar +7 días"
    }

    override fun getItemCount(): Int = lista.size
}