package paris.obsidian.bonvoyage

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.core.widget.addTextChangedListener
import com.google.android.material.textfield.*
import java.time.*

/** Resource-styled form fields shared by trip, day, and transport editors. */
class PlannerForm(val parent: LinearLayout, private val restored: Bundle? = null) {
    var updating = false
    private val context = parent.context
    private val fields = linkedMapOf<String, TextInputEditText>()
    private val checks = linkedMapOf<String, CheckBox>()
    private val initial = linkedMapOf<String, String>()
    private val initialChecks = linkedMapOf<String, Boolean>()
    private val choices = linkedMapOf<String, () -> List<String>>()
    private fun box(edit: View) = edit.parent.parent as TextInputLayout
    fun field(key: String, label: Int, value: String = "", multiline: Boolean = false): TextInputEditText {
        val input = LayoutInflater.from(context).inflate(R.layout.view_form_field, parent, false) as TextInputLayout
        input.setHint(label)
        val edit = input.editText as TextInputEditText
        edit.id = context.resources.getIdentifier("field_$key", "id", context.packageName)
        initial[key] = value
        edit.setText(restored?.getString(key) ?: value)
        edit.inputType = android.text.InputType.TYPE_CLASS_TEXT or if (multiline)
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        if (multiline) {
            edit.minLines = 3
            edit.gravity = Gravity.TOP or Gravity.START
            input.isExpandedHintEnabled = false
        }
        input.setStartIconDrawable(when (key) {
            "country", "countries", "destinationCountry" -> R.drawable.ic_country
            "hotel" -> R.drawable.ic_hotel
            "location", "destination" -> R.drawable.ic_map
            "booking" -> R.drawable.ic_link
            "notes" -> R.drawable.ic_notes
            "start", "end", "date", "departure", "arrival" -> R.drawable.ic_calendar
            "zone", "departureZone", "arrivalZone" -> R.drawable.ic_time
            else -> R.drawable.ic_edit
        })
        if (multiline) input.startIconDrawable = null
        edit.setOnFocusChangeListener { _, focused -> if (focused) edit.post { PlannerUi.reveal(edit) } }
        fields[key] = edit; parent.addView(input)
        return edit
    }
    fun date(key: String, label: Int, value: String) = field(key, label, value).apply {
        isFocusable = false; setOnClickListener { PlannerUi.datePicker(context, this) }
    }
    fun time(key: String, label: Int, value: String, defaultDate: () -> LocalDate = { LocalDate.now() }) = field(key, label, value).apply {
        isFocusable = false
        val input = box(this)
        input.endIconMode = TextInputLayout.END_ICON_CUSTOM
        input.setEndIconDrawable(R.drawable.ic_close)
        input.setEndIconContentDescription(R.string.clear_time)
        input.isEndIconVisible = !text.isNullOrEmpty()
        input.setEndIconOnClickListener { setText("") }
        addTextChangedListener { input.isEndIconVisible = !it.isNullOrEmpty() }
        setOnClickListener {
            val current = runCatching { TravelTime.localTime(text.toString()) }.getOrDefault(defaultDate().atTime(12, 0))
            PlannerUi.showDialog(context, android.app.DatePickerDialog(context, { _, year, month, day ->
                PlannerUi.showDialog(context, android.app.TimePickerDialog(context, { _, hour, minute ->
                    setText(TravelTime.timeFormat.format(LocalDateTime.of(year, month + 1, day, hour, minute)))
                }, current.hour, current.minute, true))
            }, current.year, current.monthValue - 1, current.dayOfMonth))
        }
    }
    fun check(key: String, label: Int, value: Boolean): CheckBox = (LayoutInflater.from(context).inflate(R.layout.view_form_checkbox, parent, false) as CheckBox).also {
        it.id = context.resources.getIdentifier("field_$key", "id", context.packageName)
        it.setText(label); it.isChecked = restored?.getBoolean(key) ?: value
        initialChecks[key] = value; checks[key] = it; parent.addView(it)
    }
    fun select(key: String, label: Int, selected: String, options: () -> List<String>, display: (String) -> String = { it }, changed: (String) -> Unit): TextInputEditText {
        val selectedValue = restored?.getString("$key:selected") ?: selected
        val edit = field(key, label, display(selected)).apply { isFocusable = false; tag = selectedValue; setText(display(selectedValue)) }
        choices[key] = options
        val input = box(edit)
        input.endIconMode = TextInputLayout.END_ICON_CUSTOM
        input.setEndIconDrawable(R.drawable.ic_expand_more)
        input.setEndIconContentDescription(label)
        input.isEndIconVisible = options().size > 1
        input.setEndIconOnClickListener { edit.performClick() }
        edit.setOnClickListener {
            val values = options()
            if (values.size > 1) SelectionPicker.show(context, label, R.string.search_label, values.map(display), setOf(edit.text.toString()), false) { selection ->
                val value = values[values.map(display).indexOf(selection.single())]
                edit.tag = value; edit.setText(display(value)); changed(value)
            }
        }
        return edit
    }
    fun picker(key: String, action: () -> Unit) {
        val edit = fields.getValue(key)
        edit.isFocusable = false; edit.setOnClickListener { action() }
        box(edit).apply {
            endIconMode = TextInputLayout.END_ICON_CUSTOM
            setEndIconDrawable(R.drawable.ic_expand_more)
            setEndIconContentDescription(R.string.choose_countries)
            setEndIconOnClickListener { action() }
        }
    }
    fun clearable(key: String, description: Int) {
        val edit = fields.getValue(key)
        val input = box(edit)
        input.endIconMode = TextInputLayout.END_ICON_CUSTOM
        input.setEndIconDrawable(R.drawable.ic_close)
        input.setEndIconContentDescription(description)
        input.setEndIconOnClickListener { edit.text?.clear() }
        input.isEndIconVisible = !edit.text.isNullOrEmpty()
        edit.addTextChangedListener { input.isEndIconVisible = !it.isNullOrEmpty() }
    }

    fun fillIfBlank(key: String, value: String) {
        val edit = fields.getValue(key)
        if (edit.text.isNullOrBlank()) edit.setText(value)
    }
    fun bind(key: String, value: String, selection: String? = null) {
        val edit = fields.getValue(key)
        if (!edit.hasFocus() && edit.text.toString() != value) { updating = true; edit.setText(value); updating = false }
        if (selection != null) edit.tag = selection
        choices[key]?.let { box(edit).isEndIconVisible = it().size > 1 }
    }
    fun show(key: String, visible: Boolean) { box(fields.getValue(key)).visibility = if (visible) View.VISIBLE else View.GONE }
    fun label(key: String, label: Int) { box(fields.getValue(key)).setHint(label) }
    fun watch(key: String, changed: (String) -> Unit) { fields.getValue(key).addTextChangedListener { if (!updating) changed(it.toString()) } }
    fun value(key: String) = (fields.getValue(key).tag as? String) ?: fields.getValue(key).text.toString().trim()
    fun setChecked(key: String, value: Boolean) { checks.getValue(key).isChecked = value }
    fun snapshot() = Bundle().apply {
        fields.forEach { (key, edit) -> putString(key, edit.text.toString()); (edit.tag as? String)?.let { putString("$key:selected", it) } }
        checks.forEach { (key, check) -> putBoolean(key, check.isChecked) }
    }
    fun dirty() = fields.any { (key, edit) -> edit.text.toString() != initial[key] } || checks.any { (key, check) -> check.isChecked != initialChecks[key] }
}
