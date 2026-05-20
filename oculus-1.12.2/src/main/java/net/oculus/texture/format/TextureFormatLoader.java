package net.oculus.texture.format;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipError;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.oculus.Oculus;
import net.oculus.client.ShaderPackReloader;
import net.oculus.texture.pbr.PBRTextureManager;

public final class TextureFormatLoader {
    public static final ResourceLocation LOCATION = new ResourceLocation("optifine/texture.properties");

    private static TextureFormat format;
    private static boolean registeredReloadListener;

    private TextureFormatLoader() {
    }

    public static TextureFormat getFormat() {
        return format;
    }

    public static void registerReloadListener() {
        if (registeredReloadListener) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        registerReloadListener(minecraft.getResourceManager());
    }

    static boolean registerReloadListener(IResourceManager resourceManager) {
        if (registeredReloadListener || resourceManager == null) {
            return registeredReloadListener;
        }

        if (resourceManager instanceof IReloadableResourceManager) {
            registeredReloadListener = true;
            try {
                ((IReloadableResourceManager) resourceManager).registerReloadListener(TextureFormatLoader::reload);
            } catch (RuntimeException | Error exception) {
                registeredReloadListener = false;
                throw exception;
            }
            return true;
        }

        reload(resourceManager);
        return false;
    }

    static void resetReloadListenerRegistrationForTesting() {
        registeredReloadListener = false;
    }

    public static void reload(IResourceManager resourceManager) {
        TextureFormat newFormat = loadFormat(resourceManager);
        boolean didFormatChange = !Objects.equals(format, newFormat);
        format = newFormat;
        Throwable failure = null;
        try {
            PBRTextureManager.INSTANCE.clear();
        } catch (RuntimeException | Error exception) {
            failure = exception;
        }
        if (didFormatChange) {
            try {
                onFormatChange();
            } catch (RuntimeException | Error exception) {
                failure = collectReloadFailure(failure, exception);
            }
        }
        rethrowReloadFailure(failure);
    }

    static TextureFormat loadFormat(IResourceManager resourceManager) {
        if (resourceManager == null) {
            return null;
        }

        try (IResource resource = resourceManager.getResource(LOCATION)) {
            Properties properties = new Properties();
            properties.load(resource.getInputStream());
            return loadFormat(properties);
        } catch (FileNotFoundException exception) {
            return null;
        } catch (Exception | ZipError exception) {
            Oculus.LOGGER.error("Failed to load texture format from file '{}'", LOCATION, exception);
            return null;
        }
    }

    static TextureFormat loadFormat(Properties properties) {
        if (properties == null) {
            return null;
        }

        String value = properties.getProperty("format");
        if (value == null || value.isEmpty()) {
            return null;
        }

        String[] splitFormat = value.split("/");
        if (splitFormat.length == 0) {
            return null;
        }

        String name = splitFormat[0];
        TextureFormat.Factory factory = TextureFormatRegistry.INSTANCE.getFactory(name);
        if (factory == null) {
            Oculus.LOGGER.warn("Invalid texture format '{}' in file '{}'", name, LOCATION);
            return null;
        }

        String version = splitFormat.length > 1 ? splitFormat[1] : null;
        return factory.createFormat(name, version);
    }

    private static void onFormatChange() {
        try {
            ShaderPackReloader.reload();
        } catch (RuntimeException | ZipError exception) {
            Oculus.LOGGER.warn("Failed to reload shaders after texture format change", exception);
        }
    }

    static Throwable collectReloadFailure(Throwable failure, Throwable exception) {
        if (failure != null) {
            if (exception != failure) {
                failure.addSuppressed(exception);
            }
            return failure;
        }
        return exception;
    }

    private static void rethrowReloadFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }
}
