package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.config.types.ValueType
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleCategory
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.ccbluex.liquidbounce.utils.text.asPlainText
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.util.ARGB

class ZenBounceClickGui : Screen("ZenBounce".asPlainText()) {

    private val PANEL_W  = 390
    private val PANEL_H  = 250
    private val SIDE_W   = 88
    private val TITLE_H  = 28
    private val MOD_H    = 26
    private val MOD_GAP  = 2
    private val SET_H    = 18
    private val SET_GAP  = 1
    private val PAD      = 5
    private val SB_HIT   = 10
    private val SLIDER_W = 68

    private val C_PANEL_BG   = color(0xF2, 0x0A, 0x07, 0x16)
    private val C_TITLE_BG   = color(0xFF, 0x11, 0x0D, 0x20)
    private val C_SIDEBAR_BG = color(0xCC, 0x0D, 0x0A, 0x1C)
    private val C_MODULE_BG  = color(0xCC, 0x0E, 0x0B, 0x1C)
    private val C_MODULE_HOV = color(0xCC, 0x17, 0x0F, 0x28)
    private val C_MODULE_ON  = color(0xCC, 0x16, 0x0C, 0x26)
    private val C_SET_BG     = color(0xCC, 0x0B, 0x08, 0x18)
    private val C_SET_HOV    = color(0xCC, 0x12, 0x0A, 0x22)
    private val C_DIVIDER    = color(0xFF, 0x1C, 0x10, 0x30)
    private val C_ACCENT     = color(0xFF, 0x8B, 0x2F, 0xC9)
    private val C_ACCENT_LT  = color(0xFF, 0xBB, 0x66, 0xEE)
    private val C_ACCENT_DK  = color(0xFF, 0x5B, 0x10, 0x90)
    private val C_TEXT       = color(0xFF, 0xE8, 0xE8, 0xF0)
    private val C_TEXT_DIM   = color(0xFF, 0x8A, 0x88, 0xA0)
    private val C_TEXT_HINT  = color(0xFF, 0x4A, 0x48, 0x60)
    private val C_TEXT_ACC   = color(0xFF, 0xCC, 0x88, 0xFF)
    private val C_TOGGLE_OFF = color(0xFF, 0x2B, 0x20, 0x40)
    private val C_WHITE      = color(0xFF, 0xFF, 0xFF, 0xFF)

    private var selCategory: ModuleCategory = ModuleCategories.COMBAT
    private var expandedMod: ClientModule?  = null
    private var scroll: Float               = 0f
    private var cachedContentH: Int         = 0
    private var sbDragging: Boolean         = false
    private var sbDragStartY: Float         = 0f
    private var sbDragStartScroll: Float    = 0f
    private var dragSlider: RangedValue<*>? = null
    private var sliderTrackX: Int           = 0
    private var px: Int                     = 0
    private var py: Int                     = 0
    private val openTime: Long              = System.currentTimeMillis()

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        context.fill(0, 0, width, height, color(0x88, 0, 0, 0))
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        px = (width  - PANEL_W) / 2
        py = (height - PANEL_H) / 2

        val animT  = ((System.currentTimeMillis() - openTime) / 180.0).coerceAtMost(1.0).toFloat()
        val scale  = 0.88f + 0.12f * easeOut(animT)
        val pose   = context.pose()
        val cx     = (px + PANEL_W / 2).toFloat()
        val cy     = (py + PANEL_H / 2).toFloat()

        pose.pushMatrix()
        pose.translate(cx, cy)
        pose.scale(scale, scale)
        pose.translate(-cx, -cy)

        context.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL_BG)

        context.fill(px,              py,              px + PANEL_W,     py + 1,           C_ACCENT_DK)
        context.fill(px,              py + PANEL_H - 1, px + PANEL_W,   py + PANEL_H,     C_ACCENT_DK)
        context.fill(px,              py,              px + 1,            py + PANEL_H,     C_ACCENT_DK)
        context.fill(px + PANEL_W - 1, py,            px + PANEL_W,     py + PANEL_H,     C_ACCENT_DK)

        context.fill(px, py, px + PANEL_W, py + TITLE_H, C_TITLE_BG)
        context.fill(px, py + TITLE_H - 1, px + PANEL_W, py + TITLE_H, C_ACCENT_DK)

        val titleStr = "ZenBounce"
        context.text(font, titleStr.asPlainText(), px + 10, py + (TITLE_H - font.lineHeight) / 2, C_ACCENT_LT, true)
        val hint = "RSHIFT"
        context.text(font, hint.asPlainText(), px + PANEL_W - font.width(hint) - 8, py + (TITLE_H - font.lineHeight) / 2, C_TEXT_HINT, false)

        context.fill(px, py + TITLE_H, px + SIDE_W, py + PANEL_H, C_SIDEBAR_BG)
        context.fill(px + SIDE_W, py + TITLE_H, px + SIDE_W + 1, py + PANEL_H, C_DIVIDER)

        renderSidebar(context, mouseX, mouseY)
        renderModules(context, mouseX, mouseY)

        pose.popMatrix()
    }

    private fun renderSidebar(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sx = px + 4
        val sw = SIDE_W - 8
        var sy = py + TITLE_H + 8
        for (cat in ModuleCategories.entries) {
            val sel = cat == selCategory
            val hov = mouseX in sx..(sx + sw) && mouseY in sy..(sy + 22)
            when {
                sel -> {
                    context.fill(sx, sy, sx + sw, sy + 22, color(0xFF, 0x14, 0x0A, 0x24))
                    context.fill(sx, sy + 2, sx + 2, sy + 20, C_ACCENT)
                }
                hov -> context.fill(sx, sy, sx + sw, sy + 22, color(0xFF, 0x0F, 0x08, 0x20))
            }
            val col = if (sel) C_ACCENT_LT else if (hov) C_TEXT else C_TEXT_DIM
            context.text(font, cat.tag.asPlainText(), sx + 8, sy + (22 - font.lineHeight) / 2, col, sel)
            sy += 26
        }
    }

    private fun renderModules(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val modules = filteredModules()
        val cx = px + SIDE_W + 2 + PAD
        val cw = PANEL_W - SIDE_W - 2 - PAD * 2
        val cy = py + TITLE_H + 4
        val ch = PANEL_H - TITLE_H - 8

        cachedContentH = computeContentH(modules)
        scroll = scroll.coerceIn(0f, (cachedContentH - ch).toFloat().coerceAtLeast(0f))

        if (sbDragging && cachedContentH > ch) {
            val maxS  = (cachedContentH - ch).toFloat()
            val thumbH = (ch * ch.toFloat() / cachedContentH).coerceAtLeast(18f)
            val ratio  = (mouseY - sbDragStartY) / (ch - thumbH).coerceAtLeast(1f)
            scroll = (sbDragStartScroll + ratio * maxS).coerceIn(0f, maxS)
        }

        var my = cy - scroll.toInt()
        for (mod in modules) {
            if (my + MOD_H > cy && my < cy + ch) {
                renderModuleRow(context, mod, cx, my, cw, mouseX, mouseY)
            }
            my += MOD_H + MOD_GAP
            if (mod == expandedMod) {
                for (v in displayValues(mod)) {
                    if (my + SET_H > cy && my < cy + ch) {
                        renderSettingRow(context, v, cx + 6, my, cw - 12, mouseX, mouseY)
                    }
                    my += SET_H + SET_GAP
                }
                my += 2
            }
        }

        if (cachedContentH > ch) {
            val maxS   = (cachedContentH - ch).toFloat()
            val thumbH = (ch * ch.toFloat() / cachedContentH).coerceAtLeast(18f)
            val thumbY = cy + (scroll / maxS) * (ch - thumbH)
            val sbX    = cx + cw - 5
            context.fill(sbX, cy, sbX + 4, cy + ch, color(0xFF, 0x1A, 0x10, 0x30))
            context.fill(sbX, thumbY.toInt(), sbX + 4, (thumbY + thumbH).toInt(), if (sbDragging) C_ACCENT else C_ACCENT_DK)
        }
    }

    private fun renderModuleRow(context: GuiGraphicsExtractor, mod: ClientModule, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val on  = mod.enabled
        val exp = mod == expandedMod
        val hov = mx in x..(x + w - SB_HIT) && my in y..(y + MOD_H)
        val bg  = if (on) (if (hov) C_MODULE_HOV else C_MODULE_ON) else (if (hov) C_MODULE_HOV else C_MODULE_BG)

        context.fill(x, y, x + w, y + MOD_H, bg)
        context.fill(x, y, x + w, y + 1, color(0xFF, 0x16, 0x10, 0x2A))

        if (on) {
            context.fill(x, y, x + 2, y + MOD_H, C_ACCENT)
        }

        context.text(font, mod.name.asPlainText(), x + 8, y + (MOD_H - font.lineHeight) / 2, if (on) C_ACCENT_LT else C_TEXT, on)
        drawToggle(context, x + w - SB_HIT - 26, y + (MOD_H - 10) / 2, 22, 10, on)

        if (displayValues(mod).isNotEmpty()) {
            val chev = if (exp) "^" else "v"
            context.text(font, chev.asPlainText(), x + w - SB_HIT - 46, y + (MOD_H - font.lineHeight) / 2, C_TEXT_HINT, false)
        }
    }

    private fun renderSettingRow(context: GuiGraphicsExtractor, v: Value<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val hov = mx in x..(x + w - SB_HIT) && my in y..(y + SET_H)
        context.fill(x, y, x + w, y + SET_H, if (hov) C_SET_HOV else C_SET_BG)
        context.fill(x, y, x + 1, y + SET_H, C_ACCENT_DK)
        context.text(font, v.name.asPlainText(), x + 6, y + (SET_H - font.lineHeight) / 2, C_TEXT_DIM, false)

        when (v.valueType) {
            ValueType.BOOLEAN -> drawToggle(context, x + w - SB_HIT - 26, y + (SET_H - 10) / 2, 22, 10, v.get() as Boolean)
            ValueType.FLOAT, ValueType.INT -> {
                val rv   = v as RangedValue<*>
                val cur  = (rv.get() as Number).toDouble()
                val min  = (rv.range.start as Number).toDouble()
                val max  = (rv.range.endInclusive as Number).toDouble()
                val pct  = ((cur - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
                val lbl  = if (rv.valueType == ValueType.INT) cur.toInt().toString() else "%.2f".format(cur)
                val slX  = x + w - SB_HIT - SLIDER_W - 2
                val slY  = y + (SET_H - 3) / 2
                context.text(font, lbl.asPlainText(), slX - font.width(lbl) - 4, y + (SET_H - font.lineHeight) / 2, C_TEXT_ACC, false)
                context.fill(slX, slY, slX + SLIDER_W, slY + 3, color(0xFF, 0x1E, 0x1A, 0x30))
                context.fill(slX, slY, slX + (SLIDER_W * pct).toInt(), slY + 3, C_ACCENT)
                val kx = slX + (SLIDER_W * pct).toInt()
                context.fill(kx - 2, slY - 2, kx + 2, slY + 5, C_WHITE)
            }
            else -> {}
        }
    }

    private fun drawToggle(context: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, on: Boolean) {
        context.fill(x, y, x + w, y + h, if (on) C_ACCENT else C_TOGGLE_OFF)
        val kx = if (on) x + w - h else x
        context.fill(kx + 1, y + 1, kx + h - 1, y + h - 1, C_WHITE)
    }

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        if (click.button() != 0) return super.mouseClicked(click, doubled)
        val mx = click.x.toInt()
        val my = click.y.toInt()
        px = (width  - PANEL_W) / 2
        py = (height - PANEL_H) / 2

        if (isInScrollbar(mx, my)) {
            sbDragging        = true
            sbDragStartY      = my.toFloat()
            sbDragStartScroll = scroll
            return true
        }

        val sx = px + 4
        var sy = py + TITLE_H + 8
        for (cat in ModuleCategories.entries) {
            if (mx in sx..(sx + SIDE_W - 8) && my in sy..(sy + 22)) {
                if (selCategory != cat) { selCategory = cat; expandedMod = null; scroll = 0f }
                return true
            }
            sy += 26
        }

        val cx  = px + SIDE_W + 2 + PAD
        val cw  = PANEL_W - SIDE_W - 2 - PAD * 2
        val cy  = py + TITLE_H + 4
        var modY = cy - scroll.toInt()

        for (mod in filteredModules()) {
            if (mx in cx..(cx + cw - SB_HIT) && my in modY..(modY + MOD_H)) {
                if (displayValues(mod).isNotEmpty() && mx in (cx + cw - SB_HIT - 50)..(cx + cw - SB_HIT - 28)) {
                    expandedMod = if (expandedMod == mod) null else mod
                } else {
                    mod.enabled = !mod.enabled
                }
                return true
            }
            modY += MOD_H + MOD_GAP
            if (mod == expandedMod) {
                for (v in displayValues(mod)) {
                    if (mx in (cx + 6)..(cx + cw - SB_HIT - 6) && my in modY..(modY + SET_H)) {
                        handleSettingClick(v, mx, cx + 6, cw - 12)
                        return true
                    }
                    modY += SET_H + SET_GAP
                }
                modY += 2
            }
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(click: MouseButtonEvent, offsetX: Double, offsetY: Double): Boolean {
        if (click.button() != 0) return false
        if (dragSlider != null) { updateSlider(click.x.toInt()); return true }
        return false
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean {
        dragSlider = null
        sbDragging = false
        return super.mouseReleased(click)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        scroll -= (verticalAmount * 10f).toFloat()
        return true
    }

    private fun filteredModules(): List<ClientModule> =
        ModuleManager.modules.filter { it.category == selCategory }

    private fun displayValues(mod: ClientModule): List<Value<*>> =
        mod.get().filter { it.name != "Enabled" && it.valueType in listOf(ValueType.BOOLEAN, ValueType.FLOAT, ValueType.INT) }

    private fun computeContentH(modules: List<ClientModule>): Int {
        var h = 0
        for (m in modules) {
            h += MOD_H + MOD_GAP
            if (m == expandedMod) h += displayValues(m).size * (SET_H + SET_GAP) + 2
        }
        return h
    }

    private fun handleSettingClick(v: Value<*>, mx: Int, sx: Int, sw: Int) {
        when (v.valueType) {
            ValueType.BOOLEAN -> v.inner = !(v.inner as Boolean)
            ValueType.FLOAT, ValueType.INT -> {
                sliderTrackX = sx + sw - SB_HIT - SLIDER_W - 2
                dragSlider   = v as RangedValue<*>
                updateSlider(mx)
            }
            else -> {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSlider(mx: Int) {
        val rv  = dragSlider ?: return
        val t   = ((mx - sliderTrackX).toFloat() / SLIDER_W).coerceIn(0f, 1f)
        val min = (rv.range.start as Number).toDouble()
        val max = (rv.range.endInclusive as Number).toDouble()
        val raw = min + t * (max - min)
        when (rv.valueType) {
            ValueType.FLOAT -> (rv as RangedValue<Float>).inner = raw.toFloat()
            ValueType.INT   -> (rv as RangedValue<Int>).inner   = raw.toInt()
            else -> {}
        }
    }

    private fun isInScrollbar(mx: Int, my: Int): Boolean {
        if (cachedContentH == 0) return false
        val cx = px + SIDE_W + 2 + PAD
        val cw = PANEL_W - SIDE_W - 2 - PAD * 2
        val cy = py + TITLE_H + 4
        val ch = PANEL_H - TITLE_H - 8
        return cachedContentH > ch
            && mx in (cx + cw - SB_HIT)..(cx + cw)
            && my in cy..(cy + ch)
    }

    private fun easeOut(t: Float): Float { val i = 1f - t; return 1f - i * i * i }
    private fun color(a: Int, r: Int, g: Int, b: Int) = ARGB.color(a, r, g, b)

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true
}
