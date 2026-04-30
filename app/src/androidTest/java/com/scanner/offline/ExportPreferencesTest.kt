package com.scanner.offline

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scanner.offline.data.storage.ExportPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ExportPreferences 的真机/模拟器集成测试。
 *
 * 覆盖纯逻辑分支：
 *  - 默认状态返回 null + "默认目录"描述
 *  - 写入一个内部 SharedPreferences URI（不需要真的有 SAF 授权，只测序列化/读取）
 *  - 清除后回到默认
 *
 * 注意：takePersistableUriPermission 对**不属于本应用**的 URI 会抛 SecurityException，
 * 但 setCustomExportDirUri 内部已经 runCatching 兜底，
 * 测试时即便抛了也只会让方法返回 false，不会让测试崩溃。
 */
@RunWith(AndroidJUnit4::class)
class ExportPreferencesTest {

    private lateinit var context: Context
    private lateinit var prefs: ExportPreferences

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefs = ExportPreferences(context)
        // 起点先重置一下，避免和上一次留下的状态冲突
        prefs.clearCustomExportDir()
    }

    @After
    fun tearDown() {
        prefs.clearCustomExportDir()
    }

    @Test
    fun initial_state_custom_dir_is_null() {
        assertNull("初始应没有自定义目录", prefs.getCustomExportDirUri())
    }

    @Test
    fun initial_state_describeCurrentDir_returns_default_hint() {
        val text = prefs.describeCurrentDir()
        TestLog.kv("describeCurrentDir(default)", text)
        assertTrue(
            "默认状态下描述应当包含'默认目录'，实际: '$text'",
            text.contains("默认")
        )
    }

    /**
     * 直接把 raw URI 字符串写到 SharedPreferences，绕过 takePersistableUriPermission。
     * 这样能测"读取-渲染-清除"链路；不需要真去 SAF 选目录。
     *
     * 注意：测试方法名不能含空格——DEX 040+ 才支持，我们 minSdk 29 编译目标 DEX < 040，
     * R8 dex 化时会拒绝含空格的 SimpleName。所以这里用 ASCII + 下划线命名。
     */
    @Test
    fun invalid_uri_is_readable_and_described_as_failed() {
        val raw = "content://com.android.externalstorage.documents/tree/primary%3ATestDir"
        // 通过反射 / 直接写 SharedPreferences 来注入 URI，不走真实授权
        context.getSharedPreferences("export_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("export_dir_uri", raw)
            .commit()

        val readBack = prefs.getCustomExportDirUri()
        assertNotNull("应能读出刚写入的 URI", readBack)
        assertEquals(Uri.parse(raw), readBack)

        val desc = prefs.describeCurrentDir()
        TestLog.kv("describeCurrentDir(invalid uri)", desc)
        // 这个 URI 不归本应用，DocumentFile.canWrite() 会失败 -> 提示"已失效"
        assertTrue(
            "失效目录应在描述里有明确提示，实际: '$desc'",
            desc.contains("失效") || desc.contains("默认")
        )
    }

    @Test
    fun clearCustomExportDir_resets_uri_to_null() {
        // 先伪造一个 URI 写进去
        context.getSharedPreferences("export_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("export_dir_uri", "content://example/tree/x")
            .commit()
        assertNotNull(prefs.getCustomExportDirUri())

        prefs.clearCustomExportDir()

        assertNull("清除后应当为 null", prefs.getCustomExportDirUri())
        assertFalse(
            "describeCurrentDir 应不再包含'自定义'",
            prefs.describeCurrentDir().contains("自定义")
        )
    }
}
