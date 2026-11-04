package paris.obsidian.bonvoyage

import android.content.Intent
import android.os.Bundle
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.core.view.isVisible
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.appbar.MaterialToolbar
import java.text.Collator
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch
import paris.obsidian.bonvoyage.trips.*

class MainActivity : AppCompatActivity() {
    private val model by viewModels<PlannerModel>()
    private lateinit var tripAdapter: TripAdapter
    private var trips = emptyList<Trip>()
    private var past = false
    private var pendingImportedTab: Boolean? = null
    private var query = ""
    private var sortByName = false
    private var animateTab = false
    private var resetScroll = false
    private lateinit var addTile: Trip
    private val deletionAnimator = DefaultItemAnimator().apply { supportsChangeAnimations = false }
    private val createTrip = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            query = ""
            findViewById<SearchView>(R.id.searchView).setQuery("", false)
            result.data?.takeIf { it.hasExtra(ImportTripActivity.SHOW_PAST) }?.let {
                pendingImportedTab = it.getBooleanExtra(ImportTripActivity.SHOW_PAST, false)
                render()
            }
        }
    }
    private val chooseWorkbook = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            createTrip.launch(Intent(this, ImportTripActivity::class.java).setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main); PlannerUi.insets(this)
        addTile = Trip(name = getString(R.string.addTrip), dateBegin = "", dateEnd = "")
        past = savedInstanceState?.getBoolean("past") ?: false
        pendingImportedTab = savedInstanceState?.takeIf { it.containsKey(PENDING_IMPORTED_TAB) }?.getBoolean(PENDING_IMPORTED_TAB)
        query = savedInstanceState?.getString("query").orEmpty()
        sortByName = getPreferences(MODE_PRIVATE).getBoolean(SORT_BY_NAME, false)
        tripAdapter = TripAdapter({ trip ->
            if (trip.id == 0) createTrip.launch(Intent(this, TripAddingActivity::class.java))
            else startActivity(Intent(this, TripDetailActivity::class.java).putExtra("id", trip.id))
        }, ::onTripLongClick)
        findViewById<MaterialToolbar>(R.id.homeToolbar).apply {
            overflowIcon = overflowIcon?.mutate()?.apply {
                setTint(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.planner_on_photo))
            }
            setOnMenuItemClickListener { item ->
                startActivity(Intent(this@MainActivity, AppInfoActivity::class.java)
                    .putExtra(AppInfoActivity.SHOW_ABOUT, item.itemId == R.id.aboutAction))
                true
            }
        }
        findViewById<MaterialButtonToggleGroup>(R.id.tripSort).apply {
            check(if (sortByName) R.id.sortByName else R.id.sortByDate)
            addOnButtonCheckedListener { _, id, checked ->
                if (checked) {
                    sortByName = id == R.id.sortByName
                    getPreferences(MODE_PRIVATE).edit().putBoolean(SORT_BY_NAME, sortByName).apply()
                    resetScroll = true
                    render()
                }
            }
        }
        bindSort()
        val recycler = findViewById<RecyclerView>(R.id.countryRecycler)
        recycler.itemAnimator = null
        val minWidth = resources.getDimensionPixelSize(R.dimen.tile_min_width)
        recycler.layoutManager = GridLayoutManager(this, (resources.displayMetrics.widthPixels / minWidth).coerceAtLeast(1))
        recycler.adapter = tripAdapter
        findViewById<TabLayout>(R.id.tab_layout).apply {
            getTabAt(if (past) 1 else 0)?.select()
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    val selectedPast = tab.position == 1
                    if (selectedPast == past) return
                    past = selectedPast
                    pendingImportedTab = null
                    animateTab = true
                    render()
                }
                override fun onTabUnselected(tab: TabLayout.Tab) {}
                override fun onTabReselected(tab: TabLayout.Tab) {}
            })
        }
        findViewById<SearchView>(R.id.searchView).apply {
            setQuery(this@MainActivity.query, false)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(text: String?) = true
                override fun onQueryTextChange(text: String?): Boolean { this@MainActivity.query = text.orEmpty(); render(); return true }
            })
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                model.db.tripDao().observeAll().collect { updated ->
                    val remaining = updated.mapTo(HashSet()) { it.id }
                    val removedVisible = tripAdapter.currentList.any { it.id != 0 && it.id !in remaining }
                    trips = updated
                    render(removedVisible)
                }
            }
            launch { model.error.collect { if (it) { PlannerUi.error(this@MainActivity); model.error.value = false } } }
        } }
    }
    private fun render(removedVisible: Boolean = false) {
        val (history, active) = trips.partition { trip ->
            runCatching { TravelTime.past(trip.dateEnd, trip.timeZone) }.getOrDefault(false)
        }
        val hasHistory = history.isNotEmpty()
        val importedTab = pendingImportedTab
        val nextPast = if (importedTab != null && (!importedTab || hasHistory)) {
            pendingImportedTab = null
            importedTab
        } else {
            past && hasHistory
        }
        if (past != nextPast) {
            resetScroll = true
            animateTab = true
        }
        past = nextPast
        val tabTrips = if (past) history else active
        val showControls = tabTrips.size >= resources.getInteger(R.integer.trip_controls_threshold)
        val filter = if (showControls) query.trim() else ""
        val filtered = tabTrips.filter { it.name.contains(filter, true) || it.countries.contains(filter, true) }
        val ordered = if (sortByName) {
            val names = Collator.getInstance(resources.configuration.locales[0]).apply { strength = Collator.PRIMARY }
            filtered.sortedWith(compareBy<Trip, String>(names) { it.name.trim() }.thenBy { it.id })
        } else {
            val dateOrder = if (past) nullsLast(reverseOrder<java.time.LocalDate>()) else nullsLast(naturalOrder<java.time.LocalDate>())
            filtered.sortedWith(compareBy(dateOrder) { trip: Trip -> runCatching { TravelTime.date(trip.dateBegin) }.getOrNull() })
        }
        val recycler = findViewById<RecyclerView>(R.id.countryRecycler)
        val empty = findViewById<TextView>(R.id.emptyTrips)
        val tabs = findViewById<TabLayout>(R.id.tab_layout)
        val controls = findViewById<View>(R.id.tripControls)
        val content = recycler.parent as View
        val duration = resources.getInteger(R.integer.trip_tab_fade_ms).toLong()
        val animator = if (removedVisible && !animateTab) deletionAnimator else null
        if (recycler.itemAnimator !== animator) recycler.itemAnimator = animator
        tripAdapter.submitList((if (past) emptyList() else listOf(addTile)) + ordered.map { it.copy() }) {
            if (removedVisible && (tabs.isVisible != hasHistory || controls.isVisible != showControls)) {
                TransitionManager.beginDelayedTransition(findViewById<ViewGroup>(R.id.safeContent),
                    ChangeBounds().addTarget(content).setDuration(duration))
            }
            val selectedTab = if (past) 1 else 0
            if (tabs.selectedTabPosition != selectedTab) tabs.getTabAt(selectedTab)?.select()
            tabs.isVisible = hasHistory
            controls.isVisible = showControls
            bindSort()
            empty.visibility = if (ordered.isEmpty() && past) View.VISIBLE else View.GONE
            if (resetScroll) {
                resetScroll = false
                if (tripAdapter.itemCount > 0) recycler.scrollToPosition(0)
            }
            if (animateTab) {
                animateTab = false
                recycler.scrollToPosition(0)
                content.animate().cancel()
                content.alpha = 0f
                content.animate().alpha(1f).setDuration(duration).start()
            }
        }
    }

    private fun bindSort() {
        val date = findViewById<View>(R.id.sortByDate)
        date.contentDescription = getString(dateSortLabel())
        androidx.appcompat.widget.TooltipCompat.setTooltipText(date, date.contentDescription)
    }

    private fun dateSortLabel() = if (past) R.string.sort_date_past else R.string.sort_date_upcoming

    private fun onTripLongClick(anchor: View, trip: Trip) {
        if (trip.id == 0) {
            chooseWorkbook.launch(arrayOf(TripWorkbook.MIME))
            return
        }
        PopupMenu(this, anchor).apply {
            inflate(R.menu.trip_tile_actions)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.editTileAction -> startActivity(Intent(this@MainActivity, TripAddingActivity::class.java).putExtra("id", trip.id))
                    R.id.deleteTileAction -> PlannerUi.confirm(this@MainActivity, R.string.delete_trip_confirm) {
                        model.enqueue {
                            model.db.runInTransaction {
                                model.db.dayDao().removeForTrip(trip.id)
                                model.db.tripDao().removeOne(trip)
                            }
                        }
                    }
                }
                true
            }
            show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("past", past)
        outState.putString("query", query)
        pendingImportedTab?.let { outState.putBoolean(PENDING_IMPORTED_TAB, it) }
        super.onSaveInstanceState(outState)
    }
    private companion object {
        const val SORT_BY_NAME = "sort_by_name"
        const val PENDING_IMPORTED_TAB = "pending_imported_tab"
    }
}
