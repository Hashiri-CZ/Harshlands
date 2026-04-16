package cz.hashiri.harshlands.locale;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// MockBukkit cannot load HLPlugin fully because MockUnsafeValues.fromLegacy() is not implemented
// for MC 1.21.11 / 26.1.2. The failure occurs in ToolUtils.initFallbackMaterials() during onEnable()
// (be.seeseemelk.mockbukkit.UnimplementedOperationException: Not implemented,
//  at be.seeseemelk.mockbukkit.MockUnsafeValues.fromLegacy -> Material.isBlock -> ToolUtils.initMap).
// The LocaleManager/Messages contract is fully covered by LocaleManagerTest + MessagesTest.
@Disabled("MockBukkit cannot load HLPlugin: MockUnsafeValues.fromLegacy not implemented for 1.21.11/26.1.2")
class LocaleLifecycleTest {

    private ServerMock server;
    private HLPlugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(HLPlugin.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void messages_api_returns_real_string_after_plugin_enable() {
        String v = Messages.get("commands.version");
        assertNotNull(v);
        assertFalse(v.startsWith("["), "Expected real value, got missing-key fallback: " + v);
    }
}
