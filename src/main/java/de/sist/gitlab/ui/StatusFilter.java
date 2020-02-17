package de.sist.gitlab.ui;

import de.sist.gitlab.PipelineJobStatus;

import java.util.List;
import java.util.stream.Collectors;

public class StatusFilter {

    public static List<PipelineJobStatus> filterForGuiDisplay(List<PipelineJobStatus> toFilter) {
        //todo make configurable
        return toFilter.stream().filter(x -> !x.result.equals("pending")).collect(Collectors.toList());
    }

}
