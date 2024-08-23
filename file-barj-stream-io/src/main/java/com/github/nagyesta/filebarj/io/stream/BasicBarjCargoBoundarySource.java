package com.github.nagyesta.filebarj.io.stream;

import com.github.nagyesta.filebarj.io.stream.enums.FileType;
import com.github.nagyesta.filebarj.io.stream.internal.model.BarjCargoEntryBoundaries;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

@Builder
@Data
public class BasicBarjCargoBoundarySource implements BarjCargoBoundarySource {

    private final @NonNull String path;
    private final @NonNull FileType fileType;
    private final boolean encrypted;
    private final BarjCargoEntryBoundaries contentBoundary;
    private final @NonNull BarjCargoEntryBoundaries metadataBoundary;
}
