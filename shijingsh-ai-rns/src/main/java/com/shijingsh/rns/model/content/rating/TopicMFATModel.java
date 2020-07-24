package com.shijingsh.rns.model.content.rating;

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.data.attribute.MemoryQualityAttribute;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.matrix.SparseMatrix;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.ai.model.neuralnetwork.activation.ActivationFunction;
import com.shijingsh.ai.model.neuralnetwork.activation.SoftMaxActivationFunction;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.MatrixFactorizationModel;

/**
 *
 * TopicMF AT推荐器
 *
 * <pre>
 * TopicMF: Simultaneously Exploiting Ratings and Reviews for Recommendation
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class TopicMFATModel extends MatrixFactorizationModel {

    protected String commentField;
    protected int commentDimension;
    protected SparseMatrix W;
    protected DenseMatrix documentFactors;
    protected DenseMatrix wordFactors;
    protected float K1, K2;
    protected DenseVector userBiases;
    protected DenseVector itemBiases;
    // TODO 准备取消,父类已实现.
    protected DenseMatrix userFactors;
    protected DenseMatrix itemFactors;
    // TODO topic似乎就是factor?
    protected int numberOfTopics;
    protected int numberOfWords;
    protected int numberOfDocuments;

    protected float lambda, lambdaU, lambdaV, lambdaB;

    protected Table<Integer, Integer, Integer> userItemToDocument;
    // TODO 准备取消,父类已实现.
    protected float initMean;
    protected float initStd;

    protected DenseVector topicVector;
    protected ActivationFunction function;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);

        commentField = configuration.getString("data.model.fields.comment");
        commentDimension = model.getQualityInner(commentField);
        MemoryQualityAttribute attribute = (MemoryQualityAttribute) space.getQualityAttribute(commentField);
        Object[] documentValues = attribute.getDatas();

        // init hyper-parameters
        lambda = configuration.getFloat("recommender.regularization.lambda", 0.001F);
        lambdaU = configuration.getFloat("recommender.regularization.lambdaU", 0.001F);
        lambdaV = configuration.getFloat("recommender.regularization.lambdaV", 0.001F);
        lambdaB = configuration.getFloat("recommender.regularization.lambdaB", 0.001F);
        numberOfTopics = configuration.getInteger("recommender.topic.number", 10);
        learnRatio = configuration.getFloat("recommender.iterator.learnrate", 0.01F);
        epocheSize = configuration.getInteger("recommender.iterator.maximum", 10);

        numberOfDocuments = scoreMatrix.getElementSize();

        // count the number of words, build the word dictionary and
        // userItemToDoc dictionary
        Map<String, Integer> wordDictionaries = new HashMap<>();
        Table<Integer, Integer, Float> documentTable = HashBasedTable.create();
        // TODO rowCount改为documentIndex?
        int rowCount = 0;
        userItemToDocument = HashBasedTable.create();
        for (DataInstance sample : model) {
            int userIndex = sample.getQualityFeature(userDimension);
            int itemIndex = sample.getQualityFeature(itemDimension);
            int documentIndex = sample.getQualityFeature(commentDimension);
            userItemToDocument.put(userIndex, itemIndex, rowCount);
            // convert wordIds to wordIndices
            String data = (String) documentValues[documentIndex];
            String[] words = data.isEmpty() ? new String[0] : data.split(":");
            for (String word : words) {
                Integer wordIndex = wordDictionaries.get(word);
                if (wordIndex == null) {
                    wordIndex = numberOfWords++;
                    wordDictionaries.put(word, wordIndex);
                }
                Float oldValue = documentTable.get(rowCount, wordIndex);
                if (oldValue == null) {
                    oldValue = 0F;
                }
                float newValue = oldValue + 1F / words.length;
                documentTable.put(rowCount, wordIndex, newValue);
            }
            rowCount++;
        }
        // build W
        W = SparseMatrix.valueOf(numberOfDocuments, numberOfWords, documentTable);

        userBiases = DenseVector.valueOf(userSize);
        userBiases.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        itemBiases = DenseVector.valueOf(itemSize);
        itemBiases.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        userFactors = DenseMatrix.valueOf(userSize, numberOfTopics);
        userFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        itemFactors = DenseMatrix.valueOf(itemSize, numberOfTopics);
        itemFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });

        K1 = initStd;
        K2 = initStd;

        topicVector = DenseVector.valueOf(numberOfTopics);
        function = new SoftMaxActivationFunction();

        // init theta and phi
        // TODO theta实际是documentFactors
        documentFactors = DenseMatrix.valueOf(numberOfDocuments, numberOfTopics);
        calculateTheta();
        // TODO phi实际是wordFactors
        wordFactors = DenseMatrix.valueOf(numberOfTopics, numberOfWords);
        wordFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(RandomUtility.randomFloat(0.01F));
        });

        logger.info("number of users : " + userSize);
        logger.info("number of Items : " + itemSize);
        logger.info("number of words : " + wordDictionaries.size());
    }

    @Override
    protected void doPractice() {
        DefaultScalar scalar = DefaultScalar.getInstance();
        DenseMatrix transposeThis = DenseMatrix.valueOf(numberOfTopics, numberOfTopics);
        DenseMatrix thetaW = DenseMatrix.valueOf(numberOfTopics, numberOfWords);
        DenseMatrix thetaPhi = DenseMatrix.valueOf(numberOfTopics, numberOfWords);
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            totalError = 0F;
            float wordLoss = 0F;
            for (MatrixScalar term : scoreMatrix) {
                int userIndex = term.getRow(); // userIdx
                int itemIndex = term.getColumn(); // itemIdx
                int documentIndex = userItemToDocument.get(userIndex, itemIndex);
                float y_true = term.getValue();
                float y_pred = predict(userIndex, itemIndex);

                float error = y_true - y_pred;
                totalError += error * error;

                // update user item biases
                float userBiasValue = userBiases.getValue(userIndex);
                userBiases.shiftValue(userIndex, learnRatio * (error - lambdaB * userBiasValue));
                totalError += lambdaB * userBiasValue * userBiasValue;

                float itemBiasValue = itemBiases.getValue(itemIndex);
                itemBiases.shiftValue(itemIndex, learnRatio * (error - lambdaB * itemBiasValue));
                totalError += lambdaB * itemBiasValue * itemBiasValue;

                // update user item factors
                for (int factorIndex = 0; factorIndex < numberOfTopics; factorIndex++) {
                    float userFactor = userFactors.getValue(userIndex, factorIndex);
                    float itemFactor = itemFactors.getValue(itemIndex, factorIndex);

                    userFactors.shiftValue(userIndex, factorIndex, learnRatio * (error * itemFactor - lambdaU * userFactor));
                    itemFactors.shiftValue(itemIndex, factorIndex, learnRatio * (error * userFactor - lambdaV * itemFactor));
                    totalError += lambdaU * userFactor * userFactor + lambdaV * itemFactor * itemFactor;

                    SparseVector documentVector = W.getRowVector(documentIndex);
                    for (VectorScalar documentTerm : documentVector) {
                        int wordIndex = documentTerm.getIndex();
                        float w_pred = scalar.dotProduct(documentFactors.getRowVector(documentIndex), wordFactors.getColumnVector(wordIndex)).getValue();
                        float w_true = documentTerm.getValue();
                        float w_error = w_true - w_pred;
                        wordLoss += w_error;

                        float derivative = 0F;
                        for (int topicIndex = 0; topicIndex < numberOfTopics; topicIndex++) {
                            if (factorIndex == topicIndex) {
                                derivative += w_error * wordFactors.getValue(topicIndex, wordIndex) * documentFactors.getValue(documentIndex, topicIndex) * (1 - documentFactors.getValue(documentIndex, topicIndex));
                            } else {
                                derivative += w_error * wordFactors.getValue(topicIndex, wordIndex) * documentFactors.getValue(documentIndex, topicIndex) * (-documentFactors.getValue(documentIndex, factorIndex));
                            }
                            // update K1 K2
                            K1 += learnRatio * lambda * w_error * wordFactors.getValue(topicIndex, wordIndex) * documentFactors.getValue(documentIndex, topicIndex) * (1 - documentFactors.getValue(documentIndex, topicIndex)) * Math.abs(userFactors.getValue(userIndex, topicIndex));
                            K2 += learnRatio * lambda * w_error * wordFactors.getValue(topicIndex, wordIndex) * documentFactors.getValue(documentIndex, topicIndex) * (1 - documentFactors.getValue(documentIndex, topicIndex)) * Math.abs(itemFactors.getValue(itemIndex, topicIndex));
                        }
                        userFactors.shiftValue(userIndex, factorIndex, learnRatio * K1 * derivative);
                        itemFactors.shiftValue(itemIndex, factorIndex, learnRatio * K2 * derivative);
                    }
                }
            }
            // calculate theta
            logger.info(" iter:" + epocheIndex + ", finish factors update");

            // calculate wordLoss and loss
            wordLoss = wordLoss / numberOfTopics;
            totalError += wordLoss;
            totalError *= 0.5F;
            logger.info(" iter:" + epocheIndex + ", loss:" + totalError + ", wordLoss:" + wordLoss / 2F);

            calculateTheta();
            logger.info(" iter:" + epocheIndex + ", finish theta update");

            // update phi by NMF
            // TODO 此处操作可以整合
            thetaW.dotProduct(documentFactors, true, W, false, MathCalculator.SERIAL);
            transposeThis.dotProduct(documentFactors, true, documentFactors, false, MathCalculator.SERIAL);
            thetaPhi.dotProduct(transposeThis, false, wordFactors, false, MathCalculator.SERIAL);
            for (int topicIndex = 0; topicIndex < numberOfTopics; topicIndex++) {
                for (int wordIndex = 0; wordIndex < numberOfWords; wordIndex++) {
                    float numerator = wordFactors.getValue(topicIndex, wordIndex) * thetaW.getValue(topicIndex, wordIndex);
                    float denominator = thetaPhi.getValue(topicIndex, wordIndex);
                    wordFactors.setValue(topicIndex, wordIndex, numerator / denominator);
                }
            }
            logger.info(" iter:" + epocheIndex + ", finish phi update");
        }
    }

    @Override
    protected float predict(int userIndex, int itemIndex) {
        DefaultScalar scalar = DefaultScalar.getInstance();
        float value = meanScore + userBiases.getValue(userIndex) + itemBiases.getValue(itemIndex);
        value += scalar.dotProduct(userFactors.getRowVector(userIndex), itemFactors.getRowVector(itemIndex)).getValue();
        return value;
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        instance.setQuantityMark(predict(userIndex, itemIndex));
    }

    /**
     * Calculate theta vectors via userFactors and itemFactors. thetaVector =
     * softmax( exp(K1|u| + K2|v|) )
     */
    private void calculateTheta() {
        for (MatrixScalar term : scoreMatrix) {
            int userIndex = term.getRow();
            int itemIndex = term.getColumn();
            int documentIdx = userItemToDocument.get(userIndex, itemIndex);
            DenseVector documentVector = documentFactors.getRowVector(documentIdx);
            topicVector.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                int index = scalar.getIndex();
                float value = scalar.getValue();
                value = Math.abs(userFactors.getValue(userIndex, index)) * K1 + Math.abs(itemFactors.getValue(itemIndex, index)) * K2;
                scalar.setValue(value);
            });
            function.forward(topicVector, documentVector);
        }
    }

}
