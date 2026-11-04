package paris.obsidian.bonvoyage

import android.app.Activity
import android.app.DatePickerDialog
import android.content.*
import android.net.Uri
import android.view.View
import android.widget.*
import androidx.core.view.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URI
import java.net.URLEncoder
import java.time.*

object PlannerUi {
    fun durationLabel(context: Context, departure: String, arrival: String): String {
        val minutes = TravelTime.duration(departure, arrival).toMinutes()
        return android.icu.text.MeasureFormat.getInstance(context.resources.configuration.locales[0], android.icu.text.MeasureFormat.FormatWidth.NARROW)
            .formatMeasures(android.icu.util.Measure(minutes / 60, android.icu.util.MeasureUnit.HOUR),
                android.icu.util.Measure(minutes % 60, android.icu.util.MeasureUnit.MINUTE))
    }
    private data class DialogBackground(val softInputMode: Int, var dialogs: Int = 1)
    private val dialogBackgrounds = java.util.WeakHashMap<Activity, DialogBackground>()
    fun freezeDialogBackground(activity: Activity): () -> Unit {
        val current = dialogBackgrounds[activity]
        if (current != null) current.dialogs++ else {
            val mode = activity.window.attributes.softInputMode
            dialogBackgrounds[activity] = DialogBackground(mode)
            activity.window.setSoftInputMode((mode and android.view.WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }
        return {
            dialogBackgrounds[activity]?.let { state -> if (--state.dialogs == 0) {
                dialogBackgrounds.remove(activity)
                activity.window.setSoftInputMode(state.softInputMode)
                ViewCompat.requestApplyInsets(activity.window.decorView)
            } }
        }
    }
    fun showDialog(context: Context, dialog: android.app.Dialog) {
        var owner = context
        while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
        val release = (owner as? Activity)?.let(::freezeDialogBackground)
        dialog.setOnDismissListener { release?.invoke() }
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        try { dialog.show() } catch (error: Exception) { release?.invoke(); throw error }
    }
    fun insets(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val host = activity.findViewById<android.widget.FrameLayout>(android.R.id.content)
        val content = host.getChildAt(0)
        val safe = host.findViewById<View>(R.id.safeContent) ?: content
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        val scrim = androidx.core.content.ContextCompat.getColor(activity, R.color.planner_scrim)
        val statusGuard = View(activity).apply {
            setBackgroundColor(androidx.core.content.ContextCompat.getColor(activity, R.color.planner_top_bar))
        }
        val navigationGuard = View(activity).apply { setBackgroundColor(scrim) }
        host.addView(statusGuard, FrameLayout.LayoutParams(-1, 0, android.view.Gravity.TOP))
        host.addView(navigationGuard, FrameLayout.LayoutParams(-1, 0, android.view.Gravity.BOTTOM))
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            activity.window.isStatusBarContrastEnforced = false
            activity.window.isNavigationBarContrastEnforced = false
        }
        if (android.os.Build.VERSION.SDK_INT < 35) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = androidx.core.content.ContextCompat.getColor(activity, android.R.color.transparent)
            @Suppress("DEPRECATION")
            activity.window.navigationBarColor = androidx.core.content.ContextCompat.getColor(activity, android.R.color.transparent)
        }
        val left = safe.paddingLeft; val top = safe.paddingTop; val right = safe.paddingRight; val bottom = safe.paddingBottom
        var keyboardVisible = false
        var appliedInsets = false
        ViewCompat.setOnApplyWindowInsetsListener(content) { _, insets ->
            val backgroundFrozen = dialogBackgrounds.containsKey(activity)
            if (backgroundFrozen && appliedInsets) return@setOnApplyWindowInsetsListener insets
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or if (backgroundFrozen) 0 else WindowInsetsCompat.Type.ime())
            safe.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom + bars.bottom)
            appliedInsets = true
            val visible = !backgroundFrozen && insets.isVisible(WindowInsetsCompat.Type.ime())
            if (visible != keyboardVisible) {
                keyboardVisible = visible
                (activity as? KeyboardAware)?.onKeyboardVisibilityChanged(visible)
            }
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            statusGuard.layoutParams = FrameLayout.LayoutParams(-1, systemBars.top, android.view.Gravity.TOP)
            navigationGuard.layoutParams = FrameLayout.LayoutParams(-1, systemBars.bottom, android.view.Gravity.BOTTOM)
            safe.findFocus()?.let { focus -> focus.post { reveal(focus) } }
            insets
        }
    }
    fun reveal(view: View) {
        var parent = view.parent
        while (parent is android.view.ViewGroup) {
            if (parent is ScrollView) {
                val rect = android.graphics.Rect(); view.getDrawingRect(rect)
                parent.offsetDescendantRectToMyCoords(view, rect)
                val extra = view.resources.getDimensionPixelSize(R.dimen.space_16)
                if (rect.bottom > parent.scrollY + parent.height - extra) parent.smoothScrollTo(0, rect.bottom - parent.height + extra)
                break
            }
            parent = parent.parent
        }
    }
    fun error(context: Context, text: Int = R.string.save_failed) {
        showDialog(context, MaterialAlertDialogBuilder(context).setMessage(text).setPositiveButton(android.R.string.ok, null).create())
    }
    fun confirm(context: Context, label: Int, action: () -> Unit) {
        showDialog(context, MaterialAlertDialogBuilder(context).setTitle(label).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ -> action() }.create())
    }
    fun datePicker(context: Context, field: TextView) {
        val current = runCatching { TravelTime.date(field.text.toString()) }.getOrDefault(LocalDate.now())
        showDialog(context, DatePickerDialog(context, { _, year, month, day -> field.text = TravelTime.dayFormat.format(LocalDate.of(year, month + 1, day)) },
            current.year, current.monthValue - 1, current.dayOfMonth))
    }
    fun resolveTime(context: Context, value: String, zone: String, previous: String, result: (String) -> Unit) {
        try {
            val offsets = TravelTime.offsets(value, zone)
            if (offsets.isEmpty()) { error(context, R.string.dst_gap); return }
            if (previous.isNotBlank() && TravelTime.local(previous, zone) == value) { result(previous); return }
            if (offsets.size == 1) result(TravelTime.instant(value, zone).toString())
            else MaterialAlertDialogBuilder(context).setTitle(R.string.dst_overlap)
                .setItems(offsets.map { context.getString(R.string.offset_option, value, it.id) }.toTypedArray()) { _, index ->
                    result(TravelTime.localTime(value).toInstant(offsets[index]).toString())
                }.setNegativeButton(android.R.string.cancel, null).show()
        } catch (_: Exception) { error(context, R.string.invalid_details) }
    }
    fun zonePicker(context: Context, current: String, result: (String) -> Unit) {
        val zones = (listOf(current, ZoneId.systemDefault().id, "UTC") + ZoneId.getAvailableZoneIds().sorted()).distinct()
        SelectionPicker.show(context, R.string.time_zone, R.string.search_zone, zones, setOf(current), false) { result(it.single()) }
    }
    private fun encoded(value: String) = URLEncoder.encode(value, "UTF-8")
    fun booking(value: String): String {
        val text = value.trim()
        require(text.isNotEmpty())
        if (!text.contains("://") && !text.startsWith("www.") && !text.startsWith("booking.com"))
            return "https://www.booking.com/searchresults.html?ss=${encoded(text)}"
        val uri = URI(if (text.contains("://")) text else "https://$text")
        val host = uri.host?.lowercase().orEmpty()
        require(uri.scheme == "https" && uri.userInfo == null && (host == "booking.com" || host.endsWith(".booking.com")))
        return uri.toASCIIString()
    }
    fun map(value: String): String {
        val text = value.trim(); require(text.isNotEmpty())
        if (text.startsWith("https://") || text.startsWith("http://")) {
            val uri = URI(text); require(!uri.host.isNullOrBlank() && uri.userInfo == null)
            return uri.toASCIIString()
        }
        return "https://www.google.com/maps/search/?api=1&query=${encoded(text)}"
    }
    fun open(context: Context, url: () -> String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url()))) }
        catch (_: Exception) { error(context, R.string.link_failed) }
    }
}

interface KeyboardAware {
    fun onKeyboardVisibilityChanged(visible: Boolean)
}
