package paris.obsidian.bonvoyage

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip
import java.io.File
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class PlannerVisualTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    @Before fun resetIsolatedFixtures() {
        check(context.packageName.endsWith(".uitest")) { "Use the isolated deviceTest build for UI tests." }
        DatabaseClient.getInstance(context).clearAllTables()
    }
    private fun eventually(action: () -> Unit) {
        var failure: Throwable? = null
        val deadline = SystemClock.uptimeMillis() + 10000
        do { try { action(); return } catch (e: Throwable) { failure = e; SystemClock.sleep(100) } }
        while (SystemClock.uptimeMillis() < deadline)
        throw requireNotNull(failure)
    }
    private fun capture(name: String) {
        instrumentation.waitForIdleSync(); SystemClock.sleep(350)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir("ui-check"), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun editor(id: Int) = onView(allOf(withId(id), isCompletelyDisplayed()))
    private fun scroll(id: Int) = onView(allOf(withId(id), isDescendantOfA(allOf(withId(R.id.dayCard), isCompletelyDisplayed())))).perform(scrollTo())
    private fun cardSwipe(left: Boolean) = androidx.test.espresso.action.GeneralSwipeAction(
        androidx.test.espresso.action.Swipe.FAST,
        { view -> val p = IntArray(2); view.getLocationOnScreen(p); floatArrayOf(p[0] + view.width * if (left) .75f else .25f, p[1] + view.height * .5f) },
        { view -> val p = IntArray(2); view.getLocationOnScreen(p); floatArrayOf(p[0] + view.width * if (left) .25f else .75f, p[1] + view.height * .5f) },
        androidx.test.espresso.action.Press.FINGER)
    private fun assertPinned(activity: TripDetailActivity, position: Int) {
        val recycler = activity.findViewById<RecyclerView>(R.id.tripDaysContainer)
        val root = requireNotNull(recycler.findViewHolderForAdapterPosition(position)).itemView
        val header = root.findViewById<View>(R.id.dayHeader)
        val visible = Rect(); assertTrue(header.getGlobalVisibleRect(visible))
        assertEquals(header.height, visible.height())
        assertEquals(header.width, visible.width())
        val location = IntArray(2); header.getLocationOnScreen(location)
        assertEquals(location[1], visible.top)
        assertEquals(1f, root.alpha)
        assertEquals(0, root.scrollY)
        assertNull(recycler.itemAnimator)
    }

    @Test fun inlinePlannerInBothThemes() {
        val db = DatabaseClient.getInstance(context)
        val trip = Trip(name = "France & Albania", dateBegin = "01.06.2027", dateEnd = "01.06.2027", countries = "France|Albania", timeZone = "Europe/Paris")
        trip.id = db.tripDao().insertOne(trip).toInt()
        val past = Trip(name = "Mexico", dateBegin = "01.06.2025", dateEnd = "06.06.2025", countries = "Mexico", timeZone = "America/Mexico_City")
        past.id = db.tripDao().insertOne(past).toInt()
        val day = Day(type = "day", tripID = trip.id, dayNumber = 1, date = "01.06.2027", title = "Old town", country = "France", content = "Museum at 14:00. Door code: 1234", hotel = "Hôtel de la Gare", location = "Paris, France")
        day.id = db.dayDao().insertOne(day)
        val transport = Day(type = "train", tripID = trip.id, date = "01.06.2027", title = "Night train", country = "France", destinationCountry = "Albania", departure = "2027-06-01T19:00:00Z", arrival = "2027-06-02T05:30:00Z", departureZone = "Europe/Paris", arrivalZone = "Europe/Tirane", sleeps = true, location = "Gare de Lyon", destination = "Tirana", content = "Carriage 12, berth 24")
        transport.id = db.dayDao().insertOne(transport)
        try {
            assertEquals(listOf("Europe/Tirane"), CountryZones.zones("Albania"))
            assertTrue(CountryZones.zones("United States").size > 1)
            for ((mode, name) in listOf(AppCompatDelegate.MODE_NIGHT_NO to "light", AppCompatDelegate.MODE_NIGHT_YES to "dark")) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                    eventually { onView(withText(trip.name)).check(matches(isDisplayed())) }; capture("${name}_trips")
                    scenario.onActivity { it.findViewById<TabLayout>(R.id.tab_layout).getTabAt(1)!!.select() }
                    eventually { scenario.onActivity { assertEquals(1, it.findViewById<RecyclerView>(R.id.countryRecycler).adapter!!.itemCount) } }
                    capture("${name}_past")
                    db.tripDao().removeOne(past)
                    eventually { onView(withId(R.id.tab_layout)).check(matches(withEffectiveVisibility(Visibility.GONE))) }
                    eventually { onView(withText(trip.name)).check(matches(isDisplayed())) }
                    capture("${name}_empty_history")
                    val replacement = past.copy(id = 0, name = "Japan history", countries = "Japan")
                    replacement.id = db.tripDao().insertOne(replacement).toInt()
                    eventually { onView(withId(R.id.tab_layout)).check(matches(isDisplayed())) }
                    onView(withText(R.string.previous)).perform(click())
                    eventually {
                        onView(allOf(withId(R.id.tripFlags), isDescendantOfA(allOf(withId(R.id.tripTile),
                            hasDescendant(withText(replacement.name)))))).check(matches(withText("🇯🇵")))
                    }
                    db.tripDao().removeOne(replacement)
                    past.id = db.tripDao().insertOne(past.copy(id = 0)).toInt()
                }
                ActivityScenario.launch<TripDetailActivity>(Intent(context, TripDetailActivity::class.java).putExtra("id", trip.id)).use { scenario ->
                    eventually { scenario.onActivity { assertEquals(3, it.findViewById<RecyclerView>(R.id.tripDaysContainer).adapter!!.itemCount) } }
                    capture("${name}_add_card")
                    scenario.onActivity { it.findViewById<RecyclerView>(R.id.tripDaysContainer).scrollToPosition(0) }
                    scroll(R.id.field_notes)
                    eventually { editor(R.id.field_notes).check(matches(withText(day.content))) }; capture("${name}_day")
                    val changed = "Saved inline. Door code: 5678"
                    editor(R.id.field_notes).perform(click(), replaceText(changed))
                    eventually { assertEquals(changed, db.dayDao().findByLongID(day.id)?.content) }
                    capture("${name}_keyboard")
                    scenario.onActivity { activity ->
                        val notes = activity.currentFocus!!
                        val visible = Rect(); assertTrue(notes.getGlobalVisibleRect(visible))
                        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)!!
                        val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                        assertTrue(visible.bottom <= activity.window.decorView.height - ime)
                    }
                    Espresso.closeSoftKeyboard(); day.content = changed
                    scroll(R.id.editSummary)
                    val backgroundBeforeDialog = Rect()
                    scenario.onActivity { it.findViewById<View>(R.id.tripDaysContainer).getGlobalVisibleRect(backgroundBeforeDialog) }
                    onView(allOf(withId(R.id.editSummary), isDisplayed())).perform(click())
                    eventually { editor(R.id.field_hotel).check(matches(withText(day.hotel))) }
                    onView(withId(R.id.field_location)).check(matches(withText(day.location)))
                    capture("${name}_hotel")
                    day.hotel = "Hôtel de la Gare — updated"
                    onView(withId(R.id.field_hotel)).perform(click(), replaceText(day.hotel))
                    eventually { onView(withId(R.id.field_hotel)).check { view, _ ->
                        assertTrue(ViewCompat.getRootWindowInsets(view)!!.isVisible(WindowInsetsCompat.Type.ime()))
                    } }
                    scenario.onActivity {
                        val backgroundWithDialogKeyboard = Rect()
                        it.findViewById<View>(R.id.tripDaysContainer).getGlobalVisibleRect(backgroundWithDialogKeyboard)
                        assertEquals(backgroundBeforeDialog, backgroundWithDialogKeyboard)
                    }
                    capture("${name}_dialog_keyboard")
                    onView(withId(android.R.id.button1)).perform(click())
                    eventually { assertEquals(day.hotel, db.dayDao().findByLongID(day.id)!!.hotel) }
                    scenario.recreate()
                    eventually { editor(R.id.dayTitle).check(matches(withText(day.title))) }
                    scenario.onActivity { it.findViewById<RecyclerView>(R.id.tripDaysContainer).scrollToPosition(1) }
                    eventually { editor(R.id.dayTitle).check(matches(withText(transport.title))) }; capture("${name}_transport")
                    onView(allOf(withId(R.id.mapAction), isDescendantOfA(allOf(withId(R.id.dayCard), hasDescendant(withText(transport.title)))))).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
                    onView(withId(R.id.field_country)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
                    scroll(R.id.summaryDetails)
                    onView(allOf(withId(R.id.summaryDetails), isDisplayed())).check(matches(withText(org.hamcrest.Matchers.containsString(TravelTime.local(transport.departure, transport.departureZone)))))
                    onView(withId(R.id.clockAction)).perform(click())
                    eventually { onView(allOf(withId(R.id.summaryDetails), isDisplayed())).check(matches(withText(org.hamcrest.Matchers.containsString(TravelTime.local(transport.departure, ZoneId.systemDefault().id))))) }
                    assertEquals(transport.departure, db.dayDao().findByLongID(transport.id)!!.departure)
                    eventually { assertEquals("device", db.tripDao().findByID(trip.id).displayZone) }
                    onView(withId(R.id.clockAction)).check(matches(withContentDescription(R.string.switch_to_local_time)))
                    capture("${name}_phone_time")
                    onView(withId(R.id.clockAction)).perform(click())
                    eventually { assertEquals("", db.tripDao().findByID(trip.id).displayZone) }
                    onView(withId(R.id.clockAction)).check(matches(withContentDescription(R.string.switch_to_device_time)))
                    scroll(R.id.editSummary)
                    onView(allOf(withId(R.id.editSummary), isDisplayed())).perform(click())
                    eventually { onView(withId(R.id.field_location)).check(matches(withText(transport.location))) }
                    onView(withId(R.id.field_location)).perform(replaceText("Paris Gare de Lyon")); Espresso.closeSoftKeyboard()
                    onView(withId(android.R.id.button1)).perform(click())
                    eventually { assertEquals("Paris Gare de Lyon", db.dayDao().findByLongID(transport.id)!!.location) }
                    transport.location = "Paris Gare de Lyon"
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    eventually { scenario.onActivity { assertEquals(Configuration.ORIENTATION_LANDSCAPE, it.resources.configuration.orientation) } }
                    eventually { onView(allOf(withId(R.id.dayCard), hasDescendant(allOf(withId(R.id.dayTitle), withText(transport.title))))).check(matches(isCompletelyDisplayed())) }
                    capture("${name}_landscape")
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    eventually { scenario.onActivity { assertEquals(Configuration.ORIENTATION_PORTRAIT, it.resources.configuration.orientation) } }
                }
                ActivityScenario.launch<TripAddingActivity>(Intent(context, TripAddingActivity::class.java).putExtra("id", trip.id)).use { scenario ->
                    eventually { onView(withId(R.id.field_countries)).check(matches(isDisplayed())) }; capture("${name}_edit_trip")
                    val backgroundBeforeSearch = Rect()
                    scenario.onActivity { it.findViewById<View>(R.id.tripFormScroll).getGlobalVisibleRect(backgroundBeforeSearch) }
                    onView(withId(R.id.field_countries)).perform(click())
                    eventually { onView(withId(R.id.selectionSearch)).check(matches(isDisplayed())) }; capture("${name}_countries")
                    onView(withId(R.id.selectionSearch)).perform(click(), replaceText("Albania"))
                    eventually { onView(withId(R.id.selectionSearch)).check { view, _ ->
                        assertTrue(ViewCompat.getRootWindowInsets(view)!!.isVisible(WindowInsetsCompat.Type.ime()))
                    } }
                    scenario.onActivity {
                        val backgroundWithSearchKeyboard = Rect()
                        it.findViewById<View>(R.id.tripFormScroll).getGlobalVisibleRect(backgroundWithSearchKeyboard)
                        assertEquals(backgroundBeforeSearch, backgroundWithSearchKeyboard)
                    }
                    onView(withId(android.R.id.button2)).perform(click())
                    eventually { onView(withId(R.id.field_start)).perform(scrollTo(), click()) }
                    capture("${name}_date_picker")
                }
            }
        } finally {
            db.runInTransaction { db.dayDao().removeForTrip(trip.id); db.tripDao().removeOne(trip); db.tripDao().removeOne(past) }
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        }
    }

    @Test fun newTripOpensAndNewDayExtendsDates() {
        val db = DatabaseClient.getInstance(context)
        val tripName = "Albania regression trip"
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
                onView(withId(R.id.tripControls)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(allOf(withId(R.id.tripTile), withContentDescription(context.getString(R.string.addTrip)))).perform(click())
                eventually { onView(withId(R.id.field_countries)).check(matches(isDisplayed())) }
                onView(withId(R.id.tripDuration)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
                onView(withId(R.id.field_notes)).check(matches(withHint(R.string.trip_description)))
                onView(withId(R.id.field_notes)).check { view, _ ->
                    assertEquals(android.view.Gravity.TOP, (view as TextView).gravity and android.view.Gravity.VERTICAL_GRAVITY_MASK)
                    assertFalse((view.parent.parent as com.google.android.material.textfield.TextInputLayout).isExpandedHintEnabled)
                }
                onView(withId(R.id.field_countries)).perform(click())
                onView(withId(R.id.selectionSearch)).perform(replaceText("Albania"))
                onView(allOf(withText("Albania"), isAssignableFrom(android.widget.CheckedTextView::class.java))).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.field_name)).check(matches(withText("Albania")))
                onView(withId(R.id.field_countries)).perform(click())
                onView(withText(R.string.clear_countries)).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.field_countries)).check(matches(withText("")))
                onView(withId(R.id.field_countries)).perform(click())
                onView(withId(R.id.selectionSearch)).perform(replaceText("Albania"))
                onView(allOf(withText("Albania"), isAssignableFrom(android.widget.CheckedTextView::class.java))).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
                onView(withId(R.id.field_name)).perform(scrollTo(), replaceText(tripName)); Espresso.closeSoftKeyboard()
                onView(withId(R.id.validateButton)).perform(click())
                eventually { onView(withId(R.id.clockAction)).check(matches(isDisplayed())) }
                val trip = db.tripDao().getAll().single { candidate -> candidate.name == tripName }
                assertEquals("Europe/Tirane", trip.timeZone)
                onView(withId(R.id.plusDay)).perform(click())
                eventually { assertEquals(1, db.dayDao().getAll(trip.id).size) }
                onView(allOf(withId(R.id.dayTitle), withText(context.getString(R.string.day_default_name, 1)))).perform(longClick())
                onView(withId(R.id.field_name)).check(matches(withText(context.getString(R.string.day_default_name, 1))))
                onView(withId(android.R.id.button2)).perform(click())
                Espresso.closeSoftKeyboard()
                Espresso.pressBack()
                eventually { scenario.onActivity { assertEquals("", it.findViewById<androidx.appcompat.widget.SearchView>(R.id.searchView).query.toString()) } }
            }
            val trip = db.tripDao().getAll().single { it.name == tripName }
            val model = PlannerModel(context.applicationContext as android.app.Application)
            model.addCard(trip, "day", "Second day", "Albania")
            eventually {
                val saved = db.tripDao().findByID(trip.id)
                assertEquals(TravelTime.dayFormat.format(TravelTime.date(trip.dateBegin).plusDays(1)), saved.dateEnd)
            }
        } finally {
            db.tripDao().getAll().filter { it.name == tripName }.forEach { trip -> db.runInTransaction { db.dayDao().removeForTrip(trip.id); db.tripDao().removeOne(trip) } }
        }
    }

    @Test fun tripListSortAndLongPressEdit() {
        val db = DatabaseClient.getInstance(context)
        val soon = Trip(name = "Zurich", dateBegin = "01.06.2027", dateEnd = "03.06.2027", countries = "Switzerland", timeZone = "Europe/Zurich")
        val later = Trip(name = "Albania", dateBegin = "02.07.2027", dateEnd = "04.07.2027", countries = "Albania", timeZone = "Europe/Tirane")
        soon.id = db.tripDao().insertOne(soon).toInt()
        later.id = db.tripDao().insertOne(later).toInt()
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            fun order(vararg names: String) = eventually {
                scenario.onActivity {
                    val adapter = it.findViewById<RecyclerView>(R.id.countryRecycler).adapter as paris.obsidian.bonvoyage.trips.TripAdapter
                    assertEquals(names.toList(), adapter.currentList.filter { trip -> trip.id != 0 }.map { trip -> trip.name })
                }
            }
            eventually { scenario.onActivity { assertEquals(3, it.findViewById<RecyclerView>(R.id.countryRecycler).adapter!!.itemCount) } }
            onView(withId(R.id.tripControls)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            val third = Trip(name = "France", dateBegin = "02.05.2027", dateEnd = "04.05.2027", countries = "France", timeZone = "Europe/Paris")
            db.tripDao().insertOne(third)
            eventually { onView(withId(R.id.sortByDate)).check(matches(isDisplayed())) }
            onView(withId(R.id.sortByDate)).perform(click()).check(matches(isChecked()))
            order(third.name, soon.name, later.name)
            onView(withText("01/06/2027 - 03/06/2027")).check(matches(isDisplayed()))
            onView(withId(R.id.sortByName)).perform(click()).check(matches(isChecked()))
            onView(withId(R.id.sortByDate)).check(matches(org.hamcrest.Matchers.not(isChecked())))
            order(later.name, third.name, soon.name)
            onView(allOf(withId(R.id.tripTile), hasDescendant(withText(later.name)))).perform(longClick())
            onView(withText(R.string.delete_action)).check(matches(isDisplayed()))
            onView(withText(R.string.edit_trip)).perform(click())
            onView(withId(R.id.field_name)).check(matches(withText(later.name)))
            Espresso.pressBack()
            onView(withId(R.id.sortByDate)).perform(click()).check(matches(isChecked()))
            order(third.name, soon.name, later.name)
            capture("trip_list_updated")
            onView(withId(R.id.tab_layout)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(allOf(withId(R.id.tripTile), hasDescendant(withText(third.name)))).perform(longClick())
            onView(withText(R.string.delete_action)).perform(click())
            onView(withId(android.R.id.button1)).perform(click())
            order(soon.name, later.name)
            onView(withId(R.id.tripControls)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            Espresso.openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.about_title)).perform(click())
            onView(withText(R.string.about_author)).check(matches(isDisplayed()))
            onView(withId(R.id.privacyPolicy)).check(matches(withText(R.string.privacy_policy)))
            capture("about_updated")
            Espresso.pressBack()
            Espresso.openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.settings_title)).perform(click())
            onView(withId(R.id.turboMode)).check(matches(isDisplayed()))
            onView(withId(R.id.turboMode)).perform(click())
            onView(withId(R.id.fishBackground)).check(matches(isDisplayed()))
            capture("turbo_settings")
            onView(withId(R.id.turboMode)).perform(click())
            Espresso.pressBack()
            onView(allOf(withId(R.id.tripTile), withContentDescription(context.getString(R.string.addTrip)))).perform(longClick())
            eventually {
                val activePackage = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
                assertNotNull(activePackage)
                assertNotEquals(context.packageName, activePackage)
                val activityState = android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
                ).bufferedReader().use { it.readText() }
                assertTrue(activityState.contains(Intent.ACTION_OPEN_DOCUMENT))
            }
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK")
            ).use { it.readBytes() }
        }
    }

    @Test fun deletingCurrentCardSettlesOnItsNeighbour() {
        val db = DatabaseClient.getInstance(context)
        val trip = Trip(name = "Delete card test", dateBegin = "01.06.2027", dateEnd = "03.06.2027",
            countries = "France", timeZone = "Europe/Paris")
        trip.id = db.tripDao().insertOne(trip).toInt()
        listOf("First", "Middle", "Last").forEachIndexed { index, name ->
            db.dayDao().insertOne(Day(tripID = trip.id, type = "day", title = name, country = "France",
                date = TravelTime.dayFormat.format(java.time.LocalDate.of(2027, 6, index + 1)), dayNumber = index + 1))
        }
        ActivityScenario.launch<TripDetailActivity>(Intent(context, TripDetailActivity::class.java).putExtra("id", trip.id)).use { scenario ->
            eventually { scenario.onActivity {
                val cards = it.findViewById<RecyclerView>(R.id.tripDaysContainer)
                assertEquals(4, cards.adapter!!.itemCount)
                cards.scrollToPosition(1)
            } }
            fun delete(title: String) {
                onView(allOf(withId(R.id.delete_day), isDescendantOfA(allOf(withId(R.id.dayCard),
                    hasDescendant(allOf(withId(R.id.dayTitle), withText(title))))))).perform(click())
                onView(withId(android.R.id.button1)).perform(click())
            }
            fun visible(title: String) = eventually {
                onView(allOf(withId(R.id.dayTitle), withText(title))).check(matches(isCompletelyDisplayed()))
            }
            visible("Middle")
            delete("Middle")
            visible("Last")
            delete("Last")
            visible("First")
            delete("First")
            onView(allOf(withId(R.id.plusDay), isDisplayed())).check(matches(isCompletelyDisplayed()))
        }
    }

    @Test fun importsSharedStringsWithShortDatesAndHotelDetails() {
        val title = "Laos 2025"
        val folder = java.io.File(context.cacheDir, "trip-exports").apply { mkdirs() }
        val file = java.io.File.createTempFile("import-test", ".xlsx", folder)
        val strings = listOf(title, "DATE", "PLACE", "ACCOMODATION", "ACTIVITY", "TRANSPORT", "EXTRA",
            "15/01", "Vientiane", "Hôtel du Quai\n1 Rue du Quai\nhttps://www.booking.com/hotel/la/du-quai.html",
            "Visit the city", "Train in the morning", "Book ahead")
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            fun part(path: String, xml: String) {
                zip.putNextEntry(java.util.zip.ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            part("xl/sharedStrings.xml", "<sst>" + strings.joinToString("") { "<si><t>" + it + "</t></si>" } + "</sst>")
            fun row(number: Int, values: IntRange) = "<row r=\"" + number + "\">" +
                values.mapIndexed { index, value -> "<c r=\"" + ('A' + index) + number + "\" t=\"s\"><v>" + value + "</v></c>" }
                    .joinToString("") + "</row>"
            part("xl/worksheets/sheet1.xml", "<worksheet><sheetData>" + row(1, 0..0) + row(2, 1..6) + row(3, 7..12) + "</sheetData></worksheet>")
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".trip-exports", file)
            ActivityScenario.launch<ImportTripActivity>(Intent(context, ImportTripActivity::class.java).setData(uri)).use {
                eventually { onView(withId(R.id.field_year)).check(matches(withText("2025"))) }
                onView(withId(R.id.field_countries)).check(matches(withText("Laos")))
                onView(withId(R.id.validateButton)).perform(click())
                eventually {
                    val db = DatabaseClient.getInstance(context)
                    val trip = db.tripDao().getAll().single { candidate -> candidate.name == title }
                    val cards = db.dayDao().getAll(trip.id)
                    assertEquals("15.01.2025", trip.dateBegin)
                    assertTrue(trip.timeZone in CountryZones.zones("Laos"))
                    val day = cards.single { card -> card.type == "day" }
                    assertEquals("Vientiane", day.title)
                    assertEquals("Hôtel du Quai", day.hotel)
                    assertEquals("1 Rue du Quai", day.location)
                    assertEquals("https://www.booking.com/hotel/la/du-quai.html", day.bookingUrl)
                    assertTrue(day.content.contains("Book ahead"))
                    assertEquals("Train in the morning", cards.single { card -> card.type == "other" }.title)
                }
                Espresso.pressBackUnconditionally()
            }
        } finally {
            file.delete()
        }
    }

    @Test fun shareProvidesReadableWorkbook() {
        val trip = Trip(name = "Plan & travel", dateBegin = "01.06.2027", dateEnd = "03.06.2027",
            countries = "France", timeZone = "Europe/Paris", notes = "Book ahead")
        val note = "=2+2\nWindow & aisle <together>"
        val hotel = Day(type = "day", date = trip.dateBegin, title = "Paris", content = note,
            hotel = "Hôtel du Quai", location = "1 Rue du Quai", bookingUrl = "https://www.booking.com/hotel/fr/du-quai.html")
        val train = Day(type = "train", date = "2027-06-01", title = "Night train", content = "Bring a pillow",
            location = "Gare de Lyon", destination = "Lyon", departure = "2027-06-01T21:35:00Z",
            arrival = "2027-06-02T04:00:00Z", sleeps = true)
        val share = TripWorkbook.shareIntent(context, trip, listOf(hotel, train))
        assertEquals(Intent.ACTION_SEND, share.action)
        assertTrue(share.type!!.endsWith(".spreadsheetml.sheet"))
        assertTrue(share.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val uri = share.clipData!!.getItemAt(0).uri
        val parts = mutableMapOf<String, String>()
        val stream = requireNotNull(context.contentResolver.openInputStream(uri))
        java.util.zip.ZipInputStream(stream).use { zip ->
            while (true) {
                val part = zip.nextEntry ?: break
                parts[part.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        val sheet = requireNotNull(parts["xl/worksheets/sheet1.xml"])
        val parser = android.util.Xml.newPullParser()
        parser.setInput(sheet.reader())
        val cells = mutableMapOf<String, String>()
        var cell = ""
        while (parser.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == org.xmlpull.v1.XmlPullParser.START_TAG) {
                when (parser.name) {
                    "c" -> cell = parser.getAttributeValue(null, "r")
                    "t", "v" -> cells[cell] = parser.nextText()
                }
            }
            parser.next()
        }
        assertEquals(trip.name, cells["A1"])
        assertEquals(listOf("DATE", "PLACE", "ACCOMMODATION", "ACTIVITY", "TRANSPORT", "EXTRA"),
            ('A'..'F').map { cells["${it}2"] })
        assertEquals("46539", cells["A3"])
        assertEquals("Paris", cells["B3"])
        assertTrue(cells.getValue("C3").startsWith("Hôtel du Quai\n1 Rue du Quai\nhttps://www.booking.com/"))
        assertTrue(cells.getValue("C3").contains("Used as accommodation"))
        assertEquals(note, cells["D3"])
        assertTrue(cells.getValue("E3").contains("23:35 Gare de Lyon"))
        assertTrue(cells.getValue("E3").contains("02/06 06:00 Lyon"))
        assertEquals("Book ahead\n\nBring a pillow", cells["F3"])
        assertTrue(cells.keys.none { it.endsWith("4") })
        assertTrue(sheet.contains("state=\"frozen\""))
        assertTrue(parts.containsKey("xl/styles.xml"))
        val imported = requireNotNull(context.contentResolver.openInputStream(uri)).use(TripWorkbookImport::read)
        assertFalse(imported.needsYear)
        assertEquals(java.time.LocalDate.of(2027, 6, 1), imported.rows.single().date.full)
    }

    @Test fun transportTypesAndTrailingAddCard() {
        val db = DatabaseClient.getInstance(context)
        val trip = Trip(name = "Transport choices", dateBegin = "01.06.2027", dateEnd = "01.06.2027", countries = "France|Albania", timeZone = "Europe/Paris")
        trip.id = db.tripDao().insertOne(trip).toInt()
        try {
            ActivityScenario.launch<TripDetailActivity>(Intent(context, TripDetailActivity::class.java).putExtra("id", trip.id)).use { scenario ->
                for ((index, type) in TransportMode.types.withIndex()) {
                    eventually { scenario.onActivity { activity ->
                        val cards = activity.findViewById<RecyclerView>(R.id.tripDaysContainer)
                        assertEquals(index + 1, cards.adapter!!.itemCount)
                        assertEquals(0L, cards.adapter!!.getItemId(index))
                        cards.scrollToPosition(index)
                    } }
                    eventually { onView(allOf(withId(R.id.plusTrip), isDisplayed())).check(matches(isCompletelyDisplayed())) }
                    onView(allOf(withId(R.id.plusTrip), isDisplayed())).perform(click())
                    onView(withText(TransportMode.label(type))).perform(click())
                    eventually { onView(withId(R.id.field_name)).check(matches(withText(""))) }
                    if (index == 0) {
                        onView(withId(R.id.field_name)).perform(scrollTo(), click())
                        capture("transport_name_keyboard")
                        onView(withId(R.id.field_name)).perform(pressImeActionButton())
                        onView(withId(R.id.field_location)).check(matches(hasFocus()))
                        assertTrue(db.dayDao().getAll(trip.id).isEmpty())
                    }
                    onView(withId(R.id.field_departureZone)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
                    onView(withId(R.id.field_arrivalZone)).check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist())
                    // Mode and route live in this same dialog, not a second popup.
                    onView(withId(R.id.field_location)).check(matches(withHint(TransportMode.origin(type))))
                    onView(withId(R.id.field_location)).perform(scrollTo(), replaceText("Origin platform")); Espresso.closeSoftKeyboard()
                    onView(withId(R.id.field_destination)).perform(scrollTo(), replaceText("Destination platform")); Espresso.closeSoftKeyboard()
                    capture("create_$type")
                    onView(withId(android.R.id.button1)).perform(click())
                    eventually {
                        val saved = db.dayDao().getAll(trip.id).single { it.type == type }
                        assertEquals("Origin platform", saved.location)
                        assertEquals("Destination platform", saved.destination)
                    }
                    eventually { onView(allOf(withId(R.id.dayTitle), withText(TransportMode.label(type)))).check(matches(isCompletelyDisplayed())) }
                    capture("transport_$type")
                }
            }
        } finally { db.runInTransaction { db.dayDao().removeForTrip(trip.id); db.tripDao().removeOne(trip) } }
    }

    @Test fun headersStayVisibleWhileScrollingAndSaving() {
        val db = DatabaseClient.getInstance(context)
        val trip = Trip(name = "Scrolling regression", dateBegin = "01.06.2027", dateEnd = "01.06.2027", countries = "France", timeZone = "Europe/Paris")
        trip.id = db.tripDao().insertOne(trip).toInt()
        val notes = (1..50).joinToString("\n") { "Activity $it: itinerary notes" }
        val day = Day(type = "day", tripID = trip.id, date = trip.dateBegin, title = "Full day", country = "France", content = notes, hotel = "Station hotel", location = "Paris")
        day.id = db.dayDao().insertOne(day)
        val train = Day(type = "train", tripID = trip.id, date = trip.dateBegin, title = "Evening train", country = "France", content = notes)
        train.id = db.dayDao().insertOne(train)
        try {
            for (mode in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)) {
                instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
                ActivityScenario.launch<TripDetailActivity>(Intent(context, TripDetailActivity::class.java).putExtra("id", trip.id)).use { scenario ->
                    eventually { scenario.onActivity { assertPinned(it, 0) } }
                    repeat(3) { onView(allOf(withId(R.id.cardScroll), isDescendantOfA(allOf(withId(R.id.dayCard), hasDescendant(withText(day.title)))), isDisplayed())).perform(swipeUp()) }
                    scenario.onActivity { activity ->
                        assertPinned(activity, 0)
                        val root = activity.findViewById<RecyclerView>(R.id.tripDaysContainer).findViewHolderForAdapterPosition(0)!!.itemView
                        val body = root.findViewById<LinearLayout>(R.id.contentContainer)
                        assertTrue(body.getChildAt(1) is HotelSectionView)
                        assertEquals(android.view.Gravity.CENTER, root.findViewById<TextView>(R.id.dayNumber).gravity)
                    }
                    repeat(8) { index ->
                        db.dayDao().updateOne(day.copy(content = "$notes\nSaved revision $index"))
                        eventually { scenario.onActivity { assertPinned(it, 0) } }
                    }
                    repeat(3) {
                        onView(withId(R.id.tripDaysContainer)).perform(cardSwipe(true))
                        eventually { scenario.onActivity { activity -> assertPinned(activity, 1) }; editor(R.id.dayTitle).check(matches(withText(train.title))) }
                        onView(withId(R.id.tripDaysContainer)).perform(cardSwipe(false))
                        eventually { scenario.onActivity { activity -> assertPinned(activity, 0) }; editor(R.id.dayTitle).check(matches(withText(day.title))) }
                    }
                    capture("pinned_headers_$mode")
                }
            }
        } finally {
            db.runInTransaction { db.dayDao().removeForTrip(trip.id); db.tripDao().removeOne(trip) }
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) }
        }
    }
}
