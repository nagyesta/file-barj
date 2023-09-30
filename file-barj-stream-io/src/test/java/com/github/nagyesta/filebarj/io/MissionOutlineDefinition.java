package com.github.nagyesta.filebarj.io;

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
        final var annotatedAsCiOnly = matcher()
                .dependencyWith("ci-only")
                .extractor(new TagDependencyNameExtractor()).build();
        final var hasCiSystemProperty = matcher()
                .property("ci")
                .valuePattern("true").build();
        final var taggedAsCiOnlyAndNotOnCi = matcher()
                .and(annotatedAsCiOnly)
                .andAtLast(matcher().not(hasCiSystemProperty).build()).build();
        final var anyClass = matcher().anyClass().build();
        return Map.of(SHARED_CONTEXT, ops -> {
            ops.registerHealthCheck(reportOnlyEvaluator(anyClass).build());
            ops.registerHealthCheck(abortingEvaluator(taggedAsCiOnlyAndNotOnCi)
                    .build());
        });
    }
}
