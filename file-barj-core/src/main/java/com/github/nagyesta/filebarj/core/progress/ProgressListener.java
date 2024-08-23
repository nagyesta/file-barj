package com.github.nagyesta.filebarj.core.progress;

import java.util.UUID;

public interface ProgressListener {

    UUID getId();

    void onProgressChanged(int totalProgressPercentage, int stepProgressPercentage, String stepName);
}
