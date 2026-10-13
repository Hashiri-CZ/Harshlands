package cz.hashiri.harshlands.utils;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for {@link Utils#getRespectivePlank(Material)} on wood types newer than
 * the compile-time API. MC 26.3 added poplar; POPLAR_LOG is in Tag.LOGS, so NoTreePunching's
 * axe plank-drop and saw recipe reached getRespectivePlank with it, got null back and threw
 * from {@code new ItemStack(null)}. The test API predates poplar, so the name-derivation
 * fallback is exercised through {@link Utils#derivePlankName(String)} directly.
 *
 * <p>Bukkit must be mocked because Utils has a static initializer that calls
 * {@link org.bukkit.Bukkit#getServer()}.</p>
 */
class RespectivePlankTest {

    @BeforeAll
    static void mockServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void unmockServer() {
        MockBukkit.unmock();
    }

    @Test
    void known_woods_map_to_their_planks() {
        assertEquals(Material.OAK_PLANKS, Utils.getRespectivePlank(Material.STRIPPED_OAK_WOOD));
        assertEquals(Material.CRIMSON_PLANKS, Utils.getRespectivePlank(Material.CRIMSON_HYPHAE));
        assertEquals(Material.BAMBOO_PLANKS, Utils.getRespectivePlank(Material.BAMBOO_BLOCK));
    }

    @Test
    void non_wood_has_no_plank() {
        assertNull(Utils.getRespectivePlank(Material.STONE));
        assertNull(Utils.getRespectivePlank(Material.OAK_PLANKS));
    }

    @Test
    void unknown_wood_types_derive_their_plank_by_name() {
        assertEquals("POPLAR_PLANKS", Utils.derivePlankName("POPLAR_LOG"));
        assertEquals("POPLAR_PLANKS", Utils.derivePlankName("STRIPPED_POPLAR_WOOD"));
        assertEquals("FUTURE_PLANKS", Utils.derivePlankName("STRIPPED_FUTURE_STEM"));
        assertEquals("FUTURE_PLANKS", Utils.derivePlankName("FUTURE_HYPHAE"));
    }

    @Test
    void non_wood_names_derive_nothing() {
        assertNull(Utils.derivePlankName("STONE"));
        assertNull(Utils.derivePlankName("OAK_PLANKS"));
        assertNull(Utils.derivePlankName("LOG"));
        assertNull(Utils.derivePlankName("OAK_LOG_SLAB"));
    }
}
