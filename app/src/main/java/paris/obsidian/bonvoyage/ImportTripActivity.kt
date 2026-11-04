package paris.obsidian.bonvoyage

import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ImportTripActivity : AppCompatActivity() {
    private val model by viewModels<PlannerModel>()
    private lateinit var form: PlannerForm
    private lateinit var sheet: TripWorkbookImport.Sheet
    private var countries = emptyList<String>()
    private var preparing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_trip)
        PlannerUi.insets(this)
        findViewById<MaterialToolbar>(R.id.tripToolbar).apply {
            setTitle(R.string.import_excel)
            setNavigationOnClickListener { if (!model.saving.value) finish() }
        }
        val save = findViewById<Button>(R.id.validateButton)
        save.setText(R.string.import_trip)
        save.isEnabled = false
        save.setOnClickListener { importTrip() }

        lifecycleScope.launch {
            try {
                val uri = requireNotNull(intent.data)
                val (read, name) = withContext(Dispatchers.IO) {
                    val workbook = requireNotNull(contentResolver.openInputStream(uri)).use(TripWorkbookImport::read)
                    val filename = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0).orEmpty().substringBeforeLast('.') else ""
                    }.orEmpty()
                    workbook to filename
                }
                sheet = read
                countries = savedInstanceState?.getStringArrayList("countries") ?: suggestedCountries(read.title)
                form = PlannerForm(findViewById<LinearLayout>(R.id.tripForm), savedInstanceState?.getBundle("form"))
                form.field("name", R.string.trip_name, read.title.ifBlank { name })
                form.clearable("name", R.string.clear_name)
                form.field("countries", R.string.countries_label, countryNames())
                form.picker("countries") {
                    CountryPicker.show(this@ImportTripActivity, countries.toSet()) {
                        countries = it
                        form.bind("countries", countryNames())
                    }
                }
                if (sheet.needsYear) {
                    form.field("year", R.string.import_year, sheet.suggestedYear.toString()).inputType = InputType.TYPE_CLASS_NUMBER
                }
                save.isEnabled = !model.saving.value
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlannerUi.error(this@ImportTripActivity, R.string.import_failed)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.savedTrip.collect { id ->
                        if (id != null) {
                            val saved = withContext(Dispatchers.IO) { model.db.tripDao().findByID(id) }
                            setResult(RESULT_OK, Intent().putExtra(SHOW_PAST, TravelTime.past(saved.dateEnd, saved.timeZone)))
                            startActivity(Intent(this@ImportTripActivity, TripDetailActivity::class.java).putExtra("id", id))
                            finish()
                        }
                    }
                }
                launch {
                    model.saving.collect { save.isEnabled = !it && !preparing && ::form.isInitialized }
                }
                launch {
                    model.error.collect { failed ->
                        if (failed) {
                            PlannerUi.error(this@ImportTripActivity, R.string.import_failed)
                            model.error.value = false
                        }
                    }
                }
            }
        }
    }

    private fun importTrip() {
        if (!::form.isInitialized || preparing || model.saving.value) return
        if (countries.isEmpty()) {
            PlannerUi.error(this, R.string.invalid_name)
            return
        }
        val year = if (sheet.needsYear) form.value("year").toIntOrNull() else sheet.suggestedYear
        if (year == null || year !in 1..9999) {
            PlannerUi.error(this, R.string.import_date_failed)
            return
        }
        val title = form.value("name").ifBlank { countryNames() }
        val selected = countries.toList()
        val save = findViewById<Button>(R.id.validateButton)
        preparing = true
        save.isEnabled = false
        lifecycleScope.launch {
            try {
                val itinerary = withContext(Dispatchers.IO) {
                    TripWorkbookImport.itinerary(this@ImportTripActivity, sheet, title, selected, year)
                }
                model.importTrip(itinerary)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PlannerUi.error(this@ImportTripActivity, R.string.import_date_failed)
            } finally {
                preparing = false
                save.isEnabled = !model.saving.value
            }
        }
    }

    private fun countryNames() = countries.joinToString(getString(R.string.country_separator))

    private fun suggestedCountries(title: String): List<String> =
        resources.getStringArray(R.array.country_array).filter { name ->
            val boundary = """[\p{L}\p{N}]"""
            Regex("(?<!" + boundary + ")" + Regex.escape(name) + "(?!" + boundary + ")", RegexOption.IGNORE_CASE)
                .containsMatchIn(title)
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::form.isInitialized) outState.putBundle("form", form.snapshot())
        outState.putStringArrayList("countries", ArrayList(countries))
    }

    companion object {
        const val SHOW_PAST = "show_past"
    }
}
