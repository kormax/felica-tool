package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.KeyVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesAuthenticationBehaviorTest {
    private val idm = "0102030405060708"

    private fun profile(behavior: DesAuthenticationBehavior) =
        SystemProfile(
            systemCode = "1234",
            idm = idm,
            rootAreaCode = "0000",
            nodes =
                listOf(
                    NodeProfile(
                        "FFFF",
                        AuthenticationNodeKind.SYSTEM,
                        KeyVersion.fromInt(1),
                        listOf("0000"),
                    ),
                    NodeProfile(
                        "0000",
                        AuthenticationNodeKind.AREA,
                        KeyVersion.fromInt(1),
                        listOf("0000"),
                    ),
                    NodeProfile(
                        "1008",
                        AuthenticationNodeKind.AUTHENTICATION_REQUIRED_SERVICE,
                        KeyVersion.fromInt(1),
                        listOf("0000"),
                    ),
                ),
            behavior = behavior,
        )

    private val innerSystemNode =
        DesAuthenticationScope("1234", idm, listOf("0000", "FFFF", "0000", "FFFF"), listOf("1008"))

    @Test
    fun testProvenLeadingPlacementAllowsAnyPosition() {
        val behavior =
            DesAuthenticationBehavior(
                supported = true,
                supportedPlacements =
                    setOf(
                        AuthenticationPlacement(
                            AuthenticationListKind.AREA,
                            AuthenticationNodeKind.SYSTEM,
                            AuthenticationListPosition.LEADING,
                        ),
                    ),
            )
        assertNull(behavior.rejectionReason(innerSystemNode, profile(behavior)))
    }

    @Test
    fun testUnprovenPlacementIsRejected() {
        val behavior = DesAuthenticationBehavior(supported = true)
        val rejection = behavior.rejectionReason(innerSystemNode, profile(behavior))
        assertTrue(rejection, rejection!!.contains("placement support"))
    }

    @Test
    fun testProvenTrailingPlacementDoesNotCoverLeading() {
        val behavior =
            DesAuthenticationBehavior(
                supported = true,
                supportedPlacements =
                    setOf(
                        AuthenticationPlacement(
                            AuthenticationListKind.AREA,
                            AuthenticationNodeKind.SYSTEM,
                            AuthenticationListPosition.TRAILING,
                        ),
                    ),
            )
        assertEquals(
            "Area code FFFF (SYSTEM) at 2/4 requires leading placement support",
            behavior.rejectionReason(innerSystemNode, profile(behavior)),
        )
    }
}
