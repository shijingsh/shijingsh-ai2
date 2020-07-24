package com.shijingsh.ai.model.neuralnetwork.condition;

import java.util.Map;

import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;

/**
 * 条件
 *
 * @author Birdy
 *
 */
public interface Condition {

    /**
     * 启动
     */
    default void start() {
    }

    /**
     * 停止
     *
     * @param newScore
     * @param oldScore
     * @param gradients
     * @return
     */
    boolean stop(float newScore, float oldScore, Map<String, MathMatrix> gradients);

}
