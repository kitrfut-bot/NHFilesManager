package com.nhdeveloper.nhfiles

import com.nhdeveloper.nhfiles.data.FileOperations
import com.nhdeveloper.nhfiles.data.ZipEntryInfo
import com.nhdeveloper.nhfiles.data.ZipManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipManagerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val zm = ZipManager(FileOperations())

    @Test fun createListExtractRoundTrip() = runBlocking {
        val src = tmp.newFolder("data")
        File(src, "a.txt").writeText("alpha")
        File(src, "sub").mkdir()
        File(src, "sub/b.txt").writeText("beta")
        val zip = File(tmp.root, "out.zip")
        zm.create(listOf(src), zip) { }
        val names = zm.list(zip).map { it.name }
        assertTrue("data/a.txt" in names)
        assertTrue("data/sub/b.txt" in names)
        val dest = File(tmp.root, "extracted")
        zm.extract(zip, dest, null) { }
        assertEquals("beta", File(dest, "data/sub/b.txt").readText())
        assertEquals("OK", zm.test(zip).substring(8, 10))
    }

    @Test fun zipSlipIsBlocked() = runBlocking {
        val zip = File(tmp.root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(ZipEntry("../evil.txt")); it.write(1); it.closeEntry()
        }
        val dest = File(tmp.root, "safe")
        try {
            zm.extract(zip, dest, null) { }
            fail("unsafe path was extracted")
        } catch (e: IOException) { /* expected */ }
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test fun corruptArchiveReportsError() = runBlocking {
        val bad = tmp.newFile("bad.zip").apply { writeText("this is not a zip") }
        try { zm.list(bad); fail() } catch (e: IOException) { /* expected */ }
        Unit
    }

    @Test fun deleteAndRenameEntries() = runBlocking {
        val src = tmp.newFile("one.txt").apply { writeText("1") }
        val zip = File(tmp.root, "m.zip")
        zm.create(listOf(src), zip) { }
        zm.renameEntry(zip, "one.txt", "two.txt") { }
        assertEquals(listOf("two.txt"), zm.list(zip).map { it.name })
        zm.deleteEntries(zip, setOf("two.txt")) { }
        assertTrue(zm.list(zip).isEmpty())
    }

    @Test fun childrenBuildsVirtualFolders() {
        val all = listOf(ZipEntryInfo("a/b/c.txt", false, 1, 1, 0), ZipEntryInfo("top.txt", false, 1, 1, 0))
        val root = ZipManager.children(all, "")
        assertEquals(listOf("a/", "top.txt"), root.map { it.name })
        assertEquals(listOf("a/b/"), ZipManager.children(all, "a/").map { it.name })
    }
}
