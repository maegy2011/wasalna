package com.app.wasalna.utils

import com.app.wasalna.domain.model.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Phase 10.8.0b: places backup — Home/Work + saved places to JSON.
 * - Export/import via Storage Access Framework (no permissions needed).
 * - JSON via org.json (already a dependency — no new libs).
 * - All resolver I/O on Dispatchers.IO.
 */
object PlacesBackup {

    fun buildJson(
        homeLat: Double?,
        homeLng: Double?,
        homeAddress: String,
        workLat: Double?,
        workLng: Double?,
        workAddress: String,
        places: List<Location>,
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("home", placeObj(homeLat, homeLng, homeAddress))
        root.put("work", placeObj(workLat, workLng, workAddress))
        val arr = JSONArray()
        places.forEach { place ->
            arr.put(
                JSONObject()
                    .put("name", place.name)
                    .put("lat", place.latitude)
                    .put("lng", place.longitude)
                    .put("favorite", place.isFavorite),
            )
        }
        root.put("places", arr)
        return root.toString(2)
    }

    data class ParsedBackup(
        val homeLat: Double?,
        val homeLng: Double?,
        val homeAddress: String,
        val workLat: Double?,
        val workLng: Double?,
        val workAddress: String,
        val places: List<Location>,
    )

    fun parse(json: String): ParsedBackup {
        val root = JSONObject(json)
        val home = root.optJSONObject("home")
        val work = root.optJSONObject("work")
        val places = mutableListOf<Location>()
        val arr = root.optJSONArray("places")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                val lat = o.optDouble("lat", Double.NaN)
                val lng = o.optDouble("lng", Double.NaN)
                if (name.isEmpty() || lat.isNaN() || lng.isNaN()) continue
                places.add(
                    Location(
                        name = name,
                        latitude = lat,
                        longitude = lng,
                        isFavorite = o.optBoolean("favorite", false),
                    ),
                )
            }
        }
        return ParsedBackup(
            homeLat = home?.optDouble("lat")?.takeIf { !it.isNaN() },
            homeLng = home?.optDouble("lng")?.takeIf { !it.isNaN() },
            homeAddress = home?.optString("address").orEmpty(),
            workLat = work?.optDouble("lat")?.takeIf { !it.isNaN() },
            workLng = work?.optDouble("lng")?.takeIf { !it.isNaN() },
            workAddress = work?.optString("address").orEmpty(),
            places = places,
        )
    }

    private fun placeObj(lat: Double?, lng: Double?, address: String): JSONObject =
        JSONObject()
            .put("lat", lat ?: JSONObject.NULL)
            .put("lng", lng ?: JSONObject.NULL)
            .put("address", address)

    suspend fun readText(read: suspend () -> String): ParsedBackup =
        withContext(Dispatchers.IO) { parse(read()) }
}
