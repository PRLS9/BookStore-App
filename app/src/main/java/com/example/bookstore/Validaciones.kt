package com.example.bookstore

/**
 * CR-02 (DRY): reglas de validación compartidas por toda la app.
 * Antes estaban copiadas en RegistroActivity, PerfilActivity y RegistrarLibroActivity,
 * y no eran iguales: Registro solo contaba 9 caracteres y aceptaba "98765-432".
 */
object Validaciones {

    const val CAMPO_OBLIGATORIO = "Este campo es obligatorio"
    const val ERROR_CELULAR = "El celular debe tener 9 dígitos"
    const val ERROR_CORREO = "Ingrese un correo válido"
    const val ERROR_PASSWORD = "No cumple con los requisitos indicados abajo"

    private val REGEX_SOLO_TEXTO = Regex("^[a-zA-ZÀ-ÿñÑ\\s]+$")
    private val REGEX_CELULAR = Regex("^\\d{9}$")
    private val REGEX_CORREO = Regex("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$")

    // Símbolos aceptados en la contraseña (los mismos que tenía la expresión regular original)
    private const val SIMBOLOS = "^$*.[]{}()?\"!@#%&/\\,><':;|_~`"

    // ---- Reglas: devuelven true si el texto cumple ----

    fun esSoloTexto(texto: String): Boolean = REGEX_SOLO_TEXTO.matches(texto)

    // Exactamente 9 dígitos: "987654321" sí, "98765-432" no
    fun esCelularValido(celular: String): Boolean = REGEX_CELULAR.matches(celular)

    fun esCorreoValido(correo: String): Boolean = REGEX_CORREO.matches(correo)

    // Mínimo 8 caracteres, una minúscula, una mayúscula y un símbolo
    fun esPasswordSegura(password: String): Boolean =
        password.length >= 8 &&
                password.any { it in 'a'..'z' } &&
                password.any { it in 'A'..'Z' } &&
                password.any { it in SIMBOLOS }

    // ---- Mensajes: devuelven el error a mostrar, o null si está bien ----

    fun errorSoloTexto(texto: String, mensaje: String): String? = when {
        texto.isEmpty() -> CAMPO_OBLIGATORIO
        !esSoloTexto(texto) -> mensaje
        else -> null
    }

    fun errorCelular(celular: String): String? = when {
        celular.isEmpty() -> CAMPO_OBLIGATORIO
        !esCelularValido(celular) -> ERROR_CELULAR
        else -> null
    }

    fun errorCorreo(correo: String): String? = when {
        correo.isEmpty() -> CAMPO_OBLIGATORIO
        !esCorreoValido(correo) -> ERROR_CORREO
        else -> null
    }

    fun errorPassword(password: String): String? = when {
        password.isEmpty() -> CAMPO_OBLIGATORIO
        !esPasswordSegura(password) -> ERROR_PASSWORD
        else -> null
    }
}