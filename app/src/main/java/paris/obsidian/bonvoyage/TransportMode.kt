package paris.obsidian.bonvoyage

object TransportMode {
    val types = listOf("train", "boat", "bus", "flight", "car", "other")
    fun origin(type: String) = when (type) {
        "train" -> R.string.departure_station
        "boat" -> R.string.departure_port
        "bus" -> R.string.departure_stop
        "flight" -> R.string.departure_airport
        else -> R.string.origin_label
    }
    fun destination(type: String) = when (type) {
        "train" -> R.string.arrival_station
        "boat" -> R.string.arrival_port
        "bus" -> R.string.arrival_stop
        "flight" -> R.string.arrival_airport
        else -> R.string.destination_label
    }
    fun label(type: String) = when (type) {
        "train" -> R.string.transport_train
        "boat" -> R.string.transport_boat
        "bus" -> R.string.transport_bus
        "flight" -> R.string.transport_flight
        "car" -> R.string.transport_car
        "other" -> R.string.transport_other
        else -> R.string.export_transport
    }
    fun icon(type: String) = when (type) {
        "train" -> R.drawable.ic_train
        "boat" -> R.drawable.ic_boat
        "bus" -> R.drawable.ic_bus
        "flight" -> R.drawable.ic_flight
        "car" -> R.drawable.ic_car
        "other" -> R.drawable.ic_other_transport
        else -> R.drawable.ic_transport
    }
}
