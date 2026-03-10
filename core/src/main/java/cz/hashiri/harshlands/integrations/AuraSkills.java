/*
    Copyright (C) 2026  Hashiri_

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
package cz.hashiri.harshlands.integrations;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class AuraSkills extends CompatiblePlugin {

    public static final String NAME = "AuraSkills";

    private Object api;
    private Method getUserMethod;
    private Method getSkillLevelMethod;
    private Method getGlobalRegistryMethod;
    private Method getSkillByIdMethod;
    private Method namespacedIdOfMethod;
    private Class<? extends Enum<?>> skillsEnumClass;

    public AuraSkills(@Nonnull HLPlugin plugin) {
        super(plugin, NAME);

        if (!isIntegrated
                && intConfig.getBoolean(NAME + ".Enabled")
                && intConfig.getBoolean("EnableIntegrationMessage")
                && Bukkit.getServer().getPluginManager().isPluginEnabled(NAME)) {
            String message = Utils.translateMsg(plugin.getConfig().getString("Integration"), null, Map.of("PLUGIN", NAME));
            plugin.getLogger().info(message);
        }

        if (isIntegrated) {
            initializeReflection(plugin);
            AuraSkillsRequirementsListener requirementsListener = new AuraSkillsRequirementsListener(plugin);
            Bukkit.getPluginManager().registerEvents(requirementsListener, plugin);
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    requirementsListener.enforceEquipmentRequirements(player);
                }
            }, 20L, 20L);
        }
    }

    @Override
    public boolean otherLoadOptions() {
        try {
            Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    public int getSkillLevel(@Nonnull Player player, @Nonnull String skillName) {
        if (!isIntegrated || !isReflectionReady()) {
            return 0;
        }

        try {
            Object user = getUserMethod.invoke(api, player.getUniqueId());
            if (user == null) {
                return 0;
            }

            Object skill = resolveSkill(skillName);
            if (skill == null) {
                return 0;
            }

            Object level = getSkillLevelMethod.invoke(user, skill);
            return level instanceof Number ? ((Number) level).intValue() : 0;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return 0;
        }
    }

    public boolean hasRequiredLevel(@Nonnull Player player, @Nonnull String skillName, int requiredLevel) {
        return getSkillLevel(player, skillName) >= requiredLevel;
    }

    private boolean isReflectionReady() {
        return api != null
                && getUserMethod != null
                && getSkillLevelMethod != null
                && skillsEnumClass != null;
    }

    @Nullable
    private Object resolveSkill(@Nonnull String skillName) throws InvocationTargetException, IllegalAccessException {
        String normalized = skillName.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        try {
            return Enum.valueOf((Class) skillsEnumClass, normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
        }

        if (normalized.contains("/") && getGlobalRegistryMethod != null && getSkillByIdMethod != null && namespacedIdOfMethod != null) {
            String[] split = normalized.split("/", 2);
            if (split.length == 2 && !split[0].isBlank() && !split[1].isBlank()) {
                Object registry = getGlobalRegistryMethod.invoke(api);
                Object namespacedId = namespacedIdOfMethod.invoke(null, split[0].toLowerCase(Locale.ROOT), split[1].toLowerCase(Locale.ROOT));
                return getSkillByIdMethod.invoke(registry, namespacedId);
            }
        }

        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void initializeReflection(@Nonnull HLPlugin plugin) {
        try {
            Class<?> apiClass = Class.forName("dev.aurelium.auraskills.api.AuraSkillsApi");
            api = apiClass.getMethod("get").invoke(null);
            getUserMethod = apiClass.getMethod("getUser", UUID.class);

            Class<?> skillClass = Class.forName("dev.aurelium.auraskills.api.skill.Skill");
            Class<?> userClass = Class.forName("dev.aurelium.auraskills.api.user.SkillsUser");
            getSkillLevelMethod = userClass.getMethod("getSkillLevel", skillClass);

            skillsEnumClass = (Class<? extends Enum<?>>) Class.forName("dev.aurelium.auraskills.api.skill.Skills");

            Class<?> registryClass = Class.forName("dev.aurelium.auraskills.api.registry.GlobalRegistry");
            Class<?> namespacedIdClass = Class.forName("dev.aurelium.auraskills.api.registry.NamespacedId");
            getGlobalRegistryMethod = apiClass.getMethod("getGlobalRegistry");
            getSkillByIdMethod = registryClass.getMethod("getSkill", namespacedIdClass);
            namespacedIdOfMethod = namespacedIdClass.getMethod("of", String.class, String.class);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to initialize AuraSkills API reflection bridge", exception);
            api = null;
            getUserMethod = null;
            getSkillLevelMethod = null;
            getGlobalRegistryMethod = null;
            getSkillByIdMethod = null;
            namespacedIdOfMethod = null;
            skillsEnumClass = null;
        }
    }
}

