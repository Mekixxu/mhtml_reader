package com.html_reader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ModernUiThemeTest {
    private fun context(night: Boolean, fontScale: Float = 1f): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration).apply {
            this.fontScale = fontScale
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_MHTMLReader)
    }

    @Test fun allScreensInflateInLightTheme() = assertScreensInflate(false)

    @Test fun allScreensInflateInDarkTheme() = assertScreensInflate(true)

    private fun assertScreensInflate(night: Boolean) {
        val themed = context(night)
        val layouts = listOf(
            R.layout.activity_main, R.layout.fragment_home, R.layout.fragment_files,
            R.layout.fragment_more, R.layout.fragment_reader, R.layout.fragment_favorites,
            R.layout.fragment_recents, R.layout.fragment_folders_overview,
            R.layout.fragment_tabs_overview
        )
        layouts.forEach {
            val view = LayoutInflater.from(themed).inflate(it, null)
            view.measure(
                View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
            )
            assertTrue(view.measuredWidth > 0)
        }
    }

    @Test fun primaryAndNavigationTextMeetContrastInBothThemes() {
        listOf(false, true).forEach { night ->
            val themed = context(night)
            val pairs = listOf(
                R.color.textOnPrimary to R.color.colorPrimary,
                R.color.textPrimary to R.color.colorSurface,
                R.color.textSecondary to R.color.colorSurface,
                R.color.textOnPrimaryContainer to R.color.colorPrimaryContainer
            )
            pairs.forEach { (text, background) ->
                val contrast = ColorUtils.calculateContrast(
                    ContextCompat.getColor(themed, text),
                    ContextCompat.getColor(themed, background)
                )
                assertTrue("Contrast $contrast in night=$night", contrast >= 4.5)
            }
        }
    }

    @Test fun navigationUsesThemeColorsAndPreservesAllDestinations() {
        listOf(false, true).forEach { night ->
            val themed = context(night)
            val root = LayoutInflater.from(themed).inflate(R.layout.activity_main, null)
            val nav = root.findViewById<BottomNavigationView>(R.id.main_bottom_nav)
            assertEquals(4, nav.menu.size())
            assertTrue(nav.isItemActiveIndicatorEnabled)
            val selected = nav.itemTextColor!!.getColorForState(
                intArrayOf(android.R.attr.state_checked), Color.TRANSPARENT
            )
            assertEquals(ContextCompat.getColor(themed, R.color.textOnPrimaryContainer), selected)
        }
    }

    @Test fun homePrimaryActionUsesMaterialButton() {
        val root = LayoutInflater.from(context(false)).inflate(R.layout.fragment_home, null)
        assertTrue(root.findViewById<View>(R.id.home_recents_button) is MaterialButton)
    }

    @Test fun largeTextKeepsHomeActionsAtLeastTouchTargetSize() {
        val themed = context(false, 1.5f)
        val root = LayoutInflater.from(themed).inflate(R.layout.fragment_home, null)
        val width = (320 * themed.resources.displayMetrics.density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
        )
        val action = root.findViewById<MaterialButton>(R.id.home_recents_button)
        val minimum = (48 * themed.resources.displayMetrics.density).toInt()
        assertTrue(action.measuredHeight >= minimum)
    }

    @Test fun filesSearchHasClearActionAndPdfViewportUsesRemainingHeight() {
        val inflater = LayoutInflater.from(context(true))
        val files = inflater.inflate(R.layout.fragment_files, null)
        val search = files.findViewById<TextInputEditText>(R.id.files_query_input)
        val field = search.parent.parent as TextInputLayout
        assertEquals(TextInputLayout.END_ICON_CLEAR_TEXT, field.endIconMode)
        val reader = inflater.inflate(R.layout.fragment_reader, null)
        val preview = reader.findViewById<View>(R.id.reader_pdf_preview).parent as View
        val params = preview.layoutParams as LinearLayout.LayoutParams
        assertEquals(0, params.height)
        assertEquals(1f, params.weight)
    }
}
