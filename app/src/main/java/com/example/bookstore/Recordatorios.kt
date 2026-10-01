package com.example.bookstore

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar
import java.util.concurrent.TimeUnit

object Recordatorios {

    private const val CANAL_ID = "recordatorios_prestamos"
    private const val TRABAJO_DIARIO = "revision_diaria_prestamos"
    private const val HORA_AVISO = 9 // 9:00 a. m.

    // Canal de notificaciones (obligatorio desde Android 8)
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Recordatorios de devolución",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            canal.description = "Avisos de préstamos por vencer y de turnos de reservas"
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(canal)
        }
    }

    // Programa la revisión diaria (si ya estaba programada, no la duplica)
    fun programar(context: Context) {
        val restricciones = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val solicitud = PeriodicWorkRequestBuilder<RecordatorioWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(milisHastaProximoAviso(), TimeUnit.MILLISECONDS)
            .setConstraints(restricciones)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TRABAJO_DIARIO,
            ExistingPeriodicWorkPolicy.KEEP,
            solicitud
        )
    }

    // Solo para pruebas: ejecuta la revisión de inmediato
    fun probarAhora(context: Context) {
        val solicitud = OneTimeWorkRequestBuilder<RecordatorioWorker>().build()
        WorkManager.getInstance(context).enqueue(solicitud)
    }

    private fun milisHastaProximoAviso(): Long {
        val ahora = Calendar.getInstance()
        val proximo = Calendar.getInstance()
        proximo.set(Calendar.HOUR_OF_DAY, HORA_AVISO)
        proximo.set(Calendar.MINUTE, 0)
        proximo.set(Calendar.SECOND, 0)
        proximo.set(Calendar.MILLISECOND, 0)
        if (!proximo.after(ahora)) proximo.add(Calendar.DAY_OF_YEAR, 1)
        return proximo.timeInMillis - ahora.timeInMillis
    }

    @SuppressLint("MissingPermission") // El permiso se revisa manualmente abajo
    fun mostrar(context: Context, idNotificacion: Int, titulo: String, mensaje: String) {
        // Sin permiso (Android 13+), no se muestra nada
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        crearCanal(context)

        // Al tocarla abre Mis préstamos (y "atrás" lleva al Home)
        val intentAbrir = TaskStackBuilder.create(context)
            .addNextIntent(Intent(context, HomeActivity::class.java))
            .addNextIntent(Intent(context, MisPrestamosActivity::class.java))
            .getPendingIntent(
                idNotificacion,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        val notificacion = NotificationCompat.Builder(context, CANAL_ID)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setColor(0xFF2C4770.toInt())
            .setContentTitle(titulo)
            .setContentText(mensaje)
            .setStyle(NotificationCompat.BigTextStyle().bigText(mensaje))
            .setContentIntent(intentAbrir)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(idNotificacion, notificacion)
    }
}

// Revisa préstamos y turnos, y envía los avisos que correspondan
class RecordatorioWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val db = FirebaseFirestore.getInstance()

        return try {
            // 📚 PRÉSTAMOS POR VENCER
            val prestamos = Tasks.await(
                db.collection("prestamos")
                    .whereEqualTo("usuarioId", uid)
                    .whereEqualTo("estado", ReglasPrestamo.ESTADO_ACTIVO)
                    .get(),
                30, TimeUnit.SECONDS
            )

            for (documento in prestamos) {
                val prestamo = documento.toObject(Prestamo::class.java)
                val fechaLimite = prestamo.fechaLimite?.toDate() ?: continue
                val dias = ReglasPrestamo.diasRestantes(fechaLimite)
                val fecha = ReglasPrestamo.formatear(fechaLimite)

                val (titulo, mensaje) = when {
                    dias == 3 -> "📚 Te quedan 3 días" to
                            "Recuerda devolver \"${prestamo.titulo}\" antes del $fecha."
                    dias == 1 -> "⏰ Mañana vence tu préstamo" to
                            "\"${prestamo.titulo}\" debe devolverse mañana ($fecha)."
                    dias == 0 -> "⚠️ Hoy vence tu préstamo" to
                            "Hoy es el último día para devolver \"${prestamo.titulo}\"."
                    dias < 0 -> {
                        val retraso = -dias
                        val textoDias = if (retraso == 1) "1 día" else "$retraso días"
                        "❗ Préstamo vencido" to
                                "\"${prestamo.titulo}\" venció hace $textoDias. Devuélvelo lo antes posible."
                    }
                    else -> continue // Aún faltan más de 3 días: no se avisa
                }

                Recordatorios.mostrar(applicationContext, prestamo.id.hashCode(), titulo, mensaje)
            }

            // 🎉 TURNOS DE RESERVAS
            val turnos = Tasks.await(
                db.collection("reservas")
                    .whereEqualTo("usuarioId", uid)
                    .whereEqualTo("estado", Reservas.TURNO)
                    .get(),
                30, TimeUnit.SECONDS
            )

            val ahora = System.currentTimeMillis()
            for (documento in turnos) {
                val reserva = documento.toObject(Reserva::class.java)
                val limite = reserva.fechaLimiteTurno?.toDate() ?: continue
                val restanteMs = limite.time - ahora
                if (restanteMs <= 0) continue // Ya venció

                val tiempo = Reservas.tiempoRestante(limite)
                val (titulo, mensaje) = if (restanteMs < Reservas.HORAS_URGENTE * 3_600_000L) {
                    "⏰ ¡No pierdas tu turno!" to
                            "Te quedan $tiempo para obtener \"${reserva.titulo}\". " +
                            "Después pasará al siguiente de la fila."
                } else {
                    "🎉 ¡Es tu turno!" to
                            "\"${reserva.titulo}\" ya está disponible para ti. Tienes $tiempo para obtenerlo."
                }

                Recordatorios.mostrar(applicationContext, reserva.id.hashCode(), titulo, mensaje)
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry() // Sin internet u otro error: Android lo reintenta más tarde
        }
    }
}