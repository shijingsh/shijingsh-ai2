package com.shijingsh.rns.model.collaborative.ranking;

import java.util.Map.Entry;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.rns.model.collaborative.BUCMModel;
import com.shijingsh.rns.model.collaborative.BUCMModel;

/**
 *
 * BUCM推荐器
 *
 * <pre>
 * Bayesian User Community Model
 * Modeling Item Selection and Relevance for Accurate Recommendations
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class BUCMRankingModel extends BUCMModel {

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        float value = 0F;
        for (int topicIndex = 0; topicIndex < factorSize; ++topicIndex) {
            float sum = 0F;
            for (Entry<Float, Integer> term : scoreIndexes.entrySet()) {
                double score = term.getKey();
                if (score > meanScore) {
                    sum += topicItemScoreProbabilities[topicIndex][itemIndex][term.getValue()];
                }
            }
            value += userTopicProbabilities.getValue(userIndex, topicIndex) * topicItemProbabilities.getValue(topicIndex, itemIndex) * sum;
        }
        instance.setQuantityMark(value);
    }

}
