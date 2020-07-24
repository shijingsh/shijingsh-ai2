package com.shijingsh.rns.model.benchmark.rating;

import java.util.Map.Entry;

import org.apache.commons.math3.util.FastMath;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.ProbabilisticGraphicalModel;

/**
 *
 * Item Cluster推荐器
 *
 * <pre>
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "userDimension", "itemDimension", "itemTopicProbabilities", "numberOfFactors", "scoreIndexes", "topicScoreMatrix" })
public class ItemClusterModel extends ProbabilisticGraphicalModel {

    /** 物品的每评分次数 */
    private DenseMatrix itemScoreMatrix; // Nur
    /** 物品的总评分次数 */
    private DenseVector itemScoreVector; // Nu

    /** 主题的每评分概率 */
    private DenseMatrix topicScoreMatrix; // Pkr
    /** 主题的总评分概率 */
    private DenseVector topicScoreVector; // Pi

    /** 物品主题概率映射 */
    private DenseMatrix itemTopicProbabilities; // Gamma_(u,k)

    @Override
    protected boolean isConverged(int iter) {
        // TODO 需要重构
        float loss = 0F;
        for (int i = 0; i < itemSize; i++) {
            for (int k = 0; k < factorSize; k++) {
                float rik = itemTopicProbabilities.getValue(i, k);
                float pi_k = topicScoreVector.getValue(k);

                float sum_nl = 0F;
                for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                    float nir = itemScoreMatrix.getValue(i, scoreIndex);
                    float pkr = topicScoreMatrix.getValue(k, scoreIndex);

                    sum_nl += nir * Math.log(pkr);
                }

                loss += rik * (Math.log(pi_k) + sum_nl);
            }
        }
        float deltaLoss = (float) (loss - currentError);
        if (iter > 1 && (deltaLoss > 0 || Float.isNaN(deltaLoss))) {
            return true;
        }
        currentError = loss;
        return false;
    }

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        topicScoreMatrix = DenseMatrix.valueOf(factorSize, scoreSize);
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            DenseVector probabilityVector = topicScoreMatrix.getRowVector(topicIndex);
            probabilityVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                float value = scalar.getValue();
                scalar.setValue(RandomUtility.randomInteger(scoreSize) + 1);
            });
            probabilityVector.scaleValues(1F / probabilityVector.getSum(false));
        }
        topicScoreVector = DenseVector.valueOf(factorSize);
        topicScoreVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(RandomUtility.randomInteger(factorSize) + 1);
        });
        topicScoreVector.scaleValues(1F / topicScoreVector.getSum(false));
        // TODO
        topicScoreMatrix.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue((float) Math.log(scalar.getValue()));
        });
        topicScoreVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue((float) Math.log(scalar.getValue()));
        });

        itemScoreMatrix = DenseMatrix.valueOf(itemSize, scoreSize);
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            SparseVector scoreVector = scoreMatrix.getColumnVector(itemIndex);
            for (VectorScalar term : scoreVector) {
                float score = term.getValue();
                int scoreIndex = scoreIndexes.get(score);
                itemScoreMatrix.shiftValue(itemIndex, scoreIndex, 1);
            }
        }
        itemScoreVector = DenseVector.valueOf(itemSize);
        itemScoreVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(scoreMatrix.getColumnVector(scalar.getIndex()).getElementSize());
        });
        currentError = Float.MIN_VALUE;

        itemTopicProbabilities = DenseMatrix.valueOf(itemSize, factorSize);
    }

    @Override
    protected void eStep() {
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            DenseVector probabilityVector = itemTopicProbabilities.getRowVector(itemIndex);
            SparseVector scoreVector = scoreMatrix.getColumnVector(itemIndex);
            if (scoreVector.getElementSize() == 0) {
                probabilityVector.copyVector(topicScoreVector);
            } else {
                probabilityVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                    int index = scalar.getIndex();
                    float topicProbability = topicScoreVector.getValue(index);
                    for (VectorScalar term : scoreVector) {
                        int scoreIndex = scoreIndexes.get(term.getValue());
                        float scoreProbability = topicScoreMatrix.getValue(index, scoreIndex);
                        topicProbability = topicProbability + scoreProbability;
                    }
                    scalar.setValue(topicProbability);
                });
                probabilityVector.scaleValues(1F / probabilityVector.getSum(false));
            }
        }
    }

    @Override
    protected void mStep() {
        topicScoreVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                float numerator = 0F, denorminator = 0F;
                for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                    float probability = (float) FastMath.exp(itemTopicProbabilities.getValue(itemIndex, index));
                    numerator += probability * itemScoreMatrix.getValue(itemIndex, scoreIndex);
                    denorminator += probability * itemScoreVector.getValue(itemIndex);
                }
                float probability = (numerator / denorminator);
                topicScoreMatrix.setValue(index, scoreIndex, probability);
            }
            float sumProbability = 0F;
            for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                float probability = (float) FastMath.exp(itemTopicProbabilities.getValue(itemIndex, index));
                sumProbability += probability;
            }
            scalar.setValue(sumProbability);
        });
        topicScoreVector.scaleValues(1F / topicScoreVector.getSum(false));
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        float value = 0F;
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            float topicProbability = itemTopicProbabilities.getValue(itemIndex, topicIndex); // probability
            float topicValue = 0F;
            for (Entry<Float, Integer> entry : scoreIndexes.entrySet()) {
                float score = entry.getKey();
                float probability = topicScoreMatrix.getValue(topicIndex, entry.getValue());
                topicValue += score * probability;
            }
            value += topicProbability * topicValue;
        }
        instance.setQuantityMark(value);
    }

}
