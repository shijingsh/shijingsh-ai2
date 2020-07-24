package com.shijingsh.rns.model.collaborative.ranking;

import java.util.Arrays;
import java.util.Comparator;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.data.module.ArrayInstance;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.utility.LogisticUtility;
import com.shijingsh.rns.utility.SampleUtility;

/**
 *
 * Lambda FM推荐器
 *
 * <pre>
 * LambdaFM: Learning Optimal Ranking with Factorization Machines Using Lambda Surrogates
 * </pre>
 *
 * @author Birdy
 *
 */
public class LambdaFMStaticModel extends LambdaFMModel {

    // Static
    private float staticRho;
    protected DenseVector itemProbabilities;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        staticRho = configuration.getFloat("recommender.item.distribution.parameter");
        // calculate popularity
        Integer[] orderItems = new Integer[itemSize];
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            orderItems[itemIndex] = itemIndex;
        }
        Arrays.sort(orderItems, new Comparator<Integer>() {
            @Override
            public int compare(Integer leftItemIndex, Integer rightItemIndex) {
                return (scoreMatrix.getColumnScope(leftItemIndex) > scoreMatrix.getColumnScope(rightItemIndex) ? -1 : (scoreMatrix.getColumnScope(leftItemIndex) < scoreMatrix.getColumnScope(rightItemIndex) ? 1 : 0));
            }
        });
        Integer[] itemOrders = new Integer[itemSize];
        for (int index = 0; index < itemSize; index++) {
            int itemIndex = orderItems[index];
            itemOrders[itemIndex] = index;
        }
        DefaultScalar sum = DefaultScalar.getInstance();
        sum.setValue(0F);
        itemProbabilities = DenseVector.valueOf(itemSize);
        itemProbabilities.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            float value = (float) Math.exp(-(itemOrders[index] + 1) / (itemSize * staticRho));
            sum.shiftValue(value);
            scalar.setValue(sum.getValue());
        });

        for (MatrixScalar term : scoreMatrix) {
            term.setValue(itemProbabilities.getValue(term.getColumn()));
        }
    }

    @Override
    protected float getGradientValue(DataModule[] modules, ArrayInstance positive, ArrayInstance negative, DefaultScalar scalar) {
        int userIndex;
        while (true) {
            userIndex = RandomUtility.randomInteger(userSize);
            SparseVector userVector = scoreMatrix.getRowVector(userIndex);
            if (userVector.getElementSize() == 0 || userVector.getElementSize() == itemSize) {
                continue;
            }

            DataModule module = modules[userIndex];
            DataInstance instance = module.getInstance(0);
            int positivePosition = RandomUtility.randomInteger(module.getSize());
            instance.setCursor(positivePosition);
            positive.copyInstance(instance);

            // TODO 注意,此处为了故意制造负面特征.
            int negativeItemIndex = -1;
            while (negativeItemIndex == -1) {
                int position = SampleUtility.binarySearch(userVector, 0, userVector.getElementSize() - 1, RandomUtility.randomFloat(itemProbabilities.getValue(itemProbabilities.getElementSize() - 1)));
                int low;
                int high;
                if (position == -1) {
                    low = userVector.getIndex(userVector.getElementSize() - 1);
                    high = itemProbabilities.getElementSize() - 1;
                } else if (position == 0) {
                    low = 0;
                    high = userVector.getIndex(position);
                } else {
                    low = userVector.getIndex(position - 1);
                    high = userVector.getIndex(position);
                }
                negativeItemIndex = SampleUtility.binarySearch(itemProbabilities, low, high, RandomUtility.randomFloat(itemProbabilities.getValue(high)));
            }
            int negativePosition = RandomUtility.randomInteger(module.getSize());
            ;
            instance.setCursor(negativePosition);
            negative.copyInstance(instance);
            negative.setQualityFeature(itemDimension, negativeItemIndex);
            break;
        }

        positiveVector = getFeatureVector(positive);
        negativeVector = getFeatureVector(negative);

        float positiveScore = predict(scalar, positiveVector);
        float negativeScore = predict(scalar, negativeVector);

        float error = positiveScore - negativeScore;

        // 由于pij_real默认为1,所以简化了loss的计算.
        // loss += -pij_real * Math.log(pij) - (1 - pij_real) *
        // Math.log(1 - pij);
        totalError += (float) -Math.log(LogisticUtility.getValue(error));
        float gradient = calaculateGradientValue(lossType, error);
        return gradient;
    }

}
