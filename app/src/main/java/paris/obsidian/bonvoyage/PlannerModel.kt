package paris.obsidian.bonvoyage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip
import java.time.temporal.ChronoUnit

/** Ordered immutable write requests survive screen rotation and never run on the UI thread. */
class PlannerModel(app: Application) : AndroidViewModel(app) {
    val db = DatabaseClient.getInstance(app)
    val error = PlannerWrites.error
    val saving = MutableStateFlow(false)
    val savedTrip = MutableStateFlow<Int?>(null)
    val addedCard = MutableStateFlow<Long?>(null)
    val noteDrafts = java.util.concurrent.ConcurrentHashMap<Long, String>()
    fun enqueue(write: () -> Unit) { PlannerWrites.enqueue(write) }
    val cardDrafts = java.util.concurrent.ConcurrentHashMap<Long, Day>()
    fun saveText(day: Day) = saveCard(day)
    fun saveDetails(day: Day) = saveCard(day)
    fun saveCard(day: Day) {
        val snapshot = day.copy()
        cardDrafts[snapshot.id] = snapshot
        noteDrafts[snapshot.id] = snapshot.content
        enqueue { db.dayDao().updateOne(snapshot); cardDrafts.remove(snapshot.id, snapshot) }
    }
    fun delete(day: Day) {
        val snapshot = day.copy()
        enqueue { db.runInTransaction {
            db.dayDao().removeOne(snapshot)
            if (snapshot.type == "day") {
                val trip = db.tripDao().findByID(snapshot.tripID)
                val days = db.dayDao().getAll(snapshot.tripID).filter { it.type == "day" }.sortedBy { it.dayNumber }
                days.forEachIndexed { index, item -> db.dayDao().updateOne(item.copy(dayNumber = index + 1,
                    date = TravelTime.dayFormat.format(TravelTime.date(trip.dateBegin).plusDays(index.toLong())))) }
                db.tripDao().updateOne(trip.copy(dateEnd = TravelTime.dayFormat.format(TravelTime.date(trip.dateBegin).plusDays((days.size - 1).coerceAtLeast(0).toLong()))))
            }
        } }
    }
    fun saveTrip(trip: Trip) {
        if (savedTrip.value != null) return
        if (!saving.compareAndSet(false, true)) return
        val snapshot = trip.copy()
        enqueue {
            try { db.runInTransaction {
                if (snapshot.id == 0) {
                    snapshot.id = db.tripDao().insertOne(snapshot).toInt()
                    savedTrip.value = snapshot.id
                } else {
                    val previous = db.tripDao().findByID(snapshot.id)
                    val days = db.dayDao().getAll(snapshot.id)
                    val shift = ChronoUnit.DAYS.between(TravelTime.date(previous.dateBegin), TravelTime.date(snapshot.dateBegin))
                    days.forEach { day -> if (day.date.isNotBlank()) db.dayDao().updateOne(day.copy(date = TravelTime.dayFormat.format(TravelTime.date(day.date).plusDays(shift)))) }
                    val count = days.count { it.type == "day" }.coerceAtLeast(1)
                    snapshot.dateEnd = TravelTime.dayFormat.format(TravelTime.date(snapshot.dateBegin).plusDays((count - 1).toLong()))
                    db.tripDao().updateOne(snapshot); savedTrip.value = snapshot.id
                }
            } } finally { saving.value = false }
        }
    }
    internal fun importTrip(itinerary: TripWorkbookImport.Itinerary) {
        if (savedTrip.value != null || !saving.compareAndSet(false, true)) return
        enqueue {
            try {
                var id = 0
                db.runInTransaction {
                    id = db.tripDao().insertOne(itinerary.trip.copy(id = 0)).toInt()
                    db.dayDao().insertAll(*itinerary.cards.map { it.copy(id = 0, tripID = id) }.toTypedArray())
                }
                savedTrip.value = id
            } finally {
                saving.value = false
            }
        }
    }
    fun addCard(trip: Trip, type: String, name: String = "", country: String = "", details: Day? = null) {
        val snapshot = trip.copy()
        val values = details?.copy()
        enqueue { db.runInTransaction {
            val saved = db.dayDao().getAll(snapshot.id)
            val lastDay = saved.filter { it.type == "day" }.maxOfOrNull { it.dayNumber } ?: 0
            val number = if (type == "day") lastDay + 1 else lastDay.coerceAtLeast(1)
            val date = TravelTime.date(snapshot.dateBegin).plusDays((number - 1).toLong())
            val selectedCountry = country.ifBlank { saved.lastOrNull()?.country?.takeIf { it.isNotBlank() } ?: CountryArtwork.names(snapshot.countries).firstOrNull().orEmpty() }
            val zone = CountryZones.choose(listOf(selectedCountry), snapshot.timeZone)
            val title = if (type == "day" && name.isBlank()) getApplication<Application>().getString(R.string.day_default_name, number) else name
            val day = (values ?: Day(departureZone = zone, arrivalZone = zone)).copy(id = 0, type = type, tripID = snapshot.id, dayNumber = number, title = title, country = selectedCountry, destinationCountry = selectedCountry,
                date = values?.date?.takeIf { it.isNotBlank() } ?: TravelTime.dayFormat.format(date))
            addedCard.value = db.dayDao().insertOne(day)
            if (type == "day") db.tripDao().updateOne(db.tripDao().findByID(snapshot.id).copy(dateEnd = TravelTime.dayFormat.format(date)))
        } }
    }

}

/** App-scoped queue: leaving an editor must not cancel its pending saves. */
internal object PlannerWrites {
    val error = MutableStateFlow(false)
    private val writes = Channel<() -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    init { scope.launch { for (write in writes) try { write() } catch (_: Exception) { error.value = true } } }
    fun enqueue(write: () -> Unit) { writes.trySend(write) }
}
