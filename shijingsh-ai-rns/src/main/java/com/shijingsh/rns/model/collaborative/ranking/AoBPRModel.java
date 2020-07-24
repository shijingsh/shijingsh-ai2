package com.shijingsh.rns.model.collaborative.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.KeyValue;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.MatrixFactorizationModel;
import com.shijingsh.rns.utility.LogisticUtility;
import com.shijingsh.rns.utility.SampleUtility;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 *
 * AoBPR推荐器
 *
 * <pre>
 * AoBPR: BPR with Adaptive Oversampling
 * Improving pairwise learning for item recommendation from implicit feedback
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class AoBPRModel extends MatrixFactorizationModel {
    private int loopNumber;

    /**
     * item geometric distribution parameter
     */
    private int lambdaItem;

    // TODO 考虑修改为矩阵和向量
    private float[] factorVariances;
    private int[][] factorRanks;
    private DenseVector rankProbabilities;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        // set for this alg
        lambdaItem = (int) (configuration.getFloat("recommender.item.distribution.parameter") * itemSize);
        // lamda_Item=500;
        loopNumber = (int) (itemSize * Math.log(itemSize));

        factorVariances = new float[factorSize];
        factorRanks = new int[factorSize][itemSize];
    }

    @Override
    protected void doPractice() {
        // 排序列表
        List<KeyValue<Integer, Float>> sortList = new ArrayList<>(itemSize);
        DefaultScalar sum = DefaultScalar.getInstance();
        sum.setValue(0F);
        rankProbabilities = DenseVector.valueOf(itemSize);
        rankProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            sortList.add(new KeyValue<>(index, 0F));
            float value = (float) Math.exp(-(index + 1) / lambdaItem);
            sum.shiftValue(value);
            scalar.setValue(sum.getValue());
        });
        List<IntSet> userItemSet = getUserItemSet(scoreMatrix);

        // TODO 此处需要重构
        List<Integer> userIndexes = new ArrayList<>(actionSize), itemIndexes = new ArrayList<>(actionSize);
        for (MatrixScalar term : scoreMatrix) {
            int userIndex = term.getRow();
            int itemIndex = term.getColumn();
            userIndexes.add(userIndex);
            itemIndexes.add(itemIndex);
        }

        // randoms get a f by p(f|c)
        DenseVector factorProbabilities = DenseVector.valueOf(factorSize);

        int sampleCount = 0;
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            totalError = 0F;
            for (int sampleIndex = 0, sampleTimes = userSize * 100; sampleIndex < sampleTimes; sampleIndex++) {
                // update Ranking every |I|log|I|
                if (sampleCount % loopNumber == 0) {
                    updateSortListByFactor(sortList);
                    sampleCount = 0;
                }
                sampleCount++;

                // randomly draw (u, i, j)
                int userIndex, positiveItemIndex, negativeItemIndex;
                while (true) {
                    int random = RandomUtility.randomInteger(actionSize);
                    userIndex = userIndexes.get(random);
                    IntSet itemSet = userItemSet.get(userIndex);
                    if (itemSet.size() == 0 || itemSet.size() == itemSize) {
                        continue;
                    }
                    positiveItemIndex = itemIndexes.get(random);
                    // 计算概率
                    DenseVector factorVector = userFactors.getRowVector(userIndex);
                    sum.setValue(0F);
                    factorProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                        int index = scalar.getIndex();
                        float value = Math.abs(factorVector.getValue(index)) * factorVariances[index];
                        sum.shiftValue(value);
                        scalar.setValue(sum.getValue());
                    });
                    do {
                        // randoms get a r by exp(-r/lamda)
                        int rankIndex = SampleUtility.binarySearch(rankProbabilities, 0, rankProbabilities.getElementSize() - 1, RandomUtility.randomFloat(rankProbabilities.getValue(rankProbabilities.getElementSize() - 1)));
                        int factorIndex = SampleUtility.binarySearch(factorProbabilities, 0, factorProbabilities.getElementSize() - 1, RandomUtility.randomFloat(factorProbabilities.getValue(factorProbabilities.getElementSize() - 1)));
                        // get the r-1 in f item
                        if (userFactors.getValue(userIndex, factorIndex) > 0) {
                            negativeItemIndex = factorRanks[factorIndex][rankIndex];
                        } else {
                            negativeItemIndex = factorRanks[factorIndex][itemSize - rankIndex - 1];
                        }
                    } while (itemSet.contains(negativeItemIndex));
                    break;
                }

                // update parameters
                float positiveScore = predict(userIndex, positiveItemIndex);
                float negativeScore = predict(userIndex, negativeItemIndex);
                float error = positiveScore - negativeScore;
                float value = (float) -Math.log(LogisticUtility.getValue(error));
                totalError += value;
                value = LogisticUtility.getValue(-error);

                for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                    float userFactor = userFactors.getValue(userIndex, factorIndex);
                    float positiveFactor = itemFactors.getValue(positiveItemIndex, factorIndex);
                    float negativeFactor = itemFactors.getValue(negativeItemIndex, factorIndex);
                    userFactors.shiftValue(userIndex, factorIndex, learnRatio * (value * (positiveFactor - negativeFactor) - userRegularization * userFactor));
                    itemFactors.shiftValue(positiveItemIndex, factorIndex, learnRatio * (value * userFactor - itemRegularization * positiveFactor));
                    itemFactors.shiftValue(negativeItemIndex, factorIndex, learnRatio * (value * (-userFactor) - itemRegularization * negativeFactor));
                    totalError += userRegularization * userFactor * userFactor + itemRegularization * positiveFactor * positiveFactor + itemRegularization * negativeFactor * negativeFactor;
                }
            }

            if (isConverged(epocheIndex) && isConverged) {
                break;
            }
            isLearned(epocheIndex);
            currentError = totalError;
        }
    }

    // TODO 考虑重构
    private void updateSortListByFactor(List<KeyValue<Integer, Float>> sortList) {
        // echo for each factors
        for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
            float sum = 0F;
            DenseVector factorVector = itemFactors.getColumnVector(factorIndex);
            for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                float value = factorVector.getValue(itemIndex);
                sortList.get(itemIndex).setValue(value);
                sum += value;
            }
            Collections.sort(sortList, (left, right) -> {
                // 降序
                return right.getValue().compareTo(left.getValue());
            });
            float mean = sum / factorVector.getElementSize();
            sum = 0F;
            for (int sortIndex = 0; sortIndex < itemSize; sortIndex++) {
                float value = factorVector.getValue(sortIndex);
                sum += (value - mean) * (value - mean);
                factorRanks[factorIndex][sortIndex] = sortList.get(sortIndex).getKey();
            }
            factorVariances[factorIndex] = sum / factorVector.getElementSize();
        }
    }

}
