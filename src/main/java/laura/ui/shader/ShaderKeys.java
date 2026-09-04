package laura.ui.shader;

import laura.lib.log4j.Logger;
import laura.lib.log4j.LoggerFactory;
import net.minecraft.client.gl.Defines;
import net.minecraft.client.gl.ShaderProgramKey;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Every custom core shader used by the client.
 *
 * <p>Minecraft only compiles the core shaders that are listed in {@link ShaderProgramKeys#getAll()}
 * while resources are (re)loaded, so a program whose key is missing from that list is never created.
 * {@code RenderSystem.setShader(ShaderProgramKey)} then returns {@code null} for it, which is exactly
 * what used to blow up the main menu with a NullPointerException inside the blur shader.
 *
 * <p>All keys have to be registered before the client starts its first reload, see
 * {@code platform.Initializer}.
 */
public final class ShaderKeys {
    private static final Logger logger = LoggerFactory.a(ShaderKeys.class);

    public static final ShaderProgramKey BLURRED_RECT = a("core/rect/blurred_rect", VertexFormats.POSITION_TEXTURE_COLOR);
    public static final ShaderProgramKey BLUR_UPSCALE = a("core/blur/upscale", VertexFormats.POSITION);
    public static final ShaderProgramKey BLUR_DOWNSCALE = a("core/blur/downscale", VertexFormats.POSITION);
    public static final ShaderProgramKey GRADIENT_RECT = a("core/rect/gradient_rect", VertexFormats.POSITION_COLOR);
    public static final ShaderProgramKey NOISE = a("core/noise/noise_shader", VertexFormats.POSITION_COLOR);
    public static final ShaderProgramKey RECT = a("core/rect/rect", VertexFormats.POSITION_COLOR);
    public static final ShaderProgramKey TEXTURE_RECT = a("core/rect/texture_rect", VertexFormats.POSITION_TEXTURE_COLOR);
    public static final ShaderProgramKey TEXT = a("core/text/text", VertexFormats.POSITION_TEXTURE_COLOR);

    private static final ShaderProgramKey[] KEYS = {
            BLURRED_RECT,
            BLUR_UPSCALE,
            BLUR_DOWNSCALE,
            GRADIENT_RECT,
            NOISE,
            RECT,
            TEXTURE_RECT,
            TEXT
    };

    private ShaderKeys() {
    }

    private static ShaderProgramKey a(String path, VertexFormat vertexFormat) {
        return new ShaderProgramKey(Identifier.of("laura", path), vertexFormat, Defines.EMPTY);
    }

    /**
     * Makes every custom shader of the client known to the game.
     */
    public static void register() {
        List<ShaderProgramKey> all = ShaderProgramKeys.getAll();
        for (ShaderProgramKey key : KEYS) {
            if (all.contains(key)) {
                continue;
            }
            try {
                all.add(key);
            } catch (RuntimeException e) {
                logger.f("Could not register shader program {}: {}", key, e.toString());
            }
        }
    }
}
