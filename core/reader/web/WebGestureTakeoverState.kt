package core.reader.web

/**
 * 平台无关的「有效手势接管」状态机。
 *
 * 用于把 WebView 触摸序列转换为「用户是否主动接管阅读位置」的判定：
 * - [onDown] 记录按下坐标与其绑定的内容 generation；
 * - [onMove] 仅在位移超过 touchSlop 且当前内容尺寸有效时判定为有效拖动；
 * - generation 变化（切 tab / 内容重载）会作废旧手势；
 * - [clear] 在 ACTION_CANCEL / ACTION_UP 后清理手势。
 *
 * Fragment 负责读取 ViewConfiguration 的 touchSlop 与 WebView 尺寸并接线。
 */
class WebGestureTakeoverState {
    private var downX = 0f
    private var downY = 0f
    private var downGeneration: Long? = null

    fun onDown(x: Float, y: Float, generation: Long) {
        downX = x
        downY = y
        downGeneration = generation
    }

    /**
     * 判定本次移动是否构成「有效拖动」。
     *
     * @param generation 当前内容 generation，与按下时代次不一致则忽略。
     * @param touchSlop 系统触摸阈值（[android.view.ViewConfiguration.getScaledTouchSlop]）。
     * @param hasValidContentExtent 当前内容尺寸是否有效；无效时不做接管判定，
     *        但保留手势，待尺寸有效后的后续移动仍可累计位移完成接管。
     */
    fun onMove(
        x: Float,
        y: Float,
        generation: Long,
        touchSlop: Float,
        hasValidContentExtent: Boolean
    ): Boolean {
        if (downGeneration != generation) return false
        if (!hasValidContentExtent) return false
        val dx = x - downX
        val dy = y - downY
        val slop = touchSlop.coerceAtLeast(0f)
        return dx * dx + dy * dy > slop * slop
    }

    fun clear() {
        downGeneration = null
    }
}
