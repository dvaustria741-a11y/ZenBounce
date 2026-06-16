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
import net.ccbluex.liquidbounce.utils.input.InputBind
import net.minecraft.util.ARGB

/**
 * ZenBounce ClickGUI - Lunar Client-style module grid.
 *
 * Layout:
 *  - Left sidebar: category list (vertical, accent-pill selection).
 *  - Main area: a responsive grid of module cards. Clicking a card toggles
 *    the module. Cards with settings show a "..." indicator in the
 *    bottom-right corner - clicking it opens a settings drawer on the right
 *    edge of the panel (the grid reflows to make room).
 *  - Color palette: LiquidBounce-style light blue accent on a dark navy
 *    background, instead of the previous purple ZenClient palette.
 */
class ZenBounceClickGui : Screen("ZenBounce".asPlainText()) {

    companion object {
        // Persisted across screen open/close so the user returns to where they left off.
        private var savedCategory: ModuleCategory = ModuleCategories.COMBAT
        private val savedScroll: MutableMap<ModuleCategory, Float> = mutableMapOf()

        // Toast notification queue (message, expire timestamp)
        val notifications: ArrayDeque<Pair<String, Long>> = ArrayDeque()

        fun notify(message: String) {
            notifications.addLast(Pair(message, System.currentTimeMillis() + 2500L))
        }
    }

    // -- Layout --------------------------------------------------------------
    private val PANEL_W   = 400
    private val PANEL_H   = 260
    private val SIDE_W    = 80
    private val TITLE_H   = 24
    private val PAD       = 6

    private val CARD_W    = 90
    private val CARD_H    = 40
    private val CARD_GAP  = 5
    private val GEAR_HIT  = 14   // bottom-right hit-box on a card that opens the drawer

    private val DRAWER_W  = 144
    private val SET_H     = 17
    private val SET_GAP   = 2
    private val SLIDER_W  = 54
    private val SB_W      = 4

    // -- Colours - LiquidBounce-style light blue on dark navy ----------------
    private val C_PANEL_BG    = color(0xF2, 0x07, 0x10, 0x1A)
    private val C_TITLE_BG    = color(0xFF, 0x0A, 0x16, 0x22)
    private val C_SIDEBAR_BG  = color(0xCC, 0x08, 0x12, 0x1E)
    private val C_DRAWER_BG   = color(0xEE, 0x09, 0x14, 0x22)
    private val C_CARD_BG     = color(0xCC, 0x0D, 0x1A, 0x28)
    private val C_CARD_HOV    = color(0xCC, 0x12, 0x22, 0x34)
    private val C_CARD_ON     = color(0xCC, 0x10, 0x2C, 0x44)
    private val C_CARD_ON_HOV = color(0xCC, 0x15, 0x35, 0x50)
    private val C_SET_BG      = color(0xCC, 0x0A, 0x16, 0x24)
    private val C_SET_HOV     = color(0xCC, 0x10, 0x1E, 0x2E)
    private val C_DIVIDER     = color(0xFF, 0x15, 0x24, 0x34)
    private val C_BORDER      = color(0xFF, 0x1C, 0x30, 0x44)

    // Accent: light blue, a la LiquidBounce
    private val C_ACCENT      = color(0xFF, 0x4F, 0xC3, 0xF7)
    private val C_ACCENT_LT   = color(0xFF, 0xA0, 0xE6, 0xFF)
    private val C_ACCENT_DK   = color(0xFF, 0x16, 0x5E, 0x80)
    private val C_ACCENT_GLOW = color(0x30, 0x4F, 0xC3, 0xF7)

    private val C_TEXT        = color(0xFF, 0xE8, 0xF2, 0xFA)
    private val C_TEXT_DIM    = color(0xFF, 0x86, 0x9C, 0xAE)
    private val C_TEXT_HINT   = color(0xFF, 0x46, 0x58, 0x68)
    private val C_TEXT_ACC    = color(0xFF, 0x8A, 0xDD, 0xFF)
    private val C_TOGGLE_OFF  = color(0xFF, 0x22, 0x30, 0x40)
    private val C_WHITE       = color(0xFF, 0xFF, 0xFF, 0xFF)

    // -- State -----------------------------------------------------------------
    private var selCategory: ModuleCategory = savedCategory
    private var expandedMod: ClientModule?  = null
    private var bindingMod: ClientModule?   = null   // module waiting for a key press
    private var scroll: Float               = savedScroll[savedCategory] ?: 0f
    private var cachedContentH: Int         = 0
    private var sbDragging: Boolean         = false
    private var sbDragStartY: Float         = 0f
    private var sbDragStartScroll: Float    = 0f
    private var dragSlider: RangedValue<*>? = null
    private var sliderTrackX: Int           = 0
    private var px: Int                     = 0
    private var py: Int                     = 0
    private val openTime: Long              = System.currentTimeMillis()

    // -- Render ------------------------------------------------------------------

    override fun extractBackground(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        context.fill(0, 0, width, height, color(0x88, 0, 0, 0))
    }

    override fun extractRenderState(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        px = (width  - PANEL_W) / 2
        py = (height - PANEL_H) / 2

        val animT = ((System.currentTimeMillis() - openTime) / 180.0).coerceAtMost(1.0).toFloat()
        val scale = 0.88f + 0.12f * easeOut(animT)
        val pose  = context.pose()
        val cx    = (px + PANEL_W / 2).toFloat()
        val cy    = (py + PANEL_H / 2).toFloat()

        pose.pushMatrix()
        pose.translate(cx, cy)
        pose.scale(scale, scale)
        pose.translate(-cx, -cy)

        // Panel + border + glow accent line
        context.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL_BG)
        context.fill(px, py, px + PANEL_W, py + 1, C_ACCENT)
        context.fill(px, py + 1, px + PANEL_W, py + 2, C_ACCENT_GLOW)
        context.fill(px, py + PANEL_H - 1, px + PANEL_W, py + PANEL_H, C_BORDER)
        context.fill(px, py, px + 1, py + PANEL_H, C_BORDER)
        context.fill(px + PANEL_W - 1, py, px + PANEL_W, py + PANEL_H, C_BORDER)

        // Title bar
        context.fill(px, py, px + PANEL_W, py + TITLE_H, C_TITLE_BG)
        context.fill(px, py + TITLE_H - 1, px + PANEL_W, py + TITLE_H, C_DIVIDER)
        context.text(font, "ZenBounce".asPlainText(), px + 12, py + (TITLE_H - font.lineHeight) / 2, C_ACCENT_LT, true)
        val hint = "RSHIFT"
        context.text(font, hint.asPlainText(), px + PANEL_W - font.width(hint) - 10, py + (TITLE_H - font.lineHeight) / 2, C_TEXT_HINT, false)

        // Sidebar
        context.fill(px, py + TITLE_H, px + SIDE_W, py + PANEL_H, C_SIDEBAR_BG)
        context.fill(px + SIDE_W, py + TITLE_H, px + SIDE_W + 1, py + PANEL_H, C_DIVIDER)
        renderSidebar(context, mouseX, mouseY)

        // Module grid + settings drawer
        renderGrid(context, mouseX, mouseY)
        if (expandedMod != null) {
            renderDrawer(context, mouseX, mouseY)
        }

        // Keybind listening overlay
        val bm = bindingMod
        if (bm != null) {
            context.fill(px, py, px + PANEL_W, py + PANEL_H, color(0xCC, 0, 0, 0))
            val msg1 = "Press a key to bind to:"
            val msg2 = bm.name
            val msg3 = "(ESC to unbind, BACKSPACE to cancel)"
            context.text(font, msg1.asPlainText(), px + (PANEL_W - font.width(msg1)) / 2, py + PANEL_H / 2 - 18, C_TEXT_DIM, false)
            context.text(font, msg2.asPlainText(), px + (PANEL_W - font.width(msg2)) / 2, py + PANEL_H / 2 - 6, C_ACCENT_LT, true)
            context.text(font, msg3.asPlainText(), px + (PANEL_W - font.width(msg3)) / 2, py + PANEL_H / 2 + 6, C_TEXT_HINT, false)
        }

        pose.popMatrix()

        // Notifications (rendered outside the scaled matrix so they stay at the screen edge)
        renderNotifications(context)
    }

    // -- Notifications -----------------------------------------------------------

    private fun renderNotifications(context: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        notifications.removeAll { it.second < now }
        var ny = height - 10
        for ((msg, expire) in notifications.reversed()) {
            val alpha = ((expire - now) / 400.0).coerceIn(0.0, 1.0).toFloat()
            val w = font.width(msg) + 16
            val x = width - w - 6
            ny -= font.lineHeight + 8
            val bg = color((0xCC * alpha).toInt(), 0x06, 0x14, 0x20)
            val border = color((0xFF * alpha).toInt(), 0x4F, 0xC3, 0xF7)
            context.fill(x, ny, x + w, ny + font.lineHeight + 6, bg)
            context.fill(x, ny, x + 2, ny + font.lineHeight + 6, border)
            context.text(font, msg.asPlainText(), x + 8, ny + 3, color((0xFF * alpha).toInt(), 0xE8, 0xF2, 0xFA), false)
        }
    }

    // -- Sidebar ----------------------------------------------------------------

    private fun renderSidebar(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val sx = px + 6
        val sw = SIDE_W - 12
        var sy = py + TITLE_H + 8
        for (cat in ModuleCategories.entries) {
            val sel = cat == selCategory
            val hov = mouseX in sx..(sx + sw) && mouseY in sy..(sy + 20)
            when {
                sel -> {
                    context.fill(sx, sy, sx + sw, sy + 20, C_ACCENT_GLOW)
                    context.fill(sx, sy, sx + 3, sy + 20, C_ACCENT)
                }
                hov -> context.fill(sx, sy, sx + sw, sy + 20, color(0xFF, 0x0E, 0x1C, 0x2A))
            }
            val col = if (sel) C_ACCENT_LT else if (hov) C_TEXT else C_TEXT_DIM
            context.text(font, cat.tag.asPlainText(), sx + 8, sy + (24 - font.lineHeight) / 2, col, sel)
            sy += 23
        }
    }

    // -- Module grid --------------------------------------------------------------

    /** Number of grid columns available given the current drawer state. */
    private fun gridCols(cw: Int): Int = ((cw + CARD_GAP) / (CARD_W + CARD_GAP)).coerceAtLeast(1)

    private fun gridArea(): IntArray {
        val drawerOpen = expandedMod != null
        val cx = px + SIDE_W + 1 + PAD
        val cwFull = PANEL_W - SIDE_W - 1 - PAD * 2
        val cw = if (drawerOpen) cwFull - DRAWER_W - PAD else cwFull
        val cy = py + TITLE_H + PAD
        val ch = PANEL_H - TITLE_H - PAD * 2
        return intArrayOf(cx, cy, cw, ch)
    }

    private fun renderGrid(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val (cx, cy, cw, ch) = gridArea().toList()
        val modules = filteredModules()
        val cols = gridCols(cw)

        cachedContentH = computeGridContentH(modules.size, cols)
        scroll = scroll.coerceIn(0f, (cachedContentH - ch).toFloat().coerceAtLeast(0f))

        if (sbDragging && cachedContentH > ch) {
            val maxS   = (cachedContentH - ch).toFloat()
            val thumbH = (ch * ch.toFloat() / cachedContentH).coerceAtLeast(18f)
            val ratio  = (mouseY - sbDragStartY) / (ch - thumbH).coerceAtLeast(1f)
            scroll = (sbDragStartScroll + ratio * maxS).coerceIn(0f, maxS)
        }

        for ((idx, mod) in modules.withIndex()) {
            val col = idx % cols
            val row = idx / cols
            val cardX = cx + col * (CARD_W + CARD_GAP)
            val cardY = cy + row * (CARD_H + CARD_GAP) - scroll.toInt()
            if (cardY + CARD_H > cy && cardY < cy + ch) {
                renderCard(context, mod, cardX, cardY, mouseX, mouseY)
            }
        }

        if (cachedContentH > ch) {
            val maxS   = (cachedContentH - ch).toFloat()
            val thumbH = (ch * ch.toFloat() / cachedContentH).coerceAtLeast(18f)
            val thumbY = cy + (scroll / maxS) * (ch - thumbH)
            val sbX    = cx + cw - SB_W
            context.fill(sbX, cy, sbX + SB_W, cy + ch, color(0xFF, 0x12, 0x1E, 0x2C))
            context.fill(sbX, thumbY.toInt(), sbX + SB_W, (thumbY + thumbH).toInt(), if (sbDragging) C_ACCENT else C_ACCENT_DK)
        }
    }

    private fun renderCard(context: GuiGraphicsExtractor, mod: ClientModule, x: Int, y: Int, mx: Int, my: Int) {
        val on   = mod.enabled
        val exp  = mod == expandedMod
        val hov  = mx in x..(x + CARD_W) && my in y..(y + CARD_H)

        val bg = when {
            on && hov -> C_CARD_ON_HOV
            on        -> C_CARD_ON
            hov       -> C_CARD_HOV
            else      -> C_CARD_BG
        }
        context.fill(x, y, x + CARD_W, y + CARD_H, bg)

        val borderCol = if (on) C_ACCENT else if (exp) C_ACCENT_DK else C_BORDER
        context.fill(x, y, x + CARD_W, y + 1, borderCol)
        context.fill(x, y + CARD_H - 1, x + CARD_W, y + CARD_H, borderCol)
        context.fill(x, y, x + 1, y + CARD_H, borderCol)
        context.fill(x + CARD_W - 1, y, x + CARD_W, y + CARD_H, borderCol)

        if (on) {
            // top accent strip
            context.fill(x + 1, y + 1, x + CARD_W - 1, y + 2, C_ACCENT)
        }

        // Module name (trimmed with ellipsis if needed)
        val nameCol = if (on) C_ACCENT_LT else C_TEXT
        var name = mod.name
        if (font.width(name) > CARD_W - 14) {
            while (font.width("$name..") > CARD_W - 14 && name.isNotEmpty()) {
                name = name.dropLast(1)
            }
            name += ".."
        }
        context.text(font, name.asPlainText(), x + 7, y + 7, nameCol, on)

        // ON/OFF status bottom-left
        val statusStr = if (on) "ON" else "OFF"
        val statusCol = if (on) C_ACCENT_LT else C_TEXT_HINT
        context.text(font, statusStr.asPlainText(), x + 7, y + CARD_H - font.lineHeight - 6, statusCol, false)

        // Keybind label bottom-center
        val bindLabel = if (mod == bindingMod) "..." else if (mod.bind.isUnbound) "" else mod.bind.keyName
        if (bindLabel.isNotEmpty()) {
            val bw = font.width(bindLabel)
            context.text(font, bindLabel.asPlainText(), x + (CARD_W - bw) / 2, y + CARD_H - font.lineHeight - 6, C_TEXT_HINT, false)
        }

        // Settings indicator bottom-right
        if (displayValues(mod).isNotEmpty()) {
            val gx = x + CARD_W - GEAR_HIT
            val gy = y + CARD_H - GEAR_HIT
            val gearHov = mx in gx..(x + CARD_W) && my in gy..(y + CARD_H)
            val dotCol = if (exp) C_ACCENT_LT else if (gearHov) C_TEXT else C_TEXT_HINT
            context.text(font, "...".asPlainText(), x + CARD_W - font.width("...") - 6, y + CARD_H - font.lineHeight - 6, dotCol, false)
        }
    }

    // -- Settings drawer ---------------------------------------------------------

    private fun drawerArea(): IntArray {
        val dx = px + PANEL_W - PAD - DRAWER_W
        val dy = py + TITLE_H + PAD
        val dw = DRAWER_W
        val dh = PANEL_H - TITLE_H - PAD * 2
        return intArrayOf(dx, dy, dw, dh)
    }

    private fun renderDrawer(context: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
        val mod = expandedMod ?: return
        val (dx, dy, dw, dh) = drawerArea().toList()

        context.fill(dx, dy, dx + dw, dy + dh, C_DRAWER_BG)
        context.fill(dx, dy, dx + 1, dy + dh, C_ACCENT_DK)

        // Header
        var name = mod.name
        if (font.width(name) > dw - 30) {
            while (font.width("$name..") > dw - 30 && name.isNotEmpty()) name = name.dropLast(1)
            name += ".."
        }
        context.text(font, name.asPlainText(), dx + 8, dy + 6, C_ACCENT_LT, true)
        context.fill(dx + 6, dy + 18, dx + dw - 6, dy + 19, C_DIVIDER)
        context.text(font, "x".asPlainText(), dx + dw - font.width("x") - 8, dy + 6, C_TEXT_HINT, false)

        var sy = dy + 24
        for (v in displayValues(mod)) {
            if (sy + SET_H > dy + dh) break
            renderSettingRow(context, v, dx + 6, sy, dw - 12, mouseX, mouseY)
            sy += SET_H + SET_GAP
        }
    }

    private fun renderSettingRow(context: GuiGraphicsExtractor, v: Value<*>, x: Int, y: Int, w: Int, mx: Int, my: Int) {
        val hov = mx in x..(x + w) && my in y..(y + SET_H)
        context.fill(x, y, x + w, y + SET_H, if (hov) C_SET_HOV else C_SET_BG)
        context.fill(x, y, x + 1, y + SET_H, C_ACCENT_DK)

        var label = v.name
        if (font.width(label) > w - 30) {
            while (font.width("$label..") > w - 30 && label.isNotEmpty()) label = label.dropLast(1)
            label += ".."
        }
        context.text(font, label.asPlainText(), x + 5, y + (SET_H - font.lineHeight) / 2, C_TEXT_DIM, false)

        when (v.valueType) {
            ValueType.BOOLEAN -> drawToggle(context, x + w - 26, y + (SET_H - 10) / 2, 22, 10, v.get() as Boolean)
            ValueType.FLOAT, ValueType.INT -> {
                val rv  = v as RangedValue<*>
                val cur = (rv.get() as Number).toDouble()
                val min = (rv.range.start as Number).toDouble()
                val max = (rv.range.endInclusive as Number).toDouble()
                val pct = ((cur - min) / (max - min)).coerceIn(0.0, 1.0).toFloat()
                val lbl = if (rv.valueType == ValueType.INT) cur.toInt().toString() else "%.2f".format(cur)
                val slX = x + w - SLIDER_W - 2
                val slY = y + SET_H - 4
                context.text(font, lbl.asPlainText(), slX - font.width(lbl) - 4, y + (SET_H - font.lineHeight) / 2 - 2, C_TEXT_ACC, false)
                context.fill(slX, slY, slX + SLIDER_W, slY + 3, color(0xFF, 0x16, 0x24, 0x34))
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

    // -- Input -----------------------------------------------------------------

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        // Right-click on a card = start keybind binding
        if (click.button() == 1) {
            val mx2 = click.x.toInt()
            val my2 = click.y.toInt()
            val (cx2, cy2, cw2, ch2) = gridArea().toList()
            val modules2 = filteredModules()
            val cols2 = gridCols(cw2)
            for ((idx2, mod2) in modules2.withIndex()) {
                val col2 = idx2 % cols2
                val row2 = idx2 / cols2
                val cx3 = cx2 + col2 * (CARD_W + CARD_GAP)
                val cy3 = cy2 + row2 * (CARD_H + CARD_GAP) - scroll.toInt()
                if (mx2 in cx3..(cx3 + CARD_W) && my2 in cy3..(cy3 + CARD_H)) {
                    bindingMod = if (bindingMod == mod2) null else mod2
                    return true
                }
            }
        }
        if (click.button() != 0) return super.mouseClicked(click, doubled)
        val mx = click.x.toInt()
        val my = click.y.toInt()
        px = (width  - PANEL_W) / 2
        py = (height - PANEL_H) / 2

        // Drawer takes priority when open
        if (expandedMod != null) {
            val (dx, dy, dw, dh) = drawerArea().toList()
            if (mx in dx..(dx + dw) && my in dy..(dy + dh)) {
                // Close button
                if (mx in (dx + dw - font.width("x") - 12)..(dx + dw) && my in dy..(dy + 16)) {
                    expandedMod = null
                    return true
                }
                var sy = dy + 24
                for (v in displayValues(expandedMod!!)) {
                    if (my in sy..(sy + SET_H)) {
                        handleSettingClick(v, mx, dx + 6, dw - 12)
                        return true
                    }
                    sy += SET_H + SET_GAP
                }
                return true
            } else {
                // Click outside the drawer closes it
                expandedMod = null
            }
        }

        if (isInScrollbar(mx, my)) {
            sbDragging        = true
            sbDragStartY      = my.toFloat()
            sbDragStartScroll = scroll
            return true
        }

        // Sidebar
        val sx = px + 6
        val sw = SIDE_W - 12
        var sy = py + TITLE_H + 8
        for (cat in ModuleCategories.entries) {
            if (mx in sx..(sx + sw) && my in sy..(sy + 20)) {
                if (selCategory != cat) {
                    savedScroll[selCategory] = scroll
                    selCategory = cat
                    savedCategory = cat
                    expandedMod = null
                    scroll = savedScroll[cat] ?: 0f
                }
                return true
            }
            sy += 23
        }

        // Grid cards
        val (cx, cy, cw, ch) = gridArea().toList()
        val modules = filteredModules()
        val cols = gridCols(cw)

        for ((idx, mod) in modules.withIndex()) {
            val col = idx % cols
            val row = idx / cols
            val cardX = cx + col * (CARD_W + CARD_GAP)
            val cardY = cy + row * (CARD_H + CARD_GAP) - scroll.toInt()
            if (cardY < cy || cardY + CARD_H > cy + ch) continue
            if (mx !in cardX..(cardX + CARD_W) || my !in cardY..(cardY + CARD_H)) continue

            val gx = cardX + CARD_W - GEAR_HIT
            val gy = cardY + CARD_H - GEAR_HIT
            if (displayValues(mod).isNotEmpty() && mx in gx..(cardX + CARD_W) && my in gy..(cardY + CARD_H)) {
                expandedMod = if (expandedMod == mod) null else mod
            } else {
                mod.enabled = !mod.enabled
                notify("${mod.name} " + if (mod.enabled) "enabled" else "disabled")
            }
            return true
        }

        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: net.minecraft.client.input.KeyEvent): Boolean {
        val bm = bindingMod
        if (bm != null) {
            when (input.key.value) {
                org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE -> {
                    bm.bindValue.set(InputBind.UNBOUND)
                    notify("${bm.name}: unbound")
                }
                org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                    notify("${bm.name}: bind unchanged")
                }
                else -> {
                    bm.bindValue.set(InputBind(input.key.type, input.key.value, InputBind.BindAction.TOGGLE, emptySet()))
                    notify("${bm.name}: bound to ${input.key.displayName.string}")
                }
            }
            bindingMod = null
            return true
        }
        return super.keyPressed(input)
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
        savedScroll[selCategory] = scroll
        return true
    }

    // -- Helpers -----------------------------------------------------------------

    private fun filteredModules(): List<ClientModule> =
        ModuleManager.filter { it.category == selCategory }

    private fun displayValues(mod: ClientModule): List<Value<*>> =
        mod.get().filter { it.name != "Enabled" && it.valueType in listOf(ValueType.BOOLEAN, ValueType.FLOAT, ValueType.INT) }

    private fun computeGridContentH(moduleCount: Int, cols: Int): Int {
        if (moduleCount == 0) return 0
        val rows = (moduleCount + cols - 1) / cols
        return rows * (CARD_H + CARD_GAP)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleSettingClick(v: Value<*>, mx: Int, sx: Int, sw: Int) {
        when (v.valueType) {
            ValueType.BOOLEAN -> (v as Value<Boolean>).inner = !(v.inner as Boolean)
            ValueType.FLOAT, ValueType.INT -> {
                sliderTrackX = sx + sw - SLIDER_W - 2
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
        val (cx, cy, cw, ch) = gridArea().toList()
        return cachedContentH > ch
            && mx in (cx + cw - SB_W - 2)..(cx + cw)
            && my in cy..(cy + ch)
    }

    private fun easeOut(t: Float): Float { val i = 1f - t; return 1f - i * i * i }
    private fun color(a: Int, r: Int, g: Int, b: Int) = ARGB.color(a, r, g, b)

    override fun onClose() {
        savedCategory = selCategory
        savedScroll[selCategory] = scroll
        super.onClose()
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true
}
