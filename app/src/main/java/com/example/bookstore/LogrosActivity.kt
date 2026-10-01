package com.example.bookstore

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDragHandleView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlin.math.abs

class LogrosActivity : AppCompatActivity() {

    private lateinit var adapterInsignias: InsigniaAdapter
    private lateinit var adapterRanking: RankingAdapter

    private lateinit var tvEmojiNivel: TextView
    private lateinit var tvNombreNivel: TextView
    private lateinit var tvPuntos: TextView
    private lateinit var progresoNivel: LinearProgressIndicator
    private lateinit var tvSiguienteNivel: TextView
    private lateinit var tvImpacto: TextView
    private lateinit var recyclerInsignias: RecyclerView
    private lateinit var llRanking: LinearLayout
    private lateinit var tvMiPuesto: TextView

    private var estadisticas: Estadisticas? = null
    private var uid = ""

    private val colorAzul by lazy { Color.parseColor("#2C4770") }
    private val colorVerde by lazy { Color.parseColor("#2E8B57") }
    private val colorRojo by lazy { Color.parseColor("#D64550") }
    private val colorTexto by lazy { Color.parseColor("#1E1E1E") }
    private val colorGris by lazy { Color.parseColor("#777777") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logros)

        uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            finish()
            return
        }

        tvEmojiNivel = findViewById(R.id.tvEmojiNivel)
        tvNombreNivel = findViewById(R.id.tvNombreNivel)
        tvPuntos = findViewById(R.id.tvPuntos)
        progresoNivel = findViewById(R.id.progresoNivel)
        tvSiguienteNivel = findViewById(R.id.tvSiguienteNivel)
        tvImpacto = findViewById(R.id.tvImpacto)
        recyclerInsignias = findViewById(R.id.recyclerInsignias)
        llRanking = findViewById(R.id.llRanking)
        tvMiPuesto = findViewById(R.id.tvMiPuesto)

        adapterInsignias = InsigniaAdapter { insignia -> tocarInsignia(insignia) }
        recyclerInsignias.layoutManager = GridLayoutManager(this, 3)
        recyclerInsignias.adapter = adapterInsignias

        adapterRanking = RankingAdapter(uid)
        val recyclerRanking = findViewById<RecyclerView>(R.id.recyclerRanking)
        recyclerRanking.layoutManager = LinearLayoutManager(this)
        recyclerRanking.adapter = adapterRanking

        val grupo = findViewById<MaterialButtonToggleGroup>(R.id.grupoLogros)
        grupo.check(R.id.btnPestanaInsignias)
        grupo.addOnButtonCheckedListener { _, idBoton, marcado ->
            if (!marcado) return@addOnButtonCheckedListener
            val verRanking = idBoton == R.id.btnPestanaRanking
            recyclerInsignias.visibility = if (verRanking) View.GONE else View.VISIBLE
            llRanking.visibility = if (verRanking) View.VISIBLE else View.GONE
            if (verRanking) cargarRanking()
        }

        findViewById<MaterialButton>(R.id.btnRegresarLogros).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnComoGanar).setOnClickListener { mostrarReglas() }
    }

    override fun onResume() {
        super.onResume()
        cargar()
    }

    private fun cargar() {
        Logros.sincronizar(uid) { e ->
            if (isFinishing || isDestroyed) return@sincronizar
            if (e == null) {
                Toast.makeText(this, "No se pudieron cargar tus logros", Toast.LENGTH_SHORT).show()
                return@sincronizar
            }
            estadisticas = e
            mostrarNivel(e)
            mostrarImpacto(e)

            val insignias = Logros.insignias(e)
            adapterInsignias.actualizar(insignias)

            // 🎉 Celebra las insignias nuevas
            val nuevas = Logros.insigniasNuevas(this, uid, insignias)
            Logros.marcarVistas(this, uid, insignias)
            if (nuevas.isNotEmpty()) {
                recyclerInsignias.postDelayed({ mostrarCelebracion(nuevas, 0, esNueva = true) }, 700)
            }

            if (llRanking.visibility == View.VISIBLE) cargarRanking()
        }
    }

    // Tarjeta de nivel: emoji con rebote, puntos contando y barra que se llena
    private fun mostrarNivel(e: Estadisticas) {
        val puntos = Logros.puntos(e)
        val nivel = Logros.nivel(puntos)
        val siguiente = Logros.siguienteNivel(puntos)

        tvEmojiNivel.text = nivel.emoji
        tvNombreNivel.text = "Nivel ${nivel.nombre}"
        tvSiguienteNivel.text = if (siguiente == null) {
            "¡Llegaste al nivel máximo! 👑"
        } else {
            "Te faltan ${siguiente.desde - puntos} pts para ${siguiente.emoji} ${siguiente.nombre}"
        }

        tvEmojiNivel.scaleX = 0f
        tvEmojiNivel.scaleY = 0f
        tvEmojiNivel.animate().scaleX(1f).scaleY(1f)
            .setDuration(500).setInterpolator(OvershootInterpolator(2f)).start()

        ValueAnimator.ofInt(0, puntos).apply {
            duration = 1000
            interpolator = DecelerateInterpolator()
            addUpdateListener { tvPuntos.text = "${it.animatedValue} pts" }
            start()
        }

        progresoNivel.setProgressCompat(0, false)
        progresoNivel.postDelayed({
            progresoNivel.setProgressCompat(Logros.progresoNivel(puntos), true)
        }, 300)
    }

    private fun mostrarImpacto(e: Estadisticas) {
        tvImpacto.text = when (e.impacto) {
            0 -> "Comparte tu primer libro y ayuda a otro estudiante a leer sin comprar uno nuevo 📚"
            1 -> "Gracias a ti, 1 estudiante leyó sin comprar un libro nuevo 🌍"
            else -> "Gracias a ti, ${e.impacto} estudiantes leyeron sin comprar un libro nuevo 🌍"
        }
    }

    private fun tocarInsignia(insignia: Insignia) {
        if (insignia.desbloqueada) {
            mostrarCelebracion(listOf(insignia), 0, esNueva = false)
        } else {
            val avance = minOf(insignia.progreso, insignia.meta)
            Snackbar.make(
                findViewById(R.id.raizLogros),
                "🔒 ${insignia.descripcion} · llevas $avance de ${insignia.meta}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    // 🎉 Ventana con confeti, emoji girando y vibración
    private fun mostrarCelebracion(lista: List<Insignia>, indice: Int, esNueva: Boolean) {
        if (indice >= lista.size || isFinishing || isDestroyed) return
        val insignia = lista[indice]
        val vista = layoutInflater.inflate(R.layout.dialog_insignia_nueva, null)

        vista.findViewById<TextView>(R.id.tvEtiquetaInsignia).text =
            if (esNueva) "¡NUEVA INSIGNIA!" else "INSIGNIA DESBLOQUEADA"
        val tvEmoji = vista.findViewById<TextView>(R.id.tvEmojiInsignia)
        tvEmoji.text = insignia.emoji
        vista.findViewById<TextView>(R.id.tvNombreInsignia).text = insignia.nombre
        vista.findViewById<TextView>(R.id.tvDescripcionInsignia).text = insignia.descripcion

        val dialogo = AlertDialog.Builder(this).setView(vista).create()
        dialogo.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        vista.findViewById<MaterialButton>(R.id.btnGenial).setOnClickListener { dialogo.dismiss() }
        dialogo.setOnDismissListener { mostrarCelebracion(lista, indice + 1, esNueva) }
        dialogo.show()

        tvEmoji.scaleX = 0f
        tvEmoji.scaleY = 0f
        tvEmoji.rotation = -180f
        tvEmoji.animate().scaleX(1f).scaleY(1f).rotation(0f)
            .setDuration(700).setInterpolator(OvershootInterpolator(1.8f)).start()

        vista.findViewById<ConfetiView>(R.id.confeti).lanzar()
        vista.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // 🏆 Ranking con podio animado
    private fun cargarRanking() {
        Logros.cargarRanking { lista ->
            if (isFinishing || isDestroyed) return@cargarRanking
            pintarPodio(lista.take(3))
            adapterRanking.actualizar(lista.drop(3))

            val miIndice = lista.indexOfFirst { it.uid == uid }
            tvMiPuesto.text = if (miIndice >= 0) {
                "Estás en el puesto #${miIndice + 1} de la comunidad"
            } else {
                "Aún no estás en el top ${Logros.TAMANO_RANKING}. ¡Comparte libros para subir!"
            }
        }
    }

    private fun pintarPodio(top: List<PuestoRanking>) {
        val columnas = intArrayOf(R.id.colPodio1, R.id.colPodio2, R.id.colPodio3)
        val iniciales = intArrayOf(R.id.tvInicial1, R.id.tvInicial2, R.id.tvInicial3)
        val nombres = intArrayOf(R.id.tvNombrePodio1, R.id.tvNombrePodio2, R.id.tvNombrePodio3)
        val barras = intArrayOf(R.id.barra1, R.id.barra2, R.id.barra3)
        val puntos = intArrayOf(R.id.tvPuntosPodio1, R.id.tvPuntosPodio2, R.id.tvPuntosPodio3)
        val retrasos = longArrayOf(300L, 150L, 0L) // El 3.º sube primero y el 1.º al final

        for (i in 0..2) {
            val columna = findViewById<View>(columnas[i])
            val puesto = top.getOrNull(i)
            if (puesto == null) {
                columna.visibility = View.INVISIBLE
                continue
            }
            columna.visibility = View.VISIBLE
            findViewById<TextView>(iniciales[i]).text = puesto.nombre.take(1).uppercase().ifEmpty { "?" }
            findViewById<TextView>(nombres[i]).text = if (puesto.uid == uid) "Tú" else puesto.nombre
            findViewById<TextView>(puntos[i]).text = "${puesto.puntos}"

            val barra = findViewById<View>(barras[i])
            barra.post {
                barra.pivotY = barra.height.toFloat()
                barra.scaleY = 0f
                barra.animate().scaleY(1f)
                    .setStartDelay(retrasos[i])
                    .setDuration(600)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
        }
    }

    // ⭐ Panel "¿Cómo gano puntos?"
    private fun mostrarReglas() {
        val e = estadisticas ?: return
        val hoja = BottomSheetDialog(this)

        val contenedor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), 0, dp(24), dp(24))
        }

        contenedor.addView(BottomSheetDragHandleView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        contenedor.addView(TextView(this).apply {
            text = "⭐ Cómo ganar puntos"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colorAzul)
        })
        contenedor.addView(TextView(this).apply {
            text = "Cada vez que un libro circula, ganas puntos. Así va tu cuenta:"
            textSize = 14f
            setTextColor(colorTexto)
            setPadding(0, dp(4), 0, dp(12))
        })

        Logros.reglas(e).forEachIndexed { indice, regla ->
            val fila = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            fila.addView(TextView(this).apply {
                text = regla.emoji
                textSize = 22f
                layoutParams = LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val textos = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            textos.addView(TextView(this).apply {
                text = regla.accion
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colorTexto)
            })
            val signo = if (regla.puntos > 0) "+" else "−"
            val veces = if (regla.veces == 1) "1 vez" else "${regla.veces} veces"
            textos.addView(TextView(this).apply {
                text = "$signo${abs(regla.puntos)} pts · lo hiciste $veces"
                textSize = 12f
                setTextColor(colorGris)
            })
            fila.addView(textos)

            val sinHacer = regla.veces == 0
            fila.addView(TextView(this).apply {
                text = when {
                    // Aún no lo hiciste: muestra lo que podrías ganar
                    sinHacer && regla.puntos > 0 -> "+${regla.puntos} 🎯"
                    sinHacer -> "—"
                    regla.total >= 0 -> "+${regla.total}"
                    else -> "−${abs(regla.total)}"
                }
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(
                    when {
                        sinHacer -> colorGris
                        regla.total >= 0 -> colorVerde
                        else -> colorRojo
                    }
                )
            })

            // Las filas entran deslizándose una tras otra
            fila.alpha = 0f
            fila.translationX = dp(30).toFloat()
            fila.animate().alpha(if (sinHacer) 0.55f else 1f).translationX(0f)
                .setStartDelay(indice * 60L).setDuration(280).start()

            contenedor.addView(fila)
        }

        contenedor.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                .apply { topMargin = dp(8) }
            setBackgroundColor(Color.parseColor("#E0E0E0"))
        })
        contenedor.addView(TextView(this).apply {
            text = "Total: ${Logros.puntos(e)} pts"
            textSize = 18f
            gravity = Gravity.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colorAzul)
            setPadding(0, dp(12), 0, 0)
        })

        hoja.setContentView(contenedor)
        hoja.show()
    }

    private fun dp(valor: Int) = (valor * resources.displayMetrics.density).toInt()
}