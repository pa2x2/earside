package eu.darken.capod.reaction.core.popup

import eu.darken.capod.reaction.core.popup.InEarPopUpReaction.Decision
import eu.darken.capod.reaction.core.popup.InEarPopUpReaction.Observation
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import java.time.Instant

class InEarPopUpReactionTest : BaseTest() {

    private val now = Instant.ofEpochSecond(10_000)

    private fun observation(inEar: Boolean?, connectedAt: Instant?) = Observation(
        profileId = "profile",
        eligible = true,
        keepPill = false,
        inEar = inEar,
        connectedAt = connectedAt,
    )

    private fun decide(
        previous: Observation,
        current: Observation,
        lastShownAt: Instant? = null,
        lastTakenOffAt: Instant? = null,
    ) = InEarPopUpReaction.evaluate(previous, current, lastShownAt, lastTakenOffAt, now).first

    // A session that opens with the pods already in never reports them "out" first. That has to count
    // when the pods went in while connecting, but not when AAP silently reconnects under an audio
    // connection the user has been wearing all along.
    @Test
    fun `session that comes up with the pods already in shows only for a fresh connection, once`() {
        val freshConnection = now.minusSeconds(3)
        val oldConnection = now.minusSeconds(600)

        decide(
            previous = observation(inEar = null, connectedAt = freshConnection),
            current = observation(inEar = true, connectedAt = freshConnection),
        ) shouldBe Decision.SHOW

        decide(
            previous = observation(inEar = null, connectedAt = oldConnection),
            current = observation(inEar = true, connectedAt = oldConnection),
        ) shouldBe Decision.NONE

        decide(
            previous = observation(inEar = null, connectedAt = freshConnection),
            current = observation(inEar = true, connectedAt = freshConnection),
            lastShownAt = now.minusSeconds(2),
        ) shouldBe Decision.NONE
    }

    // Seen on AirPods 5: a pod held in the hand after taking them off reported "in ear" for 1.1 s,
    // past the settle time, and the popup came back for a moment.
    @Test
    fun `putting them on right after taking them off doesn't show again`() {
        val connection = now.minusSeconds(600)
        val off = observation(inEar = false, connectedAt = connection)
        val on = observation(inEar = true, connectedAt = connection)

        decide(previous = off, current = on, lastTakenOffAt = now.minusSeconds(1)) shouldBe Decision.NONE
        decide(previous = off, current = on, lastTakenOffAt = now.minusSeconds(10)) shouldBe Decision.SHOW
    }

    // Pods that disconnect never report "taken off": the AAP session just goes away, and the cached
    // device keeps the pill up. A session drop under a live audio connection must not hide it.
    @Test
    fun `disconnecting hides, an AAP drop alone doesn't`() {
        val connection = now.minusSeconds(600)
        val worn = observation(inEar = true, connectedAt = connection)

        decide(previous = worn, current = observation(inEar = null, connectedAt = null)) shouldBe Decision.HIDE
        decide(
            previous = observation(inEar = true, connectedAt = null),
            current = observation(inEar = null, connectedAt = null),
        ) shouldBe Decision.HIDE
        decide(previous = worn, current = observation(inEar = null, connectedAt = connection)) shouldBe Decision.NONE
    }
}
