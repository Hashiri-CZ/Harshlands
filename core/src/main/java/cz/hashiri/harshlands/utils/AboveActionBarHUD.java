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
 * Manages a center-aligned strip of status icons rendered just above the action bar
 * via BossbarHUD. Visible icons are packed left-to-right with no gaps; hidden icons
 * take no space. The group is always centered around {@code centerX}.
 *
 * <p>Y positioning is handled entirely by the RP font ascent + rendertype_text.vsh
 * (bottom-anchored). Java only computes X via BossbarHUD's negative-space shifting.</p>
 */
public final class AboveActionBarHUD {

    private final int centerX;
    private final int iconWidth;

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

    public AboveActionBarHUD(BossbarHUD hud, int centerX, int iconWidth) {
        this.hud = hud;
        this.centerX = centerX;
        this.iconWidth = iconWidth;
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

        // Count visible icons
        int visibleCount = 0;
        for (Slot s : all) {
            if (visibility.get(s)) visibleCount++;
        }

        // Center the group: first icon starts at centerX - totalWidth/2
        int totalWidth = visibleCount * iconWidth;
        int startX = centerX - totalWidth / 2;

        // Place visible icons left-to-right
        int slotIndex = 0;
        for (Slot s : all) {
            String id = "aboveactionbar_" + s.name().toLowerCase();
            if (visibility.get(s)) {
                int x = startX + slotIndex * iconWidth;
                hud.setElement(id, x, Component.text(s.codepoint));
                slotIndex++;
            } else {
                hud.removeElement(id);
            }
        }
    }
}
