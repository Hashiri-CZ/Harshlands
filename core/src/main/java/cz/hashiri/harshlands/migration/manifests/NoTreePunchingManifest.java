package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class NoTreePunchingManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "notreepunching";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "Initialize",
                "Shutdown"
        );
    }
}
