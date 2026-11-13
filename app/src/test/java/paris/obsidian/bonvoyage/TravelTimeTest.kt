package paris.obsidian.bonvoyage

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import paris.obsidian.bonvoyage.days.*

class TravelTimeTest {
    @Test fun dayFirstDatesRoundTrip() {
        assertEquals("2026-11-03", TravelTime.date("03.11.2026").toString())
        assertEquals("03.11.2026", TravelTime.label("2026-11-03"))
    }
    @Test fun invalidDateIsRejected() { assertTrue(runCatching { TravelTime.date("30.02.2026") }.isFailure) }
    @Test fun crossZoneFlightDuration() {
        val departure = TravelTime.instant("01.07.2026 10:00", "Europe/Paris").toString()
        val arrival = TravelTime.instant("01.07.2026 12:00", "America/New_York").toString()
        assertEquals(java.time.Duration.ofHours(8), TravelTime.duration(departure, arrival))
    }
    @Test fun dateLineCrossing() {
        val departure = TravelTime.instant("02.07.2026 09:00", "Asia/Tokyo").toString()
        val arrival = TravelTime.instant("01.07.2026 18:00", "Pacific/Honolulu").toString()
        assertEquals(java.time.Duration.ofHours(4), TravelTime.duration(departure, arrival))
    }
    @Test fun springGapIsRejected() { assertTrue(runCatching { TravelTime.instant("29.03.2026 02:30", "Europe/Paris") }.isFailure) }
    @Test fun autumnOverlapHasExplicitChoices() {
        val early = TravelTime.instant("25.10.2026 02:30", "Europe/Paris")
        val late = TravelTime.instant("25.10.2026 02:30", "Europe/Paris", true)
        assertEquals(3600L, late.epochSecond - early.epochSecond)
    }
    @Test fun negativeDurationIsRejectedEvenBelowOneMinute() {
        assertTrue(runCatching { TravelTime.duration("2026-07-01T10:00:30Z", "2026-07-01T10:00:00Z") }.isFailure)
    }
    @Test fun tripPastStatusUsesItsOwnZone() {
        val now = Instant.parse("2026-07-02T02:00:00Z")
        assertFalse(TravelTime.past("01.07.2026", "America/New_York", now))
        assertTrue(TravelTime.past("01.07.2026", "Asia/Tokyo", now))
    }
    @Test fun recycledCardIdentityDoesNotChangeWithText() {
        val before = Day(id = 12, content = "before")
        val after = before.copy(content = "after")
        assertTrue(DayDiffCallback.areItemsTheSame(before, after))
        assertFalse(DayDiffCallback.areContentsTheSame(before, after))
        assertFalse(DayDiffCallback.areItemsTheSame(before, Day(id = 13)))
    }
    @Test fun hotelLinksAndMapCoordinates() {
        assertEquals("https://hotel.example/rooms", PlannerUi.booking("hotel.example/rooms"))
        assertEquals("https://www.expedia.com/Hotel", PlannerUi.booking("https://www.expedia.com/Hotel"))
        assertTrue(PlannerUi.map("48.8566, 2.3522").endsWith("48.8566%2C+2.3522"))
    }
    @Test fun bookingRejectsNonWebLinksAndEmbeddedCredentials() {
        assertTrue(runCatching { PlannerUi.booking("intent://booking.com/hotel") }.isFailure)
        assertTrue(runCatching { PlannerUi.booking("https://evil@booking.com/hotel") }.isFailure)
    }
}
