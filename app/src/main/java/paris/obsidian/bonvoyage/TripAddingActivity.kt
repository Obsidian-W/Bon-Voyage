package paris.obsidian.bonvoyage

import android.os.Bundle
import android.content.Intent
import com.google.android.material.appbar.MaterialToolbar
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import paris.obsidian.bonvoyage.trips.Trip
import java.time.*

class TripAddingActivity : AppCompatActivity(), KeyboardAware {
    private val model by viewModels<PlannerModel>()
    private var trip = Trip()
    private var selected = emptyList<String>()
    private var initialCountries = emptyList<String>()
    private lateinit var form: PlannerForm
    private lateinit var countryField: com.google.android.material.textfield.TextInputEditText
    private var selectedZone = java.time.ZoneId.systemDefault().id
    private var dayCount = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_trip); PlannerUi.insets(this)
        findViewById<MaterialToolbar>(R.id.tripToolbar).setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<Button>(R.id.validateButton).apply { isEnabled = false; setOnClickListener { save() } }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (model.saving.value) return
                if (::form.isInitialized && (form.dirty() || selected != initialCountries))
                    MaterialAlertDialogBuilder(this@TripAddingActivity).setTitle(R.string.discard_changes)
                        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.discard_action) { _, _ -> finish() }.show()
                else finish()
            }
        })
        lifecycleScope.launch {
            val id = intent.getIntExtra("id", 0)
            trip = if (id == 0) Trip(dateBegin = TravelTime.dayFormat.format(LocalDate.now()), dateEnd = TravelTime.dayFormat.format(LocalDate.now()), timeZone = ZoneId.systemDefault().id)
                else withContext(Dispatchers.IO) { model.db.tripDao().findByID(id) }
            initialCountries = CountryArtwork.names(trip.countries.ifBlank { trip.name })
            selected = savedInstanceState?.getStringArrayList("countries") ?: initialCountries
            findViewById<MaterialToolbar>(R.id.tripToolbar).setTitle(if (id == 0) R.string.addTitle else R.string.edit_trip)
            dayCount = if (id == 0) 0 else withContext(Dispatchers.IO) { model.db.dayDao().getAll(id).count { it.type == "day" } }
            selectedZone = savedInstanceState?.getString("zone") ?: trip.timeZone
            val parent = findViewById<LinearLayout>(R.id.tripForm)
            form = PlannerForm(parent, savedInstanceState?.getBundle("form"))
            countryField = form.field("countries", R.string.countries_label, selected.joinToString(getString(R.string.country_separator)))
            form.picker("countries") {
                CountryPicker.show(this@TripAddingActivity, selected.toSet()) {
                    selected = it
                    updateCountries()
                    if (selected.isNotEmpty()) form.fillIfBlank("name", selected.joinToString(getString(R.string.country_separator)))
                }
            }
            layoutInflater.inflate(R.layout.view_country_preview, parent, true)
            form.field("name", R.string.trip_name, trip.name)
            form.clearable("name", R.string.clear_name)
            form.date("start", R.string.start_label, TravelTime.label(trip.dateBegin))
            if (dayCount > 0) {
                layoutInflater.inflate(R.layout.view_duration, parent, true)
                findViewById<TextView>(R.id.tripDuration).text = resources.getQuantityString(R.plurals.trip_day_count, dayCount, dayCount)
            }
            form.select("zone", R.string.time_zone, selectedZone, { CountryZones.zones(selected) }, { CountryZones.label(this@TripAddingActivity, it) }) { selectedZone = it }
            form.field("notes", R.string.trip_description, trip.notes, multiline = true)
            updateCountries()
            findViewById<Button>(R.id.validateButton).isEnabled = !model.saving.value
            savedInstanceState?.getInt("scrollY")?.let { y -> findViewById<ScrollView>(R.id.tripFormScroll).post { findViewById<ScrollView>(R.id.tripFormScroll).scrollTo(0, y) } }
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch { model.savedTrip.collect { saved -> if (saved != null) {
                val savedTrip = withContext(Dispatchers.IO) { model.db.tripDao().findByID(saved) }
                setResult(RESULT_OK, Intent().putExtra(ImportTripActivity.SHOW_PAST,
                    TravelTime.past(savedTrip.dateEnd, savedTrip.timeZone)))
                if (intent.getIntExtra("id", 0) == 0) startActivity(Intent(this@TripAddingActivity, TripDetailActivity::class.java).putExtra("id", saved))
                finish()
            } } }
            launch { model.saving.collect { findViewById<Button>(R.id.validateButton).isEnabled = !it && ::form.isInitialized } }
            launch { model.error.collect { if (it) { PlannerUi.error(this@TripAddingActivity); model.error.value = false } } }
        } }
    }
    private fun updateCountries() {
        form.bind("countries", selected.joinToString(getString(R.string.country_separator)))
        selectedZone = CountryZones.choose(selected, selectedZone)
        form.bind("zone", CountryZones.label(this, selectedZone), selectedZone)
        form.show("zone", CountryZones.zones(selected).size > 1)
        findViewById<android.widget.FrameLayout>(R.id.imagePreview).apply {
            visibility = if (selected.isEmpty()) View.GONE else View.VISIBLE
            CountryArtwork.bind(this, selected)
        }
    }
    private fun save() {
        if (!::form.isInitialized || model.saving.value) return
        if (selected.isEmpty()) { PlannerUi.error(this, R.string.invalid_name); return }
        try {
            val start = TravelTime.date(form.value("start")); val end = start.plusDays((dayCount - 1).coerceAtLeast(0).toLong())
            if (end.isBefore(start)) { PlannerUi.error(this, R.string.invalid_range); return }
            ZoneId.of(selectedZone)
            val snapshot = trip.copy(name = form.value("name").ifBlank { selected.joinToString(getString(R.string.country_separator)) },
                dateBegin = TravelTime.dayFormat.format(start), dateEnd = TravelTime.dayFormat.format(end),
                countries = selected.joinToString("|"), timeZone = selectedZone, notes = form.value("notes"),
                image = CountryArtwork.resource(this, selected.first()))
            model.saveTrip(snapshot)
        } catch (_: Exception) { PlannerUi.error(this, R.string.invalid_details) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::form.isInitialized) outState.putBundle("form", form.snapshot())
        outState.putStringArrayList("countries", ArrayList(selected))
        outState.putString("zone", selectedZone)
        outState.putInt("scrollY", findViewById<ScrollView>(R.id.tripFormScroll).scrollY)
        super.onSaveInstanceState(outState)
    }
    override fun onKeyboardVisibilityChanged(visible: Boolean) {
        findViewById<View>(R.id.imagePreview)?.visibility = if (visible || selected.isEmpty()) View.GONE else View.VISIBLE
    }
}
