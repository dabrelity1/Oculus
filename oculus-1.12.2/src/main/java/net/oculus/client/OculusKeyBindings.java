package net.oculus.client;

import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;

import org.lwjgl.input.Keyboard;

/**
 * Centralizes key binding declarations for Oculus. These mirror the bindings
 * exposed by Iris so users can reload, toggle, and open the shader GUI.
 */
public final class OculusKeyBindings {
    private static final String CATEGORY = "key.categories.oculus";

    public static final KeyBinding RELOAD = new KeyBinding("key.oculus.reloadShaders", Keyboard.KEY_R, CATEGORY);
    public static final KeyBinding TOGGLE = new KeyBinding("key.oculus.toggleShaders", Keyboard.KEY_K, CATEGORY);
    public static final KeyBinding OPEN_GUI = new KeyBinding("key.oculus.shaderPackScreen", Keyboard.KEY_O, CATEGORY);

    private static boolean registered;

    private OculusKeyBindings() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        ClientRegistry.registerKeyBinding(RELOAD);
        ClientRegistry.registerKeyBinding(TOGGLE);
        ClientRegistry.registerKeyBinding(OPEN_GUI);
        registered = true;
    }
}
