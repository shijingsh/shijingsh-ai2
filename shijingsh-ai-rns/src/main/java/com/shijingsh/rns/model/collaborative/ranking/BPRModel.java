package com.shijingsh.rns.model.collaborative.ranking;

import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.MatrixFactorizationModel;
import com.shijingsh.rns.utility.LogisticUtility;

/**
 *
 * BPR推荐器
 *
 * <pre>
 * BPR: Bayesian Personalized Ranking from Implicit Feedback
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class BPRModel extends MatrixFactorizationModel {

    @Override
    protected void doPractice() {
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            totalError = 0F;
            for (int sampleIndex = 0, sampleTimes = userSize * 100; sampleIndex < sampleTimes; sampleIndex++) {
                // randomly draw (userIdx, posItemIdx, negItemIdx)
                int userIndex, positiveItemIndex, negativeItemIndex;
                while (true) {
                    userIndex = RandomUtility.randomInteger(userSize);
                    SparseVector userVector = scoreMatrix.getRowVector(userIndex);
                    if (userVector.getElementSize() == 0) {
                        continue;
                    }
                    positiveItemIndex = userVector.getIndex(RandomUtility.randomInteger(userVector.getElementSize()));
                    negativeItemIndex = RandomUtility.randomInteger(itemSize - userVector.getElementSize());
                    for (VectorScalar term : userVector) {
                        if (negativeItemIndex >= term.getIndex()) {
                            negativeItemIndex++;
                        } else {
                            break;
                        }
                    }
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

}
