package cz.hashiri.harshlands.utils;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses Audience.empty() to exercise the HUD's state machine without a running server.
 * The point is to verify preview mode toggles the internal flag; BossbarHUD element
 * content is an implementation detail tested implicitly via BossbarHUD's own tests.
 */
class AboveActionBarHUDPreviewTest {

    @Test void preview_flag_toggles_on_setPreviewContent_and_clear() {
        BossbarHUD hud = new BossbarHUD(Audience.empty());
        AboveActionBarHUD aboveHud = new AboveActionBarHUD(hud, 0, 32);

        assertFalse(aboveHud.isPreviewActive());

        aboveHud.setPreviewContent(Component.text("preview"), 100);
        assertTrue(aboveHud.isPreviewActive());

        aboveHud.clearPreview();
        assertFalse(aboveHud.isPreviewActive());
    }

    @Test void clearPreview_is_idempotent() {
        BossbarHUD hud = new BossbarHUD(Audience.empty());
        AboveActionBarHUD aboveHud = new AboveActionBarHUD(hud, 0, 32);

        aboveHud.clearPreview(); // no-op when never set
        assertFalse(aboveHud.isPreviewActive());
    }

    @Test void setVisible_works_while_preview_active_without_crashing() {
        BossbarHUD hud = new BossbarHUD(Audience.empty());
        AboveActionBarHUD aboveHud = new AboveActionBarHUD(hud, 0, 32);

        aboveHud.setPreviewContent(Component.text("preview"), 100);
        aboveHud.setVisible(AboveActionBarHUD.Slot.PROTEIN, true); // should be silently suppressed in group output
        aboveHud.setVisible(AboveActionBarHUD.Slot.WETNESS, true); // should still render in group
        // No assertions on internal BossbarHUD state — we're checking no exceptions.
        assertTrue(aboveHud.isPreviewActive());
    }
}
