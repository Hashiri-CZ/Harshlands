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

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.iceandfire.IceFireModule;
import cz.hashiri.harshlands.integrations.CompatiblePlugin;
import cz.hashiri.harshlands.integrations.PAPI;
import cz.hashiri.harshlands.tan.TanModule;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;

public class CharacterValues {

    private final FileConfiguration tanConfig;
    private final FileConfiguration ifConfig;

    // Pre-translated glyph strings (§-coded, computed once at construction)
    private final String[] temperatureGlyphs;       // [26] index = temp 0..25
    private final String[] iceVignetteGlyphs;       // [6]  index = temp 0..5
    private final String[] fireVignetteGlyphs;      // [6]  index = temp-20 (0..5)
    private final String[] thirstVignetteGlyphs;    // [6]  index = thirst 0..5
    private final String   sirenViewGlyph;
    // Thirst drops: [base+0]=empty, [base+1]=half, [base+2]=full
    // base: aboveWater+normal=0, underwater+normal=3, aboveWater+parasites=6, underwater+parasites=9
    private final String[] thirstDropGlyphs;        // [12]
    // Actionbar templates stored as raw config strings (PAPI may expand them at tick time)
    private final String templateTempThirst;
    private final String templateTempOnly;
    private final String templateThirstOnly;

    public CharacterValues() {
        HLModule tanModule = HLModule.getModule(TanModule.NAME);
        HLModule ifModule = HLModule.getModule(IceFireModule.NAME);

        this.tanConfig = tanModule.isGloballyEnabled() ? tanModule.getUserConfig().getConfig() : null;
        this.ifConfig = ifModule.isGloballyEnabled() ? ifModule.getUserConfig().getConfig() : null;

        // Build temperature glyph cache
        temperatureGlyphs = new String[26];
        if (tanConfig != null) {
            for (int i = 0; i <= 25; i++) {
                temperatureGlyphs[i] = toLegacySection(tanConfig.getString("CharacterOverrides.Temperature" + i));
            }
        }

        // Build ice vignette cache: index 0=FreezingView, 1=IceVignette5..5=IceVignette1
        iceVignetteGlyphs = new String[6];
        if (tanConfig != null) {
            iceVignetteGlyphs[0] = toLegacySection(tanConfig.getString("CharacterOverrides.FreezingView"));
            iceVignetteGlyphs[1] = toLegacySection(tanConfig.getString("CharacterOverrides.IceVignette5"));
            iceVignetteGlyphs[2] = toLegacySection(tanConfig.getString("CharacterOverrides.IceVignette4"));
            iceVignetteGlyphs[3] = toLegacySection(tanConfig.getString("CharacterOverrides.IceVignette3"));
            iceVignetteGlyphs[4] = toLegacySection(tanConfig.getString("CharacterOverrides.IceVignette2"));
            iceVignetteGlyphs[5] = toLegacySection(tanConfig.getString("CharacterOverrides.IceVignette1"));
        }

        // Build fire vignette cache: index 0=FireVignette1..4=FireVignette5, 5=BurningView
        fireVignetteGlyphs = new String[6];
        if (tanConfig != null) {
            fireVignetteGlyphs[0] = toLegacySection(tanConfig.getString("CharacterOverrides.FireVignette1"));
            fireVignetteGlyphs[1] = toLegacySection(tanConfig.getString("CharacterOverrides.FireVignette2"));
            fireVignetteGlyphs[2] = toLegacySection(tanConfig.getString("CharacterOverrides.FireVignette3"));
            fireVignetteGlyphs[3] = toLegacySection(tanConfig.getString("CharacterOverrides.FireVignette4"));
            fireVignetteGlyphs[4] = toLegacySection(tanConfig.getString("CharacterOverrides.FireVignette5"));
            fireVignetteGlyphs[5] = toLegacySection(tanConfig.getString("CharacterOverrides.BurningView"));
        }

        // Build thirst vignette cache: index 0=DehydratedView, 1=ThirstVignette5..5=ThirstVignette1
        thirstVignetteGlyphs = new String[6];
        if (tanConfig != null) {
            thirstVignetteGlyphs[0] = toLegacySection(tanConfig.getString("CharacterOverrides.DehydratedView"));
            thirstVignetteGlyphs[1] = toLegacySection(tanConfig.getString("CharacterOverrides.ThirstVignette5"));
            thirstVignetteGlyphs[2] = toLegacySection(tanConfig.getString("CharacterOverrides.ThirstVignette4"));
            thirstVignetteGlyphs[3] = toLegacySection(tanConfig.getString("CharacterOverrides.ThirstVignette3"));
            thirstVignetteGlyphs[4] = toLegacySection(tanConfig.getString("CharacterOverrides.ThirstVignette2"));
            thirstVignetteGlyphs[5] = toLegacySection(tanConfig.getString("CharacterOverrides.ThirstVignette1"));
        }

        // Siren view
        sirenViewGlyph = (ifConfig != null) ? toLegacySection(ifConfig.getString("Siren.ChangeScreen.Character")) : "";

        // Thirst drop glyphs [12]:
        // [0..2]  = aboveWater normal  (empty, half, full)
        // [3..5]  = underwater normal  (empty, half, full)
        // [6..8]  = aboveWater parasites (empty, half, full)  — empty shared with normal
        // [9..11] = underwater parasites (empty, half, full)  — empty shared with normal
        thirstDropGlyphs = new String[12];
        if (tanConfig != null) {
            thirstDropGlyphs[0] = toLegacySection(tanConfig.getString("CharacterOverrides.AboveWaterEmptyThirstDrop"));
            thirstDropGlyphs[1] = toLegacySection(tanConfig.getString("CharacterOverrides.AboveWaterHalfThirstDrop"));
            thirstDropGlyphs[2] = toLegacySection(tanConfig.getString("CharacterOverrides.AboveWaterFullThirstDrop"));
            thirstDropGlyphs[3] = toLegacySection(tanConfig.getString("CharacterOverrides.UnderwaterEmptyThirstDrop"));
            thirstDropGlyphs[4] = toLegacySection(tanConfig.getString("CharacterOverrides.UnderwaterHalfThirstDrop"));
            thirstDropGlyphs[5] = toLegacySection(tanConfig.getString("CharacterOverrides.UnderwaterFullThirstDrop"));
            thirstDropGlyphs[6]  = thirstDropGlyphs[0]; // parasites aboveWater empty = normal aboveWater empty
            thirstDropGlyphs[7]  = toLegacySection(tanConfig.getString("CharacterOverrides.ParasitesAboveWaterHalfThirstDrop"));
            thirstDropGlyphs[8]  = toLegacySection(tanConfig.getString("CharacterOverrides.ParasitesAboveWaterFullThirstDrop"));
            thirstDropGlyphs[9]  = thirstDropGlyphs[3]; // parasites underwater empty = normal underwater empty
            thirstDropGlyphs[10] = toLegacySection(tanConfig.getString("CharacterOverrides.ParasitesUnderwaterHalfThirstDrop"));
            thirstDropGlyphs[11] = toLegacySection(tanConfig.getString("CharacterOverrides.ParasitesUnderwaterFullThirstDrop"));
        }

        // Actionbar templates — stored raw (PAPI expanded per-tick)
        templateTempThirst  = (tanConfig != null) ? tanConfig.getString("CharacterOverrides.TemperatureThirstActionbar", "") : "";
        templateTempOnly    = (tanConfig != null) ? tanConfig.getString("CharacterOverrides.TemperatureActionbar", "") : "";
        templateThirstOnly  = (tanConfig != null) ? tanConfig.getString("CharacterOverrides.ThirstActionbar", "") : "";
    }

    private static String toLegacySection(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return LegacyComponentSerializer.legacySection().serialize(
                LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }

    @Nonnull
    public String getTemperatureOnlyActionbar(Player player, int temperature) {
        String template = CompatiblePlugin.isIntegrated(PAPI.NAME)
                ? PlaceholderAPI.setPlaceholders(player, templateTempOnly)
                : templateTempOnly;
        return template.replace("%TEMP%", getTemperature(temperature));
    }

    @Nonnull
    public String getThirstOnlyActionbar(Player player, int thirst, boolean isUnderwater, boolean parasitesActive) {
        String template = CompatiblePlugin.isIntegrated(PAPI.NAME)
                ? PlaceholderAPI.setPlaceholders(player, templateThirstOnly)
                : templateThirstOnly;
        return template.replace("%THIRST%", getThirst(thirst, isUnderwater, parasitesActive));
    }

    @Nonnull
    public String getTemperatureThirstActionbar(Player player, int temperature, int thirst, boolean isUnderwater, boolean parasitesActive) {
        String template = CompatiblePlugin.isIntegrated(PAPI.NAME)
                ? PlaceholderAPI.setPlaceholders(player, templateTempThirst)
                : templateTempThirst;
        return template
                .replace("%TEMP%", getTemperature(temperature))
                .replace("%THIRST%", getThirst(thirst, isUnderwater, parasitesActive));
    }

    @Nonnull
    public String getThirst(int thirst, boolean isUnderwater, boolean parasitesActive) {
        int base = parasitesActive ? (isUnderwater ? 9 : 6) : (isUnderwater ? 3 : 0);
        String emptyDrop = thirstDropGlyphs[base];
        String halfDrop  = thirstDropGlyphs[base + 1];
        String fullDrop  = thirstDropGlyphs[base + 2];

        thirst = Math.max(0, Math.min(thirst, 20));
        int numHalf  = thirst % 2;
        int numEmpty = (20 - thirst) / 2;
        int numFull  = (20 - numHalf - numEmpty * 2) / 2;

        StringBuilder s = new StringBuilder();
        for (int i = 0; i < numEmpty; i++) s.append(emptyDrop);
        if (numHalf == 1) s.append(halfDrop);
        s.append(fullDrop.repeat(numFull));
        return s.toString();
    }

    @Nonnull
    public String getIceVignette(int temperature) {
        if (temperature < 0 || temperature > 5) return "";
        return iceVignetteGlyphs[temperature];
    }

    @Nonnull
    public String getFireVignette(int temperature) {
        if (temperature < 20 || temperature > 25) return "";
        return fireVignetteGlyphs[temperature - 20];
    }

    @Nonnull
    public String getThirstVignette(int thirst) {
        if (thirst < 0 || thirst > 5) return "";
        return thirstVignetteGlyphs[thirst];
    }

    @Nonnull
    public String getTemperature(int i) {
        if (i < 0 || i > 25) return "";
        return temperatureGlyphs[i];
    }

    @Nonnull
    public String getSirenView() {
        return sirenViewGlyph;
    }
}
