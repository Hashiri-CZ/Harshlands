package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class SpartanAndFireManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "spartanandfire";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "Initialize",
                "Shutdown",
                "MaxDistanceReached",
                "MaxReturnDistanceReached",
                "FullInventoryWeaponDropped",
                "WeaponDropped"
        );
    }
}
