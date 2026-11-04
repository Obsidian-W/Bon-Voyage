package paris.obsidian.bonvoyage

import android.content.Context

object CountryPicker {
    fun show(context: Context, selected: Set<String>, result: (List<String>) -> Unit) =
        SelectionPicker.show(context, R.string.choose_countries, R.string.search_label,
            context.resources.getStringArray(R.array.country_array).toList(), selected, true, result)
}
