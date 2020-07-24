package com.shijingsh.rns.model.collaborative.ranking;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.matrix.HashMatrix;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.MatrixFactorizationModel;

import it.unimi.dsi.fastutil.longs.Long2FloatRBTreeMap;

/**
 *
 * FISM-RMSE推荐器
 *
 * <pre>
 * FISM: Factored Item Similarity Models for Top-N Recommender Systems
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
// 注意:FISM使用itemFactors来组成userFactors
public class FISMRMSEModel extends MatrixFactorizationModel {

    private int numNeighbors;

    private float rho, alpha, beta, gamma;

    /**
     * bias regularization
     */
    private float learnRatio;

    /**
     * items and users biases vector
     */
    private DenseVector itemBiases, userBiases;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        // 注意:FISM使用itemFactors来组成userFactors
        userFactors = DenseMatrix.valueOf(itemSize, factorSize);
        userFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        itemFactors = DenseMatrix.valueOf(itemSize, factorSize);
        itemFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        userBiases = DenseVector.valueOf(userSize);
        userBiases.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        itemBiases = DenseVector.valueOf(itemSize);
        itemBiases.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });

        numNeighbors = scoreMatrix.getElementSize();
        rho = configuration.getFloat("recommender.fismrmse.rho");// 3-15
        alpha = configuration.getFloat("recommender.fismrmse.alpha", 0.5F);
        beta = configuration.getFloat("recommender.fismrmse.beta", 0.6F);
        gamma = configuration.getFloat("recommender.fismrmse.gamma", 0.1F);
        learnRatio = configuration.getFloat("recommender.fismrmse.lrate", 0.0001F);
    }

    @Override
    protected void doPractice() {
        DefaultScalar scalar = DefaultScalar.getInstance();
        int sampleSize = (int) (rho * numNeighbors);
        int totalSize = userSize * itemSize;
        HashMatrix rateMatrix = new HashMatrix(true, userSize, itemSize, new Long2FloatRBTreeMap());
        for (MatrixScalar cell : scoreMatrix) {
            rateMatrix.setValue(cell.getRow(), cell.getColumn(), cell.getValue());
        }
        int[] sampleIndexes = new int[sampleSize];

        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            DenseVector userVector = DenseVector.valueOf(factorSize);
            totalError = 0F;
            // new training data by sampling negative values
            // R是一个在trainMatrix基础上增加负样本的矩阵.

            // make a random sample of negative feedback (total - nnz)
            for (int sampleIndex = 0; sampleIndex < sampleSize; sampleIndex++) {
                while (true) {
                    int randomIndex = RandomUtility.randomInteger(totalSize - numNeighbors);
                    int rowIndex = randomIndex / itemSize;
                    int columnIndex = randomIndex % itemSize;

                    if (Float.isNaN(rateMatrix.getValue(rowIndex, columnIndex))) {
                        sampleIndexes[sampleIndex] = randomIndex;
                        rateMatrix.setValue(rowIndex, columnIndex, 0F);
                        break;
                    }
                }
            }

            // update throughout each user-item-rating (u, i, rui) cell
            for (MatrixScalar cell : rateMatrix) {
                int userIndex = cell.getRow();
                int itemIndex = cell.getColumn();
                float score = cell.getValue();
                SparseVector rateVector = scoreMatrix.getRowVector(userIndex);
                int size = rateVector.getElementSize() - 1;
                if (size == 0 || size == -1) {
                    size = 1;
                }
                for (VectorScalar term : rateVector) {
                    int compareIndex = term.getIndex();
                    if (itemIndex != compareIndex) {
                        userVector.addVector(userFactors.getRowVector(compareIndex));
                    }
                }
                userVector.scaleValues((float) Math.pow(size, -alpha));
                // for efficiency, use the below code to predict rui instead of
                // simply using "predict(u,j)"
                float itemBias = itemBiases.getValue(itemIndex);
                float predict = itemBias + scalar.dotProduct(itemFactors.getRowVector(itemIndex), userVector).getValue();
                float error = score - predict;
                totalError += error * error;
                // update bi
                itemBiases.shiftValue(itemIndex, learnRatio * (error - gamma * itemBias));
                totalError += gamma * itemBias * itemBias;

                DenseVector factorVector = itemFactors.getRowVector(itemIndex);
                factorVector.iterateElement(MathCalculator.SERIAL, (element) -> {
                    int index = element.getIndex();
                    float value = element.getValue();
                    element.setValue((userVector.getValue(index) * error - value * beta) * learnRatio + value);
                });
                totalError += beta * scalar.dotProduct(factorVector, factorVector).getValue();

                for (VectorScalar term : rateVector) {
                    int compareIndex = term.getIndex();
                    if (itemIndex != compareIndex) {
                        float scale = (float) (error * Math.pow(size, -alpha));
                        factorVector = userFactors.getRowVector(compareIndex);
                        factorVector.iterateElement(MathCalculator.SERIAL, (element) -> {
                            int index = element.getIndex();
                            float value = element.getValue();
                            element.setValue((value * scale - value * beta) * learnRatio + value);
                        });
                        totalError += beta * scalar.dotProduct(factorVector, factorVector).getValue();
                    }
                }
            }

            for (int sampleIndex : sampleIndexes) {
                int rowIndex = sampleIndex / itemSize;
                int columnIndex = sampleIndex % itemSize;
                rateMatrix.setValue(rowIndex, columnIndex, Float.NaN);
            }

            totalError *= 0.5F;
            if (isConverged(epocheIndex) && isConverged) {
                break;
            }
            currentError = totalError;
        }

    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        DefaultScalar scalar = DefaultScalar.getInstance();
        float bias = userBiases.getValue(userIndex) + itemBiases.getValue(itemIndex);
        float sum = 0F;
        int count = 0;
        for (VectorScalar term : scoreMatrix.getRowVector(userIndex)) {
            int index = term.getIndex();
            // for test, i and j will be always unequal as j is unrated
            if (index != itemIndex) {
                DenseVector userVector = userFactors.getRowVector(index);
                DenseVector itemVector = itemFactors.getRowVector(itemIndex);
                sum += scalar.dotProduct(userVector, itemVector).getValue();
                count++;
            }
        }
        sum *= (float) (count > 0 ? Math.pow(count, -alpha) : 0F);
        instance.setQuantityMark(bias + sum);
    }

}
