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

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Boss bar-based HUD system that renders elements at pixel-accurate X and Y positions.
 *
 * <p>Uses a single invisible boss bar as the rendering surface. Elements are placed
 * at absolute X positions via the {@code harshlands:negative_space} font. Y positioning
 * is achieved by encoding the target Y into each element's font provider {@code ascent}
 * field, which is then decoded by the {@code rendertype_text.vsh} vertex shader.</p>
 *
 * <h3>Y Encoding</h3>
 * <pre>
 *   ADD_HEIGHT = 4095
 *   ascent = -(shaderY + ADD_HEIGHT)
 * </pre>
 * where {@code shaderY} is the desired pixel offset from the bossbar baseline (~10px
 * from the top of the screen). The shader strips the ADD_HEIGHT offset and repositions
 * the vertex to {@code bossbar_baseline + shaderY}.
 *
 * @see NegativeSpaceHelper
 */
public class BossbarHUD {

    private static final Key NEGATIVE_SPACE_FONT = Key.key("harshlands", "negative_space");
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    // Fixed total title advance. Pads trailing shift so Minecraft's bossbar auto-centering
    // anchors at a stable screen position regardless of how many elements are visible.
    private static final int CANVAS_WIDTH = 400;

    private final Audience audience;
    private final BossBar mainBar;
    // Insertion-order preserved; sorted by X when building the title
    private final Map<String, HudElement> elements = new LinkedHashMap<>();

    private static final class HudElement {
        final int x;
        final Component content;
        final int advance;

        HudElement(int x, Component content, int advance) {
            this.x = x;
            this.content = content;
            this.advance = advance;
        }
    }

    public BossbarHUD(Audience audience) {
        this.audience = audience;
        this.mainBar = BossBar.bossBar(
                Component.empty(),
                0.0f,
                BossBar.Color.PINK,
                BossBar.Overlay.PROGRESS
        );
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Show the HUD bossbar to the player. */
    public void show() {
        audience.showBossBar(mainBar);
    }

    /** Hide the HUD bossbar from the player. */
    public void hide() {
        audience.hideBossBar(mainBar);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Place or update a HUD element.
     *
     * <p>The element's Y position must be encoded into its font provider's
     * {@code ascent} field in the resource pack (see Harshlands RP {@code font/default.json}
     * and {@code shaders/core/rendertype_text.vsh}).</p>
     *
     * @param elementId unique string key for this element
     * @param xPixels   horizontal pixel position from the left edge of the bossbar
     * @param content   component containing the codepoint(s) to display
     */
    public void setElement(String elementId, int xPixels, Component content) {
        setElement(elementId, xPixels, content, 0);
    }

    /**
     * Place or update a HUD element with an explicit glyph advance.
     *
     * <p>Use this overload when {@code content} contains glyphs with non-zero font advance
     * (e.g. bitmap icons). The advance is used to track the cursor correctly for subsequent
     * elements — without it, per-element shifts drift by one glyph width per element.</p>
     *
     * @param elementId unique string key for this element
     * @param xPixels   horizontal pixel position from the bossbar anchor
     * @param content   component containing the codepoint(s) to display
     * @param advance   natural font advance width (in pixels) of {@code content}'s glyphs
     */
    public void setElement(String elementId, int xPixels, Component content, int advance) {
        elements.put(elementId, new HudElement(xPixels, content, advance));
        mainBar.name(rebuildTitle());
    }

    /**
     * Remove a HUD element by ID.
     *
     * @param elementId the ID passed to {@link #setElement}
     */
    public void removeElement(String elementId) {
        elements.remove(elementId);
        mainBar.name(elements.isEmpty() ? Component.empty() : rebuildTitle());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Rebuilds the bossbar title by composing all elements sorted by X position.
     *
     * <p>Each element is preceded by a negative-space shift to reach its X position,
     * and its content font is reset to {@code minecraft:default} to prevent it from
     * inheriting the {@code harshlands:negative_space} font (which would render ASCII
     * characters as missing-glyph boxes).</p>
     */
    private Component rebuildTitle() {
        if (elements.isEmpty()) return Component.empty();

        List<HudElement> sorted = elements.values().stream()
                .sorted(Comparator.comparingInt(e -> e.x))
                .collect(Collectors.toList());

        Component result = Component.empty();
        // Canvas spans [-CANVAS_WIDTH/2, +CANVAS_WIDTH/2]. x=0 in element coords maps to
        // the bossbar's auto-centered anchor (i.e. screen center). Total title advance
        // is padded to CANVAS_WIDTH so Minecraft's auto-centering is invariant to content.
        int cursor = -CANVAS_WIDTH / 2;

        for (HudElement el : sorted) {
            int shift = el.x - cursor;
            if (shift != 0) {
                result = result.append(NegativeSpaceHelper.shift(shift));
            }
            // Reset font to minecraft:default so content does not inherit negative_space font
            Component safeContent = el.content.style(el.content.style().font(DEFAULT_FONT));
            result = result.append(safeContent);
            cursor = el.x + el.advance;
        }

        int trailingShift = (CANVAS_WIDTH / 2) - cursor;
        if (trailingShift != 0) {
            result = result.append(NegativeSpaceHelper.shift(trailingShift));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Negative space font helper
    // -------------------------------------------------------------------------

    /**
     * Builds shift components using the {@code harshlands:negative_space} font.
     * Characters in the F8xx range provide negative advances; FAxx range provides positive advances.
     * Powers of 2 from 256 down to 1 are composed to reach any integer offset.
     */
    public static final class NegativeSpaceHelper {

        private static final int[] POWERS = {256, 128, 64, 32, 16, 8, 4, 2, 1};

        // Negative advance characters (powers of 2: 256, 128, 64, 32, 16, 8, 4, 2, 1)
        // mapped to codepoints F900, F880, F840, F820, F810, F808, F804, F802, F801
        private static final char[] NEG_CHARS = {
            '\uF900', '\uF880', '\uF840', '\uF820', '\uF810', '\uF808', '\uF804', '\uF802', '\uF801'
        };

        // Positive advance characters: \uFA00=256, \uFA80=128, \uFA40=64,
        // \uFA20=32, \uFA10=16, \uFA08=8, \uFA04=4, \uFA02=2, \uFA01=1
        private static final char[] POS_CHARS = {
            '\uFA00', '\uFA80', '\uFA40', '\uFA20', '\uFA10', '\uFA08', '\uFA04', '\uFA02', '\uFA01'
        };

        private NegativeSpaceHelper() {}

        /**
         * Returns a component that shifts the cursor by {@code pixels} pixels.
         * Negative values shift left; positive values shift right.
         */
        public static Component shift(int pixels) {
            if (pixels == 0) return Component.empty();
            boolean negative = pixels < 0;
            int abs = Math.abs(pixels);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < POWERS.length; i++) {
                int p = POWERS[i];
                while (abs >= p) {
                    sb.append(negative ? NEG_CHARS[i] : POS_CHARS[i]);
                    abs -= p;
                }
            }
            return Component.text(sb.toString()).font(NEGATIVE_SPACE_FONT);
        }
    }
}
