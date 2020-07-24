package com.shijingsh.ai.math.algorithm.correlation;

@FunctionalInterface
public interface CorrelationMonitor {

    void notifyCoefficientCalculated(int leftIndex, int rightIndex, float coefficient);

}
