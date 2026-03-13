package com.github.nagyesta.filebarj.core.validation;

import com.github.nagyesta.filebarj.core.model.BackupIncrementManifest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Annotation;

public abstract class BaseBackupSizeValidator<A extends Annotation>
        implements ConstraintValidator<A, BackupIncrementManifest> {

    private String message;

    @Override
    public void initialize(final A constraintAnnotation) {
        this.message = getMessageFromAnnotation(constraintAnnotation);
    }

    @Override
    public boolean isValid(
            final BackupIncrementManifest value,
            final ConstraintValidatorContext context) {
        if (value == null
                || value.getDataStore() == null
                || value.getFiles() == null
                || value.getArchivedEntries() == null) {
            return true;
        }
        var valid = true;
        final var dataStore = value.getDataStore();
        final var fileMetadataSetId = value.getFiles();
        final var archiveFileMetadataSetId = value.getArchivedEntries();
        final var fileCount = dataStore.fileMetadataSetRepository().countAll(fileMetadataSetId);
        final var archiveEntryCount = dataStore.archivedFileMetadataSetRepository().countAll(archiveFileMetadataSetId);
        if (fileCount > expectedMaxFiles() || fileCount < expectedMinFiles()) {
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode("files").addConstraintViolation();
            valid = false;
        }
        if (archiveEntryCount > expectedMaxArchiveFiles() || archiveEntryCount < expectedMinArchiveFiles()) {
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode("archivedEntries").addConstraintViolation();
            valid = false;
        }
        return valid;
    }

    protected abstract String getMessageFromAnnotation(A constraintAnnotation);

    protected long expectedMinFiles() {
        return 0L;
    }

    protected long expectedMinArchiveFiles() {
        return 0L;
    }

    protected long expectedMaxFiles() {
        return 0L;
    }

    protected long expectedMaxArchiveFiles() {
        return 0L;
    }
}
