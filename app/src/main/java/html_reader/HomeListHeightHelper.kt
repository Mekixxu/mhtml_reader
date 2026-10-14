package com.html_reader

import android.database.DataSetObserver
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListAdapter
import android.widget.ListView

internal class HomeListHeightController(
    private val listView: ListView,
    private val adapter: ListAdapter,
    private val maxVisibleRows: Int
) : AutoCloseable {
    private var measuredWidth = 0
    private var pendingMeasure: Runnable? = null

    private val observer = object : DataSetObserver() {
        override fun onChanged() = scheduleMeasure()
        override fun onInvalidated() = scheduleMeasure()
    }

    private val layoutListener = View.OnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
        val width = right - left
        if (width != measuredWidth) scheduleMeasure()
    }

    init {
        adapter.registerDataSetObserver(observer)
        listView.addOnLayoutChangeListener(layoutListener)
        scheduleMeasure()
    }

    override fun close() {
        adapter.unregisterDataSetObserver(observer)
        listView.removeOnLayoutChangeListener(layoutListener)
        pendingMeasure?.let(listView::removeCallbacks)
        pendingMeasure = null
    }

    private fun scheduleMeasure() {
        pendingMeasure?.let(listView::removeCallbacks)
        val task = Runnable {
            pendingMeasure = null
            val width = listView.width
            if (width <= 0) return@Runnable
            measuredWidth = width
            val targetHeight = HomeListHeightHelper.measureHeight(
                listView = listView,
                adapter = adapter,
                maxVisibleRows = maxVisibleRows,
                measuredWidth = width
            )
            val params = listView.layoutParams ?: return@Runnable
            if (params.height != targetHeight) {
                params.height = targetHeight
                listView.layoutParams = params
            }
        }
        pendingMeasure = task
        listView.post(task)
    }
}

internal object HomeListHeightHelper {
    fun measureHeight(
        listView: ListView,
        adapter: ListAdapter,
        maxVisibleRows: Int,
        measuredWidth: Int = listView.width
    ): Int {
        val verticalPadding = listView.paddingTop + listView.paddingBottom
        if (adapter.count == 0 || maxVisibleRows <= 0 || measuredWidth <= 0) return verticalPadding

        val contentWidth = (measuredWidth - listView.paddingLeft - listView.paddingRight).coerceAtLeast(1)
        val rowsToMeasure = minOf(adapter.count, maxVisibleRows)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val recycledByType = mutableMapOf<Int, View>()
        var rowHeight = 0

        repeat(rowsToMeasure) { position ->
            val viewType = adapter.getItemViewType(position)
            val row = adapter.getView(position, recycledByType[viewType], listView)
            recycledByType[viewType] = row
            row.measure(widthSpec, heightSpec)
            rowHeight += row.measuredHeight
        }

        val dividerHeight = if (rowsToMeasure > 1) {
            listView.dividerHeight * (rowsToMeasure - 1)
        } else {
            0
        }
        return verticalPadding + rowHeight + dividerHeight
    }
}

internal object HomeQuickActionLayoutHelper {
    fun apply(
        container: LinearLayout,
        recentButton: Button,
        favoritesButton: Button,
        availableWidthPx: Int,
        density: Float,
        fontScale: Float
    ): Boolean {
        if (availableWidthPx <= 0 || density <= 0f) return container.orientation == LinearLayout.VERTICAL
        val horizontalSpace = availableWidthPx - container.paddingLeft - container.paddingRight -
            (ACTION_GAP_DP * density).toInt()
        val buttonSpace = horizontalSpace / 2f
        val labelsFit = requiredWidth(recentButton) <= buttonSpace &&
            requiredWidth(favoritesButton) <= buttonSpace
        val stacked = availableWidthPx / density <= STACK_WIDTH_DP ||
            fontScale >= LARGE_FONT_STACK_SCALE ||
            !labelsFit
        val orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        if (container.orientation != orientation) container.orientation = orientation
        applyButtonParams(recentButton, stacked, isSecond = false, density)
        applyButtonParams(favoritesButton, stacked, isSecond = true, density)
        return stacked
    }

    private fun applyButtonParams(button: Button, stacked: Boolean, isSecond: Boolean, density: Float) {
        val gap = (ACTION_GAP_DP * density).toInt()
        val minHeight = (MIN_ACTION_HEIGHT_DP * density).toInt()
        if (button.minimumHeight < minHeight) button.minimumHeight = minHeight
        val width = if (stacked) LinearLayout.LayoutParams.MATCH_PARENT else 0
        val weight = if (stacked) 0f else 1f
        val topMargin = if (stacked && isSecond) gap else 0
        val startMargin = if (!stacked && isSecond) gap else 0
        val current = button.layoutParams as? LinearLayout.LayoutParams
        if (current == null ||
            current.width != width ||
            current.weight != weight ||
            current.topMargin != topMargin ||
            current.marginStart != startMargin
        ) {
            button.layoutParams = LinearLayout.LayoutParams(
                width,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
            ).apply {
                this.topMargin = topMargin
                marginStart = startMargin
            }
        }
    }

    internal fun requiredWidth(button: Button): Float {
        val startIconWidth = button.compoundDrawablesRelative.firstOrNull()?.intrinsicWidth ?: 0
        val iconGap = if (startIconWidth > 0) button.compoundDrawablePadding else 0
        return button.paint.measureText(button.text.toString()) +
            startIconWidth +
            iconGap +
            button.paddingStart +
            button.paddingEnd
    }

    private const val STACK_WIDTH_DP = 320
    private const val LARGE_FONT_STACK_SCALE = 1.3f
    private const val ACTION_GAP_DP = 8
    private const val MIN_ACTION_HEIGHT_DP = 48
}

internal object HomeSourceEntries {
    fun includeSdAuthorizationEntry(paths: List<String>, authorizationLabel: String): List<String> =
        if (paths.isEmpty()) listOf(authorizationLabel) else paths
}
