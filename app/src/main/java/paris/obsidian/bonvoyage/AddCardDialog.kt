package paris.obsidian.bonvoyage

import android.app.Dialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip

class AddCardDialog : PlannerDialogFragment() {
    private lateinit var form: PlannerForm
    private var selectedType = "train"
    @Suppress("DEPRECATION")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val trip = requireArguments().getSerializable("trip") as Trip
        selectedType = savedInstanceState?.getString("mode") ?: TransportMode.types.first()
        val country = requireArguments().getString("country").orEmpty()
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val space = resources.getDimensionPixelSize(R.dimen.space_24)
            setPadding(space, paddingTop, space, space)
        }
        val modes = layoutInflater.inflate(R.layout.view_transport_modes, content, false) as ChipGroup
        val horizontal = resources.getDimensionPixelSize(R.dimen.space_24)
        val top = resources.getDimensionPixelSize(R.dimen.space_16)
        modes.setPadding(horizontal, top, horizontal, modes.paddingBottom)
        form = PlannerForm(content, savedInstanceState?.getBundle("form"))
        form.field("name", R.string.transport_name)
        val zone = CountryZones.choose(listOf(country), trip.timeZone)
        val transport = TransportForm(form,
            Day(type = selectedType, date = trip.dateEnd, country = country, departureZone = zone, arrivalZone = zone), trip)
        val ids = listOf(R.id.modeTrain, R.id.modeBoat, R.id.modeBus, R.id.modeFlight, R.id.modeCar, R.id.modeOther)
        modes.check(ids[TransportMode.types.indexOf(selectedType)])
        modes.setOnCheckedStateChangeListener { _, checked ->
            checked.singleOrNull()?.let { id ->
                selectedType = TransportMode.types[ids.indexOf(id)]
                transport.type = selectedType
            }
        }
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(modes)
            addView(ScrollView(requireContext()).apply { addView(content) })
        }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.add_transport_dialog)
            .setView(body).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.add_card, null).create()
        dialog.setOnShowListener { dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val model = ViewModelProvider(requireActivity())[PlannerModel::class.java]
            transport.save { details ->
                model.addCard(trip, selectedType, form.value("name").ifBlank { getString(TransportMode.label(selectedType)) }, country, details)
                dismiss()
            }
        } }
        return dialog
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBundle("form", form.snapshot()); outState.putString("mode", selectedType); super.onSaveInstanceState(outState)
    }
    companion object {
        fun create(trip: Trip, country: String) = AddCardDialog().apply {
            arguments = Bundle().apply { putSerializable("trip", trip.copy()); putString("country", country) }
        }
    }
}
