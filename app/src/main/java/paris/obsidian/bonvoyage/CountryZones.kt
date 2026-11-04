package paris.obsidian.bonvoyage

import java.time.ZoneId
import java.util.Locale

object CountryZones {
    fun zones(country: String): List<String> {
        val regionZones = mapOf("Tibet" to "Asia/Shanghai", "Somaliland" to "Africa/Mogadishu",
            "Transnistria" to "Europe/Chisinau", "Artsakh" to "Asia/Baku", "Kosovo" to "Europe/Belgrade")
        regionZones[country]?.let { return listOf(it) }
        val code = CountryArtwork.flagCode(country) ?: return emptyList()
        return android.icu.util.TimeZone.getAvailableIDs(code.uppercase(Locale.ROOT))
            .mapNotNull { android.icu.util.TimeZone.getCanonicalID(it) }
            .filter { runCatching { ZoneId.of(it) }.isSuccess }.distinct().sorted()
    }
    fun zones(countries: List<String>) = countries.flatMap(::zones).distinct()
    fun choose(countries: List<String>, current: String): String {
        val options = zones(countries)
        return if (current in options) current else options.firstOrNull() ?: ZoneId.systemDefault().id
    }
    fun label(context: android.content.Context, zone: String): String {
        val locale = context.resources.configuration.locales[0]
        return android.icu.text.TimeZoneNames.getInstance(locale).getExemplarLocationName(zone)
            ?: android.icu.util.TimeZone.getTimeZone(zone).getDisplayName(false, android.icu.util.TimeZone.LONG, locale)
    }
}
