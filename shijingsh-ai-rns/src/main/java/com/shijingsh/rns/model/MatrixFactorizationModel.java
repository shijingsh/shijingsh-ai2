package com.shijingsh.rns.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.random.JDKRandomGenerator;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.algorithm.probability.QuantityProbability;
import com.shijingsh.ai.math.structure.DefaultScalar;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.matrix.SparseMatrix;
import com.shijingsh.ai.math.structure.vector.DenseVector;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.core.utility.Configurator;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * 矩阵分解推荐器
 *
 * @author Birdy
 *
 */
public abstract class MatrixFactorizationModel extends EpocheModel {

    /** 是否自动调整学习率 */
    protected boolean isLearned;

    /** 衰减率 */
    protected float learnDecay;

    /**
     * learn rate, maximum learning rate
     */
    protected float learnRatio, learnLimit;

    /**
     * user latent factors
     */
    protected DenseMatrix userFactors;

    /**
     * item latent factors
     */
    protected DenseMatrix itemFactors;

    /**
     * the number of latent factors;
     */
    protected int factorSize;

    /**
     * user regularization
     */
    protected float userRegularization;

    /**
     * item regularization
     */
    protected float itemRegularization;

    /**
     * init mean
     */
    protected float initMean;

    /**
     * init standard deviation
     */
    protected float initStd;

    protected QuantityProbability distribution;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);

        userRegularization = configuration.getFloat("recommender.user.regularization", 0.01f);
        itemRegularization = configuration.getFloat("recommender.item.regularization", 0.01f);

        factorSize = configuration.getInteger("recommender.factor.number", 10);

        isLearned = configuration.getBoolean("recommender.learnrate.bolddriver", false);
        learnDecay = configuration.getFloat("recommender.learnrate.decay", 1.0f);
        learnRatio = configuration.getFloat("recommender.iterator.learnrate", 0.01f);
        learnLimit = configuration.getFloat("recommender.iterator.learnrate.maximum", 1000.0f);

        // TODO 此处需要重构
        initMean = configuration.getFloat("recommender.init.mean", 0F);
        initStd = configuration.getFloat("recommender.init.std", 0.1F);

        distribution = new QuantityProbability(JDKRandomGenerator.class, 0, NormalDistribution.class, initMean, initStd);
        userFactors = DenseMatrix.valueOf(userSize, factorSize);
        userFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
        itemFactors = DenseMatrix.valueOf(itemSize, factorSize);
        itemFactors.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(distribution.sample().floatValue());
        });
    }

    protected float predict(int userIndex, int itemIndex) {
        DenseVector userVector = userFactors.getRowVector(userIndex);
        DenseVector itemVector = itemFactors.getRowVector(itemIndex);
        DefaultScalar scalar = DefaultScalar.getInstance();
        return scalar.dotProduct(userVector, itemVector).getValue();
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        instance.setQuantityMark(predict(userIndex, itemIndex));
    }

    /**
     * Update current learning rate after each epoch <br>
     * <ol>
     * <li>bold driver: Gemulla et al., Large-scale matrix factorization with
     * distributed stochastic gradient descent, KDD 2011.</li>
     * <li>constant decay: Niu et al, Hogwild!: A lock-free approach to
     * parallelizing stochastic gradient descent, NIPS 2011.</li>
     * <li>Leon Bottou, Stochastic Gradient Descent Tricks</li>
     * <li>more ways to adapt learning rate can refer to:
     * http://www.willamette.edu/~gorr/classes/cs449/momrate.html</li>
     * </ol>
     *
     * @param iteration the current iteration
     */
    protected void isLearned(int iteration) {
        if (learnRatio < 0F) {
            return;
        }
        if (isLearned && iteration > 1) {
            learnRatio = Math.abs(currentError) > Math.abs(totalError) ? learnRatio * 1.05F : learnRatio * 0.5F;
        } else if (learnDecay > 0 && learnDecay < 1) {
            learnRatio *= learnDecay;
        }
        // limit to max-learn-rate after update
        if (learnLimit > 0 && learnRatio > learnLimit) {
            learnRatio = learnLimit;
        }
    }

    @Deprecated
    // TODO 此方法准备取消,利用向量的有序性代替
    protected List<IntSet> getUserItemSet(SparseMatrix sparseMatrix) {
        List<IntSet> userItemSet = new ArrayList<>(userSize);
        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            SparseVector userVector = sparseMatrix.getRowVector(userIndex);
            IntSet indexes = new IntOpenHashSet();
            for (int position = 0, size = userVector.getElementSize(); position < size; position++) {
                indexes.add(userVector.getIndex(position));
            }
            userItemSet.add(indexes);
        }
        return userItemSet;
    }

}
