package com.example.bookstore

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.util.Date

class ReservaAdapter(
    private val onIrAlLibro: (Reserva) -> Unit,
    private val onCancelar: (Reserva) -> Unit
) : RecyclerView.Adapter<ReservaAdapter.ReservaViewHolder>() {

    private var lista: List<Reserva> = emptyList()
    private var posiciones: Map<String, Int> = emptyMap()

    fun actualizar(nuevaLista: List<Reserva>, nuevasPosiciones: Map<String, Int>) {
        lista = nuevaLista
        posiciones = nuevasPosiciones
        notifyDataSetChanged()
    }

    inner class ReservaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvLetra: TextView = itemView.findViewById(R.id.tvLetraReserva)
        val tvTitulo: TextView = itemView.findViewById(R.id.tvTituloReserva)
        val tvAutor: TextView = itemView.findViewById(R.id.tvAutorReserva)
        val tvEstado: TextView = itemView.findViewById(R.id.tvEstadoReserva)
        val tvDetalle: TextView = itemView.findViewById(R.id.tvDetalleReserva)
        val progreso: LinearProgressIndicator = itemView.findViewById(R.id.progresoTurno)
        val btnIr: MaterialButton = itemView.findViewById(R.id.btnIrAlLibro)
        val btnCancelar: MaterialButton = itemView.findViewById(R.id.btnCancelarReserva)

        init {
            btnIr.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onIrAlLibro(lista[posicion])
            }
            btnCancelar.setOnClickListener {
                val posicion = bindingAdapterPosition
                if (posicion != RecyclerView.NO_POSITION) onCancelar(lista[posicion])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservaViewHolder {
        val vista = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reserva, parent, false)
        return ReservaViewHolder(vista)
    }

    override fun onBindViewHolder(holder: ReservaViewHolder, position: Int) {
        val reserva = lista[position]

        holder.tvLetra.text = reserva.titulo.take(1).uppercase()
        holder.tvLetra.background.mutate().setTint(LibroAdapter.colorPorGenero(reserva.genero))
        holder.tvTitulo.text = reserva.titulo
        holder.tvAutor.text = "Autor: ${reserva.autor}"

        val limite = reserva.fechaLimiteTurno?.toDate()
        val turnoVigente = reserva.estado == Reservas.TURNO && limite?.after(Date()) == true
        val turnoVencido = reserva.estado == Reservas.EXPIRADA ||
                (reserva.estado == Reservas.TURNO && !turnoVigente)

        holder.btnCancelar.text = "Cancelar"

        when {
            // 🎉 Llegó tu turno
            turnoVigente -> {
                holder.tvEstado.text = "🎉 ¡Tu turno!"
                holder.tvEstado.background.mutate().setTint(Color.parseColor("#2E8B57"))
                holder.tvDetalle.text =
                    "El libro ya está disponible. Te quedan ${Reservas.tiempoRestante(limite)} para obtenerlo."
                holder.progreso.visibility = View.VISIBLE
                holder.progreso.setProgressCompat(Reservas.porcentajeRestante(limite), true)
                holder.btnIr.text = "📖 Obtenerlo ahora"
                holder.btnIr.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2E8B57"))
            }
            // ⌛ No lo aprovechó a tiempo
            turnoVencido -> {
                holder.tvEstado.text = "⌛ Turno vencido"
                holder.tvEstado.background.mutate().setTint(Color.parseColor("#8E8E8E"))
                holder.tvDetalle.text =
                    "Pasaron las ${Reservas.HORAS_PRIORIDAD} horas y el turno pasó al siguiente. " +
                            "Si el libro vuelve a estar prestado, puedes reservarlo otra vez."
                holder.progreso.visibility = View.GONE
                holder.btnIr.text = "📖 Ver libro"
                holder.btnIr.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2C4770"))
                holder.btnCancelar.text = "Quitar"
            }
            // 📌 En la fila de espera
            else -> {
                val posicion = posiciones[reserva.id] ?: 1
                holder.tvEstado.text = "📌 En la fila · #$posicion"
                holder.tvEstado.background.mutate().setTint(Color.parseColor("#7A6FB5"))
                holder.tvDetalle.text = if (posicion <= 1) {
                    "¡Eres el siguiente! Te avisaremos apenas lo devuelvan."
                } else {
                    "${Reservas.textoPersonas(posicion - 1)} antes que tú. Te avisaremos cuando sea tu turno."
                }
                holder.progreso.visibility = View.GONE
                holder.btnIr.text = "📖 Ver libro"
                holder.btnIr.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2C4770"))
            }
        }
    }

    override fun getItemCount(): Int = lista.size
}