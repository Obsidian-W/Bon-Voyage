package paris.obsidian.bonvoyage

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat

/** Empty-state text stays legible directly over destination photography. */
class PhotoEmptyTextView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatTextView(context, attrs) {
    init {
        val offset = resources.getDimension(R.dimen.text_shadow_offset)
        setShadowLayer(resources.getDimension(R.dimen.text_shadow_radius), offset, offset, ContextCompat.getColor(context, R.color.black))
    }
}
