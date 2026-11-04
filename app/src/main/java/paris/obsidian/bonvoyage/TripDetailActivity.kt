package paris.obsidian.bonvoyage

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import androidx.core.view.doOnNextLayout
import com.google.android.material.appbar.MaterialToolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import jp.wasabeef.glide.transformations.BlurTransformation
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import paris.obsidian.bonvoyage.days.*
import paris.obsidian.bonvoyage.trips.Trip

class TripDetailActivity : AppCompatActivity(), KeyboardAware {
    private val model by viewModels<PlannerModel>()
    private lateinit var adapter: DayAdapter
    private lateinit var recycler: RecyclerView
    private var trip = Trip()
    private var revealedID: Long? = null
    private var currentCountry = ""
    private var imageKey = ""
    private var announceTimeChange = false
    private var aligned = false
    private var deletingCard: Long? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_country_detail); PlannerUi.insets(this)
        val id = intent.getIntExtra("id", 0)
        revealedID = savedInstanceState?.getLong("revealed")?.takeIf { it != 0L }
        currentCountry = savedInstanceState?.getString("country").orEmpty()
        findViewById<MaterialToolbar>(R.id.tripToolbar).apply {
            setNavigationOnClickListener { finish() }
            inflateMenu(R.menu.trip_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.clockAction -> {
                        announceTimeChange = true
                        model.enqueue {
                            val current = model.db.tripDao().findByID(id)
                            model.db.tripDao().updateOne(current.copy(displayZone = if (current.displayZone == "device") "" else "device"))
                        }
                    }
                    R.id.editTripAction -> startActivity(Intent(this@TripDetailActivity, TripAddingActivity::class.java).putExtra("id", id))
                    R.id.shareTripAction -> shareTrip(item)
                }
                true
            }
        }
        recycler = findViewById(R.id.tripDaysContainer)
        recycler.itemAnimator = null
        recycler.layoutManager = CardLayoutManager(this)
        val snap = PagerSnapHelper(); snap.attachToRecyclerView(recycler)
        adapter = DayAdapter(trip, { day -> model.saveCard(day) }, { day ->
            PlannerUi.confirm(this, R.string.delete_card_confirm) {
                deletingCard = day.id
                model.delete(day)
            }
        }, { type -> addCard(type) }, { model.cardDrafts[it] })
        recycler.adapter = adapter
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    recycler.findFocus()?.clearFocus()
                    androidx.core.view.WindowInsetsControllerCompat(window, recycler).hide(androidx.core.view.WindowInsetsCompat.Type.ime())
                }
                if (newState == RecyclerView.SCROLL_STATE_IDLE) snap.findSnapView(recycler.layoutManager)?.let { view ->
                    val position = recycler.getChildAdapterPosition(view)
                    adapter.currentList.getOrNull(position)?.country?.takeIf { it.isNotBlank() }?.let { currentCountry = it }
                }
            }
        })
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                combine(model.db.tripDao().observeAll(), model.db.dayDao().observe(id), model.addedCard) { trips, days, added -> Triple(trips.find { it.id == id }, days, added) }
                    .collect { (loaded, days, added) ->
                        if (loaded == null) { finish(); return@collect }
                        val zoneChanged = trip.timeZone != loaded.timeZone || trip.displayZone != loaded.displayZone
                        trip = loaded.copy(); adapter.trip = trip
                        findViewById<MaterialToolbar>(R.id.tripToolbar).title = trip.name
                        findViewById<TextView>(R.id.tripDates).text = getString(R.string.trip_dates, TravelTime.label(trip.dateBegin), TravelTime.label(trip.dateEnd))
                        val clock = findViewById<MaterialToolbar>(R.id.tripToolbar).menu.findItem(R.id.clockAction)
                        clock.isChecked = trip.displayZone == "device"
                        clock.setIcon(if (clock.isChecked) R.drawable.ic_device_clock else R.drawable.ic_trip_clock)
                        clock.setTitle(if (clock.isChecked) R.string.device_time else R.string.trip_time)
                        val description = getString(if (clock.isChecked) R.string.switch_to_local_time else R.string.switch_to_device_time)
                        androidx.core.view.MenuItemCompat.setContentDescription(clock, description)
                        androidx.core.view.MenuItemCompat.setTooltipText(clock, description)
                        if (announceTimeChange && zoneChanged) {
                            Toast.makeText(this@TripDetailActivity, clock.title, Toast.LENGTH_SHORT).show()
                            announceTimeChange = false
                        }
                        if (currentCountry.isBlank()) currentCountry = days.lastOrNull()?.country?.takeIf { it.isNotBlank() } ?: CountryArtwork.names(trip.countries).firstOrNull().orEmpty()
                        val country = CountryArtwork.names(trip.countries).firstOrNull() ?: trip.name
                        if (imageKey != country) {
                            imageKey = country
                            val image = CountryArtwork.resource(this@TripDetailActivity, country)
                            Glide.with(this@TripDetailActivity).load(if (image != 0) image else R.drawable.main_background)
                                .apply(RequestOptions.bitmapTransform(BlurTransformation(10, 4)))
                                .placeholder(R.drawable.main_background)
                                .error(R.drawable.main_background)
                                .into(findViewById(R.id.imageViewBackground))
                        }
                        val cards = days.filter { it.type != "default" }.map { it.copy() } + Day()
                        val deleted = deletingCard?.takeIf { id -> cards.none { it.id == id } }
                        val deletedPosition = adapter.currentList.indexOfFirst { it.id == deleted }
                        adapter.submitList(cards) {
                            if (!aligned) {
                                recycler.doOnNextLayout {
                                    val manager = recycler.layoutManager!!
                                    snap.findSnapView(manager)?.let { view ->
                                        val distance = snap.calculateDistanceToFinalSnap(manager, view)!!
                                        recycler.scrollBy(distance[0], distance[1])
                                    }
                                    aligned = true
                                }
                                recycler.requestLayout()
                            }
                            if (deleted != null && deleted == deletingCard) {
                                deletingCard = null
                                val lastDay = (cards.lastIndex - 1).coerceAtLeast(0)
                                val position = deletedPosition.coerceIn(0, lastDay)
                                recycler.scrollToPosition(position)
                                recycler.doOnNextLayout {
                                    val manager = recycler.layoutManager ?: return@doOnNextLayout
                                    val card = manager.findViewByPosition(position) ?: return@doOnNextLayout
                                    val distance = snap.calculateDistanceToFinalSnap(manager, card) ?: return@doOnNextLayout
                                    recycler.scrollBy(distance[0], distance[1])
                                }
                            }
                            if (added != null && added != revealedID) {
                                val position = cards.indexOfFirst { it.id == added }
                                if (position >= 0) { recycler.smoothScrollToPosition(position); revealedID = added }
                            }
                            if (zoneChanged) adapter.notifyItemRangeChanged(0, adapter.itemCount, "zone")
                        }
                    }
            }
            launch { model.error.collect { if (it) { deletingCard = null; PlannerUi.error(this@TripDetailActivity); model.error.value = false } } }
        } }
    }
    private fun shareTrip(item: MenuItem) {
        val snapshot = trip.copy()
        if (snapshot.id == 0) return
        val cards = adapter.currentList.filter { it.id != 0L }.map { model.cardDrafts[it.id] ?: it }
        item.isEnabled = false
        lifecycleScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) {
                    TripWorkbook.shareIntent(this@TripDetailActivity, snapshot, cards)
                }
                startActivity(Intent.createChooser(intent, getString(R.string.share_trip)))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlannerUi.error(this@TripDetailActivity, R.string.export_failed)
            } finally {
                item.isEnabled = true
            }
        }
    }
    private fun addCard(type: String) {
        val country = currentCountry.ifBlank { CountryArtwork.names(trip.countries).firstOrNull().orEmpty() }
        if (type == "day") {
            model.addCard(trip, type, country = country)
        } else if (supportFragmentManager.findFragmentByTag("add") == null) {
            AddCardDialog.create(trip, country).show(supportFragmentManager, "add")
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("revealed", revealedID ?: 0); outState.putString("country", currentCountry); super.onSaveInstanceState(outState)
    }
    override fun onKeyboardVisibilityChanged(visible: Boolean) {
        findViewById<View>(R.id.tripDates).visibility = if (visible) View.GONE else View.VISIBLE
        if (::adapter.isInitialized && adapter.keyboardVisible != visible) {
            adapter.keyboardVisible = visible
            adapter.notifyItemRangeChanged(0, adapter.itemCount, "keyboard")
        }
    }
}
