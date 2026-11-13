package paris.obsidian.bonvoyage

import android.app.Dialog
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip

/** Hotel and transport fields are edited together, without filling the itinerary with forms. */
class CardDetailsDialog : PlannerDialogFragment() {
    private lateinit var form: PlannerForm
    @Suppress("DEPRECATION")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val day = requireArguments().getSerializable("day") as Day
        val trip = requireArguments().getSerializable("trip") as Trip
        val hotel = day.type == "day"
        val full = requireArguments().getBoolean("full")
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val space = resources.getDimensionPixelSize(R.dimen.space_24); setPadding(space, space, space, space)
        }
        form = PlannerForm(content, savedInstanceState?.getBundle("form"))
        if (full) {
            form.field("name", if (hotel) R.string.day_name else R.string.transport_name, day.title)
            if (hotel) form.select("country", R.string.country_label, day.country, { CountryArtwork.names(trip.countries) }) { }
        }
        var transport: TransportForm? = null
        if (hotel) {
            form.field("hotel", R.string.hotel_label, day.hotel)
            form.field("location", R.string.hotel_address, day.location)
            form.field("booking", R.string.booking_label, day.bookingUrl)
        } else {
            transport = TransportForm(form, day, trip)
        }
        val title = if (full) R.string.edit_details else if (hotel) R.string.edit_hotel else R.string.edit_transport
        val dialog = PlannerUi.formDialog(requireContext()).setTitle(title)
            .setView(ScrollView(requireContext()).apply { addView(content) })
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.save_action, null).create()
        dialog.setOnShowListener { dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            fun save(updated: Day) {
                val named = if (full) updated.copy(title = form.value("name")) else updated
                val result = if (full && hotel) named.copy(country = form.value("country")) else named
                ViewModelProvider(requireActivity())[PlannerModel::class.java].saveCard(result)
                dismiss()
            }
            if (hotel) {
                try {
                    val entered = form.value("booking")
                    val booking = if (entered.isBlank() || entered == day.bookingUrl) entered else PlannerUi.booking(entered)
                    save(day.copy(hotel = form.value("hotel"), location = form.value("location"), bookingUrl = booking))
                } catch (_: Exception) { PlannerUi.error(requireContext(), R.string.invalid_details) }
            } else {
                transport!!.save(::save)
            }
        } }
        return dialog
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBundle("form", form.snapshot()); super.onSaveInstanceState(outState) }
    companion object {
        fun show(context: Context, day: Day, trip: Trip, full: Boolean = false) {
            val manager = (context as AppCompatActivity).supportFragmentManager
            if (manager.findFragmentByTag("cardDetails") != null || manager.isStateSaved) return
            CardDetailsDialog().apply {
                arguments = Bundle().apply {
                    putSerializable("day", day.copy())
                    putSerializable("trip", trip.copy())
                    putBoolean("full", full)
                }
            }.show(manager, "cardDetails")
        }
    }
}
