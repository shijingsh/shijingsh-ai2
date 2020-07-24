package com.shijingsh.rns.model.collaborative.rating;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map.Entry;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.algorithm.probability.QuantityProbability;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.ProbabilisticGraphicalModel;

/**
 *
 * RBM推荐器
 *
 * <pre>
 * Restricted Boltzman Machines for Collaborative Filtering
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class RBMModel extends ProbabilisticGraphicalModel {

    private int steps;
    private float epsilonWeight;
    private float epsilonExplicitBias;
    private float epsilonImplicitBias;
    private float momentum;
    private float lamtaWeight;
    private float lamtaBias;

    private float[][][] weightSums;
    private float[][][] weightProbabilities;
    private float[][] explicitBiasSums;
    private float[][] explicitBiasProbabilities;
    private float[] implicitBiasSums;
    private float[] implicitBiasProbabilities;

    private float[][][] positiveWeights;
    private float[][][] negativeWeights;

    private float[][] positiveExplicitActs;
    private float[][] negativeExplicitActs;

    private float[] positiveImplicitActs;
    private float[] negativeImplicitActs;

    private int[] itemCount;
    private PredictionType predictionType;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        // TODO 此处可以重构
        epocheSize = configuration.getInteger("recommender.iterator.maximum", 10);
        sampleSize = configuration.getInteger("recommender.sample.mumber", 100);
        scoreSize = scoreIndexes.size() + 1;
        factorSize = configuration.getInteger("recommender.factor.number", 500);

        epsilonWeight = configuration.getFloat("recommender.epsilonw", 0.001F);
        epsilonExplicitBias = configuration.getFloat("recommender.epsilonvb", 0.001F);
        epsilonImplicitBias = configuration.getFloat("recommender.epsilonhb", 0.001F);
        steps = configuration.getInteger("recommender.tstep", 1);
        momentum = configuration.getFloat("recommender.momentum", 0F);
        lamtaWeight = configuration.getFloat("recommender.lamtaw", 0.001F);
        lamtaBias = configuration.getFloat("recommender.lamtab", 0F);
        predictionType = PredictionType.valueOf(configuration.getString("recommender.predictiontype", "mean").toUpperCase());

        weightProbabilities = new float[itemSize][scoreSize][factorSize];
        explicitBiasProbabilities = new float[itemSize][scoreSize];
        implicitBiasProbabilities = new float[factorSize];

        weightSums = new float[itemSize][scoreSize][factorSize];
        implicitBiasSums = new float[factorSize];
        explicitBiasSums = new float[itemSize][scoreSize];

        positiveWeights = new float[itemSize][scoreSize][factorSize];
        negativeWeights = new float[itemSize][scoreSize][factorSize];

        positiveImplicitActs = new float[factorSize];
        negativeImplicitActs = new float[factorSize];

        positiveExplicitActs = new float[itemSize][scoreSize];
        negativeExplicitActs = new float[itemSize][scoreSize];

        itemCount = new int[itemSize];

        // TODO 此处需要重构
        int[][] itemScoreCount = new int[itemSize][scoreSize];
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            SparseVector userVector = scoreMatrix.getRowVector(userIndex);
            if (userVector.getElementSize() == 0) {
                continue;
            }
            for (VectorScalar term : userVector) {
                int scoreIndex = scoreIndexes.get(term.getValue());
                itemScoreCount[term.getIndex()][scoreIndex]++;
            }
        }
        QuantityProbability distribution = new QuantityProbability(JDKRandomGenerator.class, 0, NormalDistribution.class, 0D, 0.01D);
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                    weightProbabilities[itemIndex][scoreIndex][factorIndex] = distribution.sample().floatValue();
                }
            }
        }

        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            double totalScore = 0D;
            for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                totalScore += itemScoreCount[itemIndex][scoreIndex];
            }
            for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                if (totalScore == 0D) {
                    explicitBiasProbabilities[itemIndex][scoreIndex] = RandomUtility.randomFloat(0.001F);
                } else {
                    explicitBiasProbabilities[itemIndex][scoreIndex] = (float) Math.log(itemScoreCount[itemIndex][scoreIndex] / totalScore);
                    // visbiases[i][k] = Math.log(((moviecount[i][k]) + 1) /
                    // (trainMatrix.columnSize(i)+ softmax));
                }
            }
        }
    }

    @Override
    protected void doPractice() {
        Collection<Integer> currentImplicitStates;
        Collection<Integer> positiveImplicitStates = new ArrayList<>(factorSize);
        Collection<Integer> negativeImplicitStates = new ArrayList<>(factorSize);
        DenseVector negativeExplicitProbabilities = DenseVector.valueOf(scoreSize);
        int[] negativeExplicitScores = new int[itemSize];
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            reset();
            // 随机遍历顺序
            Integer[] userIndexes = new Integer[userSize];
            for (int userIndex = 0; userIndex < userSize; userIndex++) {
                userIndexes[userIndex] = userIndex;
            }
            RandomUtility.shuffle(userIndexes);
            for (int userIndex : userIndexes) {
                SparseVector userVector = scoreMatrix.getRowVector(userIndex);
                if (userVector.getElementSize() == 0) {
                    continue;
                }
                DenseVector factorSum = DenseVector.valueOf(factorSize);
                for (VectorScalar term : userVector) {
                    int itemIndex = term.getIndex();
                    int scoreIndex = scoreIndexes.get(term.getValue());
                    itemCount[itemIndex]++;
                    positiveExplicitActs[itemIndex][scoreIndex] += 1F;
                    factorSum.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                        int index = scalar.getIndex();
                        float value = scalar.getValue();
                        scalar.setValue(value + weightProbabilities[itemIndex][scoreIndex][index]);
                    });
                }

                for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                    float probability = (float) (1F / (1F + Math.exp(-factorSum.getValue(factorIndex) - implicitBiasProbabilities[factorIndex])));
                    if (probability > RandomUtility.randomFloat(1F)) {
                        positiveImplicitStates.add(factorIndex);
                        positiveImplicitActs[factorIndex] += 1F;
                    }
                }

                currentImplicitStates = positiveImplicitStates;

                int step = 0;

                do {
                    boolean isLast = (step + 1 >= steps);
                    for (VectorScalar term : userVector) {
                        negativeExplicitProbabilities.setValues(0F);
                        int itemIndex = term.getIndex();
                        for (int factorIndex : currentImplicitStates) {
                            negativeExplicitProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                                int index = scalar.getIndex();
                                float value = scalar.getValue();
                                scalar.setValue(value + weightProbabilities[itemIndex][index][factorIndex]);
                            });
                        }

                        // 归一化
                        negativeExplicitProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                            int index = scalar.getIndex();
                            float value = scalar.getValue();
                            value = (float) (1F / (1F + Math.exp(-value - explicitBiasProbabilities[itemIndex][index])));
                            scalar.setValue(value);
                        });
                        negativeExplicitProbabilities.scaleValues(1F / negativeExplicitProbabilities.getSum(false));

                        // TODO 此处随机概率落在某个分段(需要重构,否则永远最多落在5个分段,应该是Bug.)
                        float random = RandomUtility.randomFloat(1F);
                        negativeExplicitScores[itemIndex] = scoreSize - 1;
                        for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                            if ((random -= negativeExplicitProbabilities.getValue(scoreIndex)) <= 0F) {
                                negativeExplicitScores[itemIndex] = scoreIndex;
                                break;
                            }
                        }
                        if (isLast) {
                            negativeExplicitActs[itemIndex][negativeExplicitScores[itemIndex]] += 1F;
                        }
                    }

                    factorSum.setValues(0F);
                    for (VectorScalar term : userVector) {
                        int itemIndex = term.getIndex();
                        factorSum.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                            int index = scalar.getIndex();
                            float value = scalar.getValue();
                            scalar.setValue(value + weightProbabilities[itemIndex][negativeExplicitScores[itemIndex]][index]);
                        });
                    }
                    for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                        float probability = (float) (1F / (1F + Math.exp(-factorSum.getValue(factorIndex) - implicitBiasProbabilities[factorIndex])));
                        if (probability > RandomUtility.randomFloat(1F)) {
                            negativeImplicitStates.add(factorIndex);
                            if (isLast) {
                                negativeImplicitActs[factorIndex] += 1.0;
                            }
                        }
                    }

                    if (!isLast) {
                        currentImplicitStates = negativeImplicitStates;
                    }
                } while (++step < steps);

                for (VectorScalar term : userVector) {
                    int itemIndex = term.getIndex();
                    int scoreIndex = scoreIndexes.get(term.getValue());
                    for (int factorIndex : positiveImplicitStates) {
                        positiveWeights[itemIndex][scoreIndex][factorIndex] += 1D;
                    }
                    for (int factorIndex : negativeImplicitStates) {
                        negativeWeights[itemIndex][negativeExplicitScores[itemIndex]][factorIndex] += 1D;
                    }
                }

                positiveImplicitStates.clear();
                negativeImplicitStates.clear();
                update(userIndex);
            }
        }

    }

    private void update(int userIndex) {
        // TODO size是否应该由参数指定?
        if (((userIndex + 1) % sampleSize) == 0 || (userIndex + 1) == userSize) {
            int numCases = userIndex % sampleSize;
            numCases++;

            float positiveExplicitAct;
            float negativeExplicitAct;
            float positiveImplicitAct;
            float negativeImplicitAct;
            float positiveWeight;
            float negativeWeight;

            for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                if (itemCount[itemIndex] == 0) {
                    continue;
                }
                for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                    positiveExplicitAct = positiveExplicitActs[itemIndex][scoreIndex];
                    negativeExplicitAct = negativeExplicitActs[itemIndex][scoreIndex];
                    if (positiveExplicitAct != 0F || negativeExplicitAct != 0F) {
                        positiveExplicitAct /= itemCount[itemIndex];
                        negativeExplicitAct /= itemCount[itemIndex];
                        explicitBiasSums[itemIndex][scoreIndex] = momentum * explicitBiasSums[itemIndex][scoreIndex] + epsilonExplicitBias * (positiveExplicitAct - negativeExplicitAct - lamtaBias * explicitBiasProbabilities[itemIndex][scoreIndex]);
                        explicitBiasProbabilities[itemIndex][scoreIndex] += explicitBiasSums[itemIndex][scoreIndex];
                        positiveExplicitActs[itemIndex][scoreIndex] = 0F;
                        negativeExplicitActs[itemIndex][scoreIndex] = 0F;
                    }
                    for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                        positiveWeight = positiveWeights[itemIndex][scoreIndex][factorIndex];
                        negativeWeight = negativeWeights[itemIndex][scoreIndex][factorIndex];
                        if (positiveWeight != 0F || negativeWeight != 0F) {
                            positiveWeight /= itemCount[itemIndex];
                            negativeWeight /= itemCount[itemIndex];
                            weightSums[itemIndex][scoreIndex][factorIndex] = momentum * weightSums[itemIndex][scoreIndex][factorIndex] + epsilonWeight * ((positiveWeight - negativeWeight) - lamtaWeight * weightProbabilities[itemIndex][scoreIndex][factorIndex]);
                            weightProbabilities[itemIndex][scoreIndex][factorIndex] += weightSums[itemIndex][scoreIndex][factorIndex];
                            positiveWeights[itemIndex][scoreIndex][factorIndex] = 0F;
                            negativeWeights[itemIndex][scoreIndex][factorIndex] = 0F;
                        }
                    }
                }
                itemCount[itemIndex] = 0;
            }

            for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                positiveImplicitAct = positiveImplicitActs[factorIndex];
                negativeImplicitAct = negativeImplicitActs[factorIndex];
                if (positiveImplicitAct != 0F || negativeImplicitAct != 0F) {
                    positiveImplicitAct /= numCases;
                    negativeImplicitAct /= numCases;
                    implicitBiasSums[factorIndex] = momentum * implicitBiasSums[factorIndex] + epsilonImplicitBias * (positiveImplicitAct - negativeImplicitAct - lamtaBias * implicitBiasProbabilities[factorIndex]);
                    implicitBiasProbabilities[factorIndex] += implicitBiasSums[factorIndex];
                    positiveImplicitActs[factorIndex] = 0F;
                    negativeImplicitActs[factorIndex] = 0F;
                }
            }
        }

    }

    private void reset() {
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            itemCount[itemIndex] = 0;
            for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                positiveExplicitActs[itemIndex][scoreIndex] = 0F;
                negativeExplicitActs[itemIndex][scoreIndex] = 0F;
                for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                    positiveWeights[itemIndex][scoreIndex][factorIndex] = 0F;
                    negativeWeights[itemIndex][scoreIndex][factorIndex] = 0F;
                }
            }
        }

        for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
            positiveImplicitActs[factorIndex] = 0F;
            negativeImplicitActs[factorIndex] = 0F;
        }
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        float[] socreProbabilities = new float[scoreSize];
        float[] factorProbabilities = new float[factorSize];
        float[] factorSums = new float[factorSize];
        // 用户历史分数记录?
        SparseVector userVector = scoreMatrix.getRowVector(userIndex);
        for (VectorScalar term : userVector) {
            int termIndex = term.getIndex();
            int scoreIndex = scoreIndexes.get(term.getValue());
            for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                factorSums[factorIndex] += weightProbabilities[termIndex][scoreIndex][factorIndex];
            }
        }
        for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
            factorProbabilities[factorIndex] = (float) (1F / (1F + Math.exp(0F - factorSums[factorIndex] - implicitBiasProbabilities[factorIndex])));
        }
        for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
            for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
                socreProbabilities[scoreIndex] += factorProbabilities[factorIndex] * weightProbabilities[itemIndex][scoreIndex][factorIndex];
            }
        }
        float probabilitySum = 0F;
        for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
            socreProbabilities[scoreIndex] = (float) (1F / (1F + Math.exp(0F - socreProbabilities[scoreIndex] - explicitBiasProbabilities[itemIndex][scoreIndex])));
            probabilitySum += socreProbabilities[scoreIndex];
        }
        for (int scoreIndex = 0; scoreIndex < scoreSize; scoreIndex++) {
            socreProbabilities[scoreIndex] /= probabilitySum;
        }
        float predict = 0F;
        switch (predictionType) {
        case MAX:
            float score = 0F;
            float probability = 0F;
            for (Entry<Float, Integer> term : scoreIndexes.entrySet()) {
                if (socreProbabilities[term.getValue()] > probability) {
                    probability = socreProbabilities[term.getValue()];
                    score = term.getKey();
                }
            }
            predict = score;
            break;
        case MEAN:
            float mean = 0f;
            for (Entry<Float, Integer> term : scoreIndexes.entrySet()) {
                mean += socreProbabilities[term.getValue()] * term.getKey();
            }
            predict = mean;
            break;
        }
        instance.setQuantityMark(predict);
    }

    @Override
    protected void eStep() {
        // TODO Auto-generated method stub

    }

    @Override
    protected void mStep() {
        // TODO Auto-generated method stub

    }

}

enum PredictionType {
    MAX, MEAN
}
