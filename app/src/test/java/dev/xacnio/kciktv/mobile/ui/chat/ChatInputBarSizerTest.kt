package dev.xacnio.kciktv.mobile.ui.chat

import dev.xacnio.kciktv.mobile.ui.chat.ChatInputBarSizer.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInputBarSizerTest {

    @Test
    fun resolveTier_matchesExactWidthBoundaries() {
        assertEquals(Tier.ULTRA_MINIMAL, ChatInputBarSizer.resolveTier(110))
        assertEquals(Tier.ULTRA_MINIMAL, ChatInputBarSizer.resolveTier(148))
        assertEquals(Tier.ULTRA_MINIMAL, ChatInputBarSizer.resolveTier(149))
        assertEquals(Tier.MINIMAL, ChatInputBarSizer.resolveTier(150))
        assertEquals(Tier.MINIMAL, ChatInputBarSizer.resolveTier(167))
        assertEquals(Tier.MINIMAL, ChatInputBarSizer.resolveTier(209))
        assertEquals(Tier.COMPACT, ChatInputBarSizer.resolveTier(210))
        assertEquals(Tier.COMPACT, ChatInputBarSizer.resolveTier(245))
        assertEquals(Tier.COMPACT, ChatInputBarSizer.resolveTier(294))
        assertEquals(Tier.NORMAL, ChatInputBarSizer.resolveTier(295))
        assertEquals(Tier.NORMAL, ChatInputBarSizer.resolveTier(420))
    }

    @Test
    fun sweepAllWidthsAndStates_estimatedInputWidthAlwaysAtLeast72DpFrom115DpUp() {
        val bools = listOf(false, true)
        for (widthDp in 110..420) {
            for (isLoggedIn in bools) {
                for (hasText in bools) {
                    for (celebrationEligible in bools) {
                        for (storeEligible in bools) {
                            val spec = ChatInputBarSizer.compute(
                                containerWidthDp = widthDp,
                                isLoggedIn = isLoggedIn,
                                hasText = hasText,
                                celebrationEligible = celebrationEligible,
                                storeEligible = storeEligible
                            )
                            if (widthDp >= 115) {
                                assertTrue(
                                    "Expected input width >= 72dp at container=${widthDp}dp " +
                                        "(loggedIn=$isLoggedIn, hasText=$hasText, celeb=$celebrationEligible, " +
                                        "store=$storeEligible, tier=${spec.tier}), got ${spec.estimatedInputWidthDp}dp",
                                    spec.estimatedInputWidthDp >= 72
                                )
                            } else {
                                assertTrue(
                                    "Expected input width >= 70dp at container=${widthDp}dp, got ${spec.estimatedInputWidthDp}dp",
                                    spec.estimatedInputWidthDp >= 70
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun narrowSideChatAt19PercentPhoneWidths_keepsGenerousInputBox() {
        // 19% of 780dp landscape phone = 148dp (ULTRA_MINIMAL)
        val narrow780 = ChatInputBarSizer.compute(
            containerWidthDp = 148,
            isLoggedIn = true,
            hasText = false,
            celebrationEligible = true
        )
        assertEquals(Tier.ULTRA_MINIMAL, narrow780.tier)
        assertFalse(narrow780.showLoyaltyPoints)
        assertFalse(narrow780.showStoreButton)
        assertFalse(narrow780.showCelebrationButton)
        assertFalse(narrow780.showEmojiButton)
        assertFalse(narrow780.allowModeIndicator)
        assertEquals(30, narrow780.trailingButtonSizeDp)
        assertEquals(8, narrow780.inputPaddingStartDp)
        assertEquals(8, narrow780.inputPaddingEndDp)
        assertEquals(108, narrow780.estimatedInputWidthDp)
        assertTrue(narrow780.estimatedTypingWidthDp >= 75)

        // 19% of 880dp landscape phone = 167dp (MINIMAL)
        val narrow880 = ChatInputBarSizer.compute(
            containerWidthDp = 167,
            isLoggedIn = true,
            hasText = false,
            celebrationEligible = true
        )
        assertEquals(Tier.MINIMAL, narrow880.tier)
        assertFalse(narrow880.showLoyaltyPoints)
        assertFalse(narrow880.showStoreButton)
        assertFalse(narrow880.showCelebrationButton)
        assertTrue(narrow880.showEmojiButton)
        assertEquals(28, narrow880.emojiButtonSizeDp)
        assertEquals(32, narrow880.trailingButtonSizeDp)
        assertEquals(8, narrow880.inputPaddingStartDp)
        assertEquals(30, narrow880.inputPaddingEndDp)
        assertEquals(125, narrow880.estimatedInputWidthDp)
    }

    @Test
    fun ultraMinimalAt115Dp_guaranteesAtLeast75DpInputBoxWidth() {
        val spec = ChatInputBarSizer.compute(
            containerWidthDp = 115,
            isLoggedIn = true,
            hasText = true,
            celebrationEligible = true
        )
        assertEquals(Tier.ULTRA_MINIMAL, spec.tier)
        assertEquals(75, spec.estimatedInputWidthDp)
        assertTrue(spec.showSendButton)
        assertFalse(spec.showSettingsButton)
    }

    @Test
    fun compactTier_hidesLoyaltyTextAndAdaptsStoreAndCelebrationAround245Dp() {
        val compactNarrowIdle = ChatInputBarSizer.compute(
            containerWidthDp = 220,
            isLoggedIn = true,
            hasText = false,
            celebrationEligible = true
        )
        assertEquals(Tier.COMPACT, compactNarrowIdle.tier)
        assertTrue(compactNarrowIdle.showLoyaltyPoints)
        assertFalse(compactNarrowIdle.showLoyaltyPointsText)
        assertEquals(32, compactNarrowIdle.loyaltyButtonWidthDp)
        assertTrue(compactNarrowIdle.showStoreButton)
        assertFalse(compactNarrowIdle.showCelebrationButton)

        val compactNarrowTyping = ChatInputBarSizer.compute(
            containerWidthDp = 220,
            isLoggedIn = true,
            hasText = true,
            celebrationEligible = true
        )
        assertFalse(compactNarrowTyping.showStoreButton)
        assertFalse(compactNarrowTyping.showCelebrationButton)
        assertTrue(compactNarrowTyping.showSendButton)

        val compactWideIdle = ChatInputBarSizer.compute(
            containerWidthDp = 260,
            isLoggedIn = true,
            hasText = false,
            celebrationEligible = true
        )
        assertTrue(compactWideIdle.showStoreButton)
        assertTrue(compactWideIdle.showCelebrationButton)
        assertEquals(32, compactWideIdle.celebrationButtonSizeDp)

        val compactWideTyping = ChatInputBarSizer.compute(
            containerWidthDp = 260,
            isLoggedIn = true,
            hasText = true,
            celebrationEligible = true
        )
        assertTrue(compactWideTyping.showStoreButton)
        assertFalse(compactWideTyping.showCelebrationButton)
    }

    @Test
    fun normalTier_restoresFullControlsAndLoggedOutHidesAllPaidControls() {
        val normalLoggedIn = ChatInputBarSizer.compute(
            containerWidthDp = 360,
            isLoggedIn = true,
            hasText = false,
            celebrationEligible = true
        )
        assertEquals(Tier.NORMAL, normalLoggedIn.tier)
        assertEquals(12, normalLoggedIn.containerHorizontalPaddingDp)
        assertTrue(normalLoggedIn.showLoyaltyPoints)
        assertTrue(normalLoggedIn.showLoyaltyPointsText)
        assertNull(normalLoggedIn.loyaltyButtonWidthDp)
        assertTrue(normalLoggedIn.showStoreButton)
        assertTrue(normalLoggedIn.showCelebrationButton)
        assertEquals(40, normalLoggedIn.storeButtonSizeDp)
        assertEquals(44, normalLoggedIn.trailingButtonSizeDp)

        val narrowLoggedOut = ChatInputBarSizer.compute(
            containerWidthDp = 167,
            isLoggedIn = false,
            hasText = true,
            celebrationEligible = true
        )
        assertTrue(narrowLoggedOut.showLoginOverlay)
        assertFalse(narrowLoggedOut.showLoginPromptPrefix)
        assertFalse(narrowLoggedOut.showLoyaltyPoints)
        assertFalse(narrowLoggedOut.showStoreButton)
        assertFalse(narrowLoggedOut.showCelebrationButton)
        assertFalse(narrowLoggedOut.showSendButton)
        assertTrue(narrowLoggedOut.showSettingsButton)
        assertEquals(125, narrowLoggedOut.estimatedInputWidthDp)
    }
}
