package paris.obsidian.bonvoyage

import androidx.fragment.app.DialogFragment

/** The dialog resizes for its keyboard; the activity behind it keeps its geometry. */
abstract class PlannerDialogFragment : DialogFragment() {
    private var releaseBackground: (() -> Unit)? = null
    override fun onStart() {
        super.onStart()
        releaseBackground = PlannerUi.freezeDialogBackground(requireActivity())
        dialog?.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    override fun onStop() {
        releaseBackground?.invoke(); releaseBackground = null
        super.onStop()
    }
}
