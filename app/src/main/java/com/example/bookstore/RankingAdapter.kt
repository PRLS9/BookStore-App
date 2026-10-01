package com.example.bookstore

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class RankingAdapter(
    private val uidPropio: String
) : RecyclerView.Adapter<RankingAdapter.RankingViewHolder>() {

    private var lista: List<PuestoRanking> = emptyList()

    fun actualizar(nuevaLista: List<PuestoRanking>) {
        lista = nuevaLista
        notifyDataSetChanged()
    }

    inner class RankingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView as MaterialCardView
        val tvPosicion: TextView = itemView.findViewById(R.id.tvPosicionRanking)
        val tvInicial: TextView = itemView.findViewById(R.id.tvInicialRanking)
        val tvNombre: TextView = itemView.findViewById(R.id.tvNombreRanking)
        val tvNivel: TextView = itemView.findViewById(R.id.tvNivelRanking)
        val tvPuntos: TextView = itemView.findViewById(R.id.tvPuntosRanking)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RankingViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ranking, parent, false)
        return RankingViewHolder(vista)
    }

    override fun onBindViewHolder(holder: RankingViewHolder, position: Int) {
        val puesto = lista[position]
        val esYo = puesto.uid == uidPropio
        val densidad = holder.itemView.resources.displayMetrics.density

        holder.tvPosicion.text = "#${position + 4}"
        holder.tvInicial.text = puesto.nombre.take(1).uppercase().ifEmpty { "?" }
        holder.tvInicial.background.mutate().setTint(
            if (esYo) Color.parseColor("#2C4770") else Color.parseColor("#B8B3D9")
        )
        holder.tvNombre.text = if (esYo) "${puesto.nombre} (tú)" else puesto.nombre
        holder.tvNivel.text = "${puesto.nivel} · ${puesto.insignias} 🏅"
        holder.tvPuntos.text = "${puesto.puntos} pts"

        // Tu fila, destacada
        if (esYo) {
            holder.card.setCardBackgroundColor(Color.parseColor("#EEF2FA"))
            holder.card.strokeColor = Color.parseColor("#2C4770")
            holder.card.strokeWidth = (2 * densidad).toInt()
        } else {
            holder.card.setCardBackgroundColor(Color.WHITE)
            holder.card.strokeWidth = 0
        }
    }

    override fun getItemCount(): Int = lista.size
}