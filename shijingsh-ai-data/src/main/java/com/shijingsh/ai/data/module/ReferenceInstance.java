package com.shijingsh.ai.data.module;

import com.shijingsh.ai.data.QuantityAccessor;
import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.IntegerArray;
import com.shijingsh.ai.data.QualityAccessor;
import com.shijingsh.ai.data.*;

/**
 * 引用实例
 *
 * @author Birdy
 *
 */
public class ReferenceInstance implements DataInstance {

    /** 游标 */
    private int cursor;

    /** 引用 */
    private IntegerArray references;

    /** 实例 */
    private DataInstance instance;

    public ReferenceInstance(int cursor, IntegerArray references, DataModule module) {
        this.cursor = cursor;
        this.references = references;
        this.instance = module.getInstance(references.getData(cursor));
    }

    @Override
    public void setCursor(int cursor) {
        this.cursor = cursor;
        instance.setCursor(references.getData(cursor));
    }

    @Override
    public int getCursor() {
        return cursor;
    }

    @Override
    public int getQualityFeature(int dimension) {
        return instance.getQualityFeature(dimension);
    }

    @Override
    public float getQuantityFeature(int dimension) {
        return instance.getQuantityFeature(dimension);
    }

    @Override
    public DataInstance iterateQualityFeatures(QualityAccessor accessor) {
        instance.iterateQualityFeatures(accessor);
        return this;
    }

    @Override
    public DataInstance iterateQuantityFeatures(QuantityAccessor accessor) {
        instance.iterateQuantityFeatures(accessor);
        return this;
    }

    @Override
    public int getQualityOrder() {
        return instance.getQualityOrder();
    }

    @Override
    public int getQuantityOrder() {
        return instance.getQuantityOrder();
    }

    @Override
    public int getQualityMark() {
        return instance.getQualityMark();
    }

    @Override
    public float getQuantityMark() {
        return instance.getQuantityMark();
    }

    @Override
    public float getWeight() {
        return instance.getWeight();
    }

    @Override
    public void setQualityMark(int mark) {
        instance.setQualityMark(mark);
    }

    @Override
    public void setQuantityMark(float mark) {
        instance.setQuantityMark(mark);
    }

    @Override
    public void setWeight(float weight) {
        instance.setWeight(weight);
    }

}
