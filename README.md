# BookStore

Aplicación móvil Android para la circulación de libros entre estudiantes de la UPN: préstamo, intercambio, venta y donación.

*Versión actual:* 1.0.1 · Historial de cambios en [CHANGELOG.md](CHANGELOG.md)

## Funcionalidades

- Registro e inicio de sesión de estudiantes.
- Búsqueda de libros por título, autor, género o curso, con filtros y ordenamiento.
- Préstamos, solicitudes y reservas de libros.
- Ofertas de venta, intercambio y donación.
- Perfil del estudiante, logros e insignias.

## Tecnologías

- *Lenguaje:* Kotlin
- *Interfaz:* Activities con layouts XML, Material Components y RecyclerView
- *Backend:* Firebase Authentication y Cloud Firestore
- *Pruebas:* JUnit 4 (pruebas unitarias)

## Requisitos

- Android Studio *2026.2.1 (Rabbit)* o superior (el proyecto usa AGP 9.3.2).
- JDK incluido en Android Studio (el código compila con Java 11).
- minSdk 24 · targetSdk 37 · compileSdk 37.
- Conexión a internet (Firebase).

## Cómo ejecutar

1. Clonar el repositorio *fuera de OneDrive* (por ejemplo C:\Proyectos):

   git clone https://github.com/PRLS9/BookStore-App.git

2. Abrir la carpeta en Android Studio y esperar el Gradle Sync.
3. El archivo app/google-services.json ya está incluido (proyecto Firebase del curso).
4. Ejecutar en un emulador o en un dispositivo con Android 7.0 o superior.

## Pruebas unitarias

Desde Android Studio: clic derecho en la carpeta test → Run Tests.
Desde la terminal (Windows):

gradlew.bat testDebugUnitTest

Incluye pruebas de PrestamoService, TextoUtils y Validaciones.

## Estructura relevante

| Clase | Responsabilidad |
|---|---|
| PrestamoService | Reglas de préstamo (límite de libros por estudiante). |
| PrestamoRepository / FirestorePrestamoRepository | Acceso a datos de préstamos (separado de Firebase). |
| TextoUtils | Normalización de texto para las búsquedas (sin tildes ni mayúsculas). |
| Validaciones | Reglas compartidas de nombre, celular, correo y contraseña. |
| Oferta | Tipos de oferta y estados de un libro. |
| FiltrosActivity | Pantalla de filtros y constantes de orden y disponibilidad. |

## Flujo de trabajo del equipo

- Rama principal master, protegida: todo cambio entra por *Pull Request* con *1 aprobación*.
- Ramas cortas por cambio (cr01-..., cr02-..., mant-...) y merge con Create a merge commit.
- Versionamiento semántico (MAYOR.MENOR.PATCH).

## Equipo

Peter Lazo · Alfonso Ríos · Fernando Calvo · Jonatan Hilario · Daniel Solís · Alexander Vega

Proyecto académico del curso Evolución y Configuración de Software – Universidad Privada del Norte.