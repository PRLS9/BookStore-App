package com.example.bookstore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class InsigniaAdapter(
    private val onTocar: (Insignia) -> Unit
) : RecyclerView.Adapter<InsigniaAdapter.InsigniaViewHolder>() {

    private var lista: List<Insignia> = emptyList()
    private var animarEntrada = true

    fun actualizar(nuevaLista: List<Insignia>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    inner class InsigniaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val tvEmoji: TextView = itemView.findViewById(R.id.tvEmojiInsigniaItem)
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreInsigniaItem)
        val progreso: LinearProgressIndicator = itemView.findViewById(R.id.progresoInsignia)
        val tvProgreso: TextView = itemView.findViewById(R.id.tvProgresoInsignia)

        init {
            card.setOnClickListener {
                val posicion = adapterPosition
                if (posicion != RecyclerView.NO_POSITION) onTocar(lista[posicion])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InsigniaViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_insignia, parent, false)
        return InsigniaViewHolder(vista)
    }

    override fun onBindViewHolder(holder: InsigniaViewHolder, position: Int) {
        val insignia = lista[position]
        val densidad = holder.itemView.resources.displayMetrics.density
        val avance = minOf(insignia.progreso, insignia.meta)

        holder.tvEmoji.text = insignia.emoji
        holder.tvNombre.text = insignia.nombre

        if (insignia.desbloqueada) {
            // 🏅 Desbloqueada: borde dorado y fondo cálido
            holder.card.setCardBackgroundColor(Color.parseColor("#FFF8E6"))
            holder.card.strokeColor = Color.parseColor("#D9A62E")
            holder.card.strokeWidth = (2 * densidad).toInt()
            holder.tvEmoji.alpha = 1f
            holder.progreso.visibility = View.GONE
            holder.tvProgreso.text = "✓ Desbloqueada"
            holder.tvProgreso.setTextColor(Color.parseColor("#2E8B57"))
        } else {
            // 🔒 Bloqueada: apagada, con su progreso
            holder.card.setCardBackgroundColor(Color.WHITE)
            holder.card.strokeWidth = 0
            holder.tvEmoji.alpha = 0.35f
            holder.progreso.visibility = View.VISIBLE
            holder.progreso.setProgressCompat(avance * 100 / insignia.meta, false)
            holder.tvProgreso.text = "🔒 $avance/${insignia.meta}"
            holder.tvProgreso.setTextColor(Color.parseColor("#777777"))
        }

        // Entrada en cascada (solo la primera vez)
        if (animarEntrada) {
            holder.itemView.alpha = 0f
            holder.itemView.translationY = 30 * densidad
            holder.itemView.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(position * 60L)
                .setDuration(320)
                .start()
            if (position == lista.size - 1) animarEntrada = false
        }
    }

    override fun getItemCount(): Int = lista.size
}