package paris.obsidian.bonvoyage

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import paris.obsidian.bonvoyage.days.Day
import paris.obsidian.bonvoyage.trips.Trip
import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes the trip as an OOXML workbook with an itinerary table and a frozen header. */
internal object TripWorkbook {
    const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    private const val SHEET_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PACKAGE_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val columns = listOf(12, 22, 36, 42, 35, 48)
    private val dayMonth = DateTimeFormatter.ofPattern("dd/MM")
    private val clock = DateTimeFormatter.ofPattern("HH:mm")
    private val excelEpoch = LocalDate.of(1899, 12, 31)
    private val excelLeapDayCutover = LocalDate.of(1900, 3, 1)
    private val lastExcelDate = LocalDate.of(9999, 12, 31)

    private data class DayRow(val date: LocalDate?, val dateText: String, val cards: List<Day>)

    fun shareIntent(context: Context, trip: Trip, cards: List<Day>): Intent {
        val root = File(context.cacheDir, "trip-exports")
        val directory = File(root, UUID.randomUUID().toString())
        check(directory.mkdirs())
        val name = trip.name.replace(Regex("""[\p{Cntrl}<>:"/\\|?*]"""), "_").trim().trim('.').take(80)
            .ifBlank { context.getString(R.string.itinerary) }
        val file = File(directory, "$name.xlsx")
        file.outputStream().use { write(context, trip, cards, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.trip-exports", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun write(context: Context, trip: Trip, cards: List<Day>, output: OutputStream) {
        val sheetName = context.getString(R.string.itinerary)
        val headings = listOf(
            R.string.export_date, R.string.export_place, R.string.accommodation,
            R.string.export_activity, R.string.export_transport, R.string.export_extra
        ).map { context.getString(it).uppercase(context.resources.configuration.locales[0]) }

        ZipOutputStream(output).use { zip ->
            zip.part("[Content_Types].xml", """
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """)
            zip.part("_rels/.rels", """
                <Relationships xmlns="$PACKAGE_REL_NS">
                  <Relationship Id="rId1" Type="$REL_NS/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """)
            zip.part("xl/workbook.xml", """
                <workbook xmlns="$SHEET_NS" xmlns:r="$REL_NS">
                  <sheets><sheet name="${xml(sheetName)}" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """)
            zip.part("xl/_rels/workbook.xml.rels", """
                <Relationships xmlns="$PACKAGE_REL_NS">
                  <Relationship Id="rId1" Type="$REL_NS/worksheet" Target="worksheets/sheet1.xml"/>
                  <Relationship Id="rId2" Type="$REL_NS/styles" Target="styles.xml"/>
                </Relationships>
            """)
            zip.part("xl/styles.xml", styles(context))
            zip.part("xl/worksheets/sheet1.xml", worksheet(context, trip, cards, headings))
        }
    }

    private fun worksheet(context: Context, trip: Trip, cards: List<Day>, headings: List<String>): String = buildString {
        val dates = cards.filter { it.type.isNotBlank() && it.type != "default" }.groupBy {
            val date = runCatching { TravelTime.date(it.date) }.getOrNull()
            date to (date?.let(dayMonth::format) ?: it.date)
        }.map { (key, entries) -> DayRow(key.first, key.second, entries) }
            .sortedWith(compareBy(nullsLast()) { it.date })
        append("""<worksheet xmlns="$SHEET_NS"><sheetViews><sheetView showGridLines="0" workbookViewId="0"><pane ySplit="2" topLeftCell="A3" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><cols>""")
        columns.forEachIndexed { index, width ->
            val column = index + 1
            append("""<col min="$column" max="$column" width="$width" customWidth="1"/>""")
        }
        append("</cols><sheetData>")
        row(1, listOf(trip.name), 1, 30)
        row(2, headings, 2, 28)
        dates.forEachIndexed { index, day ->
            val values = values(context, trip, day, if (index == 0) trip.notes else "")
            row(index + 3, values, 4, rowHeight(values, columns), day.date?.let(::excelDate))
        }
        if (dates.isEmpty() && trip.notes.isNotBlank()) {
            val values = List(columns.lastIndex) { "" } + trip.notes
            row(3, values, 4, rowHeight(values, columns))
        }
        append("""</sheetData><mergeCells count="1"><mergeCell ref="A1:F1"/></mergeCells></worksheet>""")
    }

    private fun values(context: Context, trip: Trip, day: DayRow, description: String): List<String> {
        val (days, transport) = day.cards.partition { it.type == "day" }
        val places = days.map { it.title.ifBlank { it.country } }.filter(String::isNotBlank)
            .ifEmpty { transport.map { it.destination.ifBlank { it.location }.ifBlank { it.country } }.filter(String::isNotBlank) }
        val hotels = days.map { lines(it.hotel, it.location, it.bookingUrl) }.filter(String::isNotBlank)
        val overnight = transport.filter { it.sleeps }.map {
            context.getString(R.string.export_transport_name, transportName(context, it), context.getString(R.string.sleeper_label))
        }
        val journeys = transport.map { card ->
            lines(
                transportName(context, card),
                endpoint(context, trip, day.date, R.string.departure_label, card.location, card.departure, card.departureZone),
                endpoint(context, trip, day.date, R.string.arrival_label, card.destination, card.arrival, card.arrivalZone)
            )
        }
        val extra = listOf(description) + transport.map { it.content }
        return listOf(
            day.dateText,
            places.distinct().joinToString("\n"),
            (hotels + overnight).distinct().joinToString("\n\n"),
            days.map { it.content }.filter(String::isNotBlank).joinToString("\n\n"),
            journeys.joinToString("\n\n"),
            extra.filter(String::isNotBlank).joinToString("\n\n")
        )
    }

    private fun lines(vararg values: String) = values.filter(String::isNotBlank).joinToString("\n")

    private fun transportName(context: Context, card: Day): String {
        val mode = context.getString(TransportMode.label(card.type))
        return if (card.title.isBlank() || card.title.equals(mode, ignoreCase = true)) mode
        else context.getString(R.string.export_transport_name, card.title, mode)
    }

    private fun endpoint(
        context: Context, trip: Trip, rowDate: LocalDate?, label: Int, place: String, time: String, zone: String
    ): String {
        val displayZone = if (trip.displayZone == "device") ZoneId.systemDefault().id else zone.ifBlank { trip.timeZone }
        val local = if (time.isBlank()) "" else runCatching {
            val dateTime = Instant.parse(time).atZone(ZoneId.of(displayZone))
            if (dateTime.toLocalDate() == rowDate) clock.format(dateTime)
            else context.getString(R.string.export_date_time, dayMonth.format(dateTime), clock.format(dateTime))
        }.getOrDefault(time)
        val value = listOf(local, place).filter(String::isNotBlank).joinToString(" ")
        return if (value.isBlank()) "" else context.getString(R.string.export_endpoint, context.getString(label), value)
    }

    private fun excelDate(date: LocalDate): Long? {
        if (date <= excelEpoch || date > lastExcelDate) return null
        // Excel reserves serial 60 for its fictional 29 February 1900.
        val days = ChronoUnit.DAYS.between(excelEpoch, date)
        return if (date >= excelLeapDayCutover) days + 1 else days
    }

    private fun rowHeight(values: List<String>, widths: List<Int>): Int {
        val lines = values.zip(widths).maxOf { (value, width) ->
            value.lines().sumOf { ((it.length + width - 3) / (width - 2)).coerceAtLeast(1) }
        }
        return (lines * 16 + 10).coerceIn(26, 408)
    }

    private fun StringBuilder.row(number: Int, values: List<String>, style: Int, height: Int, date: Long? = null) {
        append("""<row r="$number" ht="$height" customHeight="1">""")
        values.forEachIndexed { index, value ->
            val column = 'A' + index
            val cellStyle = if (index == 0 && style == 4) 3 else style
            if (index == 0 && date != null) {
                append("""<c r="$column$number" s="$cellStyle"><v>$date</v></c>""")
            } else {
                append("""<c r="$column$number" s="$cellStyle" t="inlineStr"><is><t xml:space="preserve">""")
                append(xml(value))
                append("</t></is></c>")
            }
        }
        append("</row>")
    }

    private fun styles(context: Context): String {
        fun rgb(id: Int) = Integer.toHexString(ContextCompat.getColor(context, id)).uppercase(java.util.Locale.ROOT)
        val border = rgb(R.color.export_border)
        return """
            <styleSheet xmlns="$SHEET_NS">
              <numFmts count="1"><numFmt numFmtId="164" formatCode="dd/mm"/></numFmts>
              <fonts count="3">
                <font><sz val="11"/><color rgb="${rgb(R.color.export_ink)}"/><name val="Calibri"/></font>
                <font><b/><sz val="19"/><color rgb="${rgb(R.color.export_ink)}"/><name val="Calibri"/></font>
                <font><b/><sz val="11"/><color rgb="${rgb(R.color.export_ink)}"/><name val="Calibri"/></font>
              </fonts>
              <fills count="4">
                <fill><patternFill patternType="none"/></fill>
                <fill><patternFill patternType="gray125"/></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="${rgb(R.color.export_paper)}"/><bgColor indexed="64"/></patternFill></fill>
                <fill><patternFill patternType="solid"><fgColor rgb="${rgb(R.color.export_itinerary)}"/><bgColor indexed="64"/></patternFill></fill>
              </fills>
              <borders count="2">
                <border><left/><right/><top/><bottom/><diagonal/></border>
                <border><left style="thin"><color rgb="$border"/></left><right style="thin"><color rgb="$border"/></right><top style="thin"><color rgb="$border"/></top><bottom style="thin"><color rgb="$border"/></bottom><diagonal/></border>
              </borders>
              <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
              <cellXfs count="5">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
                <xf numFmtId="0" fontId="2" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
                <xf numFmtId="164" fontId="0" fillId="2" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
                <xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center" wrapText="1"/></xf>
              </cellXfs>
              <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
            </styleSheet>
        """
    }

    private fun ZipOutputStream.part(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.trimIndent().toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun xml(value: String): String = buildString {
        val limit = minOf(value.length, 32_767)
        var index = 0
        while (index < limit) {
            val point = value.codePointAt(index)
            val count = Character.charCount(point)
            if (index + count > limit) break
            when (point) {
                '&'.code -> append("&amp;")
                '<'.code -> append("&lt;")
                '>'.code -> append("&gt;")
                '"'.code -> append("&quot;")
                '\r'.code -> append("&#13;")
                9, 10, in 32..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(point)
            }
            index += count
        }
    }
}
