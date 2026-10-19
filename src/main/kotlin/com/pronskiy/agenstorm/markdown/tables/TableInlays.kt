package com.pronskiy.agenstorm.markdown.tables

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayProperties
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import com.intellij.ui.scale.JBUIScale
import com.pronskiy.agenstorm.markdown.LiveMarkupController
import com.pronskiy.agenstorm.markdown.MarkupKind
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.util.IdentityHashMap

/**
 * Step Q2.3. The rendered table itself: a block inlay's renderer that lays the [model] out for the width the
 * editor offers, caches the geometry until the width, the font or the colours change, and paints it with
 * [TablePainter]. [indent] is the pixel width of the table's own indentation (a table nested in a list or a
 * quote), so the block sits where its first pipe would.
 */
class TableInlayRenderer(private val editor: EditorEx, model: TableModel, private val indent: Int) : EditorCustomElementRenderer {

    @Volatile
    var model: TableModel = model
        private set

    private class Cache(val width: Int, val key: List<Any?>, val measurer: FontMetricsMeasurer, val palette: TablePalette, val geometry: TableGeometry)

    private var cache: Cache? = null

    /** A model with the same content keeps the cached geometry; only the offsets it carries are refreshed. */
    fun update(model: TableModel) {
        if (!this.model.sameContent(model)) cache = null
        this.model = model
    }

    fun geometry(): TableGeometry = current().geometry

    fun measurer(): FontMetricsMeasurer = current().measurer

    fun palette(): TablePalette = current().palette

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = indent + geometry().width

    override fun calcHeightInPixels(inlay: Inlay<*>): Int = geometry().height + 2 * margin()

    override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
        val current = current()
        TablePainter.paint(g, current.geometry, targetRegion.x.toInt() + indent, targetRegion.y.toInt() + margin(), current.measurer, current.palette)
    }

    /** What is under [point], given in the editor content component's coordinates; null off the table. */
    fun hitTest(inlay: Inlay<*>, point: Point): Hit? {
        val bounds = inlay.bounds ?: return null
        return geometry().hitTest(point.x - bounds.x - indent, point.y - bounds.y - margin())
    }

    /** The cell's rectangle, padding included, in the editor content component's coordinates; null while not shown. */
    fun cellBounds(inlay: Inlay<*>, row: Int, column: Int): Rectangle? {
        val bounds = inlay.bounds ?: return null
        val cell = geometry().cells.firstOrNull { it.row == row && it.column == column } ?: return null
        return Rectangle(bounds.x + indent + cell.bounds.x, bounds.y + margin() + cell.bounds.y, cell.bounds.width, cell.bounds.height)
    }

    private fun current(): Cache {
        val width = availableWidth()
        val scheme = editor.colorsScheme
        val key = listOf(scheme.editorFontSize2D, scheme.editorFontName, scheme.defaultForeground, scheme.defaultBackground, JBUIScale.scale(1f))
        cache?.takeIf { it.width == width && it.key == key }?.let { return it }
        val measurer = FontMetricsMeasurer(editor.contentComponent, TableFonts.of(scheme))
        val geometry = TableLayout.layout(model, width, measurer, Padding(JBUIScale.scale(HORIZONTAL_PADDING), JBUIScale.scale(VERTICAL_PADDING)))
        return Cache(width, key, measurer, TablePalette.of(scheme), geometry).also { cache = it }
    }

    /** The viewport minus the editor's insets and the table's own indent; a fixed width before the editor is shown. */
    private fun availableWidth(): Int {
        val visible = editor.scrollingModel.visibleArea.width
        val viewport = if (visible > 0) visible else DEFAULT_WIDTH
        return maxOf(MIN_WIDTH, viewport - editor.insets.left - editor.insets.right - indent - JBUIScale.scale(RIGHT_MARGIN))
    }

    private fun margin(): Int = JBUIScale.scale(MARGIN)

    companion object {
        const val HORIZONTAL_PADDING = 12
        const val VERTICAL_PADDING = 6
        const val MARGIN = 4
        const val RIGHT_MARGIN = 16
        const val DEFAULT_WIDTH = 800
        const val MIN_WIDTH = 120
    }
}

/**
 * One block inlay per collapsed table region, owned by [LiveMarkupController] the way its block renderer is:
 * [sync] after every collector run, [refresh] after every caret-policy pass. The inlay hangs off the region's
 * start offset — the line break before the table, i.e. the end of the line above — shown below that line and
 * kept visible while the offset is folded. It is gone the moment the table is revealed and back when it collapses.
 */
class TableInlays(private val editor: EditorEx) : Disposable {

    private class Shown(val region: FoldRegion, val inlay: Inlay<TableInlayRenderer>)

    /** A table region as the last sync saw it: the table, and where the region was at the time. */
    private class Synced(val model: TableModel, val start: Int, val end: Int)

    private var shown = ArrayList<Shown>()
    private var synced: Map<FoldRegion, Synced> = emptyMap()
    private var updateScheduled = false

    init {
        editor.scrollingModel.addVisibleAreaListener(VisibleAreaListener(::visibleAreaChanged), this)
    }

    /** The old rectangle is null on an editor's first viewport change, which counts as a width change. */
    internal fun visibleAreaChanged(event: VisibleAreaEvent) {
        val width = event.newRectangle.width
        if (width > 0 && width != event.oldRectangle?.width) scheduleUpdate()
    }

    /** EDT. [tables] is the collector's latest list and [regions] the controller's regions, both of the same text. */
    fun sync(tables: List<TableModel>, regions: List<FoldRegion>) {
        val matched = IdentityHashMap<FoldRegion, Synced>()
        for (region in regions) {
            if (!region.isValid || region.getUserData(LiveMarkupController.KIND) != MarkupKind.TABLE) continue
            val model = tables.firstOrNull { it.span.endOffset == region.endOffset && it.span.startOffset >= region.startOffset } ?: continue
            matched[region] = Synced(model, region.startOffset, region.endOffset)
        }
        synced = matched
        refresh(regions)
    }

    /**
     * EDT. One inlay for every collapsed table region, none for a revealed one. Between two syncs the text moves on
     * — the caret pass after every keystroke lands here — so a table is found by its region, which follows the
     * text, and not by the offsets of the last sync: matching those took every table below the caret away while
     * text above it was typed, until the next sync put it back (the blinking a user reported). A region that only
     * moved carries its table along; one whose own text changed keeps what it shows until the next sync.
     */
    fun refresh(regions: List<FoldRegion>) {
        if (editor.isDisposed) return
        // A null model: the table's text changed since the sync, and only the next one knows what it is now.
        val wanted = IdentityHashMap<FoldRegion, TableModel?>()
        for (region in regions) {
            if (!region.isValid || region.isExpanded) continue
            val table = synced[region] ?: continue
            val moved = region.startOffset - table.start
            wanted[region] = if (region.endOffset - table.end == moved) table.model.shiftedBy(moved) else null
        }
        val kept = ArrayList<Shown>()
        for (entry in shown) {
            // An inlay left behind by text typed at its own offset is made again where the region starts.
            val inPlace = entry.inlay.isValid && entry.inlay.offset == entry.region.startOffset
            if (!inPlace || !wanted.containsKey(entry.region)) {
                Disposer.dispose(entry.inlay)
                continue
            }
            wanted.remove(entry.region)?.let { model ->
                val resized = !entry.inlay.renderer.model.sameContent(model)
                entry.inlay.renderer.update(model)
                if (resized) entry.inlay.update()
            }
            kept += entry
        }
        for ((region, model) in wanted) {
            model ?: continue
            val renderer = TableInlayRenderer(editor, model, indentOf(model))
            val properties = InlayProperties().showAbove(false).relatesToPrecedingText(true).showWhenFolded(true)
            val inlay = editor.inlayModel.addBlockElement(region.startOffset, properties, renderer) ?: continue
            kept += Shown(region, inlay)
        }
        shown = kept
    }

    fun inlays(): List<Inlay<TableInlayRenderer>> = shown.map { it.inlay }.filter { it.isValid }

    /** The rendered table and what is under [point] (editor content coordinates), or null when none is there. */
    fun hitAt(point: Point): Pair<Inlay<TableInlayRenderer>, Hit>? {
        for (inlay in inlays()) {
            val hit = inlay.renderer.hitTest(inlay, point) ?: continue
            return inlay to hit
        }
        return null
    }

    fun removeAll() {
        shown.forEach { Disposer.dispose(it.inlay) }
        shown.clear()
    }

    override fun dispose() = removeAll()

    /** Pixels between the line start and the table's first pipe: a nested table keeps its indent. */
    private fun indentOf(model: TableModel): Int {
        val document = editor.document
        val start = model.span.startOffset.coerceIn(0, document.textLength)
        val columns = start - document.getLineStartOffset(document.getLineNumber(start))
        return columns * EditorUtil.getPlainSpaceWidth(editor)
    }

    /** A width change re-lays every table out once per event-loop turn, however many events the drag produced. */
    private fun scheduleUpdate() {
        if (updateScheduled || editor.isDisposed) return
        updateScheduled = true
        ApplicationManager.getApplication().invokeLater({
            updateScheduled = false
            if (!editor.isDisposed) inlays().forEach { it.update() }
        }) { editor.isDisposed }
    }
}
