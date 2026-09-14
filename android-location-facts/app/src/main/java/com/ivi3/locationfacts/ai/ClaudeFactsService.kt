package com.ivi3.locationfacts.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaTextBlock
import com.anthropic.models.beta.messages.BetaUserLocation
import com.anthropic.models.beta.messages.BetaWebSearchTool20260209
import com.anthropic.models.beta.messages.MessageCreateParams
import java.time.Duration

/**
 * Asks Claude what is interesting about a specific point on the map, with the
 * server-side web search tool doing the actual looking-up.
 *
 * Two things matter about the shape of this call:
 *
 *  * **Search is not optional.** The system prompt forbids answering from memory, and
 *    [FactsResult.searchCount] reports how many searches the server really ran, so the
 *    UI can flag an answer that nothing backed.
 *  * **Sources come from the API, not from the model's prose.** Web search results are
 *    returned as citation blocks attached to the text they support; we read those
 *    ([BetaTextBlock.citations]) instead of asking the model to type URLs, which is
 *    where invented citations come from.
 *
 * Calls block, so run them off the main thread (the ViewModel uses `Dispatchers.IO`).
 */
class ClaudeFactsService(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val maxWebSearches: Long = 6,
) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        // One request can involve several web searches plus thinking; the SDK default is
        // 10 minutes, which is longer than a phone user will ever wait.
        .timeout(Duration.ofMinutes(3))
        .maxRetries(2)
        .build()

    /**
     * @throws FactsException for anything the UI should show as a message rather than a crash.
     */
    fun factsFor(place: PlaceContext, maxFacts: Int = 5): FactsResult {
        var params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(8_000L)
            // Refusal is unlikely for local trivia, but Opus 5 can decline in principle;
            // "default" routing picks a fallback model by refusal category server-side.
            .addBeta(AnthropicBeta.SERVER_SIDE_FALLBACK_2026_07_01)
            .fallbacksDefault()
            // Thinking is on by default on Opus 5. Medium effort keeps the wait for a
            // phone user reasonable; raise it if you want more digging per fact.
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.MEDIUM).build())
            .system(SYSTEM_PROMPT)
            .addTool(webSearchTool(place))
            .addUserMessage(userPrompt(place, maxFacts))
            .build()

        val segments = mutableListOf<TextSegment>()
        var searchCount = 0
        var resumes = 0

        while (true) {
            val message = send(params)
            searchCount += message.content().count { it.serverToolUse().isPresent }
            message.content().forEach { block ->
                block.text().ifPresent { segments += it.toSegment() }
            }

            val stopReason = message.stopReason().orElse(null)
            if (stopReason == BetaStopReason.REFUSAL) {
                val explanation = message.stopDetails()
                    .flatMap { it.explanation() }
                    .orElse("The model declined to answer for this location.")
                throw FactsException(explanation, retryable = false)
            }
            // The server's own tool loop hit its iteration limit. Re-send the exchange and
            // it picks up where it left off; no extra user message is needed.
            if (stopReason == BetaStopReason.PAUSE_TURN && resumes < MAX_RESUMES) {
                resumes++
                params = params.toBuilder().addMessage(message.toParam()).build()
                continue
            }
            break
        }

        val parsed = FactsParser.parse(segments)
        if (parsed.facts.isEmpty()) {
            throw FactsException(
                "Claude answered, but nothing came back in a readable shape. Try again.",
                retryable = true,
            )
        }
        return FactsResult(
            facts = parsed.facts.take(maxFacts),
            note = parsed.note,
            searchCount = searchCount,
            model = model,
        )
    }

    private fun send(params: MessageCreateParams): BetaMessage =
        try {
            client.beta().messages().create(params)
        } catch (e: UnauthorizedException) {
            throw FactsException("That API key was rejected. Check it in Settings.", retryable = false, cause = e)
        } catch (e: RateLimitException) {
            throw FactsException("Rate limited by the API. Wait a moment and retry.", retryable = true, cause = e)
        } catch (e: AnthropicServiceException) {
            throw FactsException("The API returned an error: ${e.message}", retryable = true, cause = e)
        } catch (e: AnthropicIoException) {
            throw FactsException("Could not reach the API. Check your connection.", retryable = true, cause = e)
        }

    private fun webSearchTool(place: PlaceContext): BetaWebSearchTool20260209 {
        val builder = BetaWebSearchTool20260209.builder().maxUses(maxWebSearches)
        // Localizing search gets local-news and local-archive hits instead of national ones.
        val location = BetaUserLocation.builder().apply {
            place.city?.let { city(it) }
            place.region?.let { region(it) }
            place.countryCode?.takeIf { it.length == 2 }?.let { country(it.uppercase()) }
            place.timezone?.let { timezone(it) }
        }.build()
        if (place.city != null || place.region != null || place.countryCode != null || place.timezone != null) {
            builder.userLocation(location)
        }
        return builder.build()
    }

    private fun BetaTextBlock.toSegment(): TextSegment {
        val sources = citations().orElse(emptyList()).mapNotNull { citation ->
            citation.webSearchResultLocation()
                .map { Source(url = it.url(), title = it.title().orElse(null)) }
                .orElse(null)
        }
        return TextSegment(text = text(), sources = sources)
    }

    private fun userPrompt(place: PlaceContext, maxFacts: Int): String = buildString {
        appendLine("I have just opened an app at this spot and want to know what is interesting about it.")
        appendLine()
        append("Coordinates: %.5f, %.5f".format(place.latitude, place.longitude))
        place.accuracyMeters?.let { append(" (fix accurate to about ${it.toInt()} m)") }
        appendLine()
        place.addressLine?.let { appendLine("Nearest address: $it") }
        val administrative = listOfNotNull(
            place.city?.let { "city: $it" },
            place.region?.let { "region: $it" },
            place.countryCode?.let { "country: $it" },
        )
        if (administrative.isNotEmpty()) appendLine(administrative.joinToString(" | "))
        place.timezone?.let { appendLine("Local time zone: $it") }
        appendLine()
        append("Give me at most $maxFacts facts, in the required format.")
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"
        private const val MAX_RESUMES = 3

        internal val SYSTEM_PROMPT = """
            You are the local-knowledge engine inside a phone app. The user has just opened
            the app somewhere and wants a few genuinely interesting facts about that exact spot.

            Rules:
            1. Search the web before answering. Search for the specific street, neighbourhood,
               landmarks, and history around the coordinates you are given. Do not answer from
               memory alone.
            2. Every fact must come from a search result you actually read. Never state a fact
               you did not find in a result, and never invent a date, name, quotation or source.
               If the searches turn up little, return fewer facts and say so in a NOTE line.
               A short honest answer is better than a padded one.
            3. Be specific to the spot, not the country. What happened on this block, who lived
               or worked here, what a building used to be, a local ordinance, a geological or
               ecological quirk, a dish or a tradition that belongs to this place. Skip anything
               that would be equally true a thousand kilometres away.
            4. Write for someone standing there, looking around. Plain language, no preamble,
               no restating the place name back at them, no "did you know".

            Answer in exactly this format, and nothing else:

            TITLE: short headline, at most 60 characters
            BODY: two to four sentences

            Repeat that pair for each fact, separated by a blank line. If you need to caveat
            the results, add one final line starting with "NOTE:".
        """.trimIndent()
    }
}

/** A failure worth showing the user verbatim. [retryable] drives whether the UI offers Retry. */
class FactsException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)
