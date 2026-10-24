package cz.perfhub;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.server.level.ParticleStatus;

/** Hardwarové profily. Nastavují jen vanilla možnosti. */
public enum Profile {
    WEAK("Competitive / max FPS", 8, 5, 260, 0, 0,
            ParticleStatus.MINIMAL, GraphicsPreset.FAST, CloudStatus.OFF, 0.5, false, false, 0.0),
    MEDIUM("Balanced", 10, 6, 180, 1, 1,
            ParticleStatus.DECREASED, GraphicsPreset.FAST, CloudStatus.FAST, 0.75, true, false, 0.5),
    STRONG("Visuals", 16, 10, 260, 4, 3,
            ParticleStatus.ALL, GraphicsPreset.FANCY, CloudStatus.FANCY, 1.0, true, false, 0.5);

    public final String label;
    public final int renderDistance, simulationDistance, fpsLimit, mipmaps, biomeBlend;
    public final ParticleStatus particles;
    public final GraphicsPreset graphics;
    public final CloudStatus clouds;
    public final double entityDistance, textBackground;
    public final boolean entityShadows, vsync;

    Profile(String label, int renderDistance, int simulationDistance, int fpsLimit, int mipmaps, int biomeBlend,
            ParticleStatus particles, GraphicsPreset graphics, CloudStatus clouds,
            double entityDistance, boolean entityShadows, boolean vsync, double textBackground) {
        this.label = label;
        this.renderDistance = renderDistance;
        this.simulationDistance = simulationDistance;
        this.fpsLimit = fpsLimit;
        this.mipmaps = mipmaps;
        this.biomeBlend = biomeBlend;
        this.particles = particles;
        this.graphics = graphics;
        this.clouds = clouds;
        this.entityDistance = entityDistance;
        this.entityShadows = entityShadows;
        this.vsync = vsync;
        this.textBackground = textBackground;
    }
}
