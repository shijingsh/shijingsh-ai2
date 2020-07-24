package com.shijingsh.ai.math.algorithm.correlation.similarity;

import java.util.List;

import com.shijingsh.ai.math.algorithm.correlation.AbstractCorrelation;
import com.shijingsh.ai.math.algorithm.correlation.MathDistance;
import com.shijingsh.ai.math.algorithm.correlation.MathSimilarity;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.core.utility.Float2FloatKeyValue;
import com.shijingsh.ai.math.algorithm.correlation.AbstractCorrelation;
import com.shijingsh.ai.math.algorithm.correlation.MathSimilarity;
import com.shijingsh.ai.math.structure.vector.MathVector;

/**
 * Cosine相似度
 *
 * @author Birdy
 *
 */
public class CosineSimilarity extends AbstractCorrelation implements MathSimilarity {

    private float getCoefficient(List<Float2FloatKeyValue> scores) {
        float power = 0F, leftPower = 0F, rightPower = 0F;
        for (Float2FloatKeyValue term : scores) {
            float leftScore = term.getKey();
            float rightScore = term.getValue();
            power += leftScore * rightScore;
            leftPower += leftScore * leftScore;
            rightPower += rightScore * rightScore;
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
