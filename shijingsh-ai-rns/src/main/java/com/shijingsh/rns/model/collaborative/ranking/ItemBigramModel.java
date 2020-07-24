package com.shijingsh.rns.model.collaborative.ranking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.KeyValue;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.core.utility.StringUtility;
import com.shijingsh.rns.model.ProbabilisticGraphicalModel;
import com.shijingsh.rns.utility.GammaUtility;
import com.shijingsh.rns.utility.SampleUtility;

import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;

/**
 *
 * Item Bigram推荐器
 *
 * <pre>
 * Topic modeling: beyond bag-of-words
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class ItemBigramModel extends ProbabilisticGraphicalModel {

    /** 上下文字段 */
    private String instantField;

    /** 上下文维度 */
    private int instantDimension;

    private Map<Integer, List<Integer>> userItemMap;

    /**
     * k: current topic; j: previously rated item; i: current item
     */
    private int[][][] topicItemBigramTimes;
    private DenseMatrix topicItemProbabilities;
    private float[][][] topicItemBigramProbabilities, topicItemBigramSums;

    private DenseMatrix beta;

    /**
     * vector of hyperparameters for alpha
     */
    private DenseVector alpha;

    /**
     * Dirichlet hyper-parameters of user-topic distribution: typical value is 50/K
     */
    private float initAlpha;

    /**
     * Dirichlet hyper-parameters of topic-item distribution, typical value is 0.01
     */
    private float initBeta;

    /**
     * cumulative statistics of theta, phi
     */
    private DenseMatrix userTopicSums;

    /**
     * entry[u, k]: number of tokens assigned to topic k, given user u.
     */
    private DenseMatrix userTopicTimes;

    /**
     * entry[u]: number of tokens rated by user u.
     */
    private DenseVector userTokenNumbers;

    /**
     * posterior probabilities of parameters
     */
    private DenseMatrix userTopicProbabilities;

    /**
     * entry[u, i, k]: topic assignment as sparse structure
     */
    // TODO 考虑DenseMatrix支持Integer类型
    private Int2IntRBTreeMap topicAssignments;

    private DenseVector randomProbabilities;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        initAlpha = configuration.getFloat("recommender.user.dirichlet.prior", 0.01F);
        initBeta = configuration.getFloat("recommender.topic.dirichlet.prior", 0.01F);

        instantField = configuration.getString("data.model.fields.instant");
        instantDimension = model.getQualityInner(instantField);
        Int2IntRBTreeMap instantTabel = new Int2IntRBTreeMap();
        instantTabel.defaultReturnValue(-1);
        for (DataInstance sample : model) {
            int instant = instantTabel.get(sample.getQualityFeature(userDimension) * itemSize + sample.getQualityFeature(itemDimension));
            if (instant == -1) {
                instant = sample.getQualityFeature(instantDimension);
            } else {
                instant = sample.getQualityFeature(instantDimension) > instant ? sample.getQualityFeature(instantDimension) : instant;
            }
            instantTabel.put(sample.getQualityFeature(userDimension) * itemSize + sample.getQualityFeature(itemDimension), instant);
        }
        // build the training data, sorting by date
        userItemMap = new HashMap<>();
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            // TODO 考虑优化
            SparseVector userVector = scoreMatrix.getRowVector(userIndex);
            if (userVector.getElementSize() == 0) {
                continue;
            }

            // 按照时间排序
            List<KeyValue<Integer, Integer>> instants = new ArrayList<>(userVector.getElementSize());
            for (VectorScalar term : userVector) {
                int itemIndex = term.getIndex();
                instants.add(new KeyValue<>(itemIndex, instantTabel.get(userIndex * itemSize + itemIndex)));
            }
            Collections.sort(instants, (left, right) -> {
                // 升序
                return left.getValue().compareTo(right.getValue());
            });
            List<Integer> items = new ArrayList<>(userVector.getElementSize());
            for (KeyValue<Integer, Integer> term : instants) {
                items.add(term.getKey());
            }

            userItemMap.put(userIndex, items);
        }

        // count variables
        // initialize count variables.
        userTopicTimes = DenseMatrix.valueOf(userSize, factorSize);
        userTokenNumbers = DenseVector.valueOf(userSize);

        // 注意:numItems + 1的最后一个元素代表没有任何记录的概率
        topicItemBigramTimes = new int[factorSize][itemSize + 1][itemSize];
        topicItemProbabilities = DenseMatrix.valueOf(factorSize, itemSize + 1);

        // Logs.debug("topicPreItemCurItemNum consumes {} bytes",
        // Strings.toString(Memory.bytes(topicPreItemCurItemNum)));

        // parameters
        userTopicSums = DenseMatrix.valueOf(userSize, factorSize);
        topicItemBigramSums = new float[factorSize][itemSize + 1][itemSize];
        topicItemBigramProbabilities = new float[factorSize][itemSize + 1][itemSize];

        // hyper-parameters
        alpha = DenseVector.valueOf(factorSize);
        alpha.setValues(initAlpha);

        beta = DenseMatrix.valueOf(factorSize, itemSize + 1);
        beta.setValues(initBeta);

        // initialization
        topicAssignments = new Int2IntRBTreeMap();
        for (Entry<Integer, List<Integer>> term : userItemMap.entrySet()) {
            int userIndex = term.getKey();
            List<Integer> items = term.getValue();

            for (int index = 0; index < items.size(); index++) {
                int nextItemIndex = items.get(index);
                // TODO 需要重构
                int topicIndex = RandomUtility.randomInteger(factorSize);
                topicAssignments.put(userIndex * itemSize + nextItemIndex, topicIndex);

                userTopicTimes.shiftValue(userIndex, topicIndex, 1F);
                userTokenNumbers.shiftValue(userIndex, 1F);

                int previousItemIndex = index > 0 ? items.get(index - 1) : itemSize;
                topicItemBigramTimes[topicIndex][previousItemIndex][nextItemIndex]++;
                topicItemProbabilities.shiftValue(topicIndex, previousItemIndex, 1F);
            }
        }

        randomProbabilities = DenseVector.valueOf(factorSize);
    }

    @Override
    protected void eStep() {
        float sumAlpha = alpha.getSum(false);
        DenseVector topicVector = DenseVector.valueOf(factorSize);
        topicVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(beta.getRowVector(scalar.getIndex()).getSum(false));
        });

        for (Entry<Integer, List<Integer>> term : userItemMap.entrySet()) {
            int userIndex = term.getKey();
            List<Integer> items = term.getValue();

            for (int index = 0; index < items.size(); index++) {
                int nextItemIndex = items.get(index);
                int assignmentIndex = topicAssignments.get(userIndex * itemSize + nextItemIndex);

                userTopicTimes.shiftValue(userIndex, assignmentIndex, -1F);
                userTokenNumbers.shiftValue(userIndex, -1F);

                int previousItemIndex = index > 0 ? items.get(index - 1) : itemSize;
                topicItemBigramTimes[assignmentIndex][previousItemIndex][nextItemIndex]--;
                topicItemProbabilities.shiftValue(assignmentIndex, previousItemIndex, -1F);

                // 计算概率
                DefaultScalar sum = DefaultScalar.getInstance();
                sum.setValue(0F);
                randomProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                    int topicIndex = scalar.getIndex();
                    float userProbability = (userTopicTimes.getValue(userIndex, assignmentIndex) + alpha.getValue(topicIndex)) / (userTokenNumbers.getValue(userIndex) + sumAlpha);
                    float topicProbability = (topicItemBigramTimes[topicIndex][previousItemIndex][nextItemIndex] + beta.getValue(topicIndex, previousItemIndex)) / (topicItemProbabilities.getValue(topicIndex, previousItemIndex) + topicVector.getValue(topicIndex));
                    float value = userProbability * topicProbability;
                    sum.shiftValue(value);
                    scalar.setValue(sum.getValue());
                });

                int randomIndex = SampleUtility.binarySearch(randomProbabilities, 0, randomProbabilities.getElementSize() - 1, RandomUtility.randomFloat(sum.getValue()));
                topicAssignments.put(userIndex * itemSize + nextItemIndex, randomIndex);
                userTopicTimes.shiftValue(userIndex, randomIndex, 1F);
                userTokenNumbers.shiftValue(userIndex, 1F);
                topicItemBigramTimes[randomIndex][previousItemIndex][nextItemIndex]++;
                topicItemProbabilities.shiftValue(randomIndex, previousItemIndex, 1F);
            }
        }
    }

    @Override
    protected void mStep() {
        float denominator = 0F;
        float value = 0F;

        float alphaSum = alpha.getSum(false);
        float alphaDigamma = GammaUtility.digamma(alphaSum);
        float alphaValue;
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            // TODO 应该修改为稀疏向量
            value = userTokenNumbers.getValue(userIndex);
            if (value != 0F) {
                denominator += GammaUtility.digamma(value + alphaSum) - alphaDigamma;
            }
        }
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            alphaValue = alpha.getValue(topicIndex);
            alphaDigamma = GammaUtility.digamma(alphaValue);
            float numerator = 0F;
            for (int userIndex = 0; userIndex < userSize; userIndex++) {
                // TODO 应该修改为稀疏矩阵
                value = userTopicTimes.getValue(userIndex, topicIndex);
                if (value != 0F) {
                    numerator += GammaUtility.digamma(value + alphaValue) - alphaDigamma;
                }
            }
            if (numerator != 0D) {
                alpha.setValue(topicIndex, alphaValue * (numerator / denominator));
            }
        }

        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            float betaSum = beta.getRowVector(topicIndex).getSum(false);
            float betaDigamma = GammaUtility.digamma(betaSum);
            float betaValue;
            float[] denominators = new float[itemSize + 1];
            for (int itemIndex = 0; itemIndex < itemSize + 1; itemIndex++) {
                // TODO 应该修改为稀疏矩阵
                value = topicItemProbabilities.getValue(topicIndex, itemIndex);
                if (value != 0F) {
                    denominators[itemIndex] = GammaUtility.digamma(value + betaSum) - betaDigamma;
                }
            }
            for (int previousItemIndex = 0; previousItemIndex < itemSize + 1; previousItemIndex++) {
                betaValue = beta.getValue(topicIndex, previousItemIndex);
                betaDigamma = GammaUtility.digamma(betaValue);
                float numerator = 0F;
                denominator = 0F;
                for (int nextItemIndex = 0; nextItemIndex < itemSize; nextItemIndex++) {
                    // TODO 应该修改为稀疏张量
                    value = topicItemBigramTimes[topicIndex][previousItemIndex][nextItemIndex];
                    if (value != 0F) {
                        numerator += GammaUtility.digamma(value + betaValue) - betaDigamma;
                    }
                    denominator += denominators[previousItemIndex];
                }
                if (numerator != 0F) {
                    beta.setValue(topicIndex, previousItemIndex, betaValue * (numerator / denominator));
                }
            }
        }
    }

    @Override
    protected void readoutParameters() {
        float value;
        float sumAlpha = alpha.getSum(false);
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
                value = (userTopicTimes.getValue(userIndex, topicIndex) + alpha.getValue(topicIndex)) / (userTokenNumbers.getValue(userIndex) + sumAlpha);
                userTopicSums.shiftValue(userIndex, topicIndex, value);
            }
        }
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            float betaTopicValue = beta.getRowVector(topicIndex).getSum(false);
            for (int previousItemIndex = 0; previousItemIndex < itemSize + 1; previousItemIndex++) {
                for (int nextItemIndex = 0; nextItemIndex < itemSize; nextItemIndex++) {
                    value = (topicItemBigramTimes[topicIndex][previousItemIndex][nextItemIndex] + beta.getValue(topicIndex, previousItemIndex)) / (topicItemProbabilities.getValue(topicIndex, previousItemIndex) + betaTopicValue);
                    topicItemBigramSums[topicIndex][previousItemIndex][nextItemIndex] += value;
                }
            }
        }
        if (logger.isInfoEnabled()) {
            String message = StringUtility.format("sumAlpha is {}", sumAlpha);
            logger.info(message);
        }
        numberOfStatistics++;
    }

    @Override
    protected void estimateParameters() {
        userTopicProbabilities = DenseMatrix.copyOf(userTopicSums);
        userTopicProbabilities.scaleValues(1F / numberOfStatistics);

        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            for (int previousItemIndex = 0; previousItemIndex < itemSize + 1; previousItemIndex++) {
                for (int nextItemIndex = 0; nextItemIndex < itemSize; nextItemIndex++) {
                    topicItemBigramProbabilities[topicIndex][previousItemIndex][nextItemIndex] = topicItemBigramSums[topicIndex][previousItemIndex][nextItemIndex] / numberOfStatistics;
                }
            }
        }
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        List<Integer> items = userItemMap.get(userIndex);
        int scoreIndex = items == null ? itemSize : items.get(items.size() - 1); // last
        // rated
        // item
        float value = 0F;
        for (int topicIndex = 0; topicIndex < factorSize; topicIndex++) {
            value += userTopicProbabilities.getValue(userIndex, topicIndex) * topicItemBigramProbabilities[topicIndex][scoreIndex][itemIndex];
        }

        instance.setQuantityMark(value);
    }

}
