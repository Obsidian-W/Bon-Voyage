package paris.obsidian.bonvoyage

import java.time.*
import java.time.format.*
import java.util.Locale

object TravelTime {
    val dayFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT)
    val tripListDateFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    val timeFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm").withResolverStyle(ResolverStyle.STRICT)
    fun date(value: String): LocalDate = sequenceOf(dayFormat, DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)).mapNotNull {
        runCatching { LocalDate.parse(value.trim(), it) }.getOrNull()
    }.firstOrNull() ?: throw IllegalArgumentException("Invalid date")
    fun label(value: String) = runCatching { dayFormat.format(date(value)) }.getOrDefault(value)
    fun localTime(value: String): LocalDateTime = sequenceOf(timeFormat,
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT)).mapNotNull {
        runCatching { LocalDateTime.parse(value.trim(), it) }.getOrNull()
    }.firstOrNull() ?: throw IllegalArgumentException("Invalid time")
    fun offsets(value: String, zone: String) = ZoneId.of(zone).rules.getValidOffsets(localTime(value))
    fun instant(value: String, zone: String, later: Boolean = false): Instant {
        val offsets = offsets(value, zone)
        require(offsets.isNotEmpty()) { "Nonexistent time" }
        return localTime(value).toInstant(if (later) offsets.last() else offsets.first())
    }
    fun local(value: String, zone: String) = if (value.isBlank()) "" else timeFormat.format(Instant.parse(value).atZone(ZoneId.of(zone)))
    fun display(value: String, zone: String) = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm (XXX)", Locale.getDefault())
        .format(Instant.parse(value).atZone(ZoneId.of(zone)))
    fun duration(departure: String, arrival: String): Duration {
        val duration = Duration.between(Instant.parse(departure), Instant.parse(arrival))
        require(!duration.isNegative) { "Arrival before departure" }
        return duration
    }
    fun past(end: String, zone: String, now: Instant = Instant.now()) = date(end).isBefore(now.atZone(ZoneId.of(zone)).toLocalDate())
}
