package paris.obsidian.bonvoyage

import android.content.Context
import android.view.LayoutInflater
import android.widget.*
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Search, selection, sizing, and spacing shared by country and time-zone pickers. */
object SelectionPicker {
    fun show(context: Context, title: Int, searchHint: Int, options: List<String>, selected: Set<String>,
             multiple: Boolean, result: (List<String>) -> Unit) {
        val content = LayoutInflater.from(context).inflate(R.layout.view_selection_picker, null)
        val search = content.findViewById<EditText>(R.id.selectionSearch).apply { setHint(searchHint) }
        val list = content.findViewById<ListView>(R.id.selectionList)
        list.layoutParams.height = (context.resources.displayMetrics.heightPixels / 3)
            .coerceAtMost(context.resources.getDimensionPixelSize(R.dimen.picker_height))
        list.choiceMode = if (multiple) ListView.CHOICE_MODE_MULTIPLE else ListView.CHOICE_MODE_SINGLE
        val draft = selected.toMutableSet()
        var visible = options
        fun refresh() {
            visible = options.filter { it.contains(search.text.toString(), true) }
            list.adapter = ArrayAdapter(context, if (multiple) android.R.layout.simple_list_item_multiple_choice else android.R.layout.simple_list_item_single_choice, visible)
            visible.forEachIndexed { index, name -> list.setItemChecked(index, name in draft) }
        }
        refresh(); search.addTextChangedListener { refresh() }
        val builder = MaterialAlertDialogBuilder(context).setTitle(title).setView(content).setNegativeButton(android.R.string.cancel, null)
        if (multiple) builder.setPositiveButton(android.R.string.ok) { _, _ -> result(options.filter { it in draft }) }
        val dialog = builder.create()
        list.setOnItemClickListener { _, _, position, _ ->
            if (multiple) { if (!draft.add(visible[position])) draft.remove(visible[position]) }
            else { result(listOf(visible[position])); dialog.dismiss() }
        }
        PlannerUi.showDialog(context, dialog)
    }
}
