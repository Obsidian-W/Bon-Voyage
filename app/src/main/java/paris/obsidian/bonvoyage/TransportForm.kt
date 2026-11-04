package paris.obsidian.bonvoyage

import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip
import java.time.Instant

/** Shared by creation and editing so a journey is entered in one form. */
class TransportForm(private val form: PlannerForm, private val day: Day, trip: Trip) {
    private val context = form.parent.context
    private val departureZone = day.departureZone.ifBlank { CountryZones.choose(listOf(day.country), trip.timeZone) }
    private val arrivalZone = day.arrivalZone.ifBlank { departureZone }
    var type = day.type
        set(value) {
            field = value
            form.label("location", TransportMode.origin(value))
            form.label("destination", TransportMode.destination(value))
        }
    init {
        form.field("location", TransportMode.origin(type), day.location)
        form.time("departure", R.string.departure_label, TravelTime.local(day.departure, departureZone)) { TravelTime.date(day.date) }
        form.field("destination", TransportMode.destination(type), day.destination)
        form.time("arrival", R.string.arrival_label, TravelTime.local(day.arrival, arrivalZone)) { TravelTime.date(day.date) }
        form.check("sleeps", R.string.sleeper_label, day.sleeps)
    }
    fun save(result: (Day) -> Unit) {
        fun resolve(key: String, previous: String, resolved: (String) -> Unit) {
            val value = form.value(key)
            if (value.isBlank()) resolved("") else PlannerUi.resolveTime(context, value, if (key == "departure") departureZone else arrivalZone, previous, resolved)
        }
        resolve("departure", day.departure) { departure -> resolve("arrival", day.arrival) { arrival ->
            if (departure.isNotBlank() && arrival.isNotBlank() && Instant.parse(arrival).isBefore(Instant.parse(departure)))
                PlannerUi.error(context, R.string.arrival_before_departure)
            else result(day.copy(type = type, location = form.value("location"), destination = form.value("destination"), departure = departure, arrival = arrival,
                date = if (form.value("departure").isBlank()) day.date else TravelTime.dayFormat.format(TravelTime.localTime(form.value("departure")).toLocalDate()),
                departureZone = departureZone, arrivalZone = arrivalZone, sleeps = form.snapshot().getBoolean("sleeps")))
        } }
    }
}
