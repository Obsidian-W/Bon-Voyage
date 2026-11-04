package paris.obsidian.bonvoyage

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Text fields scroll inside their card; taking focus must not shift the horizontal strip. */
class CardLayoutManager(private val context: Context) : LinearLayoutManager(context, RecyclerView.HORIZONTAL, false) {
    override fun checkLayoutParams(lp: RecyclerView.LayoutParams): Boolean {
        val preview = if (itemCount > 2) context.resources.getDimensionPixelSize(R.dimen.space_16) else 0
        lp.width = (width - paddingLeft - paddingRight - preview).coerceAtLeast(1)
        return true
    }
    override fun onRequestChildFocus(parent: RecyclerView, state: RecyclerView.State, child: View, focused: View?): Boolean = true
    override fun requestChildRectangleOnScreen(parent: RecyclerView, child: View, rect: android.graphics.Rect, immediate: Boolean, focusedChildVisible: Boolean): Boolean = false
}
