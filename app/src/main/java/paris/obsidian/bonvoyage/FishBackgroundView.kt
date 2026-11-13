package paris.obsidian.bonvoyage

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class FishBackgroundView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val step = resources.getDimension(R.dimen.fish_pattern_step)
    private val fish = listOf("🐟", "🐠", "🐡")
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = resources.getDimension(R.dimen.size_large)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var row = 0
        var y = step
        while (y < height) {
            var column = 0
            var x = if (row % 2 == 0) step / 4 else step * 3 / 4
            while (x < width) {
                canvas.drawText(fish[(row + column) % fish.size], x, y, paint)
                column++
                x += step
            }
            row++
            y += step
        }
    }
}
