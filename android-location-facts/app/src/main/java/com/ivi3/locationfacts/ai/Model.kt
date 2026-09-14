package com.ivi3.locationfacts.ai

/**
 * Everything the model is told about where the phone is. Built by the Android layer
 * (see `location/`), consumed by [ClaudeFactsService]. Deliberately plain Kotlin so
 * this whole package compiles and unit-tests on a desktop JVM.
 */
data class PlaceContext(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    /** Single-line street address, when the geocoder could produce one. */
    val addressLine: String? = null,
    val city: String? = null,
    val region: String? = null,
    /** Two-letter uppercase ISO 3166-1 code; the web search tool rejects anything else. */
    val countryCode: String? = null,
    /** IANA zone id, e.g. "America/Chicago". */
    val timezone: String? = null,
) {
    /** Short human label for the app bar: "Austin, Texas" or, failing that, coordinates. */
    val label: String
        get() = listOfNotNull(city, region).joinToString(", ").ifEmpty {
            "%.4f, %.4f".format(latitude, longitude)
        }
}

/**
 * A web page the model actually read, as reported by the API's own citation blocks —
 * not a URL the model typed into its answer. If a fact has no sources, nothing
 * grounded it and the UI says so rather than implying a source exists.
 */
data class Source(val url: String, val title: String?)

data class Fact(
    val title: String,
    val body: String,
    val sources: List<Source>,
)

data class FactsResult(
    val facts: List<Fact>,
    /** The model's own caveat line, if it wrote one. */
    val note: String? = null,
    /** How many web searches the server ran. Zero means nothing here is sourced. */
    val searchCount: Int = 0,
    val model: String = "",
)
