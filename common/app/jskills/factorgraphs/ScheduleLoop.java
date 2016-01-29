package jskills.factorgraphs;

import play.Logger;

public class ScheduleLoop<T> extends Schedule<T> {

    private static final int MAX_ITERATIONS = 1000;

    private final double maxDelta;
    private final Schedule<T> scheduleToLoop;

    public ScheduleLoop(String name, Schedule<T> scheduleToLoop, double maxDelta) {
        super(name);
        this.scheduleToLoop = scheduleToLoop;
        this.maxDelta = maxDelta;
    }

    @Override
    public double visit(int depth, int maxDepth) {
        double delta = scheduleToLoop.visit(depth + 1, maxDepth);

        int totalIterations = 1;
        for(totalIterations = 1; delta > maxDelta; totalIterations++) {
            delta = scheduleToLoop.visit(depth + 1, maxDepth);

            if(totalIterations > MAX_ITERATIONS) {
                /*
                throw new RuntimeException(String.format(
                        "Maximum iterations (%d) reached.", MAX_ITERATIONS));
                */
                break;
            }
        }

        if(totalIterations > 100) {
            Logger.warn("ScheduleLoop: iterations: {} delta: {}", totalIterations, delta);
        }
        // Logger.warn("ScheduleLoop: iterations: {} delta: {}", totalIterations, delta);

        return delta;
    }
}