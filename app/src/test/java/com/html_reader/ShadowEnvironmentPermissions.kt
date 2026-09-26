package com.html_reader

import android.os.Environment
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowEnvironment

/**
 * Robolectric 未实现 Environment.isExternalStorageManager（API 30+），
 * 测试中统一返回 true，避免 FilesFragment/HomeFragment 初始化崩溃。
 */
@Implements(Environment::class)
class ShadowEnvironmentPermissions : ShadowEnvironment() {
    companion object {
        @Implementation
        @JvmStatic
        fun isExternalStorageManager(): Boolean = true
    }
}
