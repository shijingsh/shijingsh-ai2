package com.shijingsh.rns.model;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.matrix.HashMatrix;
import com.shijingsh.ai.math.structure.matrix.SparseMatrix;
import com.shijingsh.core.utility.Configurator;

import it.unimi.dsi.fastutil.longs.Long2FloatRBTreeMap;

/**
 * 社交推荐器
 *
 * <pre>
 * 注意:基缘,是指构成人际关系的最基本的因素,包括血缘,地缘,业缘,趣缘.
 * 实际业务使用过程中要注意人与人之间社区关系(趣缘)与社会关系(血缘,地缘,业缘)的区分.
 * </pre>
 *
 * @author Birdy
 *
 */
public abstract class SocialModel extends MatrixFactorizationModel {

    protected String trusterField, trusteeField, coefficientField;

    protected int trusterDimension, trusteeDimension, coefficientDimension;
    /**
     * socialMatrix: social rate matrix, indicating a user is connecting to a number
     * of other users
     */
    protected SparseMatrix socialMatrix;

    /**
     * social regularization
     */
    protected float socialRegularization;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);

        socialRegularization = configuration.getFloat("recommender.social.regularization", 0.01f);
        // social path for the socialMatrix
        // TODO 此处是不是应该使用context.getSimilarity().getSimilarityMatrix();代替?
        DataModule socialModel = space.getModule("social");
        // TODO 此处需要重构,trusterDimension与trusteeDimension要配置
        coefficientField = configuration.getString("data.model.fields.coefficient");
        trusterDimension = socialModel.getQualityInner(userField) + 0;
        trusteeDimension = socialModel.getQualityInner(userField) + 1;
        coefficientDimension = socialModel.getQuantityInner(coefficientField);
        HashMatrix matrix = new HashMatrix(true, userSize, userSize, new Long2FloatRBTreeMap());
        for (DataInstance instance : socialModel) {
            matrix.setValue(instance.getQualityFeature(trusterDimension), instance.getQualityFeature(trusteeDimension), instance.getQuantityFeature(coefficientDimension));
        }
        socialMatrix = SparseMatrix.valueOf(userSize, userSize, matrix);
    }

    /**
     * 逆态化
     *
     * <pre>
     * 把数值从(0,1)转换为(minimumOfScore,maximumOfScore)
     * </pre>
     *
     * @param value
     * @return
     */
    protected float denormalize(float value) {
        return minimumScore + value * (maximumScore - minimumScore);
    }

    /**
     * 正态化
     *
     * <pre>
     * 把数值从(minimumOfScore,maximumOfScore)转换为(0,1)
     * </pre>
     *
     * @param value
     * @return
     */
    protected float normalize(float value) {
        return (value - minimumScore) / (maximumScore - minimumScore);
    }

}
