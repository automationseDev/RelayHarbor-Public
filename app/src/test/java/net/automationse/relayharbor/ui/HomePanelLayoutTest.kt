package net.automationse.relayharbor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomePanelLayoutTest {
    @Test fun coversAndExpandedHaveIndependentDefaultLayouts() {
        assertEquals(2, HomePanelLayout.columns(HomePanelLayout.COVER))
        assertEquals(4, HomePanelLayout.columns(HomePanelLayout.EXPANDED))
        val cover = HomePanelLayout.default(HomePanelLayout.COVER)
        val expanded = HomePanelLayout.default(HomePanelLayout.EXPANDED)
        assertEquals(HomePanelLayout.panelIds.toSet(), cover.map { it.id }.toSet())
        assertEquals(HomePanelLayout.panelIds.toSet(), expanded.map { it.id }.toSet())
        assertTrue(cover.all { it.span in 1..2 })
        assertTrue(expanded.all { it.span in 1..4 })
    }

    @Test fun dragMoveReordersWithoutLosingOrDuplicatingPanels() {
        val start = HomePanelLayout.default(HomePanelLayout.COVER)
        val changed = HomePanelLayout.move(HomePanelLayout.COVER, start, "apps", "overview")
        assertEquals("apps", changed.first().id)
        assertEquals(start.map { it.id }.toSet(), changed.map { it.id }.toSet())
        assertEquals(start.size, changed.size)
    }

    @Test fun resizeClampsLimitsAndKeepsLayoutsSeparate() {
        val cover = HomePanelLayout.default(HomePanelLayout.COVER)
        val smaller = HomePanelLayout.resize(HomePanelLayout.COVER, cover, "apps", -30, -30)
        assertEquals(1, smaller.first { it.id == "apps" }.span)
        assertEquals(1, smaller.first { it.id == "apps" }.height)
        val larger = HomePanelLayout.resize(HomePanelLayout.COVER, cover, "apps", 30, 30)
        assertEquals(2, larger.first { it.id == "apps" }.span)
        assertEquals(6, larger.first { it.id == "apps" }.height)
        assertEquals(4, HomePanelLayout.resize(
            HomePanelLayout.EXPANDED, HomePanelLayout.default(HomePanelLayout.EXPANDED),
            "overview", 20, 0
        ).first { it.id == "overview" }.span)
    }

    @Test fun visibilityAndSerializationRestoreAfterRestart() {
        val start = HomePanelLayout.default(HomePanelLayout.EXPANDED)
        val hidden = HomePanelLayout.visibility(HomePanelLayout.EXPANDED, start, "activity", false)
        val resized = HomePanelLayout.resize(HomePanelLayout.EXPANDED, hidden, "sync", -1, 2)
        val raw = HomePanelLayout.encode(HomePanelLayout.EXPANDED, resized)
        val restored = HomePanelLayout.decode(HomePanelLayout.EXPANDED, raw)
        assertEquals(resized, restored)
        assertFalse(restored.first { it.id == "activity" }.visible)
        assertFalse(HomePanelLayout.rows(HomePanelLayout.EXPANDED, restored)
            .flatten().any { it.id == "activity" })
    }

    @Test fun unknownDuplicateAndCorruptedDataCannotBreakLayouts() {
        val input = listOf(
            HomePanel("unknown", 99, 99),
            HomePanel("apps", 99, -7),
            HomePanel("apps", 1, 1),
            HomePanel("overview", -1, 100)
        )
        val normalized = HomePanelLayout.normalize(HomePanelLayout.COVER, input)
        assertEquals(HomePanelLayout.panelIds.size, normalized.size)
        assertEquals(2, normalized.first { it.id == "apps" }.span)
        assertEquals(1, normalized.first { it.id == "apps" }.height)
        assertEquals(1, normalized.first { it.id == "overview" }.span)
        assertEquals(HomePanelLayout.default(HomePanelLayout.COVER),
            HomePanelLayout.decode(HomePanelLayout.COVER, "not json"))
    }

    @Test fun localWidgetChoicesPersistAndOneTapFillKeepsExistingSizes() {
        val original = HomePanelLayout.default(HomePanelLayout.COVER)
        val edited = HomePanelLayout.resize(HomePanelLayout.COVER, original, "channels", -1, 2)
        val hidden = HomePanelLayout.miniWidgetIds.fold(edited) { result, id ->
            HomePanelLayout.visibility(HomePanelLayout.COVER, result, id, false)
        }
        val filled = HomePanelLayout.enableMiniWidgets(HomePanelLayout.COVER, hidden)
        assertTrue(filled.filter { it.id in HomePanelLayout.miniWidgetIds }.all { it.visible })
        assertEquals(
            edited.first { it.id == "channels" },
            filled.first { it.id == "channels" }
        )
        assertEquals(
            filled,
            HomePanelLayout.decode(
                HomePanelLayout.COVER,
                HomePanelLayout.encode(HomePanelLayout.COVER, filled)
            )
        )
    }

    @Test fun rowsAlwaysStayWithinGridWidth() {
        listOf(HomePanelLayout.COVER, HomePanelLayout.EXPANDED).forEach { profile ->
            val cards = HomePanelLayout.default(profile).map { it.copy(span = 1) }
            val rows = HomePanelLayout.rows(profile, cards)
            assertTrue(rows.all { row -> row.sumOf { it.span } <= HomePanelLayout.columns(profile) })
            assertEquals(cards.size, rows.flatten().size)
        }
    }
}
