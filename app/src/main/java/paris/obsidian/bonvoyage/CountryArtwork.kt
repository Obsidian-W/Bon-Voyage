package paris.obsidian.bonvoyage

import android.content.Context
import android.view.View
import android.widget.*
import com.bumptech.glide.Glide
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sqrt

object CountryArtwork {
    fun names(value: String) = value.split('|').filter { it.isNotBlank() }.distinct()
    fun flag(name: String): String? = flagCode(name)?.uppercase(Locale.ROOT)?.let { code ->
        buildString { code.forEach { appendCodePoint(0x1F1E6 + it.code - 'A'.code) } }
    }
    fun resource(context: Context, name: String): Int {
        val key = when (name) {
            "Bosnia & Herzegovina" -> "bosnia"
            "Antigua & Barbuda" -> "antigua___barbuda"
            else -> name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "_")
        }
        return context.resources.getIdentifier(key, "drawable", context.packageName)
    }
    fun flagCode(name: String): String? {
        val aliases = mapOf("Congo Brazzaville" to "cg", "DR Congo" to "cd", "Cote d’Ivoire" to "ci",
            "Czech Republic" to "cz", "Palestine" to "ps", "Bosnia & Herzegovina" to "ba",
            "The Gambia" to "gm", "Cabo Verde" to "cv", "Kosovo" to "xk", "Vatican City" to "va",
            "Turkey" to "tr", "Antigua & Barbuda" to "ag", "Saint Kitts & Nevis" to "kn",
            "St Vincent & Grenadines" to "vc", "Sao Tome & Principe" to "st", "Trinidad & Tobago" to "tt")
        return aliases[name] ?: Locale.getISOCountries().firstOrNull {
            Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.ENGLISH) == name
        }?.lowercase(Locale.ROOT)
    }
    fun bind(container: FrameLayout, countries: List<String>) {
        container.removeAllViews()
        val items = countries.ifEmpty { listOf("") }
        val columns = ceil(sqrt(items.size.toDouble())).toInt()
        val grid = GridLayout(container.context).apply { columnCount = columns }
        container.addView(grid, FrameLayout.LayoutParams(-1, -1))
        items.forEachIndexed { index, name ->
            val image = ImageView(container.context).apply { scaleType = ImageView.ScaleType.CENTER_CROP; contentDescription = name }
            grid.addView(image, GridLayout.LayoutParams(GridLayout.spec(index / columns, 1f), GridLayout.spec(index % columns, 1f)).apply { width = 0; height = 0 })
            val resource = resource(container.context, name)
            val source: Any = if (resource != 0) resource else flagCode(name)?.let { "https://flagcdn.com/w640/$it.png" } ?: R.drawable.main_background
            Glide.with(image).load(source).placeholder(R.drawable.main_background).error(R.drawable.main_background).into(image)
        }
    }
}
