package com.ivi3.locationfacts.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FactsParserTest {

    private val cityHall = Source("https://example.org/city-hall", "City Hall archive")
    private val geology = Source("https://example.org/fault", "State geological survey")

    @Test
    fun `splits facts and keeps each fact's own sources`() {
        val parsed = FactsParser.parse(
            listOf(
                TextSegment("TITLE: The dance hall under the parking garage\nBODY: "),
                TextSegment(
                    "A 1920s dance hall stood on this corner until 1961, when it was demolished for the garage.",
                    sources = listOf(cityHall),
                ),
                TextSegment("\n\nTITLE: You are standing on a fault scarp\nBODY: "),
                TextSegment(
                    "The ridge two blocks west is the surface trace of the Balcones fault zone.",
                    sources = listOf(geology),
                ),
                TextSegment("\n\nNOTE: Only two searches returned local results."),
            )
        )

        assertEquals(2, parsed.facts.size)
        assertEquals("The dance hall under the parking garage", parsed.facts[0].title)
        assertTrue(parsed.facts[0].body.startsWith("A 1920s dance hall"))
        assertEquals(listOf(cityHall), parsed.facts[0].sources)
        assertEquals(listOf(geology), parsed.facts[1].sources)
        assertEquals("Only two searches returned local results.", parsed.note)
    }

    @Test
    fun `joins a multi-line body and de-duplicates repeated sources`() {
        val parsed = FactsParser.parse(
            listOf(
                TextSegment("TITLE: A street that changed names four times\nBODY: "),
                TextSegment("It was Water Street until 1873.", sources = listOf(cityHall)),
                TextSegment("\nThen Commerce, then Front, then its present name.", sources = listOf(cityHall)),
            )
        )

        assertEquals(1, parsed.facts.size)
        assertEquals(
            "It was Water Street until 1873. Then Commerce, then Front, then its present name.",
            parsed.facts[0].body,
        )
        assertEquals(1, parsed.facts[0].sources.size)
    }

    @Test
    fun `reports no sources when nothing was cited`() {
        val parsed = FactsParser.parse(
            listOf(TextSegment("TITLE: Unsourced claim\nBODY: Something the model just knew."))
        )

        assertEquals(1, parsed.facts.size)
        assertTrue(parsed.facts[0].sources.isEmpty())
        assertEquals(null, parsed.note)
    }

    @Test
    fun `keeps prose that ignores the requested format`() {
        val parsed = FactsParser.parse(
            listOf(TextSegment("This corner was the terminus of the interurban line.", listOf(cityHall)))
        )

        assertEquals(1, parsed.facts.size)
        assertEquals("", parsed.facts[0].title)
        assertEquals("This corner was the terminus of the interurban line.", parsed.facts[0].body)
        assertEquals(listOf(cityHall), parsed.facts[0].sources)
    }

    @Test
    fun `tolerates lower-case labels and a missing BODY label`() {
        val parsed = FactsParser.parse(
            listOf(TextSegment("title: Old ferry landing\nThe ferry ran from here until the bridge opened."))
        )

        assertEquals("Old ferry landing", parsed.facts[0].title)
        assertEquals("The ferry ran from here until the bridge opened.", parsed.facts[0].body)
    }
}
