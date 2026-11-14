package net.automationse.relayharbor.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidNotificationRulesTest {
    private val pkg = "jp.naver.line.android"
    private val route = AndroidAppRoute(pkg, 42L)

    @Test fun captureIsOptInAndSelfNotificationsAlwaysExcluded() {
        assertFalse(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", null, pkg, "Alice", "Hello"
        ))
        assertTrue(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", route, pkg, "Alice", "Hello"
        ))
        assertFalse(AndroidNotificationRules.allowRoute(
            pkg, route, pkg, "Alice", "Hello"
        ))
        assertFalse(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", route.copy(enabled = false), pkg, "Alice", "Hello"
        ))
        assertFalse(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", route.copy(channelId = 0L), pkg, "Alice", "Hello"
        ))
    }

    @Test fun keywordMatchesTitleOrBodyButNotUnrelatedApp() {
        val filtered = route.copy(keyword = "会議")
        assertTrue(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", filtered, pkg, "会議の予定", "開始"
        ))
        assertTrue(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", filtered, pkg, "予定", "会議が始まります"
        ))
        assertFalse(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", filtered, pkg, "配達", "荷物が届きます"
        ))
        assertFalse(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", filtered, "com.google.android.gm",
            "会議", "開始"
        ))
    }

    @Test fun credentialsAndOneTimeCodesAreConservativelyExcluded() {
        val sensitive = listOf(
            "ログインの認証コードは 123456 です",
            "ワンタイムパスワード 147258",
            "Your verification code: 123456",
            "2FA code 846392",
            "123456"
        )
        sensitive.forEach { content ->
            assertTrue(content, AndroidNotificationRules.isSensitive("SMS", content))
            assertFalse(AndroidNotificationRules.allowRoute(
                "net.automationse.relayharbor", route, pkg, "SMS", content
            ))
        }
        assertFalse(AndroidNotificationRules.isSensitive("Alice", "明日の予定、了解です"))
    }

    @Test fun malformedPackagesAndEmptyNotificationsAreIgnored() {
        assertFalse(AndroidNotificationRules.validPackage("bad package"))
        assertFalse(AndroidNotificationRules.validPackage("line"))
        assertTrue(AndroidNotificationRules.validPackage("com.example.mail"))
        assertFalse(AndroidNotificationRules.allowRoute(
            "net.automationse.relayharbor", route, pkg, "", ""
        ))
    }

    @Test fun stableNotificationRevisionsDedupeIdenticalUpdates() {
        val source = AndroidNotificationRules.source(pkg, "0|jp.naver.line.android|13")
        assertEquals(source, AndroidNotificationRules.source(pkg, "0|jp.naver.line.android|13"))
        assertFalse(source.contains("0|jp.naver.line.android|13"))
        assertEquals(
            AndroidNotificationRules.revision("Alice", "こんにちは"),
            AndroidNotificationRules.revision("Alice", "こんにちは")
        )
        assertNotEquals(
            AndroidNotificationRules.revision("Alice", "こんにちは"),
            AndroidNotificationRules.revision("Alice", "新しいメッセージ")
        )
    }
}
