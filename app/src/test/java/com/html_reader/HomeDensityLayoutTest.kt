package com.html_reader

import android.content.Context
import android.content.res.Configuration
import android.app.Activity
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeDensityLayoutTest {

    @Test
    fun sourceListsMeasureActualRowsAndRetainConfiguredScrollCap() {
        val context = themedContext(fontScale = 1f)
        val list = ListView(context).apply {
            setPadding(0, dp(context, 4), 0, dp(context, 4))
            dividerHeight = dp(context, 4)
        }
        val singleAdapter = ArrayAdapter(context, R.layout.item_home_rect, listOf("Downloads"))
        val multiAdapter = ArrayAdapter(context, R.layout.item_home_rect, listOf("one", "two", "three", "four", "five"))
        list.adapter = multiAdapter
        val width = dp(context, 320)

        val oneRowHeight = HomeListHeightHelper.measureHeight(list, singleAdapter, 1, width)
        val threeRowsHeight = HomeListHeightHelper.measureHeight(list, multiAdapter, 3, width)
        val fiveRowsCappedHeight = HomeListHeightHelper.measureHeight(list, multiAdapter, 3, width)

        assertTrue(oneRowHeight > list.paddingTop + list.paddingBottom)
        assertTrue(threeRowsHeight > oneRowHeight)
        assertEquals(threeRowsHeight, fiveRowsCappedHeight)
    }

    @Test
    fun quickActionsStackForNarrowAndLargeFontButRemainInlineWhenTheyFit() {
        val context = themedContext(fontScale = 1f)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_home, null)
        val container = root.findViewById<LinearLayout>(R.id.home_actions_container)
        val recent = root.findViewById<Button>(R.id.home_recents_button)
        val favorites = root.findViewById<Button>(R.id.home_favorites_button)

        val narrowStacked = HomeQuickActionLayoutHelper.apply(
            container, recent, favorites, dp(context, 320), context.resources.displayMetrics.density, 1f
        )
        assertTrue(narrowStacked)
        assertEquals(LinearLayout.VERTICAL, container.orientation)
        assertTrue(recent.minimumHeight >= dp(context, 48))
        assertTrue(favorites.minimumHeight >= dp(context, 48))

        val inline = HomeQuickActionLayoutHelper.apply(
            container, recent, favorites, dp(context, 400), context.resources.displayMetrics.density, 1f
        )
        assertTrue(!inline)
        assertEquals(LinearLayout.HORIZONTAL, container.orientation)

        val largeFontStacked = HomeQuickActionLayoutHelper.apply(
            container, recent, favorites, dp(context, 400), context.resources.displayMetrics.density, 1.5f
        )
        assertTrue(largeFontStacked)

        recent.text = "Recent ${"translated ".repeat(30)}"
        favorites.text = "Favorites ${"translated ".repeat(30)}"
        assertTrue(HomeQuickActionLayoutHelper.requiredWidth(recent) > dp(context, 180))
        val longLabelsStack = HomeQuickActionLayoutHelper.apply(
            container, recent, favorites, dp(context, 400), context.resources.displayMetrics.density, 1f
        )
        assertTrue(longLabelsStack)
    }

    @Test
    fun sourceListHeightRecalculatesWhenAdapterRowsChange() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val context = themedContext(fontScale = 1f)
        val values = mutableListOf("Downloads")
        val adapter = ArrayAdapter(context, R.layout.item_home_rect, values)
        val list = ListView(context)
        list.adapter = adapter
        val root = FrameLayout(context).apply {
            addView(list, FrameLayout.LayoutParams(-1, dp(context, 56)))
        }
        activity.setContentView(root)
        val width = dp(context, 320)
        val height = dp(context, 400)
        activity.window.decorView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        activity.window.decorView.layout(0, 0, width, height)
        val controller = HomeListHeightController(list, adapter, maxVisibleRows = 3)
        shadowOf(Looper.getMainLooper()).idle()
        val singleRowHeight = list.layoutParams.height

        values += listOf("SD card", "Network")
        adapter.notifyDataSetChanged()
        shadowOf(Looper.getMainLooper()).idle()
        val multipleRowsHeight = list.layoutParams.height

        controller.close()
        activityController.pause().stop().destroy()
        assertTrue(
            "Expected adapter change to grow list: one=$singleRowHeight, many=$multipleRowsHeight, width=${list.width}",
            multipleRowsHeight > singleRowHeight
        )
    }

    @Test
    fun compactHomeKeepsSourceCardNearTitleAndRetainsSafEntry() {
        val context = themedContext(fontScale = 1f)
        val root = LayoutInflater.from(context).inflate(R.layout.fragment_home, null)
        val width = dp(context, 360)
        val height = dp(context, 640)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, width, height)

        val title = root.findViewById<View>(R.id.home_page_title)
        val sourceHeading = root.findViewById<View>(R.id.home_storage_heading)
        val titleLocation = IntArray(2)
        val sourceLocation = IntArray(2)
        title.getLocationOnScreen(titleLocation)
        sourceHeading.getLocationOnScreen(sourceLocation)
        val reducedGap = sourceLocation[1] - titleLocation[1]

        assertTrue(reducedGap < dp(context, 180))
        assertEquals(
            listOf(context.getString(R.string.home_sd_add_saf)),
            HomeSourceEntries.includeSdAuthorizationEntry(emptyList(), context.getString(R.string.home_sd_add_saf))
        )
        assertTrue(root.findViewById<ListView>(R.id.home_sd_list) != null)
    }

    private fun themedContext(fontScale: Float): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply {
            this.fontScale = fontScale
        }
        return ContextThemeWrapper(
            base.createConfigurationContext(configuration),
            R.style.Theme_MHTMLReader
        )
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()
}
