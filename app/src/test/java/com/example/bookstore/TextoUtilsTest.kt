package com.example.bookstore

import org.junit.Assert.assertEquals
import org.junit.Test

class TextoUtilsTest {

    @Test
    fun normalizar_quitaTildesYMayusculas() {
        assertEquals("fantasia", TextoUtils.normalizar("Fantasía"))
        assertEquals("cien anos de soledad", TextoUtils.normalizar("Cien AÑOS de Soledad"))
    }

    @Test
    fun normalizar_quitaEspaciosDeLosExtremos() {
        assertEquals("garcia marquez", TextoUtils.normalizar("  García Márquez  "))
    }

    @Test
    fun normalizar_textoVacioDevuelveVacio() {
        assertEquals("", TextoUtils.normalizar(""))
    }
}