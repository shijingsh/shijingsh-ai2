
package com.shijingsh.ai.jsat.regression;

import java.io.Serializable;

import com.shijingsh.ai.jsat.classifiers.DataPoint;

/**
 *
 * @author Edward Raff
 */
public interface Regressor extends Cloneable, Serializable {
    public double regress(DataPoint data);

    public void train(RegressionDataSet dataSet, boolean parallel);

    default public void train(RegressionDataSet dataSet) {
        train(dataSet, false);
    }

    public boolean supportsWeightedData();

    public Regressor clone();
}
