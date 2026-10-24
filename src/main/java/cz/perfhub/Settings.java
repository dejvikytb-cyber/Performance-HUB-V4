package cz.perfhub;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

import java.util.List;

/** Definice všech sekcí a jejich nastavení. */
public final class Settings {
    private Settings() {}

    private static net.minecraft.client.Options o() {
        return Minecraft.getInstance().options;
    }

    private static final java.util.function.BiPredicate<Double, Double> NEAR = (a, b) -> Math.abs(a - b) < 0.01;

    public static final List<Setting<?>> VIDEO = List.of(
            new Setting<GraphicsPreset>("Grafika", () -> o().graphicsPreset(),
                    List.of(GraphicsPreset.FAST, GraphicsPreset.FANCY),
                    v -> v == GraphicsPreset.FAST ? "Rychlá" : v == GraphicsPreset.FANCY ? "Pěkná" : v.name()),
            new Setting<Boolean>("VSync", () -> o().enableVsync(),
                    List.of(false, true), v -> v ? "Zapnuto" : "Vypnuto"),
            new Setting<Integer>("Limit FPS", () -> o().framerateLimit(),
                    List.of(30, 60, 90, 120, 144, 165, 240, 260),
                    v -> v >= 260 ? "Neomezeno" : v + " FPS"),
            new Setting<Integer>("Mipmapy", () -> o().mipmapLevels(),
                    List.of(0, 1, 2, 3, 4), v -> v == 0 ? "Vypnuto" : "Úroveň " + v),
            new Setting<CloudStatus>("Mraky", () -> o().cloudStatus(),
                    List.of(CloudStatus.OFF, CloudStatus.FAST, CloudStatus.FANCY),
                    v -> v == CloudStatus.OFF ? "Vypnuto" : v == CloudStatus.FAST ? "Rychlé" : "Pěkné"),
            new Setting<Boolean>("Ambient occlusion", () -> o().ambientOcclusion(),
                    List.of(false, true), v -> v ? "Zapnuto" : "Vypnuto"),
            new Setting<Boolean>("Průhlednost (náročná)", () -> o().improvedTransparency(),
                    List.of(false, true), v -> v ? "Zapnuto" : "Vypnuto"),
            new Setting<Boolean>("Vignette", () -> o().vignette(),
                    List.of(false, true), v -> v ? "Zapnuto" : "Vypnuto"),
            new Setting<Boolean>("Průhledné mezery v listech", () -> o().cutoutLeaves(),
                    List.of(false, true), v -> v ? "Zapnuto" : "Vypnuto")
    );

    public static final List<Setting<?>> WORLD = List.of(
            new Setting<Integer>("Vzdálenost renderu", () -> o().renderDistance(),
                    List.of(4, 6, 8, 10, 12, 16, 20, 24, 32), v -> v + " chunků"),
            new Setting<Integer>("Simulační vzdálenost", () -> o().simulationDistance(),
                    List.of(5, 6, 8, 10, 12, 16, 24, 32), v -> v + " chunků"),
            new Setting<Integer>("Prolínání biomů", () -> o().biomeBlendRadius(),
                    List.of(0, 1, 2, 3, 5, 7), v -> v == 0 ? "Vypnuto" : v + " bloků")
    );

    public static final List<Setting<?>> ENTITIES = List.of(
            new Setting<Double>("Vzdálenost entit", () -> o().entityDistanceScaling(),
                    List.of(0.5, 0.75, 1.0, 1.5, 2.0), v -> Math.round(v * 100) + " %", NEAR),
            new Setting<Boolean>("Stíny entit", () -> o().entityShadows(),
                    List.of(false, true), v -> v ? "Zapnuto" : "Vypnuto")
    );

    public static final List<Setting<?>> PARTICLES = List.of(
            new Setting<ParticleStatus>("Částice", () -> o().particles(),
                    List.of(ParticleStatus.ALL, ParticleStatus.DECREASED, ParticleStatus.MINIMAL),
                    v -> v == ParticleStatus.ALL ? "Všechny" : v == ParticleStatus.DECREASED ? "Snížené" : "Minimální")
    );

    public static final List<Setting<?>> HUD = List.of(
            new Setting<Double>("Pozadí textu (chat, titulky)", () -> o().textBackgroundOpacity(),
                    List.of(0.0, 0.25, 0.5, 1.0), v -> Math.round(v * 100) + " %", NEAR)
    );
}
