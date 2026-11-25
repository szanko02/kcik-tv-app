/**
 * File: VideoSettingsPanelSizer.kt
 *
 * Description: Pure-Kotlin responsive layout and height-clamping calculator for the
 * player-level Video Settings bottom sheet overlay across portrait, landscape, wide/tablet,
 * and short-screen configurations.
 */
package dev.xacnio.kciktv.mobile.ui.player

object VideoSettingsPanelSizer {

    /** Mirrors ViewGroup.LayoutParams.MATCH_PARENT without introducing an Android dependency. */
    const val MATCH_PARENT = -1

    /** Width threshold (dp) below which the sheet stays full-width even in landscape. */
    const val WIDE_BREAKPOINT_DP = 480

    /** Maximum width (dp) of the centered floating card in landscape / wide windows. */
    const val MAX_CARD_WIDTH_DP = 420

    /** Horizontal margin (dp) on each side of the floating card in landscape / wide windows. */
    const val HORIZONTAL_MARGIN_DP = 16

    /** Bottom margin (dp) separating the floating card from the bottom edge (plus safe inset). */
    const val FLOATING_BOTTOM_MARGIN_DP = 12

    /** Maximum fraction of available container height the panel content may occupy. */
    const val MAX_HEIGHT_FRACTION = 0.80f

    /** Vertical chrome overhead (dp): top handle(20) + header(52) + separator(1) + vertical list padding(16). */
    const val PANEL_OVERHEAD_DP = 89

    /** Row height (dp) on the root settings page. */
    const val ROOT_ROW_HEIGHT_DP = 56

    /** Row height (dp) on Quality and Playback Speed sub-pages. */
    const val SUB_PAGE_ROW_HEIGHT_DP = 52

    /** Maximum desired height (dp) for sub-pages before screen-height clamping. */
    const val SUB_PAGE_MAX_DESIRED_HEIGHT_DP = 420

    data class PanelLayoutMetrics(
        val isFloatingCard: Boolean,
        val widthPx: Int,
        val heightPx: Int,
        val horizontalMarginPx: Int,
        val bottomMarginPx: Int,
        val bottomPaddingPx: Int
    )

    fun dpToPx(dp: Int, density: Float): Int {
        val safeDensity = sanitizeDensity(density)
        return (dp * safeDensity).toInt()
    }

    fun computeRootDesiredHeightPx(visibleItemCount: Int, density: Float): Int {
        val safeCount = visibleItemCount.coerceAtLeast(0)
        return dpToPx(PANEL_OVERHEAD_DP + safeCount * ROOT_ROW_HEIGHT_DP, density)
    }

    fun computeSubPageDesiredHeightPx(itemCount: Int, density: Float): Int {
        val safeCount = itemCount.coerceAtLeast(0)
        val rawDp = (PANEL_OVERHEAD_DP + safeCount * SUB_PAGE_ROW_HEIGHT_DP)
            .coerceAtMost(SUB_PAGE_MAX_DESIRED_HEIGHT_DP)
        return dpToPx(rawDp, density)
    }

    fun computeLayout(
        containerWidthPx: Int,
        containerHeightPx: Int,
        density: Float,
        desiredContentHeightPx: Int,
        safeTopInsetPx: Int = 0,
        safeBottomInsetPx: Int = 0
    ): PanelLayoutMetrics {
        if (containerWidthPx <= 0 || containerHeightPx <= 0) {
            return PanelLayoutMetrics(
                isFloatingCard = false,
                widthPx = MATCH_PARENT,
                heightPx = 0,
                horizontalMarginPx = 0,
                bottomMarginPx = 0,
                bottomPaddingPx = 0
            )
        }

        val safeDensity = sanitizeDensity(density)
        val safeTop = safeTopInsetPx.coerceAtLeast(0)
        val safeBottom = safeBottomInsetPx.coerceAtLeast(0)
        val safeDesiredHeight = desiredContentHeightPx.coerceAtLeast(0)

        val wideBreakpointPx = dpToPx(WIDE_BREAKPOINT_DP, safeDensity)
        val isPortraitOrNarrow = containerWidthPx <= containerHeightPx || containerWidthPx < wideBreakpointPx
        val isFloatingCard = !isPortraitOrNarrow

        val availableHeightPx = (containerHeightPx - safeTop - safeBottom).coerceAtLeast(0)
        val maxContentHeightPx = (availableHeightPx * MAX_HEIGHT_FRACTION).toInt().coerceAtLeast(0)
        val clampedContentHeightPx = minOf(safeDesiredHeight, maxContentHeightPx)

        return if (isFloatingCard) {
            val horizontalMarginPx = dpToPx(HORIZONTAL_MARGIN_DP, safeDensity)
            val maxCardWidthPx = dpToPx(MAX_CARD_WIDTH_DP, safeDensity)
            val cardWidthPx = minOf(
                (containerWidthPx - horizontalMarginPx * 2).coerceAtLeast(0),
                maxCardWidthPx
            )
            val bottomMarginPx = dpToPx(FLOATING_BOTTOM_MARGIN_DP, safeDensity) + safeBottom
            val remainingHeightPx = (containerHeightPx - safeTop - bottomMarginPx).coerceAtLeast(0)
            val finalHeightPx = minOf(clampedContentHeightPx, remainingHeightPx)

            PanelLayoutMetrics(
                isFloatingCard = true,
                widthPx = cardWidthPx,
                heightPx = finalHeightPx,
                horizontalMarginPx = horizontalMarginPx,
                bottomMarginPx = bottomMarginPx,
                bottomPaddingPx = 0
            )
        } else {
            val remainingHeightPx = (containerHeightPx - safeTop).coerceAtLeast(0)
            val finalHeightPx = minOf(clampedContentHeightPx + safeBottom, remainingHeightPx)

            PanelLayoutMetrics(
                isFloatingCard = false,
                widthPx = MATCH_PARENT,
                heightPx = finalHeightPx,
                horizontalMarginPx = 0,
                bottomMarginPx = 0,
                bottomPaddingPx = safeBottom
            )
        }
    }

    private fun sanitizeDensity(density: Float): Float =
        if (density.isFinite() && density > 0f) density else 1f
}
