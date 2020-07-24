package com.shijingsh.rns.model.content.ranking;

import java.util.Arrays;
import java.util.Comparator;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.rns.model.content.EFMModel;
import com.shijingsh.rns.model.content.EFMModel;

/**
 *
 * EFM推荐器
 *
 * <pre>
 * Explicit factor models for explainable recommendation based on phrase-level sentiment analysis
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class EFMRankingModel extends EFMModel {

    private float threshold;

    private int featureLimit;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        threshold = configuration.getFloat("efmranking.threshold", 1F);
        featureLimit = configuration.getInteger("efmranking.featureLimit", 250);
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        DefaultScalar scalar = DefaultScalar.getInstance();

        // TODO 此处可以优化性能
        Integer[] orderIndexes = new Integer[numberOfFeatures];
        for (int featureIndex = 0; featureIndex < numberOfFeatures; featureIndex++) {
            orderIndexes[featureIndex] = featureIndex;
        }
        MathVector vector = DenseVector.valueOf(numberOfFeatures);
        vector.dotProduct(userExplicitFactors.getRowVector(userIndex), featureFactors, true, MathCalculator.SERIAL);
        Arrays.sort(orderIndexes, new Comparator<Integer>() {
            @Override
            public int compare(Integer leftIndex, Integer rightIndex) {
                return (vector.getValue(leftIndex) > vector.getValue(rightIndex) ? -1 : (vector.getValue(leftIndex) < vector.getValue(rightIndex) ? 1 : 0));
            }
        });

        float value = 0F;
        for (int index = 0; index < featureLimit; index++) {
            int featureIndex = orderIndexes[index];
            value += predictUserFactor(scalar, userIndex, featureIndex) * predictItemFactor(scalar, itemIndex, featureIndex);
        }
        value = threshold * (value / (featureLimit * maximumScore));
        value = value + (1F - threshold) * predict(userIndex, itemIndex);
        instance.setQuantityMark(value);
    }

}
