package net.oculus.texture.pbr.loader;

import java.io.IOException;
import java.util.zip.ZipError;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.oculus.Oculus;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.texture.pbr.PBRType;

public class SimplePBRLoader implements PBRTextureLoader<SimpleTexture> {
    @Override
    public void load(SimpleTexture texture, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer) {
        ResourceLocation location = getTextureLocation(texture);
        if (location == null) {
            return;
        }

        AbstractTexture normalTexture = null;
        AbstractTexture specularTexture = null;
        boolean normalAccepted = false;
        boolean specularAccepted = false;
        try {
            normalTexture = createPBRTexture(location, resourceManager, PBRType.NORMAL);
            specularTexture = createPBRTexture(location, resourceManager, PBRType.SPECULAR);

            if (normalTexture != null) {
                pbrTextureConsumer.acceptNormalTexture(normalTexture);
                normalAccepted = true;
            }

            if (specularTexture != null) {
                pbrTextureConsumer.acceptSpecularTexture(specularTexture);
                specularAccepted = true;
            }
        } catch (RuntimeException | Error failure) {
            closeUnacceptedPbrTexture(normalTexture, PBRType.NORMAL.appendToFileLocation(location), normalAccepted,
                failure);
            if (specularTexture != normalTexture) {
                closeUnacceptedPbrTexture(specularTexture, PBRType.SPECULAR.appendToFileLocation(location),
                    specularAccepted, failure);
            }
            throw failure;
        }
    }

    protected AbstractTexture createPBRTexture(ResourceLocation imageLocation, IResourceManager resourceManager, PBRType pbrType) {
        ResourceLocation pbrImageLocation = pbrType.appendToFileLocation(imageLocation);
        SimpleTexture pbrTexture = createSimpleTexture(pbrImageLocation);
        try {
            pbrTexture.loadTexture(resourceManager);
            OculusRuntimeValidation.logPBRSimpleTextureLoaded(
                imageLocation.toString(),
                pbrType,
                pbrImageLocation.toString(),
                pbrTexture.getGlTextureId());
            return pbrTexture;
        } catch (IOException exception) {
            closeFailedPbrTexture(pbrTexture, pbrImageLocation, exception);
            return null;
        } catch (ZipError exception) {
            closeFailedPbrTexture(pbrTexture, pbrImageLocation, exception);
            return null;
        } catch (RuntimeException exception) {
            closeFailedPbrTexture(pbrTexture, pbrImageLocation, exception);
            throw exception;
        } catch (Error error) {
            closeFailedPbrTexture(pbrTexture, pbrImageLocation, error);
            throw error;
        }
    }

    protected SimpleTexture createSimpleTexture(ResourceLocation pbrImageLocation) {
        return new SimpleTexture(pbrImageLocation);
    }

    private void closeFailedPbrTexture(SimpleTexture pbrTexture, ResourceLocation pbrImageLocation, Throwable loadFailure) {
        try {
            pbrTexture.deleteGlTexture();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(loadFailure, cleanupFailure);
            Oculus.LOGGER.debug("Failed to close PBR texture {} after load failure", pbrImageLocation, cleanupFailure);
        }
    }

    private void closeUnacceptedPbrTexture(AbstractTexture pbrTexture, ResourceLocation pbrImageLocation,
                                           boolean accepted, Throwable failure) {
        if (pbrTexture == null || accepted) {
            return;
        }

        try {
            pbrTexture.deleteGlTexture();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
            Oculus.LOGGER.debug("Failed to close unaccepted PBR texture {} after load failure",
                pbrImageLocation, cleanupFailure);
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private ResourceLocation getTextureLocation(SimpleTexture texture) {
        try {
            return ObfuscationReflectionHelper.getPrivateValue(
                SimpleTexture.class,
                texture,
                "textureLocation",
                "field_110568_b");
        } catch (RuntimeException exception) {
            Oculus.LOGGER.warn("Failed to access SimpleTexture location for PBR lookup", exception);
            return null;
        }
    }
}
