package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class FoodExpansionManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "foodexpansion";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "FoodExpansion"
        );
    }
}
