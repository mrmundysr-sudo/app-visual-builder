package com.personal.appvisualbuilder.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NeedsParserTest {

    private fun sample(): String = File("../sample/sample_needs.json").readText()

    @Test fun sampleFileParses() {
        val r = NeedsParser.parse(sample())
        assertTrue(r.errors.toString(), r.ok)
        val f = r.file!!
        assertEquals("Sample Word Hunt", f.appName)
        assertEquals(2, f.pages.size)
        val gameplay = f.pages[1].need
        assertEquals("gameplay", gameplay.key)
        assertEquals(4, gameplay.assets.size)
        assertEquals(3, gameplay.regions.size)
        assertFalse(gameplay.regions[2].required)
        assertEquals(1080, gameplay.width)
    }

    @Test fun badJsonIsReportedNotThrown() {
        val r = NeedsParser.parse("{ nope")
        assertFalse(r.ok)
        assertTrue(r.errors.first().startsWith("Not valid JSON"))
    }

    @Test fun wrongSchemaAndMissingFieldsAreErrors() {
        val r = NeedsParser.parse("""{"schema":"other","pages":[{"name":""}]}""")
        assertFalse(r.ok)
        assertTrue(r.errors.any { it.contains("schema") })
        assertTrue(r.errors.any { it.contains("app_name") })
        assertTrue(r.errors.any { it.contains("no name") })
    }

    @Test fun secondCleanFileIsIgnoredWithWarning() {
        val r = NeedsParser.parse("""
            {"schema":"app-visual-builder/needs@1","app_name":"A","pages":[{"name":"P","assets":[
              {"slot":"clean","name":"a.png"},{"slot":"clean","name":"b.png"}]}]}""")
        assertTrue(r.ok)
        assertEquals(1, r.file!!.pages[0].need.assets.size)
        assertTrue(r.warnings.isNotEmpty())
    }

    @Test fun missingKeyIsDerivedFromName() {
        val r = NeedsParser.parse("""
            {"schema":"app-visual-builder/needs@1","app_name":"A","pages":[{"name":"Game Over","assets":[
              {"slot":"other","name":"retry_btn.png"}]}]}""")
        val need = r.file!!.pages[0].need
        assertEquals("game_over", need.key)
        assertEquals("retry_btn", need.assets[0].key)
    }

    @Test fun fillingANeedUsesTheWantedNameWithThePickedExtension() {
        val png = NeedAsset("c", Slot.CLEAN, "gameplay_clean.png")
        assertEquals("gameplay_clean.png", Naming.forNeed(png, "IMG_2041.PNG"))
        assertEquals("gameplay_clean.jpg", Naming.forNeed(png, "photo.jpg"))
        val psd = NeedAsset("l", Slot.PSD, "gameplay_layers.psd")
        assertEquals("gameplay_layers.psd", Naming.forNeed(psd, "whatever.bin"))
        assertNotNull(Naming.safeFile("odd name!.png"))
        assertEquals("odd_name_.png", Naming.safeFile("odd name!.png"))
    }

    @Test fun singleSlotNeedCountsAsFilledByAnyFileInTheSlot() {
        val need = NeedAsset("clean", Slot.CLEAN, "x.png")
        val empty = Page("page_01", "X")
        assertFalse(need.isFilledIn(empty))
        val free = AssetFile("1", Slot.CLEAN, "mine.png", "1.png", "mine.png", "png", 0L)
        assertTrue(need.isFilledIn(empty.copy(files = listOf(free))))
    }

    @Test fun regionPlacedRules() {
        assertFalse(NeedRegion("a", "box").placed)
        assertTrue(NeedRegion("a", "box", x = 0.1, y = 0.1, w = 0.2, h = 0.2).placed)
        assertFalse(NeedRegion("a", "box", x = 0.1, y = 0.1).placed)
        assertTrue(NeedRegion("p", "point", x = 0.5, y = 0.5).placed)
    }
}
