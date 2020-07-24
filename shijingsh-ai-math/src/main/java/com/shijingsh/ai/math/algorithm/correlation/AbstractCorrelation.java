package com.shijingsh.ai.math.algorithm.correlation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.apache.commons.math3.util.FastMath;

import com.shijingsh.ai.environment.EnvironmentContext;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.matrix.SymmetryMatrix;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.Float2FloatKeyValue;

public abstract class AbstractCorrelation implements MathCorrelation {

    /**
     * 交集
     *
     * @param leftVector
     * @param rightVector
     * @return
     */
    // TODO 准备使用Coefficient代替
    @Deprecated
    protected List<Float2FloatKeyValue> getIntersectionScores(MathVector leftVector, MathVector rightVector) {
        int leftCursor = 0, rightCursor = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
        List<Float2FloatKeyValue> scores = new ArrayList<>(FastMath.max(leftSize, rightSize));
        if (leftSize != 0 && rightSize != 0) {
            Iterator<VectorScalar> leftIterator = leftVector.iterator();
            Iterator<VectorScalar> rightIterator = rightVector.iterator();
            VectorScalar leftTerm = leftIterator.next();
            VectorScalar rightTerm = rightIterator.next();
            // 判断两个有序数组中是否存在相同的数字
            while (leftCursor < leftSize && rightCursor < rightSize) {
                if (leftTerm.getIndex() == rightTerm.getIndex()) {
                    scores.add(new Float2FloatKeyValue(leftTerm.getValue(), rightTerm.getValue()));
                    leftTerm = leftIterator.next();
                    rightTerm = rightIterator.next();
                    leftCursor++;
                    rightCursor++;
                } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                    rightTerm = rightIterator.next();
                    rightCursor++;
                } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                    leftTerm = leftIterator.next();
                    leftCursor++;
                }
            }
        }
        return scores;
    }

    /**
     * 并集
     *
     * @param leftVector
     * @param rightVector
     * @return
     */
    // TODO 准备使用Coefficient代替
    @Deprecated
    protected List<Float2FloatKeyValue> getUnionScores(MathVector leftVector, MathVector rightVector) {
        LinkedList<Float2FloatKeyValue> scores = new LinkedList<>();
        Iterator<VectorScalar> leftIterator = leftVector.iterator();
        Iterator<VectorScalar> rightIterator = rightVector.iterator();
        VectorScalar leftTerm = leftIterator.hasNext() ? leftIterator.next() : null;
        VectorScalar rightTerm = rightIterator.hasNext() ? rightIterator.next() : null;
        // 判断两个有序数组中是否存在相同的数字
        while (leftTerm != null || rightTerm != null) {
            if (leftTerm != null && rightTerm != null) {
                if (leftTerm.getIndex() == rightTerm.getIndex()) {
                    scores.add(new Float2FloatKeyValue(leftTerm.getValue(), rightTerm.getValue()));
                    leftTerm = leftIterator.hasNext() ? leftIterator.next() : null;
                    rightTerm = rightIterator.hasNext() ? rightIterator.next() : null;
                } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                    scores.add(new Float2FloatKeyValue(Float.NaN, rightTerm.getValue()));
                    rightTerm = rightIterator.hasNext() ? rightIterator.next() : null;
                } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                    scores.add(new Float2FloatKeyValue(leftTerm.getValue(), Float.NaN));
                    leftTerm = leftIterator.hasNext() ? leftIterator.next() : null;
                }
                continue;
            }
            if (leftTerm != null) {
                scores.add(new Float2FloatKeyValue(leftTerm.getValue(), Float.NaN));
                leftTerm = leftIterator.hasNext() ? leftIterator.next() : null;
                continue;
            }
            if (rightTerm != null) {
                scores.add(new Float2FloatKeyValue(Float.NaN, rightTerm.getValue()));
                rightTerm = rightIterator.hasNext() ? rightIterator.next() : null;
                continue;
            }
        }
        return scores;
    }

}
