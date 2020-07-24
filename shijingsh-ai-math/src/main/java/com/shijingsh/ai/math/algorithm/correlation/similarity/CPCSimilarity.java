package com.shijingsh.ai.math.algorithm.correlation.similarity;

import java.util.List;

import com.shijingsh.ai.math.algorithm.correlation.AbstractCorrelation;
import com.shijingsh.ai.math.algorithm.correlation.MathDistance;
import com.shijingsh.ai.math.algorithm.correlation.MathSimilarity;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.matrix.SymmetryMatrix;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.core.utility.Float2FloatKeyValue;
import com.shijingsh.ai.math.algorithm.correlation.AbstractCorrelation;
import com.shijingsh.ai.math.algorithm.correlation.MathSimilarity;
import com.shijingsh.ai.math.structure.vector.MathVector;

/**
 * Constrained Pearson Correlation相似度
 *
 * @author Birdy
 *
 */
public class CPCSimilarity extends AbstractCorrelation implements MathSimilarity {

    private float median;

    public CPCSimilarity(float median) {
        this.median = median;
    }

    private float getCoefficient(List<Float2FloatKeyValue> scores) {
        float power = 0F, leftPower = 0F, rightPower = 0F;
        for (Float2FloatKeyValue term : scores) {
            float leftDelta = term.getKey() - median;
            float rightDelta = term.getValue() - median;
            power += leftDelta * rightDelta;
            leftPower += leftDelta * leftDelta;
            rightPower += rightDelta * rightDelta;
        }
        return (float) (power / Math.sqrt(leftPower * rightPower));
    }

    @Override
    public float getCoefficient(MathVector leftVector, MathVector rightVector) {
        List<Float2FloatKeyValue> scores = getIntersectionScores(leftVector, rightVector);
        int intersection = scores.size();
        if (intersection == 0) {
            return 0F;
        }
        int union = leftVector.getElementSize() + rightVector.getElementSize() - intersection;
        float coefficient = getCoefficient(scores);
        coefficient *= intersection;
        coefficient /= union;
        return coefficient;
    }

}
