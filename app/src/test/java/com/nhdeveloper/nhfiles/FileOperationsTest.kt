package com.nhdeveloper.nhfiles

import com.nhdeveloper.nhfiles.data.Conflict
import com.nhdeveloper.nhfiles.data.FileOperations
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

class FileOperationsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ops = FileOperations()

    @Test fun createRenameDelete() = runBlocking {
        val dir = ops.createFolder(tmp.root, "a")
        assertTrue(dir.isDirectory)
        val f = ops.createFile(dir, "x.txt")
        val r = ops.rename(f, "y.txt")
        assertTrue(r.exists())
        assertFalse(f.exists())
        ops.delete(listOf(dir)) { }
        assertFalse(dir.exists())
    }

    @Test fun duplicateNameIsRejected() {
        ops.createFolder(tmp.root, "dup")
        try { ops.createFolder(tmp.root, "dup"); fail() } catch (e: IOException) { /* expected */ }
    }

    @Test fun invalidNamesRejected() {
        for (n in listOf("", "..", "a/b")) {
            try { ops.validateName(n); fail("accepted $n") } catch (e: IOException) { /* expected */ }
        }
    }

    @Test fun copyAndMoveKeepContent() = runBlocking {
        val src = tmp.newFolder("src")
        File(src, "f.txt").writeText("hello")
        val dest = tmp.newFolder("dest")
        ops.transfer(listOf(src), dest, false, Conflict.RENAME) { }
        assertEquals("hello", File(dest, "src/f.txt").readText())
        assertTrue(File(src, "f.txt").exists())
        val dest2 = tmp.newFolder("dest2")
        ops.transfer(listOf(src), dest2, true, Conflict.RENAME) { }
        assertEquals("hello", File(dest2, "src/f.txt").readText())
        assertFalse(src.exists())
    }

    @Test fun conflictPoliciesWork() = runBlocking {
        val a = tmp.newFile("a.txt").apply { writeText("new") }
        val dest = tmp.newFolder("d")
        File(dest, "a.txt").writeText("old")
        ops.transfer(listOf(a), dest, false, Conflict.SKIP) { }
        assertEquals("old", File(dest, "a.txt").readText())
        ops.transfer(listOf(a), dest, false, Conflict.RENAME) { }
        assertEquals("new", File(dest, "a (1).txt").readText())
        ops.transfer(listOf(a), dest, false, Conflict.REPLACE) { }
        assertEquals("new", File(dest, "a.txt").readText())
    }

    @Test fun cannotCopyFolderIntoItself() = runBlocking {
        val src = tmp.newFolder("self")
        val inner = File(src, "inner").apply { mkdir() }
        try {
            ops.transfer(listOf(src), inner, false, Conflict.RENAME) { }
            fail()
        } catch (e: IOException) { /* expected */ }
        Unit
    }
}
