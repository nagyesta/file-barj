package com.github.nagyesta.filebarj.core.validation;

public class NotEmptyBackupValidator
        extends BaseBackupSizeValidator<NotEmptyBackup> {

    @Override
    protected String getMessageFromAnnotation(final NotEmptyBackup constraintAnnotation) {
        return constraintAnnotation.message();
    }

    @Override
    protected long expectedMinFiles() {
        return 1L;
    }

    @Override
    protected long expectedMaxFiles() {
        return Long.MAX_VALUE;
    }

    @Override
    protected long expectedMaxArchiveFiles() {
        return Long.MAX_VALUE;
    }
}
