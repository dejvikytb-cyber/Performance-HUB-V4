package cz.perfhub;

import net.fabricmc.loader.api.FabricLoader;

import java.util.List;

/** Detects optional optimization mods without bundling or replacing their code. */
public final class ModCompatibility {
    public record Mod(String name, String id, String purpose, String installUrl) {
        public boolean installed() { return FabricLoader.getInstance().isModLoaded(id); }
        public String status() { return installed() ? "INSTALLED" : "NOT FOUND"; }
    }

    public static final List<Mod> OPTIMIZERS = List.of(
        new Mod("Sodium", "sodium", "Faster chunk rendering", "https://modrinth.com/mod/sodium"),
        new Mod("Sodium Extra", "sodium-extra", "Extra video settings for Sodium", "https://modrinth.com/mod/sodium-extra"),
        new Mod("Lithium", "lithium", "Optimizes game logic and ticking", "https://modrinth.com/mod/lithium"),
        new Mod("FerriteCore", "ferritecore", "Reduces memory usage", "https://modrinth.com/mod/ferrite-core"),
        new Mod("ImmediatelyFast", "immediatelyfast", "Optimizes immediate-mode rendering", "https://modrinth.com/mod/immediatelyfast")
    );

    private ModCompatibility() {}

    public static int installedCount() {
        int count = 0;
        for (Mod mod : OPTIMIZERS) if (mod.installed()) count++;
        return count;
    }
}
