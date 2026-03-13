package com.github.nagyesta.filebarj.core.validation;

public class EmptyBackupValidator
        extends BaseBackupSizeValidator<EmptyBackup> {

    @Override
    protected String getMessageFromAnnotation(final EmptyBackup constraintAnnotation) {
        return constraintAnnotation.message();
    }
}
