package com.shijingsh.rns.model.collaborative.rating;

import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.rns.model.MatrixFactorizationModel;

/**
 *
 * PMF推荐器
 *
 * <pre>
 * PMF: Probabilistic Matrix Factorization
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class PMFModel extends MatrixFactorizationModel {

    @Override
    protected void doPractice() {
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            totalError = 0F;
            for (MatrixScalar term : scoreMatrix) {
                int userIndex = term.getRow(); // user
                int itemIndex = term.getColumn(); // item
                float score = term.getValue();
                float predict = predict(userIndex, itemIndex);
                float error = score - predict;
                totalError += error * error;

                // update factors
                for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                    float userFactor = userFactors.getValue(userIndex, factorIndex), itemFactor = itemFactors.getValue(itemIndex, factorIndex);
                    userFactors.shiftValue(userIndex, factorIndex, learnRatio * (error * itemFactor - userRegularization * userFactor));
                    itemFactors.shiftValue(itemIndex, factorIndex, learnRatio * (error * userFactor - itemRegularization * itemFactor));
                    totalError += userRegularization * userFactor * userFactor + itemRegularization * itemFactor * itemFactor;
                }
            }

            totalError *= 0.5F;
            if (isConverged(epocheIndex) && isConverged) {
                break;
            }
            isLearned(epocheIndex);
            currentError = totalError;
        }
    }

}
