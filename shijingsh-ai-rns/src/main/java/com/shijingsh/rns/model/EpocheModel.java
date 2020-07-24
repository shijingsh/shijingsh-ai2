package com.shijingsh.rns.model;

import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.MathUtility;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.StringUtility;
import com.shijingsh.rns.model.exception.ModelException;
import com.shijingsh.rns.utility.LogisticUtility;
import com.shijingsh.rns.model.exception.ModelException;
import com.shijingsh.rns.utility.LogisticUtility;

/**
 * 模型推荐器
 *
 * <pre>
 * 与机器学习相关
 * </pre>
 *
 * @author Birdy
 *
 */
public abstract class EpocheModel extends AbstractModel {

    /** 周期次数 */
    protected int epocheSize;

    /** 是否收敛(early-stop criteria) */
    protected boolean isConverged;

    /** 用于观察损失率 */
    protected float totalError, currentError;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        // 参数部分
        epocheSize = configuration.getInteger("recommender.iterator.maximum", 100);
        isConverged = configuration.getBoolean("recommender.recommender.earlystop", false);
    }

    /**
     * 是否收敛
     *
     * @param iteration
     * @return
     */
    protected boolean isConverged(int iteration) {
        float deltaError = currentError - totalError;
        // print out debug info
        if (logger.isInfoEnabled()) {
            String message = StringUtility.format("{} : epoch is {}, total is {}, delta is {}", getClass().getSimpleName(), iteration, totalError, deltaError);
            logger.info(message);
        }
        if (Float.isNaN(totalError) || Float.isInfinite(totalError)) {
            throw new ModelException("Loss = NaN or Infinity: current settings does not fit the recommender! Change the settings and try again!");
        }
        // check if converged
        boolean converged = Math.abs(deltaError) < MathUtility.EPSILON;
        return converged;
    }

    /**
     * fajie To calculate cmg based on pairwise loss function type
     *
     * @param lossType
     * @param error
     * @return
     */
    protected final float calaculateGradientValue(int lossType, float error) {
        final float constant = 1F;
        float value = 0F;
        switch (lossType) {
        case 0:// Hinge loss
            if (constant * error <= 1F)
                value = constant;
            break;
        case 1:// Rennie loss
            if (constant * error <= 0F)
                value = -constant;
            else if (constant * error <= 1F)
                value = (1F - constant * error) * (-constant);
            else
                value = 0F;
            value = -value;
            break;
        case 2:// logistic loss, BPR
            value = LogisticUtility.getValue(-error);
            break;
        case 3:// Frank loss
            value = (float) (Math.sqrt(LogisticUtility.getValue(error)) / (1F + Math.exp(error)));
            break;
        case 4:// Exponential loss
            value = (float) Math.exp(-error);
            break;
        case 5:// quadratically smoothed
            if (error <= 1F)
                value = 0.5F * (1F - error);
            break;
        default:
            break;
        }
        return value;
    }

}
