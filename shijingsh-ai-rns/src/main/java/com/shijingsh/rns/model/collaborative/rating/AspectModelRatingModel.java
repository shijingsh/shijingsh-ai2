package com.shijingsh.rns.model.collaborative.rating;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.MathUtility;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.ProbabilisticGraphicalModel;
import com.shijingsh.rns.utility.GaussianUtility;

/**
 *
 * Aspect Model推荐器
 *
 * <pre>
 * Latent class models for collaborative filtering
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class AspectModelRatingModel extends ProbabilisticGraphicalModel {
    /*
     * Conditional distribution: P(u|z)
     */
    private DenseMatrix userProbabilities, userSums;
    /*
     * Conditional distribution: P(i|z)
     */
    private DenseMatrix itemProbabilities, itemSums;
    /*
     * topic distribution: P(z)
     */
    private DenseVector topicProbabilities, topicSums;
    /*
     *
     */
    private DenseVector meanProbabilities, meanSums;
    /*
     *
     */
    private DenseVector varianceProbabilities, varianceSums;

    /*
     * small value
     */
    private static float smallValue = MathUtility.EPSILON;
    /*
     * {user, item, {topic z, probability}}
     */
    private float[][] probabilityTensor;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        // Initialize topic distribution
        topicProbabilities = DenseVector.valueOf(factorSize);
        topicProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(RandomUtility.randomInteger(factorSize) + 1);
        });
        topicProbabilities.scaleValues(1F / topicProbabilities.getSum(false));
        topicSums = DenseVector.valueOf(factorSize);

        // intialize conditional distribution P(u|z)
        userProbabilities = DenseMatrix.valueOf(factorSize, userSize);
        userSums = DenseMatrix.valueOf(factorSize, userSize);
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            DenseVector probabilityVector = userProbabilities.getRowVector(topicIndex);
            probabilityVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                scalar.setValue(RandomUtility.randomInteger(userSize) + 1);
            });
            probabilityVector.scaleValues(1F / probabilityVector.getSum(false));
        }

        // initialize conditional distribution P(i|z)
        itemProbabilities = DenseMatrix.valueOf(factorSize, itemSize);
        itemSums = DenseMatrix.valueOf(factorSize, itemSize);
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            DenseVector probabilityVector = itemProbabilities.getRowVector(topicIndex);
            probabilityVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                scalar.setValue(RandomUtility.randomInteger(itemSize) + 1);
            });
            probabilityVector.scaleValues(1F / probabilityVector.getSum(false));
        }

        // initialize Q
        probabilityTensor = new float[actionSize][factorSize];

        float globalMean = scoreMatrix.getSum(false) / scoreMatrix.getElementSize();
        meanProbabilities = DenseVector.valueOf(factorSize);
        varianceProbabilities = DenseVector.valueOf(factorSize);
        meanSums = DenseVector.valueOf(factorSize);
        varianceSums = DenseVector.valueOf(factorSize);
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            meanProbabilities.setValue(topicIndex, globalMean);
            varianceProbabilities.setValue(topicIndex, 2);
        }
    }

    @Override
    protected void eStep() {
        topicSums.setValues(smallValue);
        userSums.setValues(0F);
        itemSums.setValues(0F);
        meanSums.setValues(0F);
        varianceSums.setValues(smallValue);
        // variational inference to compute Q
        int actionIndex = 0;
        for (MatrixScalar term : scoreMatrix) {
            int userIndex = term.getRow();
            int itemIndex = term.getColumn();
            float denominator = 0F;
            float[] numerator = probabilityTensor[actionIndex++];
            for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
                float value = topicProbabilities.getValue(topicIndex) * userProbabilities.getValue(topicIndex, userIndex) * itemProbabilities.getValue(topicIndex, itemIndex) * GaussianUtility.probabilityDensity(term.getValue(), meanProbabilities.getValue(topicIndex), varianceProbabilities.getValue(topicIndex));
                numerator[topicIndex] = value;
                denominator += value;
            }
            for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
                float probability = denominator > 0 ? numerator[topicIndex] / denominator : 0F;
                numerator[topicIndex] = probability;
            }

            float score = term.getValue();
            for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
                float probability = numerator[topicIndex];
                topicSums.shiftValue(topicIndex, probability);
                userSums.shiftValue(topicIndex, userIndex, probability);
                itemSums.shiftValue(topicIndex, itemIndex, probability);
                meanSums.shiftValue(topicIndex, score * probability);
            }
        }

        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            float mean = meanSums.getValue(topicIndex) / topicSums.getValue(topicIndex);
            meanProbabilities.setValue(topicIndex, mean);
        }

        actionIndex = 0;
        for (MatrixScalar term : scoreMatrix) {
            float[] probabilities = probabilityTensor[actionIndex++];
            for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
                float mean = meanProbabilities.getValue(topicIndex);
                float error = term.getValue() - mean;
                float probability = probabilities[topicIndex];
                varianceSums.shiftValue(topicIndex, error * error * probability);
            }
        }
    }

    @Override
    protected void mStep() {
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            varianceProbabilities.setValue(topicIndex, varianceSums.getValue(topicIndex) / topicSums.getValue(topicIndex));
            topicProbabilities.setValue(topicIndex, topicSums.getValue(topicIndex) / actionSize);
            for (int userIndex = 0; userIndex < userSize; userIndex++) {
                userProbabilities.setValue(topicIndex, userIndex, userSums.getValue(topicIndex, userIndex) / topicSums.getValue(topicIndex));
            }
            for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                itemProbabilities.setValue(topicIndex, itemIndex, itemSums.getValue(topicIndex, itemIndex) / topicSums.getValue(topicIndex));
            }
        }
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        float value = 0F;
        float denominator = 0F;
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            float weight = topicProbabilities.getValue(topicIndex) * userProbabilities.getValue(topicIndex, userIndex) * itemProbabilities.getValue(topicIndex, itemIndex);
            denominator += weight;
            value += weight * meanProbabilities.getValue(topicIndex);
        }
        value = value / denominator;
        if (Float.isNaN(value)) {
            value = meanScore;
        }
        instance.setQuantityMark(value);
    }

}
