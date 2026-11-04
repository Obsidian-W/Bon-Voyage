package paris.obsidian.bonvoyage

import android.content.Context
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.LinearLayout
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip

class HotelSectionView(context: Context) : FrameLayout(context) {
    private val summary = CardSummaryView(context)
    private val actions = CardActionsView(context)
    init {
        LayoutInflater.from(context).inflate(R.layout.view_hotel_section, this, true)
        findViewById<LinearLayout>(R.id.hotelSectionContent).apply { addView(summary); addView(actions) }
    }
    fun bind(day: Day, trip: Trip) { summary.bind(day, trip); actions.bind(day) }
}
