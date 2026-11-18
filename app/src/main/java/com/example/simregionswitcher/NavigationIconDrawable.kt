package com.example.simregionswitcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Small native icons for the four UI destinations. */
class NavigationIconDrawable(private val kind: Int,color: Int): Drawable() {
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; style=Paint.Style.STROKE; strokeWidth=1.8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun draw(canvas: Canvas) {
        val save=canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
        when(kind) {
            0 -> { canvas.drawPath(Path().apply { moveTo(3f,11f);lineTo(12f,3f);lineTo(21f,11f);moveTo(5f,10f);lineTo(5f,21f);lineTo(10f,21f);lineTo(10f,15f);lineTo(14f,15f);lineTo(14f,21f);lineTo(19f,21f);lineTo(19f,10f) },pen) }
            1 -> { canvas.drawCircle(12f,7f,4f,pen); canvas.drawPath(Path().apply { moveTo(4f,21f);cubicTo(4f,12f,20f,12f,20f,21f);close() },pen) }
            2 -> { canvas.drawRoundRect(5f,3f,19f,21f,2f,2f,pen); for(y in listOf(8f,12f,16f)) canvas.drawLine(9f,y,15f,y,pen) }
            else -> { canvas.drawCircle(12f,12f,7f,pen);canvas.drawCircle(12f,12f,3f,pen); for(i in 0..7) { val a=i*Math.PI/4; canvas.drawLine((12+7*kotlin.math.cos(a)).toFloat(),(12+7*kotlin.math.sin(a)).toFloat(),(12+10*kotlin.math.cos(a)).toFloat(),(12+10*kotlin.math.sin(a)).toFloat(),pen) } }
        }
        canvas.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { pen.alpha=alpha;invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { pen.colorFilter=filter;invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
