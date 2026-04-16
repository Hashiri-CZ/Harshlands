package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class FearManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "fear";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "Initialize",
                "Shutdown",
                "CharacterOverrides"
        );
    }
}
