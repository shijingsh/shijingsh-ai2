package com.shijingsh.ai.data.module;

import com.shijingsh.ai.data.QuantityAccessor;
import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.FloatArray;
import com.shijingsh.ai.data.IntegerArray;
import com.shijingsh.ai.data.QualityAccessor;
import com.shijingsh.ai.data.*;

/**
 * 稠密实例
 *
 * @author Birdy
 *
 */
public class DenseInstance implements DataInstance {

    /** 游标 */
    private int cursor;

    /** 离散秩 */
    private int qualityOrder;

    /** 连续秩 */
    private int quantityOrder;

    /** 离散特征 */
    private IntegerArray[] qualityValues;

    /** 连续特征 */
    private FloatArray[] quantityValues;

    /** 离散标记 */
    private IntegerArray qualityMarks;

    /** 连续标记 */
    private FloatArray quantityMarks;

    /** 权重 */
    private FloatArray weights;

    DenseInstance(int cursor, DenseModule module) {
        this.cursor = cursor;
        this.qualityOrder = module.getQualityOrder();
        this.quantityOrder = module.getQuantityOrder();
        this.qualityValues = module.getQualityValues();
        this.quantityValues = module.getQuantityValues();
        this.qualityMarks = module.getQualityMarks();
        this.quantityMarks = module.getQuantityMarks();
        this.weights = module.getWeights();
    }

    @Override
    public void setCursor(int cursor) {
        this.cursor = cursor;
    }

    @Override
    public int getCursor() {
        return cursor;
    }

    @Override
    public int getQualityFeature(int dimension) {
        return qualityValues[dimension].getData(cursor);
    }

    @Override
    public float getQuantityFeature(int dimension) {
        return quantityValues[dimension].getData(cursor);
    }

    @Override
    public DenseInstance iterateQualityFeatures(QualityAccessor accessor) {
        for (int dimension = 0; dimension < qualityOrder; dimension++) {
            accessor.accessorFeature(dimension, qualityValues[dimension].getData(cursor));
        }
        return this;
    }

    @Override
    public DenseInstance iterateQuantityFeatures(QuantityAccessor accessor) {
        for (int index = 0; index < quantityOrder; index++) {
            accessor.accessorFeature(index, quantityValues[index].getData(cursor));
        }
        return this;
    }

    @Override
    public int getQualityOrder() {
        return qualityOrder;
    }

    @Override
    public int getQuantityOrder() {
        return quantityOrder;
    }

    @Override
    public int getQualityMark() {
        return qualityMarks.getData(cursor);
    }

    @Override
    public float getQuantityMark() {
        return quantityMarks.getData(cursor);
    }

    @Override
    public float getWeight() {
        return weights.getData(cursor);
    }

    @Override
    public void setQualityMark(int mark) {
        qualityMarks.setData(cursor, mark);
    }

    @Override
    public void setQuantityMark(float mark) {
        quantityMarks.setData(cursor, mark);
    }

    @Override
    public void setWeight(float weight) {
        weights.setData(cursor, weight);
    }

}
