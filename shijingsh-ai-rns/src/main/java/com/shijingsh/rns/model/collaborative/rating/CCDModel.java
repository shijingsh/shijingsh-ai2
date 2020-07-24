package com.shijingsh.rns.model.collaborative.rating;

import java.util.Date;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.StringUtility;
import com.shijingsh.rns.model.MatrixFactorizationModel;

/**
 *
 * CCD推荐器
 *
 * <pre>
 * Large-Scale Parallel Collaborative Filtering for the Netflix Prize
 * http://www.hpl.hp.com/personal/Robert_Schreiber/papers/2008%20AAIM%20Netflix/
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class CCDModel extends MatrixFactorizationModel {

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        userFactors = DenseMatrix.valueOf(userSize, factorSize);
        userFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        itemFactors = DenseMatrix.valueOf(itemSize, factorSize);
        itemFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
    }

    @Override
    protected void doPractice() {
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            for (int userIndex = 0; userIndex < userSize; userIndex++) {
                SparseVector userVector = scoreMatrix.getRowVector(userIndex);
                for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                    float userFactor = 0F;
                    float numerator = 0F;
                    float denominator = 0F;
                    for (VectorScalar term : userVector) {
                        int itemIndex = term.getIndex();
                        numerator += (term.getValue() + userFactors.getValue(userIndex, factorIndex) * itemFactors.getValue(itemIndex, factorIndex)) * itemFactors.getValue(itemIndex, factorIndex);
                        denominator += itemFactors.getValue(itemIndex, factorIndex) * itemFactors.getValue(itemIndex, factorIndex);
                    }
                    userFactor = numerator / (denominator + userRegularization);
                    for (VectorScalar term : userVector) {
                        int itemIndex = term.getIndex();
                        term.setValue(term.getValue() - (userFactor - userFactors.getValue(userIndex, factorIndex)) * itemFactors.getValue(itemIndex, factorIndex));
                    }
                    userFactors.setValue(userIndex, factorIndex, userFactor);
                }
            }
            for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
                SparseVector itemVector = scoreMatrix.getColumnVector(itemIndex);
                for (int factorIndex = 0; factorIndex < factorSize; factorIndex++) {
                    float itemFactor = 0F;
                    float numerator = 0F;
                    float denominator = 0F;
                    for (VectorScalar term : itemVector) {
                        int userIndex = term.getIndex();
                        numerator += (term.getValue() + userFactors.getValue(userIndex, factorIndex) * itemFactors.getValue(itemIndex, factorIndex)) * userFactors.getValue(userIndex, factorIndex);
                        denominator += userFactors.getValue(userIndex, factorIndex) * userFactors.getValue(userIndex, factorIndex);
                    }
                    itemFactor = numerator / (denominator + itemRegularization);
                    for (VectorScalar term : itemVector) {
                        int userIndex = term.getIndex();
                        term.setValue(term.getValue() - (itemFactor - itemFactors.getValue(itemIndex, factorIndex)) * userFactors.getValue(userIndex, factorIndex));
                    }
                    itemFactors.setValue(itemIndex, factorIndex, itemFactor);
                }
            }
            logger.info(StringUtility.format("{} runs at iter {}/{} {}", this.getClass().getSimpleName(), epocheIndex, epocheSize, new Date()));
        }
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        DefaultScalar scalar = DefaultScalar.getInstance();
        float score = scalar.dotProduct(userFactors.getRowVector(userIndex), itemFactors.getRowVector(itemIndex)).getValue();
        if (score == 0F) {
            score = meanScore;
        }
        instance.setQuantityMark(score);
    }

}
