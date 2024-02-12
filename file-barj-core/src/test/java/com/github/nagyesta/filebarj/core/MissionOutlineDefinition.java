package com.github.nagyesta.filebarj.core;

import com.github.nagyesta.abortmission.core.AbortMissionCommandOps;
import com.github.nagyesta.abortmission.core.outline.MissionOutline;

import java.util.Map;
import java.util.function.Consumer;

import static com.github.nagyesta.abortmission.core.MissionControl.matcher;
import static com.github.nagyesta.abortmission.core.MissionControl.reportOnlyEvaluator;

@SuppressWarnings("unused")
public class MissionOutlineDefinition extends MissionOutline {
    @Override
    protected Map<String, Consumer<AbortMissionCommandOps>> defineOutline() {
        final var anyClass = matcher().anyClass().build();
        return Map.of(SHARED_CONTEXT, ops -> ops.registerHealthCheck(reportOnlyEvaluator(anyClass).build()));
    }
}
