package net.oculus.texture.pbr;

public final class PBRAtlasHolder {
    private PBRAtlasTexture normalAtlas;
    private PBRAtlasTexture specularAtlas;

    public PBRAtlasTexture getNormalAtlas() {
        return normalAtlas;
    }

    public PBRAtlasTexture getSpecularAtlas() {
        return specularAtlas;
    }

    public void setNormalAtlas(PBRAtlasTexture atlas) {
        normalAtlas = atlas;
    }

    public void setSpecularAtlas(PBRAtlasTexture atlas) {
        specularAtlas = atlas;
    }

    public boolean isEmpty() {
        return normalAtlas == null && specularAtlas == null;
    }

    public void updateAnimations() {
        Throwable failure = null;
        if (normalAtlas != null) {
            failure = updateAtlasAnimations(failure, normalAtlas);
        }
        if (specularAtlas != null) {
            failure = updateAtlasAnimations(failure, specularAtlas);
        }
        rethrowFailure(failure);
    }

    private static Throwable updateAtlasAnimations(Throwable failure, PBRAtlasTexture atlas) {
        try {
            atlas.updateAnimations();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                return collectFailure(failure, exception);
            }
            return exception;
        }
        return failure;
    }

    private static Throwable collectFailure(Throwable failure, Throwable exception) {
        if (failure != null) {
            if (exception != failure) {
                failure.addSuppressed(exception);
            }
            return failure;
        }
        return exception;
    }

    private static void rethrowFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected PBR atlas animation failure", failure);
    }
}
