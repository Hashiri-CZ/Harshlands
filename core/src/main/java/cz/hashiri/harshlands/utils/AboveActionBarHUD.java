/*
    Copyright (C) 2025  Hashiri_

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.hashiri.harshlands.utils;

import net.kyori.adventure.text.Component;
import java.util.EnumMap;
import java.util.Map;

/**
 * Manages a right-aligned strip of status icons rendered just above the action bar
 * via BossbarHUD. Visible icons are packed left-to-right with no gaps; hidden icons
 * take no space.
 *
 * <p>Y positioning is handled entirely by the RP font ascent + rendertype_text.vsh
 * (bottom-anchored, ADD_HEIGHT_BOTTOM=8192, ICON_FROM_BOTTOM=65). Java does not
 * compute Y — only X via BossbarHUD's negative-space shifting.</p>
 */
public final class AboveActionBarHUD {

    // Rightmost visible icon's left edge (pixels from bossbar left). Calibrate vs. screen.
    private static final int RIGHT_ANCHOR_X = 200;
    // Pixel width of one slot (glyph advance + any spacing). Calibrate vs. RP glyph advance.
    private static final int ICON_WIDTH = 48;

    // -------------------------------------------------------------------------
    // Slot definitions — enum order = left-to-right display order
    // -------------------------------------------------------------------------
    public enum Slot {
        WETNESS ("\uE8B0"),
        PROTEIN ("\uE8B1"),
        CARBS   ("\uE8B2"),
        FAT     ("\uE8B3");

        final String codepoint;
        Slot(String codepoint) { this.codepoint = codepoint; }
    }

    // -------------------------------------------------------------------------
    private final BossbarHUD hud;
    private final Map<Slot, Boolean> visibility = new EnumMap<>(Slot.class);

    public AboveActionBarHUD(BossbarHUD hud) {
        this.hud = hud;
        for (Slot s : Slot.values()) visibility.put(s, false);
    }

    /** Show or hide a slot. Recalculates all X positions immediately. */
    public void setVisible(Slot slot, boolean visible) {
        if (visibility.get(slot) == visible) return; // no-op guard
        visibility.put(slot, visible);
        relayout();
    }

    // -------------------------------------------------------------------------
    private void relayout() {
        Slot[] all = Slot.values();
        // Walk right-to-left: rightmost visible slot lands at RIGHT_ANCHOR_X
        int slotsFromRight = 0;
        for (int i = all.length - 1; i >= 0; i--) {
            Slot s = all[i];
            String id = "aboveactionbar_" + s.name().toLowerCase();
            if (visibility.get(s)) {
                int x = RIGHT_ANCHOR_X - slotsFromRight * ICON_WIDTH;
                hud.setElement(id, x, Component.text(s.codepoint));
                slotsFromRight++;
            } else {
                hud.removeElement(id);
            }
        }
    }
}
