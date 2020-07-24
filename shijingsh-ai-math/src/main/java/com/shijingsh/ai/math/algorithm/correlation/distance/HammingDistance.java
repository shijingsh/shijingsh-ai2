package com.shijingsh.ai.math.algorithm.correlation.distance;

import java.util.List;

import com.shijingsh.ai.math.algorithm.correlation.AbstractCorrelation;
import com.shijingsh.ai.math.algorithm.correlation.MathDistance;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.core.utility.Float2FloatKeyValue;
import com.shijingsh.ai.math.algorithm.correlation.AbstractCorrelation;
import com.shijingsh.ai.math.algorithm.correlation.MathDistance;

/**
 * 汉明距离
 *
 * @author Birdy
 *
 */
public class HammingDistance extends AbstractCorrelation implements MathDistance {

    private float getCoefficient(List<Float2FloatKeyValue> scores) {
        float coefficient = 0F;
        for (Float2FloatKeyValue term : scores) {
            if (term.getKey() != term.getValue()) {
                coefficient++;
            }
        }
        return coefficient;
    }

    @Override
    public float getCoefficient(MathVector leftVector, MathVector rightVector) {
        List<Float2FloatKeyValue> scores = getUnionScores(leftVector, rightVector);
        float coefficient = getCoefficient(scores);
        return coefficient;
    }

}
