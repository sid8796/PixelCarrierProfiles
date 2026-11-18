package com.example.simregionswitcher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.View

/** Small code-native chevron; follows the current palette and RTL layout. */
class PixelChevronDrawable(color: Int, private val density: Float, private val forward: Boolean = false) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color=color; style=Paint.Style.STROKE; strokeWidth=2*density
        strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND
    }
    override fun draw(canvas: Canvas) {
        val x=if(layoutDirection==View.LAYOUT_DIRECTION_RTL) bounds.left+18*density else bounds.right-18*density
        val y=bounds.exactCenterY()
        canvas.drawPath(Path().apply {
            if(forward) {
                val direction=if(layoutDirection==View.LAYOUT_DIRECTION_RTL) -1 else 1
                moveTo(x-2*density*direction,y-4*density);lineTo(x+2*density*direction,y);lineTo(x-2*density*direction,y+4*density)
            } else { moveTo(x-4*density,y-2*density);lineTo(x,y+2*density);lineTo(x+4*density,y-2*density) }
        },paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter=colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
