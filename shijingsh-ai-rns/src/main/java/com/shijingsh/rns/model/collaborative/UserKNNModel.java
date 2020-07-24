package com.shijingsh.rns.model.collaborative;

import java.util.Collection;
import java.util.Comparator;

import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.algorithm.correlation.MathCorrelation;
import com.shijingsh.ai.math.structure.vector.ArrayVector;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.core.common.reflection.ReflectionUtility;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.Integer2FloatKeyValue;
import com.shijingsh.core.utility.Neighborhood;
import com.shijingsh.rns.model.AbstractModel;

import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap;

/**
 *
 * User KNN推荐器
 *
 * <pre>
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public abstract class UserKNNModel extends AbstractModel {

    /** 邻居数量 */
    private int neighborSize;

    protected DenseVector userMeans;

    /**
     * user's nearest neighbors for kNN > 0
     */
    protected MathVector[] userNeighbors;

    protected SparseVector[] userVectors;

    protected SparseVector[] itemVectors;

    private Comparator<Integer2FloatKeyValue> comparator = new Comparator<Integer2FloatKeyValue>() {

        @Override
        public int compare(Integer2FloatKeyValue left, Integer2FloatKeyValue right) {
            int compare = -(Float.compare(left.getValue(), right.getValue()));
            if (compare == 0) {
                compare = Integer.compare(left.getKey(), right.getKey());
            }
            return compare;
        }

    };

    protected MathVector getNeighborVector(Collection<Integer2FloatKeyValue> neighbors) {
        int size = neighbors.size();
        int[] indexes = new int[size];
        float[] values = new float[size];
        Int2FloatSortedMap keyValues = new Int2FloatRBTreeMap();
        for (Integer2FloatKeyValue term : neighbors) {
            keyValues.put(term.getKey(), term.getValue());
        }
        int cursor = 0;
        for (Int2FloatMap.Entry term : keyValues.int2FloatEntrySet()) {
            indexes[cursor] = term.getIntKey();
            values[cursor] = term.getFloatValue();
            cursor++;
        }
        return new ArrayVector(size, indexes, values);
    }

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        neighborSize = configuration.getInteger("recommender.neighbors.knn.number");
        // TODO 设置容量
        userNeighbors = new MathVector[userSize];
        Neighborhood<Integer2FloatKeyValue>[] knns = new Neighborhood[userSize];
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            knns[userIndex] = new Neighborhood<Integer2FloatKeyValue>(neighborSize, comparator);
        }
        // TODO 修改为配置枚举
        try {
            Class<MathCorrelation> correlationClass = (Class<MathCorrelation>) Class.forName(configuration.getString("recommender.correlation.class"));
            MathCorrelation correlation = ReflectionUtility.getInstance(correlationClass);
            correlation.calculateCoefficients(scoreMatrix, false, (leftIndex, rightIndex, coefficient) -> {
                if (leftIndex == rightIndex) {
                    return;
                }
                // 忽略相似度为0的物品
                if (coefficient == 0F) {
                    return;
                }
                knns[leftIndex].updateNeighbor(new Integer2FloatKeyValue(rightIndex, coefficient));
                knns[rightIndex].updateNeighbor(new Integer2FloatKeyValue(leftIndex, coefficient));
            });
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            userNeighbors[userIndex] = getNeighborVector(knns[userIndex].getNeighbors());
        }

        userMeans = DenseVector.valueOf(userSize);

        userVectors = new SparseVector[userSize];
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            userVectors[userIndex] = scoreMatrix.getRowVector(userIndex);
        }

        itemVectors = new SparseVector[itemSize];
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            itemVectors[itemIndex] = scoreMatrix.getColumnVector(itemIndex);
        }
    }

    @Override
    protected void doPractice() {
        meanScore = scoreMatrix.getSum(false) / scoreMatrix.getElementSize();
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            SparseVector userVector = scoreMatrix.getRowVector(userIndex);
            userMeans.setValue(userIndex, userVector.getElementSize() > 0 ? userVector.getSum(false) / userVector.getElementSize() : meanScore);
        }
    }

}
