package paris.obsidian.bonvoyage

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.*
import paris.obsidian.bonvoyage.days.Day

/** Reused on every itinerary card; it owns map and booking link behavior. */
class CardActionsView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    init { LayoutInflater.from(context).inflate(R.layout.view_card_actions, this, true) }
    fun bind(day: Day) {
        visibility = if (day.type == "day") VISIBLE else GONE
        findViewById<Button>(R.id.mapAction).apply {
            visibility = if (day.location.isBlank() && day.hotel.isBlank()) GONE else VISIBLE
            setOnClickListener {
                val location = day.location.trim()
                val query = if (location.startsWith("https://") || location.startsWith("http://")) location
                    else listOf(day.hotel, location).filter { it.isNotBlank() }.joinToString(context.getString(R.string.country_separator))
                PlannerUi.open(context) { PlannerUi.map(query) }
            }
        }
        findViewById<Button>(R.id.bookingAction).apply {
            visibility = if (day.hotel.isBlank() && day.bookingUrl.isBlank()) GONE else VISIBLE
            setOnClickListener { PlannerUi.open(context) { PlannerUi.booking(day.bookingUrl.ifBlank { day.hotel }) } }
        }
    }
}
