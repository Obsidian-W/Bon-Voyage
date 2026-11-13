package paris.obsidian.bonvoyage.trips

import android.view.*
import android.widget.*
import androidx.recyclerview.widget.*
import androidx.recyclerview.widget.ListAdapter
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK
import paris.obsidian.bonvoyage.*

class TripAdapter(private val onClick: (Trip) -> Unit, private val onLongClick: (View, Trip) -> Unit) :
    ListAdapter<Trip, TripAdapter.TripViewHolder>(TripDiffCallback) {
    init { setHasStableIds(true); stateRestorationPolicy = StateRestorationPolicy.PREVENT_WHEN_EMPTY }
    override fun getItemId(position: Int) = getItem(position).id.toLong()
    class TripViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var artworkKey: String? = null
        var boundTrip: Trip? = null
    }
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TripViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_country, parent, false)
        val columns = (parent as RecyclerView).layoutManager.let { (it as GridLayoutManager).spanCount }
        val width = (parent.width - parent.paddingLeft - parent.paddingRight) / columns
        view.layoutParams.height = width.coerceAtLeast((parent.resources.getDimensionPixelSize(R.dimen.tile_height) * parent.resources.configuration.fontScale).toInt())
        return TripViewHolder(view)
    }
    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        val trip = getItem(position)
        holder.boundTrip = trip
        val view = holder.itemView
        val add = trip.id == 0
        view.findViewById<View>(R.id.plusImage).visibility = if (add) View.VISIBLE else View.GONE
        val caption = view.findViewById<View>(R.id.tripCaption)
        val flags = view.findViewById<TextView>(R.id.tripFlags)
        flags.text = ""
        flags.visibility = View.GONE
        caption.visibility = if (add) View.GONE else View.VISIBLE
        if (!add) {
            view.findViewById<TextView>(R.id.countryName).text = trip.name
            val dates = view.context.getString(R.string.tile_date_range, tileDate(trip.dateBegin), tileDate(trip.dateEnd))
            view.findViewById<TextView>(R.id.tileDates).text = dates
            val row = view.findViewById<View>(R.id.tripNameRow)
            val candidates = CountryArtwork.names(trip.countries.ifBlank { trip.name }).mapNotNull(CountryArtwork::flag)
                .take(view.resources.getInteger(R.integer.tile_max_flags))
            row.doOnLayout {
                if (holder.boundTrip !== trip) return@doOnLayout
                val available = row.width / 2
                val visibleFlags = candidates.indices.reversed().map { candidates.take(it + 1).joinToString(" ") }
                    .firstOrNull { flags.paint.measureText(it) <= available }.orEmpty()
                flags.text = visibleFlags
                flags.visibility = if (visibleFlags.isEmpty()) View.GONE else View.VISIBLE
            }
        }
        view.findViewById<FrameLayout>(R.id.artworkContainer).apply {
            visibility = if (add) View.GONE else View.VISIBLE
            val key = trip.countries.ifBlank { trip.name }
            if (!add && holder.artworkKey != key) { CountryArtwork.bind(this, CountryArtwork.names(key)); holder.artworkKey = key }
        }
        view.findViewById<View>(R.id.tripTile).apply {
            contentDescription = trip.name
            setOnClickListener { onClick(trip.copy()) }
            setOnLongClickListener { anchor ->
                onLongClick(anchor, trip.copy())
                true
            }
            if (add) {
                ViewCompat.replaceAccessibilityAction(this, ACTION_LONG_CLICK, context.getString(R.string.import_excel)) { target, _ ->
                    target.performLongClick()
                }
            } else {
                ViewCompat.removeAccessibilityAction(this, ACTION_LONG_CLICK.id)
            }
        }
    }
    private fun tileDate(value: String) = runCatching { TravelTime.tripListDateFormat.format(TravelTime.date(value)) }.getOrDefault(value)
}
object TripDiffCallback : DiffUtil.ItemCallback<Trip>() {
    override fun areItemsTheSame(oldItem: Trip, newItem: Trip) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: Trip, newItem: Trip) = oldItem == newItem
}
