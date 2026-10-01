package com.example.bookstore

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

class ConfetiView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particula(
        var x: Float, var y: Float,
        val vx: Float, var vy: Float,
        val tamano: Float, val color: Int,
        var rotacion: Float, val giro: Float
    )

    private val particulas = mutableListOf<Particula>()
    private val pincel = Paint(Paint.ANTI_ALIAS_FLAG)
    private var animador: ValueAnimator? = null

    private val colores = intArrayOf(
        Color.parseColor("#2C4770"), Color.parseColor("#7A6FB5"),
        Color.parseColor("#D9A62E"), Color.parseColor("#2E8B57"),
        Color.parseColor("#D64550"), Color.parseColor("#4A6FA5")
    )

    // Lanza una explosión de confeti desde el centro
    fun lanzar() {
        if (width == 0) {
            post { lanzar() }
            return
        }
        val densidad = resources.displayMetrics.density
        particulas.clear()
        repeat(90) {
            particulas.add(
                Particula(
                    x = width / 2f,
                    y = height * 0.35f,
                    vx = (Random.nextFloat() - 0.5f) * 14f * densidad,
                    vy = -(Random.nextFloat() * 12f + 4f) * densidad,
                    tamano = (4f + Random.nextFloat() * 6f) * densidad,
                    color = colores.random(),
                    rotacion = Random.nextFloat() * 360f,
                    giro = (Random.nextFloat() - 0.5f) * 20f
                )
            )
        }
        animador?.cancel()
        animador = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2600
            addUpdateListener {
                val gravedad = 0.45f * densidad
                for (p in particulas) {
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += gravedad
                    p.rotacion += p.giro
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (p in particulas) {
            if (p.y > height + 50) continue
            pincel.color = p.color
            canvas.save()
            canvas.rotate(p.rotacion, p.x, p.y)
            canvas.drawRect(
                p.x - p.tamano / 2, p.y - p.tamano / 4,
                p.x + p.tamano / 2, p.y + p.tamano / 4,
                pincel
            )
            canvas.restore()
        }
    }

    override fun onDetachedFromWindow() {
        animador?.cancel()
        super.onDetachedFromWindow()
    }
}