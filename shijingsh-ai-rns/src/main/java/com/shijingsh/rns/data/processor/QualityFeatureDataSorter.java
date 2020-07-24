package com.shijingsh.rns.data.processor;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.processor.DataSorter;

public class QualityFeatureDataSorter implements DataSorter {

    private int dimension;

    public QualityFeatureDataSorter(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public int sort(DataInstance left, DataInstance right) {
        int leftValue = left.getQualityFeature(dimension);
        int rightValue = right.getQualityFeature(dimension);
        if (leftValue != rightValue) {
            return leftValue < rightValue ? -1 : 1;
        }
        return 0;
    }

}
