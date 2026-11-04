package paris.obsidian.bonvoyage.days

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
data class Day (
    @PrimaryKey(autoGenerate = true) var id: Long = 0,
    @ColumnInfo (name = "type")  var type: String = "", //Values: Trip(transportation)/Day/...
    @ColumnInfo (name = "content")  var content: String = "", //What's in the day
    @ColumnInfo (name = "hotel")  var hotel: String = "", //Where to sleep (Lat/Lon)
    @ColumnInfo (name = "dayNumber")  var dayNumber: Int = 0, //Which day is it in the trip
    @ColumnInfo (name = "tripID")  var tripID: Int = 0,
    @ColumnInfo(defaultValue = "''") var date: String = "",
    @ColumnInfo(defaultValue = "''") var title: String = "",
    @ColumnInfo(defaultValue = "''") var location: String = "",
    @ColumnInfo(defaultValue = "''") var bookingUrl: String = "",
    @ColumnInfo(defaultValue = "''") var departure: String = "",
    @ColumnInfo(defaultValue = "''") var arrival: String = "",
    @ColumnInfo(defaultValue = "''") var departureZone: String = "",
    @ColumnInfo(defaultValue = "''") var arrivalZone: String = "",
    @ColumnInfo(defaultValue = "0") var sleeps: Boolean = false,
    @ColumnInfo(defaultValue = "''") var destination: String = "",
    var country: String = "",
    var destinationCountry: String = ""
) : java.io.Serializable {
}
