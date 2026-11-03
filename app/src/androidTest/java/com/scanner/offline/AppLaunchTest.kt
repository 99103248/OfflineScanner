package com.scanner.offline

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 启动冒烟测试：验证 Hilt 依赖图、主题设置、Compose 根界面能正常创建。
 * 任何 DI / 初始化崩溃都会在这里暴露。
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchTest {

    @Test
    fun main_activity_launches_without_crash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("MainActivity 启动后不应立即结束", activity.isFinishing)
            }
        }
    }
}
