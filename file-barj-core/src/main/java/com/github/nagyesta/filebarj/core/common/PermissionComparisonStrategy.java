package com.github.nagyesta.filebarj.core.common;

import com.github.nagyesta.filebarj.core.model.FileMetadata;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Implements multiple strategies for comparing permissions.
 */
@Getter
public enum PermissionComparisonStrategy {

    /**
     * Compare permissions, owner and group names strictly.
     */
    STRICT(true, true) {
        @Override
        public boolean matches(
                @NonNull final FileMetadata previousMetadata,
                @NonNull final FileMetadata currentMetadata) {
            return Objects.equals(previousMetadata.getPosixPermissions(), currentMetadata.getPosixPermissions())
                    && Objects.equals(previousMetadata.getOwner(), currentMetadata.getOwner())
                    && Objects.equals(previousMetadata.getGroup(), currentMetadata.getGroup());
        }
    },

    /**
     * Compare permissions only ignoring owner and group names.
     */
    PERMISSION_ONLY(true, false) {
        @Override
        public boolean matches(
                @NonNull final FileMetadata previousMetadata,
                @NonNull final FileMetadata currentMetadata) {
            return Objects.equals(previousMetadata.getPosixPermissions(), currentMetadata.getPosixPermissions());
        }
    },

    /**
     * Only compare the first three characters of permissions, ignoring owner and group names.
     */
    RELAXED(true, false) {
        private static final int FIRST_SIGNIFICANT_CHAR_INCLUSIVE = 0;
        private static final int LAST_SIGNIFICANT_CHAR_EXCLUSIVE = 3;

        @Override
        public boolean matches(
                @NonNull final FileMetadata previousMetadata,
                @NonNull final FileMetadata currentMetadata) {
            return Objects.equals(transform(previousMetadata.getPosixPermissions()), transform(currentMetadata.getPosixPermissions()));
        }

        @Nullable
        private static String transform(final String permissions) {
            return Optional.ofNullable(permissions)
                    .map(s -> s.substring(FIRST_SIGNIFICANT_CHAR_INCLUSIVE, LAST_SIGNIFICANT_CHAR_EXCLUSIVE))
                    .orElse(null);
        }
    },

    /**
     * Ignore permission and owner/group name differences.
     */
    IGNORE(false, false) {
        @Override
        public boolean matches(
                @NonNull final FileMetadata previousMetadata,
                @NonNull final FileMetadata currentMetadata) {
            return true;
        }
    };

    private final boolean permissionImportant;
    private final boolean ownerImportant;

    /**
     * Creates a new instance and initializes it.
     *
     * @param permissionImportant true if the permissions should be compared
     * @param ownerImportant      true if the owner and group name should be compared
     */
    PermissionComparisonStrategy(final boolean permissionImportant, final boolean ownerImportant) {
        this.permissionImportant = permissionImportant;
        this.ownerImportant = ownerImportant;
    }

    /**
     * Compares the permissions, owner name and group name.
     *
     * @param previousMetadata The previous metadata
     * @param currentMetadata  The current metadata
     * @return true if the permissions are the same
     */
    public abstract boolean matches(
            @NonNull FileMetadata previousMetadata,
            @NonNull FileMetadata currentMetadata);
}
