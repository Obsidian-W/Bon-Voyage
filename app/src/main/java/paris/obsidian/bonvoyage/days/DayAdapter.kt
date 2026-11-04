package paris.obsidian.bonvoyage.days

import android.view.*
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import paris.obsidian.bonvoyage.*
import paris.obsidian.bonvoyage.trips.Trip

class DayAdapter(var trip: Trip, private val save: (Day) -> Unit, private val remove: (Day) -> Unit,
                 private val add: (String) -> Unit, private val draft: (Long) -> Day?) : ListAdapter<Day, DayAdapter.Holder>(DayDiffCallback) {
    var keyboardVisible = false
    init { setHasStableIds(true); stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    override fun getItemId(position: Int) = getItem(position).id
    override fun getItemViewType(position: Int) = if (getItem(position).id == 0L) 0 else if (getItem(position).type == "day") 1 else 2
    inner class Holder(view: View, private val kind: Int) : RecyclerView.ViewHolder(view) {
        private var current: Day? = null
        private var binding = false
        private val name = view.findViewById<EditText>(R.id.dayTitle)
        private val flag = view.findViewById<TextView>(R.id.cardFlag)
        private val form = PlannerForm(view.findViewById(R.id.contentContainer))
        private val summary = if (kind == 2) CardSummaryView(view.context) else null
        private val hotel = if (kind == 1) HotelSectionView(view.context) else null
        init {
            if (kind == 0) {
                name.isFocusable = false; name.gravity = Gravity.CENTER; name.setText(R.string.add_day_title)
                view.findViewById<View>(R.id.plusDay).setOnClickListener { add("day") }
                view.findViewById<View>(R.id.plusTrip).setOnClickListener { add("trip") }
            } else {
            val openDetails = View.OnLongClickListener {
                current?.let { CardDetailsDialog.show(view.context, it, trip, full = true) }
                current != null
            }
            listOf(R.id.dayCard, R.id.dayHeader, R.id.dayNumber, R.id.dayTitle, R.id.contentContainer)
                .forEach { view.findViewById<View>(it).setOnLongClickListener(openDetails) }
            view.findViewById<View>(R.id.delete_day).setOnClickListener { current?.let { remove(it.copy()) } }
            name.addTextChangedListener { text -> if (!binding) change { it.copy(title = text.toString()) } }
            flag.setOnClickListener {
                val day = current ?: return@setOnClickListener
                val countries = CountryArtwork.names(trip.countries)
                if (countries.size > 1) SelectionPicker.show(view.context, R.string.country_label, R.string.search_label, countries, setOf(day.country), false) { selected ->
                    val country = selected.single()
                    change {
                        val zone = CountryZones.choose(listOf(country), it.departureZone)
                        val followsCard = it.destinationCountry.isBlank() || it.destinationCountry == it.country
                        it.copy(country = country, departureZone = zone,
                            destinationCountry = if (followsCard) country else it.destinationCountry,
                            arrivalZone = if (followsCard) zone else it.arrivalZone)
                    }
                }
            }
            summary?.let { form.parent.addView(it) }
            form.field("notes", R.string.notes_label, multiline = true)
            hotel?.let { form.parent.addView(it) }
            form.watch("notes") { text -> change { it.copy(content = text) } }
            }
        }
        private fun change(transform: (Day) -> Day) {
            if (binding) return
            current?.let { old -> val updated = transform(old.copy()); current = updated; save(updated); bindFields(updated) }
        }
        fun bind(item: Day) {
            val placeholder = kind == 0
            itemView.findViewById<View>(R.id.plusButtonContainer).visibility = if (placeholder) View.VISIBLE else View.GONE
            itemView.findViewById<View>(R.id.cardScroll).visibility = if (placeholder) View.GONE else View.VISIBLE
            itemView.findViewById<View>(R.id.cardFlag).visibility = if (placeholder) View.GONE else View.VISIBLE
            itemView.findViewById<View>(R.id.delete_day).visibility = if (placeholder) View.GONE else View.VISIBLE
            itemView.findViewById<View>(R.id.dayNumber).visibility = if (placeholder) View.GONE else View.VISIBLE
            if (placeholder) return
            val day = (draft(item.id) ?: item).copy(); current = day; bindFields(day)
        }
        private fun bindFields(day: Day) {
            binding = true
            if (!name.hasFocus() && name.text.toString() != day.title) name.setText(day.title)
            flag.text = CountryArtwork.flag(day.country).orEmpty()
            flag.contentDescription = day.country
            itemView.findViewById<View>(R.id.dayHeader).setBackgroundColor(ContextCompat.getColor(itemView.context, if (kind == 2) R.color.planner_transport else R.color.planner_day))
            itemView.findViewById<TextView>(R.id.dayNumber).apply {
                text = if (kind == 2 && day.departure.isNotBlank() && day.arrival.isNotBlank())
                    context.getString(R.string.card_title_date, TravelTime.label(day.date), PlannerUi.durationLabel(context, day.departure, day.arrival)) else TravelTime.label(day.date)
            }
            form.bind("notes", day.content)
            summary?.bind(day, trip); hotel?.bind(day, trip)
            binding = false
        }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.view_day, parent, false), viewType)
    override fun onBindViewHolder(holder: Holder, position: Int) { holder.bind(getItem(position)) }
}
object DayDiffCallback : DiffUtil.ItemCallback<Day>() {
    override fun areItemsTheSame(oldItem: Day, newItem: Day) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Day, newItem: Day) = oldItem == newItem
}
