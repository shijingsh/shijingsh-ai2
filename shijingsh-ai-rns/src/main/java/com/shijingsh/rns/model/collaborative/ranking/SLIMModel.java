package com.shijingsh.rns.model.collaborative.ranking;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.algorithm.correlation.MathCorrelation;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.matrix.SymmetryMatrix;
import com.shijingsh.ai.math.structure.vector.ArrayVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.common.reflection.ReflectionUtility;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.Integer2FloatKeyValue;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.EpocheModel;
import com.shijingsh.rns.model.exception.ModelException;

import com.shijingsh.rns.model.exception.ModelException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 *
 * SLIM推荐器
 *
 * <pre>
 * SLIM: Sparse Linear Methods for Top-N Recommender Systems
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class SLIMModel extends EpocheModel {

    /**
     * W in original paper, a sparse matrix of aggregation coefficients
     */
    // TODO 考虑修改为对称矩阵?
    private DenseMatrix coefficientMatrix;

    /**
     * item's nearest neighbors for kNN > 0
     */
    private int[][] itemNeighbors;

    /**
     * regularization parameters for the L1 or L2 term
     */
    private float regL1Norm, regL2Norm;

    /**
     * number of nearest neighbors
     */
    private int neighborSize;

    /**
     * item similarity matrix
     */
    private SymmetryMatrix symmetryMatrix;

    private ArrayVector[] userVectors;

    private ArrayVector[] itemVectors;

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

    /**
     * initialization
     *
     * @throws ModelException if error occurs
     */
    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        neighborSize = configuration.getInteger("recommender.neighbors.knn.number", 50);
        regL1Norm = configuration.getFloat("recommender.slim.regularization.l1", 1.0F);
        regL2Norm = configuration.getFloat("recommender.slim.regularization.l2", 1.0F);

        // TODO 考虑重构
        coefficientMatrix = DenseMatrix.valueOf(itemSize, itemSize);
        coefficientMatrix.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(RandomUtility.randomFloat(1F));
        });
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            coefficientMatrix.setValue(itemIndex, itemIndex, 0F);
        }

        // initial guesses: make smaller guesses (e.g., W.init(0.01)) to speed
        // up training
        // TODO 修改为配置枚举
        try {
            Class<MathCorrelation> correlationClass = (Class<MathCorrelation>) Class.forName(configuration.getString("recommender.correlation.class"));
            MathCorrelation correlation = ReflectionUtility.getInstance(correlationClass);
            symmetryMatrix = new SymmetryMatrix(scoreMatrix.getColumnSize());
            correlation.calculateCoefficients(scoreMatrix, true, symmetryMatrix::setValue);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }

        // TODO 设置容量
        itemNeighbors = new int[itemSize][];
        Int2ObjectMap<TreeSet<Integer2FloatKeyValue>> itemNNs = new Int2ObjectOpenHashMap<>();
        for (MatrixScalar term : symmetryMatrix) {
            int row = term.getRow();
            int column = term.getColumn();
            if (row == column) {
                continue;
            }
            float value = term.getValue();
            // 忽略相似度为0的物品
            if (value == 0F) {
                continue;
            }
            TreeSet<Integer2FloatKeyValue> neighbors = itemNNs.get(row);
            if (neighbors == null) {
                neighbors = new TreeSet<>(comparator);
                itemNNs.put(row, neighbors);
            }
            neighbors.add(new Integer2FloatKeyValue(column, value));
            neighbors = itemNNs.get(column);
            if (neighbors == null) {
                neighbors = new TreeSet<>(comparator);
                itemNNs.put(column, neighbors);
            }
            neighbors.add(new Integer2FloatKeyValue(row, value));
        }

        // 构建物品邻居映射
        for (Int2ObjectMap.Entry<TreeSet<Integer2FloatKeyValue>> term : itemNNs.int2ObjectEntrySet()) {
            TreeSet<Integer2FloatKeyValue> neighbors = term.getValue();
            int[] value = new int[neighbors.size() < neighborSize ? neighbors.size() : neighborSize];
            int index = 0;
            for (Integer2FloatKeyValue neighbor : neighbors) {
                value[index++] = neighbor.getKey();
                if (index >= neighborSize) {
                    break;
                }
            }
            Arrays.sort(value);
            itemNeighbors[term.getIntKey()] = value;
        }

        userVectors = new ArrayVector[userSize];
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            userVectors[userIndex] = new ArrayVector(scoreMatrix.getRowVector(userIndex));
        }

        itemVectors = new ArrayVector[itemSize];
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            itemVectors[itemIndex] = new ArrayVector(scoreMatrix.getColumnVector(itemIndex));
        }
    }

    /**
     * train model
     *
     * @throws ModelException if error occurs
     */
    @Override
    protected void doPractice() {
        float[] scores = new float[userSize];
        // number of iteration cycles
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            totalError = 0F;
            // each cycle iterates through one coordinate direction
            for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                int[] neighborIndexes = itemNeighbors[itemIndex];
                if (neighborIndexes == null) {
                    continue;
                }
                ArrayVector itemVector = itemVectors[itemIndex];
                for (VectorScalar term : itemVector) {
                    scores[term.getIndex()] = term.getValue();
                }
                // for each nearest neighbor nearestNeighborItemIdx, update
                // coefficienMatrix by the coordinate
                // descent update rule
                for (int neighborIndex : neighborIndexes) {
                    itemVector = itemVectors[neighborIndex];
                    float valueSum = 0F, rateSum = 0F, errorSum = 0F;
                    int count = itemVector.getElementSize();
                    for (VectorScalar term : itemVector) {
                        int userIndex = term.getIndex();
                        float neighborScore = term.getValue();
                        float userScore = scores[userIndex];
                        float error = userScore - predict(userIndex, itemIndex, neighborIndexes, neighborIndex);
                        valueSum += neighborScore * error;
                        rateSum += neighborScore * neighborScore;
                        errorSum += error * error;
                    }
                    valueSum /= count;
                    rateSum /= count;
                    errorSum /= count;
                    // TODO 此处考虑重构
                    float coefficient = coefficientMatrix.getValue(neighborIndex, itemIndex);
                    totalError += errorSum + 0.5F * regL2Norm * coefficient * coefficient + regL1Norm * coefficient;
                    if (regL1Norm < Math.abs(valueSum)) {
                        if (valueSum > 0) {
                            coefficient = (valueSum - regL1Norm) / (regL2Norm + rateSum);
                        } else {
                            // One doubt: in this case, wij<0, however, the
                            // paper says wij>=0. How to gaurantee that?
                            coefficient = (valueSum + regL1Norm) / (regL2Norm + rateSum);
                        }
                    } else {
                        coefficient = 0F;
                    }
                    coefficientMatrix.setValue(neighborIndex, itemIndex, coefficient);
                }
                itemVector = itemVectors[itemIndex];
                for (VectorScalar term : itemVector) {
                    scores[term.getIndex()] = 0F;
                }
            }

            if (isConverged(epocheIndex) && isConverged) {
                break;
            }
            currentError = totalError;
        }
    }

    /**
     * predict a specific ranking score for user userIdx on item itemIdx.
     *
     * @param userIndex   user index
     * @param itemIndex   item index
     * @param excludIndex excluded item index
     * @return a prediction without the contribution of excluded item
     */
    private float predict(int userIndex, int itemIndex, int[] neighbors, int currentIndex) {
        float value = 0F;
        ArrayVector userVector = userVectors[userIndex];
        if (userVector.getElementSize() == 0) {
            return value;
        }
        int leftCursor = 0, rightCursor = 0, leftSize = userVector.getElementSize(), rightSize = neighbors.length;
        Iterator<VectorScalar> iterator = userVector.iterator();
        VectorScalar term = iterator.next();
        // 判断两个有序数组中是否存在相同的数字
        while (leftCursor < leftSize && rightCursor < rightSize) {
            if (term.getIndex() == neighbors[rightCursor]) {
                if (neighbors[rightCursor] != currentIndex) {
                    value += term.getValue() * coefficientMatrix.getValue(neighbors[rightCursor], itemIndex);
                }
                if (iterator.hasNext()) {
                    term = iterator.next();
                }
                leftCursor++;
                rightCursor++;
            } else if (term.getIndex() > neighbors[rightCursor]) {
                rightCursor++;
            } else if (term.getIndex() < neighbors[rightCursor]) {
                if (iterator.hasNext()) {
                    term = iterator.next();
                }
                leftCursor++;
            }
        }
        return value;
    }

    /**
     * predict a specific ranking score for user userIdx on item itemIdx.
     *
     * @param userIndex user index
     * @param itemIndex item index
     * @return predictive ranking score for user userIdx on item itemIdx
     * @throws ModelException if error occurs
     */
    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        int[] neighbors = itemNeighbors[itemIndex];
        if (neighbors == null) {
            instance.setQuantityMark(0F);
            return;
        }
        instance.setQuantityMark(predict(userIndex, itemIndex, neighbors, -1));
    }

}
