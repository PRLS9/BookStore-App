package com.example.bookstore

import java.text.Normalizer

/**
 * CR-02 (DRY): utilidades de texto compartidas.
 * Antes, normalizar() estaba copiada en LibroAdapter y en AdaptadorSugerencias.
 */
object TextoUtils {

    // Marcas de acento que deja Normalizer al separar "á" en "a" + "´"
    private val MARCAS_DE_ACENTO = Regex("\\p{Mn}+")

    // "  Fantasía " -> "fantasia": sin tildes, en minúsculas y sin espacios en los extremos
    fun normalizar(texto: String): String {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
            .replace(MARCAS_DE_ACENTO, "")
            .lowercase()
            .trim()
    }
}