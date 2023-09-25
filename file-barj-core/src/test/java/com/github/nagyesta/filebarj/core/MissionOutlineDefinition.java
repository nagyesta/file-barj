package com.github.nagyesta.filebarj.core;

import com.github.nagyesta.abortmission.core.AbortMissionCommandOps;
import com.github.nagyesta.abortmission.core.MissionControl;
import com.github.nagyesta.abortmission.core.outline.MissionOutline;

import java.util.Map;
import java.util.function.Consumer;

import static com.github.nagyesta.abortmission.core.MissionControl.reportOnlyEvaluator;

public class MissionOutlineDefinition extends MissionOutline {
    @Override
    protected Map<String, Consumer<AbortMissionCommandOps>> defineOutline() {
        return Map.of(SHARED_CONTEXT, ops -> {
            ops.registerHealthCheck(reportOnlyEvaluator(MissionControl.matcher().anyClass().build()).build());
        });
    }
}
