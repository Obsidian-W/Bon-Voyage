package paris.obsidian.bonvoyage

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.Normalizer
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.zip.ZipInputStream

internal object TripWorkbookImport {
    private const val MAX_WORKBOOK_BYTES = 32 * 1024 * 1024
    private const val MAX_DAYS = 10_000
    private val worksheetPath = Regex("""xl/worksheets/[^/]+\.xml""")
    private val shortDate = Regex("""(\d{1,2})[./-](\d{1,2})""")
    private val yearInTitle = Regex("""\b\d{4}\b""")
    private val fullDate = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT)

    // These aliases are accepted column names in external workbook files.
    private val headers = listOf(
        setOf("date"), setOf("place", "lieu"), setOf("accommodation", "accomodation", "hebergement"),
        setOf("activity", "activite"), setOf("transport"), setOf("extra", "complements")
    )

    data class CellDate(val full: LocalDate?, val partial: MonthDay?)
    data class Row(val date: CellDate, val place: String, val stay: String, val activity: String, val transport: String, val extra: String)
    data class Sheet(val title: String, val rows: List<Row>) {
        val needsYear = rows.any { it.date.partial != null }
        val suggestedYear = yearInTitle.find(title)?.value?.toIntOrNull()
            ?: rows.firstNotNullOfOrNull { it.date.full?.year }
            ?: LocalDate.now().year
    }
    data class Itinerary(val trip: Trip, val cards: List<Day>)

    fun read(input: InputStream): Sheet {
        val parts = readPackage(input)
        val shared = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
        val use1904 = parts["xl/workbook.xml"]?.let(::uses1904Epoch) ?: false
        for ((path, xml) in parts) {
            if (!worksheetPath.matches(path)) continue
            val rows = sheetRows(xml, shared)
            val headerIndex = rows.indexOfFirst { headerColumns(it) != null }
            if (headerIndex < 0) continue
            val columns = requireNotNull(headerColumns(rows[headerIndex]))
            val title = rows.take(headerIndex).asSequence().flatMap { it.values.asSequence() }
                .map { it.text }.firstOrNull { it.isNotBlank() }.orEmpty()
            val data = rows.drop(headerIndex + 1).mapNotNull { cells ->
                val values = columns.map { cells[it] ?: Cell("", "") }
                if (values.all { it.text.isBlank() }) return@mapNotNull null
                Row(parseDate(values[0], use1904), values[1].text, values[2].text,
                    values[3].text, values[4].text, values[5].text)
            }
            require(data.isNotEmpty())
            return Sheet(title, data)
        }
        throw IllegalArgumentException("Itinerary columns were not found")
    }

    fun itinerary(context: Context, sheet: Sheet, title: String, countries: List<String>, year: Int): Itinerary {
        require(year in 1..9999)
        val country = countries.first()
        val zone = CountryZones.choose(countries, "")
        var currentYear = year
        var previous: LocalDate? = null
        val byDate = sheet.rows.groupBy { row ->
            val date = when {
                row.date.full != null -> row.date.full
                row.date.partial != null -> {
                    if (previous?.month == Month.DECEMBER && row.date.partial.month == Month.JANUARY) currentYear++
                    require(row.date.partial.isValidYear(currentYear))
                    row.date.partial.atYear(currentYear)
                }
                else -> requireNotNull(previous)
            }
            require(date.year in 1..9999)
            currentYear = date.year
            previous = date
            date
        }.toSortedMap()
        val start = byDate.firstKey()
        val end = byDate.lastKey()
        val count = ChronoUnit.DAYS.between(start, end) + 1
        require(count in 1..MAX_DAYS.toLong())

        val cards = buildList {
            repeat(count.toInt()) { index ->
                val date = start.plusDays(index.toLong())
                val rows = byDate[date].orEmpty()
                val storedDate = TravelTime.dayFormat.format(date)
                val place = rows.map { it.place }.filter(String::isNotBlank).distinct().joinToString("\n")
                val stay = rows.map { it.stay }.filter(String::isNotBlank).distinct().joinToString("\n")
                val details = stay.lines().map(String::trim).filter(String::isNotBlank)
                val bookingLine = details.drop(1).firstOrNull(::isBookingLink)
                val booking = bookingLine?.let(PlannerUi::booking).orEmpty()
                val activities = rows.map { it.activity }.filter(String::isNotBlank)
                val extra = rows.map { it.extra }.filter(String::isNotBlank).joinToString("\n")
                val notes = if (extra.isBlank()) activities else activities + context.getString(R.string.import_extra_notes, extra)
                add(Day(type = "day", dayNumber = index + 1, date = storedDate, country = country,
                    title = place.ifBlank { context.getString(R.string.day_default_name, index + 1) },
                    content = notes.joinToString("\n\n"), hotel = details.firstOrNull().orEmpty(),
                    location = details.drop(1).filterNot { it == bookingLine }.joinToString("\n"), bookingUrl = booking))
                rows.map { it.transport }.filter(String::isNotBlank).forEach { transport ->
                    add(Day(type = "other", title = transport, dayNumber = index + 1, date = storedDate, country = country,
                        destinationCountry = country, departureZone = zone, arrivalZone = zone))
                }
            }
        }
        val trip = Trip(name = title, dateBegin = TravelTime.dayFormat.format(start), dateEnd = TravelTime.dayFormat.format(end),
            countries = countries.joinToString("|"), timeZone = zone, image = CountryArtwork.resource(context, country))
        return Itinerary(trip, cards)
    }

    private fun isBookingLink(value: String): Boolean =
        (value.startsWith("https://") || value.startsWith("www.") || value.startsWith("booking.com")) &&
            runCatching { PlannerUi.booking(value) }.isSuccess

    private fun parseDate(cell: Cell, use1904: Boolean): CellDate {
        val text = cell.text.trim()
        if (text.isBlank()) return CellDate(null, null)
        val serial = if (cell.type.isEmpty() || cell.type == "n") text.toDoubleOrNull() else null
        if (serial != null) {
            require(serial.isFinite() && serial in 0.0..2_958_465.0)
            val days = serial.toLong()
            require(use1904 || days != 60L)
            val epoch = if (use1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 31)
            return CellDate(epoch.plusDays(if (!use1904 && days > 60) days - 1 else days), null)
        }
        val exact = runCatching { TravelTime.date(text.substringBefore('T')) }.getOrNull()
            ?: runCatching { LocalDate.parse(text, fullDate) }.getOrNull()
        if (exact != null) return CellDate(exact, null)
        val (day, month) = requireNotNull(shortDate.matchEntire(text)).destructured
        return CellDate(null, MonthDay.of(month.toInt(), day.toInt()))
    }

    private fun headerColumns(row: Map<String, Cell>): List<String>? {
        val normalized = row.mapValues { (_, cell) ->
            Normalizer.normalize(cell.text, Normalizer.Form.NFD).lowercase(Locale.ROOT).filter(Char::isLetter)
        }
        return headers.map { aliases -> normalized.entries.firstOrNull { it.value in aliases }?.key ?: return null }
    }

    private data class Cell(val text: String, val type: String)
    private fun sheetRows(bytes: ByteArray, shared: List<String>): List<Map<String, Cell>> = buildList {
        val parser = parser(bytes)
        var row = linkedMapOf<String, Cell>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "row") row = linkedMapOf()
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "c") {
                val column = parser.getAttributeValue(null, "r").orEmpty().takeWhile(Char::isLetter)
                val type = parser.getAttributeValue(null, "t").orEmpty()
                val raw = readText(parser, "c", setOf("v", "t"))
                val text = if (type == "s") shared.getOrNull(raw.toIntOrNull() ?: -1).orEmpty() else raw
                if (column.isNotEmpty() && text.isNotBlank()) row[column] = Cell(text, type)
            }
            if (parser.eventType == XmlPullParser.END_TAG && parser.name == "row" && row.isNotEmpty()) add(row)
        }
    }

    private fun sharedStrings(bytes: ByteArray): List<String> = buildList {
        val parser = parser(bytes)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "si") add(readText(parser, "si", setOf("t")))
        }
    }

    private fun readText(parser: XmlPullParser, endTag: String, textTags: Set<String>): String = buildString {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT || (event == XmlPullParser.END_TAG && parser.depth == depth && parser.name == endTag)) break
            if (event == XmlPullParser.START_TAG && parser.name in textTags) append(parser.nextText())
        }
    }

    private fun uses1904Epoch(bytes: ByteArray): Boolean {
        val parser = parser(bytes)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "workbookPr") {
                return parser.getAttributeValue(null, "date1904") in setOf("1", "true")
            }
        }
        return false
    }

    private fun parser(bytes: ByteArray) = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(bytes.inputStream(), null)
    }

    private fun readPackage(input: InputStream): Map<String, ByteArray> = buildMap {
        var total = 0L
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val part = zip.nextEntry ?: break
                val keep = part.name == "xl/sharedStrings.xml" || part.name == "xl/workbook.xml" || worksheetPath.matches(part.name)
                val bytes = if (keep) ByteArrayOutputStream() else null
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_WORKBOOK_BYTES)
                    bytes?.write(buffer, 0, count)
                }
                if (bytes != null) put(part.name, bytes.toByteArray())
                zip.closeEntry()
            }
        }
    }
}
