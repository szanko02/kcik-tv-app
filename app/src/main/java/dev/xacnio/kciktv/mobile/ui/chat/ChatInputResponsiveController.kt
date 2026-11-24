package dev.xacnio.kciktv.mobile.ui.chat

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import dev.xacnio.kciktv.mobile.MobilePlayerActivity
import kotlin.math.roundToInt

/**
 * Applies [ChatInputBarSizer] tiers to the live-chat input row whenever its measured width,
 * login state, text/celebration state, or channel store/celebration availability changes.
 */
class ChatInputResponsiveController(
    private val activity: MobilePlayerActivity
) {
    var isCelebrationAvailable: Boolean = false
        private set

    private var isSetup = false
    private var lastAppliedWidthPx: Int = -1
    private var lastAppliedSpec: ChatInputBarSizer.LayoutSpec? = null

    val allowModeIndicator: Boolean
        get() = lastAppliedSpec?.allowModeIndicator ?: true

    fun setup() {
        if (isSetup) return
        isSetup = true

        val container = activity.binding.chatInputContainer
        container.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val newWidthPx = right - left
            val oldWidthPx = oldRight - oldLeft
            if (newWidthPx > 0 && (newWidthPx != lastAppliedWidthPx || newWidthPx != oldWidthPx)) {
                container.post { applyForCurrentState() }
            }
        }
        applyForCurrentState()
    }

    fun setCelebrationAvailable(available: Boolean) {
        isCelebrationAvailable = available
        reapply()
    }

    fun reapply() {
        applyForCurrentState()
    }

    private fun applyForCurrentState() {
        val binding = try {
            activity.binding
        } catch (_: UninitializedPropertyAccessException) {
            return
        }

        val density = activity.resources.displayMetrics.density.coerceAtLeast(0.5f)
        val measuredContainerWidthPx = binding.chatInputContainer.width
        val effectiveWidthPx = when {
            measuredContainerWidthPx > 0 -> {
                lastAppliedWidthPx = measuredContainerWidthPx
                measuredContainerWidthPx
            }
            binding.sideChatContainer.visibility == View.VISIBLE && binding.sideChatContainer.width > 0 -> {
                lastAppliedWidthPx = -1
                binding.sideChatContainer.width
            }
            binding.chatContainer.width > 0 -> {
                lastAppliedWidthPx = -1
                binding.chatContainer.width
            }
            else -> {
                lastAppliedWidthPx = -1
                activity.resources.displayMetrics.widthPixels
            }
        }

        val containerWidthDp = (effectiveWidthPx / density).toInt()
        val isLoggedIn = activity.prefs.isLoggedIn
        val isCelebrationMode = try {
            activity.chatUiManager.isCelebrationMode
        } catch (_: Exception) {
            false
        }
        val isCommandLoading = binding.chatSendLoading.visibility == View.VISIBLE
        val hasText = !binding.chatInput.text.isNullOrEmpty() || isCelebrationMode || isCommandLoading

        val spec = ChatInputBarSizer.compute(
            containerWidthDp = containerWidthDp,
            isLoggedIn = isLoggedIn,
            hasText = hasText,
            celebrationEligible = isCelebrationAvailable,
            storeEligible = activity.isSubscriptionEnabled
        )

        val previousAllowModeIndicator = lastAppliedSpec?.allowModeIndicator ?: true
        lastAppliedSpec = spec

        fun Int.dp(): Int = (this * density).roundToInt()

        // 1. Container horizontal padding
        val containerPadPx = spec.containerHorizontalPaddingDp.dp()
        if (binding.chatInputContainer.paddingLeft != containerPadPx ||
            binding.chatInputContainer.paddingRight != containerPadPx
        ) {
            binding.chatInputContainer.setPadding(
                containerPadPx,
                binding.chatInputContainer.paddingTop,
                containerPadPx,
                binding.chatInputContainer.paddingBottom
            )
        }

        // 2. EditText inner start/end padding
        val inputStartPx = spec.inputPaddingStartDp.dp()
        val inputEndPx = spec.inputPaddingEndDp.dp()
        if (binding.chatInput.paddingStart != inputStartPx ||
            binding.chatInput.paddingEnd != inputEndPx
        ) {
            binding.chatInput.setPaddingRelative(
                inputStartPx,
                binding.chatInput.paddingTop,
                inputEndPx,
                binding.chatInput.paddingBottom
            )
        }

        // 3. Emoji button frame + countdown badge + mode indicator
        setVisibilityIfChanged(binding.chatEmoteButtonFrame, spec.showEmojiButton)
        val emojiSizePx = spec.emojiButtonSizeDp.dp()
        val emojiPadPx = spec.emojiButtonPaddingDp.dp()
        updateSizeAndMarginStart(binding.chatEmoteButtonFrame, emojiSizePx, emojiSizePx, null)
        updateSizeAndMarginStart(binding.chatEmoteButton, emojiSizePx, emojiSizePx, null)
        setUniformPaddingIfChanged(binding.chatEmoteButton, emojiPadPx)
        updateSizeAndMarginStart(binding.chatSlowModeCountdown, emojiSizePx, emojiSizePx, null)

        val modeSizePx = spec.modeIndicatorSizeDp.dp()
        updateSizeAndMarginStart(binding.chatModeIndicator, modeSizePx, modeSizePx, null)
        if (!spec.allowModeIndicator) {
            setVisibilityIfChanged(binding.chatModeIndicator, false)
        } else if (!previousAllowModeIndicator) {
            try {
                activity.chatUiManager.updateChatModeIndicators()
            } catch (_: Exception) {
            }
        }

        // 4. Logged-out prompt responsiveness
        setVisibilityIfChanged(binding.chatLoginPromptPrefix, spec.showLoginPromptPrefix)
        val desiredLoginSpPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            spec.loginTextSizeSp.toFloat(),
            activity.resources.displayMetrics
        )
        if (kotlin.math.abs(binding.chatLoginButton.textSize - desiredLoginSpPx) > 0.5f) {
            binding.chatLoginButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, spec.loginTextSizeSp.toFloat())
        }

        // 5. Loyalty points wrapper + icon/text group
        setVisibilityIfChanged(binding.loyaltyPointsWrapper, spec.showLoyaltyPoints)
        updateSizeAndMarginStart(
            binding.loyaltyPointsWrapper,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            spec.loyaltyMarginStartDp.dp()
        )
        setVisibilityIfChanged(binding.loyaltyPointsValueGroup, spec.showLoyaltyPointsText)
        val loyaltyWidthPx = spec.loyaltyButtonWidthDp?.dp() ?: ViewGroup.LayoutParams.WRAP_CONTENT
        val loyaltyHeightPx = spec.loyaltyButtonHeightDp.dp()
        updateSizeAndMarginStart(binding.loyaltyPointsButton, loyaltyWidthPx, loyaltyHeightPx, null)
        val loyaltyPadPx = spec.loyaltyHorizontalPaddingDp.dp()
        if (binding.loyaltyPointsButton.paddingLeft != loyaltyPadPx ||
            binding.loyaltyPointsButton.paddingRight != loyaltyPadPx
        ) {
            binding.loyaltyPointsButton.setPadding(
                loyaltyPadPx,
                binding.loyaltyPointsButton.paddingTop,
                loyaltyPadPx,
                binding.loyaltyPointsButton.paddingBottom
            )
        }

        // 6. Store & Celebration buttons
        setVisibilityIfChanged(binding.loyaltyStoreButton, spec.showStoreButton)
        val storeSizePx = spec.storeButtonSizeDp.dp()
        updateSizeAndMarginStart(binding.loyaltyStoreButton, storeSizePx, storeSizePx, spec.storeMarginStartDp.dp())
        setUniformPaddingIfChanged(binding.loyaltyStoreButton, spec.storeButtonPaddingDp.dp())

        setVisibilityIfChanged(binding.chatInputCelebrationButton, spec.showCelebrationButton)
        val celebSizePx = spec.celebrationButtonSizeDp.dp()
        updateSizeAndMarginStart(
            binding.chatInputCelebrationButton,
            celebSizePx,
            celebSizePx,
            spec.celebrationMarginStartDp.dp()
        )
        setUniformPaddingIfChanged(binding.chatInputCelebrationButton, spec.celebrationButtonPaddingDp.dp())

        // 7. Trailing action slot (settings vs send)
        val trailingSizePx = spec.trailingButtonSizeDp.dp()
        val trailingMarginPx = spec.trailingMarginStartDp.dp()

        setVisibilityIfChanged(binding.chatSettingsButton, spec.showSettingsButton)
        updateSizeAndMarginStart(binding.chatSettingsButton, trailingSizePx, trailingSizePx, trailingMarginPx)
        setUniformPaddingIfChanged(binding.chatSettingsButton, spec.settingsButtonPaddingDp.dp())

        setVisibilityIfChanged(binding.chatSendContainer, spec.showSendButton)
        updateSizeAndMarginStart(binding.chatSendContainer, trailingSizePx, trailingSizePx, trailingMarginPx)
        setVisibilityIfChanged(binding.chatSendButton, spec.showSendButton && !isCommandLoading)
        updateSizeAndMarginStart(binding.chatSendButton, trailingSizePx, trailingSizePx, null)
        setUniformPaddingIfChanged(binding.chatSendButton, spec.sendButtonPaddingDp.dp())
    }

    private fun setVisibilityIfChanged(view: View, visible: Boolean) {
        val desired = if (visible) View.VISIBLE else View.GONE
        if (view.visibility != desired) {
            view.visibility = desired
        }
    }

    private fun setUniformPaddingIfChanged(view: View, padPx: Int) {
        if (view.paddingLeft != padPx ||
            view.paddingTop != padPx ||
            view.paddingRight != padPx ||
            view.paddingBottom != padPx
        ) {
            view.setPadding(padPx, padPx, padPx, padPx)
        }
    }

    private fun updateSizeAndMarginStart(
        view: View,
        widthPx: Int,
        heightPx: Int,
        marginStartPx: Int?
    ) {
        val lp = view.layoutParams ?: return
        var changed = false
        if (lp.width != widthPx) {
            lp.width = widthPx
            changed = true
        }
        if (lp.height != heightPx) {
            lp.height = heightPx
            changed = true
        }
        if (marginStartPx != null && lp is ViewGroup.MarginLayoutParams && lp.marginStart != marginStartPx) {
            lp.marginStart = marginStartPx
            changed = true
        }
        if (changed) {
            view.layoutParams = lp
        }
    }
}
