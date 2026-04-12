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

    private static final String GROUP_ID = "aboveactionbar_group";

    private final int centerX;
    private final int iconWidth;
    private final int iconSpacing;
    private final int offset1;
    private final int offset2;
    private final int offset3;

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

    public AboveActionBarHUD(BossbarHUD hud, int centerX, int iconWidth, int iconSpacing,
                             int offset1, int offset2, int offset3) {
        this.hud = hud;
        this.centerX = centerX;
        this.iconWidth = iconWidth;
        this.iconSpacing = iconSpacing;
        this.offset1 = offset1;
        this.offset2 = offset2;
        this.offset3 = offset3;
        for (Slot s : Slot.values()) visibility.put(s, false);
    }

    public AboveActionBarHUD(BossbarHUD hud, int centerX, int iconWidth) {
        this(hud, centerX, iconWidth, 0, 0, 0, 0);
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

        // Clean up any stale per-slot elements from the old implementation.
        for (Slot s : all) {
            hud.removeElement("aboveactionbar_" + s.name().toLowerCase());
        }

        // Collect visible slots in enum order.
        java.util.List<Slot> visible = new java.util.ArrayList<>();
        for (Slot s : all) {
            if (visibility.get(s)) visible.add(s);
        }

        int visibleCount = visible.size();
        if (visibleCount == 0) {
            hud.removeElement(GROUP_ID);
            return;
        }

        // Build one content component: icon_1 + shift(spacing) + icon_2 + ... + icon_N.
        // Icons inherit the DEFAULT_FONT forced by BossbarHUD; shift components retain
        // their own NEGATIVE_SPACE_FONT explicitly, so nesting is safe.
        Component content = Component.empty();
        for (int i = 0; i < visibleCount; i++) {
            content = content.append(Component.text(visible.get(i).codepoint));
            if (i < visibleCount - 1 && iconSpacing != 0) {
                content = content.append(BossbarHUD.NegativeSpaceHelper.shift(iconSpacing));
            }
        }

        int effectiveCenterX = centerX + offsetForCount(visibleCount);
        int totalWidth = visibleCount * iconWidth + Math.max(0, visibleCount - 1) * iconSpacing;
        int startX = effectiveCenterX - totalWidth / 2;
        hud.setElement(GROUP_ID, startX, content, totalWidth);
    }

    private int offsetForCount(int n) {
        switch (n) {
            case 1: return offset1;
            case 2: return offset2;
            case 3: return offset3;
            default: return 0;
        }
    }
}
