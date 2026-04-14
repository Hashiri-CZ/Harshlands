package cz.hashiri.harshlands.migration.manifests;

import cz.hashiri.harshlands.migration.ModuleManifest;

import java.util.Set;

public class ToughAsNailsManifest extends ModuleManifest {

    @Override
    public String moduleId() {
        return "toughasnails";
    }

    @Override
    protected Set<String> translationRoots() {
        return Set.of(
                "Initialize",
                "Shutdown",
                "DehydrationDeath",
                "ParasiteDeath",
                "HyperthermiaDeath",
                "HypothermiaDeath"
        );
    }
}
