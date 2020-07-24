package com.shijingsh.ai.jsat.parameters;

import com.shijingsh.ai.jsat.DataSet;
import com.shijingsh.ai.jsat.classifiers.ClassificationDataSet;
import com.shijingsh.ai.jsat.distributions.Distribution;
import com.shijingsh.ai.jsat.regression.RegressionDataSet;
import com.shijingsh.ai.jsat.DataSet;
import com.shijingsh.ai.jsat.classifiers.ClassificationDataSet;
import com.shijingsh.ai.jsat.distributions.Distribution;
import com.shijingsh.ai.jsat.regression.RegressionDataSet;

/**
 * A double parameter that may be altered.
 *
 * @author Edward Raff
 */
public abstract class DoubleParameter extends Parameter {

    private static final long serialVersionUID = 4132422231433472554L;

    /**
     * Returns the current value for the parameter.
     *
     * @return the value for this parameter.
     */
    abstract public double getValue();

    /**
     * Sets the value for this parameter.
     *
     * @return <tt>true</tt> if the value was set, <tt>false</tt> if the value was
     *         invalid, and thus ignored.
     */
    abstract public boolean setValue(double val);

    /**
     * This method allows one to obtain a distribution that represents a reasonable
     * "guess" at the range of values that would work for this parameter. If the
     * DataSet is an instance of {@link ClassificationDataSet} or
     * {@link RegressionDataSet}, the method may choose to assume that the value is
     * being guessed for the specified task and change its behavior<br>
     * <br>
     * Providing a getGuess is not required, and returns {@code null} if guessing is
     * not supported.
     *
     * @param data the data with which we want a reasonable guess for this parameter
     * @return a distribution that represents a reasonable guess of a good value for
     *         this parameter given the input data
     */
    public Distribution getGuess(DataSet data) {
        return null;
    }

    @Override
    public String getValueString() {
        return Double.toString(getValue());
    }
}
