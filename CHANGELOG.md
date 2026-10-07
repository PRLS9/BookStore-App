# Changelog

Todos los cambios importantes de BookStore se registran en este archivo.

El formato se basa en [Keep a Changelog](https://keepachangelog.com/es-ES/1.0.0/)
y el proyecto sigue [Versionamiento Semántico](https://semver.org/lang/es/).

## [1.0.1] - 2026-10-05

Versión PATCH: refactorización, mantenimiento y una corrección, sin funcionalidad nueva.

### Corregido
- El registro aceptaba celulares con caracteres no numéricos (por ejemplo "98765-432"); ahora usa la misma regla que el perfil (CR-02).

### Cambiado
- CR-01: PrestamoService concentra las reglas de préstamo (SRP); BuscarLibrosActivity delega en él.
- CR-01: PrestamoService depende de la interfaz PrestamoRepository, implementada por FirestorePrestamoRepository e inyectada desde la Activity (DIP).
- CR-02: TextoUtils.normalizar() reemplaza las dos copias que había en LibroAdapter y AdaptadorSugerencias (DRY).
- CR-02: Validaciones centraliza las reglas de nombre, celular, correo y contraseña de Registro, Perfil y Registrar libro; Registrar libro reutiliza EstadoSolicitud.nombreCorto() (DRY).
- Mantenimiento adaptativo: adapterPosition (obsoleto) reemplazado por bindingAdapterPosition en los adaptadores; overridePendingTransition reemplazado por ActivityOptionsCompat en SplashActivity; dependencia androidx.recyclerview 1.3.2.
- Mantenimiento perfectivo: textos repetidos reemplazados por constantes (Oferta.ESTADO_DISPONIBLE y constantes de orden y disponibilidad en FiltrosActivity).

### Añadido
- Pruebas unitarias: PrestamoService (4), TextoUtils (3) y Validaciones (7).
- Documentación: README.md y este CHANGELOG.md.

## [1.0] - 2026-09-30

### Añadido
- Versión base (baseline): acceso, búsqueda y filtros, préstamos, solicitudes, reservas, venta, intercambio y donación, perfil y logros. Kotlin con Firebase Authentication y Cloud Firestore.

[1.0.1]: https://github.com/PRLS9/BookStore-App/compare/v1.0-baseline...v1.0.1
[1.0]: https://github.com/PRLS9/BookStore-App/releases/tag/v1.0-baseline