package core.reader.web

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

class MeasuringWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {
    fun contentWidthPxScaled(): Int = super.computeHorizontalScrollRange()
}
