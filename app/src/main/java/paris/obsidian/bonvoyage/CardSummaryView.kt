package paris.obsidian.bonvoyage

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip
import java.time.ZoneId

/** Compact card content. Editing opens the same focused editor for every card. */
class CardSummaryView(context: Context) : LinearLayout(context) {
    init { orientation = VERTICAL; LayoutInflater.from(context).inflate(R.layout.view_card_summary, this, true) }
    fun bind(day: Day, trip: Trip) {
        val hotel = day.type == "day"
        findViewById<TextView>(R.id.summaryName).apply {
            text = if (hotel) day.hotel.ifBlank { context.getString(R.string.hotel_section) } else context.getString(TransportMode.label(day.type))
            setCompoundDrawablesRelativeWithIntrinsicBounds(if (hotel) R.drawable.ic_hotel else TransportMode.icon(day.type), 0, 0, 0)
            compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.space_8)
        }
        findViewById<ImageButton>(R.id.editSummary).apply {
            setContentDescription(context.getString(if (hotel) R.string.edit_hotel else R.string.edit_transport))
            setOnClickListener { CardDetailsDialog.show(context, day, trip) }
        }
        findViewById<TextView>(R.id.summaryDetails).apply {
            visibility = if (hotel) GONE else VISIBLE
            if (!hotel) {
                fun time(value: String, zone: String) = if (value.isBlank()) context.getString(R.string.time_unset)
                    else TravelTime.local(value, if (trip.displayZone == "device") ZoneId.systemDefault().id else zone.ifBlank { trip.timeZone })
                text = context.getString(R.string.transport_summary,
                    day.location.ifBlank { context.getString(TransportMode.origin(day.type)) }, time(day.departure, day.departureZone),
                    day.destination.ifBlank { context.getString(TransportMode.destination(day.type)) }, time(day.arrival, day.arrivalZone))
            }
        }
        findViewById<View>(R.id.summarySleeper).visibility = if (!hotel && day.sleeps) VISIBLE else GONE
    }
}
