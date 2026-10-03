package com.example.bookstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Gracias a la inyección de dependencias, PrestamoService se prueba
 * con un repositorio falso, sin internet y sin Firebase.
 */
class PrestamoServiceTest {

    // Repositorio de mentira: devuelve lo que le digamos y anota si se registró algo
    private class RepositorioFalso(
        private val activos: Result<Int>
    ) : PrestamoRepository {
        var seRegistro = false
        var fechaLimiteRecibida: Date? = null

        override fun contarPrestamosActivos(uid: String, alTerminar: (Result<Int>) -> Unit) {
            alTerminar(activos)
        }

        override fun registrarPrestamo(
            libro: Libro,
            uid: String,
            fechaPrestamo: Date,
            fechaLimite: Date,
            alTerminar: (ResultadoPrestamo) -> Unit
        ) {
            seRegistro = true
            fechaLimiteRecibida = fechaLimite
            alTerminar(ResultadoPrestamo.Exito(fechaLimite))
        }
    }

    private val libro = Libro(id = "libro1", titulo = "Rayuela", autor = "Julio Cortázar")

    @Test
    fun conMenosDelLimite_registraElPrestamoPor30Dias() {
        val repo = RepositorioFalso(Result.success(1))
        var resultado: ResultadoPrestamo? = null

        PrestamoService(repo).obtenerLibro(libro, "uid1") { resultado = it }

        assertTrue(repo.seRegistro)
        assertTrue(resultado is ResultadoPrestamo.Exito)
        assertEquals(ReglasPrestamo.DIAS_PRESTAMO, ReglasPrestamo.diasRestantes(repo.fechaLimiteRecibida!!))
    }

    @Test
    fun conElLimiteAlcanzado_noRegistraNada() {
        val repo = RepositorioFalso(Result.success(ReglasPrestamo.MAX_PRESTAMOS_ACTIVOS))
        var resultado: ResultadoPrestamo? = null

        PrestamoService(repo).obtenerLibro(libro, "uid1") { resultado = it }

        assertFalse(repo.seRegistro)
        assertEquals(ResultadoPrestamo.LimiteAlcanzado, resultado)
    }

    @Test
    fun siFallaLaConsulta_devuelveErrorConsulta() {
        val repo = RepositorioFalso(Result.failure(Exception("sin conexión")))
        var resultado: ResultadoPrestamo? = null

        PrestamoService(repo).obtenerLibro(libro, "uid1") { resultado = it }

        assertFalse(repo.seRegistro)
        assertEquals(ResultadoPrestamo.ErrorConsulta("sin conexión"), resultado)
    }

    @Test
    fun verificarLimite_permiteHastaDosPrestamos() {
        var limite: ResultadoLimite? = null
        PrestamoService(RepositorioFalso(Result.success(2))).verificarLimite("uid1") { limite = it }
        assertEquals(ResultadoLimite.Permitido, limite)
    }
}