package com.example.bookstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionesTest {

    @Test
    fun celular_conNueveDigitos_esValido() {
        assertTrue(Validaciones.esCelularValido("987654321"))
    }

    // Defecto corregido: antes RegistroActivity aceptaba este celular
    @Test
    fun celular_conGuion_esInvalido() {
        assertFalse(Validaciones.esCelularValido("98765-432"))
        assertEquals(Validaciones.ERROR_CELULAR, Validaciones.errorCelular("98765-432"))
    }

    @Test
    fun celular_vacio_esCampoObligatorio() {
        assertEquals(Validaciones.CAMPO_OBLIGATORIO, Validaciones.errorCelular(""))
    }

    @Test
    fun soloTexto_aceptaTildesYEnie_rechazaNumeros() {
        assertTrue(Validaciones.esSoloTexto("José Muñoz"))
        assertFalse(Validaciones.esSoloTexto("Peter2"))
    }

    @Test
    fun correo_validoEInvalido() {
        assertNull(Validaciones.errorCorreo("n00320876@upn.pe"))
        assertEquals(Validaciones.ERROR_CORREO, Validaciones.errorCorreo("peter@upn"))
    }

    @Test
    fun password_exigeMayusculaYSimbolo() {
        assertTrue(Validaciones.esPasswordSegura("Clave#2026"))
        assertFalse(Validaciones.esPasswordSegura("clave2026"))
    }

    @Test
    fun nombreCorto_primerNombreEInicialDelApellido() {
        assertEquals("Peter L.", EstadoSolicitud.nombreCorto("Peter Raúl", "Lazo Sánchez"))
        assertEquals("Peter", EstadoSolicitud.nombreCorto("Peter", ""))
    }
}