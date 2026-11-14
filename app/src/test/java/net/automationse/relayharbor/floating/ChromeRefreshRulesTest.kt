package net.automationse.relayharbor.floating

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeRefreshRulesTest {
    @Test fun chromeOnly() {
        assertTrue(ChromeRefreshRules.isChrome("com.android.chrome"))
        listOf(
            "com.sec.android.app.sbrowser", "org.mozilla.firefox",
            "net.automationse.relayharbor", "com.chrome.beta", null
        ).forEach { assertFalse(ChromeRefreshRules.isChrome(it)) }
    }

    @Test fun chromeNativeReloadButtonsAcceptedWithJapaneseAndEnglishLabels() {
        assertTrue(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/menu_item_icon", "Reload this page", null
        ))
        assertTrue(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/refresh_menu_id", "再読み込み", null
        ))
        assertTrue(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/reload_button", null, "Reload"
        ))
    }

    @Test fun chromeWebsiteCannotSpoofBrowserRefreshButton() {
        assertFalse(ChromeRefreshRules.isReloadButton(null, "Reload this page", null))
        assertFalse(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/content", "Reload this page", null
        ))
        assertFalse(ChromeRefreshRules.isReloadButton(
            "com.other.app:id/menu_item_icon", "Reload this page", null
        ))
        assertFalse(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/toolbar_menu_button", "Stop loading page", null
        ))
        assertFalse(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/toolbar_menu_button", "アプリを更新", null
        ))
        assertFalse(ChromeRefreshRules.isReloadButton(
            "com.android.chrome:id/toolbar_menu_button", "更新", null
        ))
    }

    @Test fun chromeOverflowOnlyAndNoBroadWebButtons() {
        assertTrue(ChromeRefreshRules.isOverflowButton(
            "com.android.chrome:id/menu_button", "More options", null
        ))
        assertTrue(ChromeRefreshRules.isOverflowButton(
            "com.android.chrome:id/menu_button", "その他のオプション", null
        ))
        assertFalse(ChromeRefreshRules.isOverflowButton(
            null, "More options", null
        ))
        assertFalse(ChromeRefreshRules.isOverflowButton(
            "com.android.chrome:id/content", "More options", null
        ))
        assertFalse(ChromeRefreshRules.isOverflowButton(
            "com.android.chrome:id/menu_button", "Clear browsing data", null
        ))
    }
}
