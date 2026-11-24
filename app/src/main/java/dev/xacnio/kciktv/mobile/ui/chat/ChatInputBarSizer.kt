package dev.xacnio.kciktv.mobile.ui.chat

/**
 * Pure-Kotlin responsive layout calculator for the mobile live-chat input row.
 *
 * Computes visibility, button dimensions, margins, and paddings (all in dp/sp) from the
 * measured container width so `chatInput` (and logged-out `chatLoginOverlay`) never collapses
 * to 0px in narrow landscape side-chat splits (15%..35%).
 */
object ChatInputBarSizer {

    const val NORMAL_MIN_WIDTH_DP = 295
    const val COMPACT_WIDE_MIN_WIDTH_DP = 245
    const val COMPACT_MIN_WIDTH_DP = 210
    const val MINIMAL_MIN_WIDTH_DP = 150

    /** Conservative width estimate (dp) of the full loyalty pill (icon + gap + "12.4K" + padding). */
    const val NORMAL_LOYALTY_ESTIMATED_WIDTH_DP = 60

    enum class Tier {
        NORMAL,
        COMPACT,
        MINIMAL,
        ULTRA_MINIMAL
    }

    data class LayoutSpec(
        val tier: Tier,
        val containerHorizontalPaddingDp: Int,
        val inputPaddingStartDp: Int,
        val inputPaddingEndDp: Int,
        val showEmojiButton: Boolean,
        val emojiButtonSizeDp: Int,
        val emojiButtonPaddingDp: Int,
        val allowModeIndicator: Boolean,
        val modeIndicatorSizeDp: Int,
        val showLoyaltyPoints: Boolean,
        val showLoyaltyPointsText: Boolean,
        /** `null` means `WRAP_CONTENT` (used in [Tier.NORMAL]); fixed dp in [Tier.COMPACT]. */
        val loyaltyButtonWidthDp: Int?,
        val loyaltyButtonHeightDp: Int,
        val loyaltyHorizontalPaddingDp: Int,
        val loyaltyMarginStartDp: Int,
        val showStoreButton: Boolean,
        val storeButtonSizeDp: Int,
        val storeButtonPaddingDp: Int,
        val storeMarginStartDp: Int,
        val showCelebrationButton: Boolean,
        val celebrationButtonSizeDp: Int,
        val celebrationButtonPaddingDp: Int,
        val celebrationMarginStartDp: Int,
        val showSendButton: Boolean,
        val showSettingsButton: Boolean,
        val trailingButtonSizeDp: Int,
        val settingsButtonPaddingDp: Int,
        val sendButtonPaddingDp: Int,
        val trailingMarginStartDp: Int,
        val showLoginOverlay: Boolean,
        val showLoginPromptPrefix: Boolean,
        val loginTextSizeSp: Int,
        val estimatedInputWidthDp: Int,
        val estimatedTypingWidthDp: Int
    )

    fun resolveTier(containerWidthDp: Int): Tier = when {
        containerWidthDp >= NORMAL_MIN_WIDTH_DP -> Tier.NORMAL
        containerWidthDp >= COMPACT_MIN_WIDTH_DP -> Tier.COMPACT
        containerWidthDp >= MINIMAL_MIN_WIDTH_DP -> Tier.MINIMAL
        else -> Tier.ULTRA_MINIMAL
    }

    fun compute(
        containerWidthDp: Int,
        isLoggedIn: Boolean,
        hasText: Boolean = false,
        celebrationEligible: Boolean = false,
        storeEligible: Boolean = true,
        loyaltyEligible: Boolean = true
    ): LayoutSpec {
        val tier = resolveTier(containerWidthDp)
        val showSend = isLoggedIn && hasText
        val showSettings = !showSend

        val containerHorizontalPaddingDp: Int
        val inputPaddingStartDp: Int
        val inputPaddingEndDp: Int
        val showEmojiButton: Boolean
        val emojiButtonSizeDp: Int
        val emojiButtonPaddingDp: Int
        val allowModeIndicator: Boolean
        val modeIndicatorSizeDp: Int
        val showLoyaltyPoints: Boolean
        val showLoyaltyPointsText: Boolean
        val loyaltyButtonWidthDp: Int?
        val loyaltyButtonHeightDp: Int
        val loyaltyHorizontalPaddingDp: Int
        val loyaltyMarginStartDp: Int
        val showStoreButton: Boolean
        val storeButtonSizeDp: Int
        val storeButtonPaddingDp: Int
        val showCelebrationButton: Boolean
        val celebrationButtonSizeDp: Int
        val celebrationButtonPaddingDp: Int
        val trailingButtonSizeDp: Int
        val settingsButtonPaddingDp: Int
        val sendButtonPaddingDp: Int
        val trailingMarginStartDp: Int

        when (tier) {
            Tier.NORMAL -> {
                containerHorizontalPaddingDp = 12
                inputPaddingStartDp = 16
                inputPaddingEndDp = 44
                showEmojiButton = true
                emojiButtonSizeDp = 36
                emojiButtonPaddingDp = 6
                allowModeIndicator = true
                modeIndicatorSizeDp = 24

                showLoyaltyPoints = isLoggedIn && loyaltyEligible
                showLoyaltyPointsText = showLoyaltyPoints
                loyaltyButtonWidthDp = null
                loyaltyButtonHeightDp = 40
                loyaltyHorizontalPaddingDp = 4
                loyaltyMarginStartDp = if (showLoyaltyPoints) 6 else 0

                showStoreButton = isLoggedIn && storeEligible
                storeButtonSizeDp = 40
                storeButtonPaddingDp = 8

                showCelebrationButton = isLoggedIn && celebrationEligible
                celebrationButtonSizeDp = 40
                celebrationButtonPaddingDp = 8

                trailingButtonSizeDp = 44
                settingsButtonPaddingDp = 8
                sendButtonPaddingDp = 10
                trailingMarginStartDp = when {
                    showSend -> 2
                    isLoggedIn && storeEligible -> 4
                    else -> 12
                }
            }

            Tier.COMPACT -> {
                containerHorizontalPaddingDp = 6
                inputPaddingStartDp = 10
                inputPaddingEndDp = 34
                showEmojiButton = true
                emojiButtonSizeDp = 30
                emojiButtonPaddingDp = 5
                allowModeIndicator = true
                modeIndicatorSizeDp = 20

                showLoyaltyPoints = isLoggedIn && loyaltyEligible
                showLoyaltyPointsText = false
                loyaltyButtonWidthDp = 32
                loyaltyButtonHeightDp = 32
                loyaltyHorizontalPaddingDp = 0
                loyaltyMarginStartDp = if (showLoyaltyPoints) 2 else 0

                val allowCompactExtras = containerWidthDp >= COMPACT_WIDE_MIN_WIDTH_DP
                showStoreButton = isLoggedIn && storeEligible && (!hasText || allowCompactExtras)
                storeButtonSizeDp = 32
                storeButtonPaddingDp = 6

                showCelebrationButton = isLoggedIn && celebrationEligible && !hasText && allowCompactExtras
                celebrationButtonSizeDp = 32
                celebrationButtonPaddingDp = 6

                trailingButtonSizeDp = 34
                settingsButtonPaddingDp = 6
                sendButtonPaddingDp = 7
                trailingMarginStartDp = 2
            }

            Tier.MINIMAL -> {
                containerHorizontalPaddingDp = 4
                inputPaddingStartDp = 8
                inputPaddingEndDp = 30
                showEmojiButton = true
                emojiButtonSizeDp = 28
                emojiButtonPaddingDp = 4
                allowModeIndicator = true
                modeIndicatorSizeDp = 18

                showLoyaltyPoints = false
                showLoyaltyPointsText = false
                loyaltyButtonWidthDp = 32
                loyaltyButtonHeightDp = 32
                loyaltyHorizontalPaddingDp = 0
                loyaltyMarginStartDp = 0

                showStoreButton = false
                storeButtonSizeDp = 32
                storeButtonPaddingDp = 6

                showCelebrationButton = false
                celebrationButtonSizeDp = 32
                celebrationButtonPaddingDp = 6

                trailingButtonSizeDp = 32
                settingsButtonPaddingDp = 6
                sendButtonPaddingDp = 7
                trailingMarginStartDp = 2
            }

            Tier.ULTRA_MINIMAL -> {
                containerHorizontalPaddingDp = 4
                inputPaddingStartDp = 8
                inputPaddingEndDp = 8
                showEmojiButton = false
                emojiButtonSizeDp = 28
                emojiButtonPaddingDp = 4
                allowModeIndicator = false
                modeIndicatorSizeDp = 18

                showLoyaltyPoints = false
                showLoyaltyPointsText = false
                loyaltyButtonWidthDp = 32
                loyaltyButtonHeightDp = 32
                loyaltyHorizontalPaddingDp = 0
                loyaltyMarginStartDp = 0

                showStoreButton = false
                storeButtonSizeDp = 32
                storeButtonPaddingDp = 6

                showCelebrationButton = false
                celebrationButtonSizeDp = 32
                celebrationButtonPaddingDp = 6

                trailingButtonSizeDp = 30
                settingsButtonPaddingDp = 5
                sendButtonPaddingDp = 6
                trailingMarginStartDp = 2
            }
        }

        val storeMarginStartDp = if (showStoreButton) 2 else 0
        val celebrationMarginStartDp = if (showCelebrationButton) 2 else 0

        val loyaltyTotalDp = if (showLoyaltyPoints) {
            (loyaltyButtonWidthDp ?: NORMAL_LOYALTY_ESTIMATED_WIDTH_DP) + loyaltyMarginStartDp
        } else {
            0
        }
        val storeTotalDp = if (showStoreButton) storeButtonSizeDp + storeMarginStartDp else 0
        val celebrationTotalDp = if (showCelebrationButton) celebrationButtonSizeDp + celebrationMarginStartDp else 0
        val trailingTotalDp = trailingButtonSizeDp + trailingMarginStartDp

        val fixedChromeDp = (containerHorizontalPaddingDp * 2) +
            loyaltyTotalDp +
            storeTotalDp +
            celebrationTotalDp +
            trailingTotalDp

        val estimatedInputWidthDp = (containerWidthDp - fixedChromeDp).coerceAtLeast(0)
        val estimatedTypingWidthDp = (estimatedInputWidthDp - inputPaddingStartDp - inputPaddingEndDp)
            .coerceAtLeast(0)

        return LayoutSpec(
            tier = tier,
            containerHorizontalPaddingDp = containerHorizontalPaddingDp,
            inputPaddingStartDp = inputPaddingStartDp,
            inputPaddingEndDp = inputPaddingEndDp,
            showEmojiButton = showEmojiButton,
            emojiButtonSizeDp = emojiButtonSizeDp,
            emojiButtonPaddingDp = emojiButtonPaddingDp,
            allowModeIndicator = allowModeIndicator,
            modeIndicatorSizeDp = modeIndicatorSizeDp,
            showLoyaltyPoints = showLoyaltyPoints,
            showLoyaltyPointsText = showLoyaltyPointsText,
            loyaltyButtonWidthDp = loyaltyButtonWidthDp,
            loyaltyButtonHeightDp = loyaltyButtonHeightDp,
            loyaltyHorizontalPaddingDp = loyaltyHorizontalPaddingDp,
            loyaltyMarginStartDp = loyaltyMarginStartDp,
            showStoreButton = showStoreButton,
            storeButtonSizeDp = storeButtonSizeDp,
            storeButtonPaddingDp = storeButtonPaddingDp,
            storeMarginStartDp = storeMarginStartDp,
            showCelebrationButton = showCelebrationButton,
            celebrationButtonSizeDp = celebrationButtonSizeDp,
            celebrationButtonPaddingDp = celebrationButtonPaddingDp,
            celebrationMarginStartDp = celebrationMarginStartDp,
            showSendButton = showSend,
            showSettingsButton = showSettings,
            trailingButtonSizeDp = trailingButtonSizeDp,
            settingsButtonPaddingDp = settingsButtonPaddingDp,
            sendButtonPaddingDp = sendButtonPaddingDp,
            trailingMarginStartDp = trailingMarginStartDp,
            showLoginOverlay = !isLoggedIn,
            showLoginPromptPrefix = !isLoggedIn && tier == Tier.NORMAL,
            loginTextSizeSp = if (tier == Tier.ULTRA_MINIMAL) 12 else 13,
            estimatedInputWidthDp = estimatedInputWidthDp,
            estimatedTypingWidthDp = estimatedTypingWidthDp
        )
    }
}
