package com.pronskiy.agenstorm.terminal

import junit.framework.TestCase

/** The editor's area above a maximized terminal stays empty while it is a strip and no file is open (Roman, 2026-10-19). */
class EmptyEditorStripTest : TestCase() {

    fun testTheEditorsGoOnlyWhileNoFileIsOpenAndTheAreaIsAStrip() {
        assertTrue(EmptyEditorStrip.shouldHide(noFiles = true, areaHeight = 30, maxStripHeight = 64))
        assertFalse("a file open: its tab row shows", EmptyEditorStrip.shouldHide(noFiles = false, areaHeight = 30, maxStripHeight = 64))
        assertFalse("room for the empty editor", EmptyEditorStrip.shouldHide(noFiles = true, areaHeight = 300, maxStripHeight = 64))
        assertFalse("not laid out yet", EmptyEditorStrip.shouldHide(noFiles = true, areaHeight = 0, maxStripHeight = 64))
        assertTrue(EmptyEditorStrip.shouldHide(noFiles = true, areaHeight = 64, maxStripHeight = 64))
    }
}
