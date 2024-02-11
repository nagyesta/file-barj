package com.github.nagyesta.filebarj.core;

import com.github.nagyesta.abortmission.booster.jupiter.extractor.TagDependencyNameExtractor;
import com.github.nagyesta.abortmission.core.AbortMissionCommandOps;
import com.github.nagyesta.abortmission.core.outline.MissionOutline;

import java.util.Map;
import java.util.function.Consumer;

import static com.github.nagyesta.abortmission.core.MissionControl.*;

@SuppressWarnings("unused")
public class MissionOutlineDefinition extends MissionOutline {
    @Override
    protected Map<String, Consumer<AbortMissionCommandOps>> defineOutline() {
        final var annotatedAsUnixOnly = matcher()
                .dependencyWith("unix-only")
                .extractor(new TagDependencyNameExtractor()).build();
        final var hasUnixSystemProperty = matcher()
                .property("os.name")
                .valuePattern("^(?!Win).*$").build();
        final var taggedAsUnixOnlyAndNotOnUnix = matcher()
                .and(annotatedAsUnixOnly)
                .andAtLast(matcher().not(hasUnixSystemProperty).build()).build();
        final var anyClass = matcher().anyClass().build();
        return Map.of(SHARED_CONTEXT, ops -> {
            ops.registerHealthCheck(reportOnlyEvaluator(anyClass).build());
            ops.registerHealthCheck(abortingEvaluator(taggedAsUnixOnlyAndNotOnUnix)
                    .build());
        });
    }
}
