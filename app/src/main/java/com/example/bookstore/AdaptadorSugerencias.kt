package com.example.bookstore

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

class AdaptadorSugerencias(
    context: Context,
    private val todasLasOpciones: List<String>
) : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, ArrayList(todasLasOpciones)) {

    private val filtro = object : Filter() {
        override fun performFiltering(texto: CharSequence?): FilterResults {
            val consulta = TextoUtils.normalizar(texto?.toString() ?: "")
            val coincidencias = if (consulta.isEmpty()) {
                todasLasOpciones
            } else {
                todasLasOpciones.filter { TextoUtils.normalizar(it).contains(consulta) }
            }
            val resultados = FilterResults()
            resultados.values = coincidencias
            resultados.count = coincidencias.size
            return resultados
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(texto: CharSequence?, resultados: FilterResults?) {
            clear()
            addAll((resultados?.values as? List<String>) ?: emptyList())
        }

        override fun convertResultToString(resultado: Any?): CharSequence {
            return resultado as String
        }
    }

    override fun getFilter(): Filter = filtro
}