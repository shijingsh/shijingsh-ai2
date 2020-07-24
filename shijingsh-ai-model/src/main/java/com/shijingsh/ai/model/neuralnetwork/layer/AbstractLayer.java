package com.shijingsh.ai.model.neuralnetwork.layer;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.GlobalMatrix;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.model.neuralnetwork.activation.ActivationFunction;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.math3.util.FastMath;

import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.GlobalMatrix;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.model.neuralnetwork.activation.ActivationFunction;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.core.utility.KeyValue;

/**
 * 抽象层
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "numberOfInputs", "numberOfOutputs", "configurators", "parameters", "gradients", "function" })
public abstract class AbstractLayer implements Layer {

    protected static MathMatrix getMatrix(MathMatrix matrix) {
        if (matrix instanceof GlobalMatrix) {
            GlobalMatrix matrixes = GlobalMatrix.class.cast(matrix);
            if (matrixes.getComponentSize() == 1) {
                return matrixes.getComponentMatrix(0);
            }
        }
        return matrix;
    }

    protected int numberOfInputs, numberOfOutputs;

    /** 键为inputData(不可以为null),值为outerError(可以为null) */
    protected KeyValue<MathMatrix, MathMatrix> inputKeyValue;

    /** 键为outputData(不可以为null),值为innerError(不可以为null) */
    protected KeyValue<MathMatrix, MathMatrix> outputKeyValue;

    /** 键为middleData(不可以为null),值为middleError(不可以为null) */
    protected KeyValue<MathMatrix, MathMatrix> middleKeyValue;

    protected Map<String, ParameterConfigurator> configurators;

    /** 参数与梯度 */
    protected Map<String, MathMatrix> parameters, gradients;

    protected ActivationFunction function;

    protected AbstractLayer() {
    }

    protected AbstractLayer(int numberOfInputs, int numberOfOutputs, Map<String, ParameterConfigurator> configurators, ActivationFunction function) {
        this.numberOfInputs = numberOfInputs;
        this.numberOfOutputs = numberOfOutputs;
        this.function = function;
        this.configurators = configurators;
        this.parameters = new HashMap<>();
        this.gradients = new HashMap<>();
    }

    @Override
    public void doCache(MathCache factory, KeyValue<MathMatrix, MathMatrix> samples) {
        inputKeyValue = samples;
        int rowSize = inputKeyValue.getKey().getRowSize();
        int columnSize = inputKeyValue.getKey().getColumnSize();

        // 检查维度
        if (columnSize != numberOfInputs) {
            throw new IllegalArgumentException();
        }

        middleKeyValue = new KeyValue<>(null, null);
        outputKeyValue = new KeyValue<>(null, null);

        MathMatrix middleData = factory.makeMatrix(rowSize, numberOfOutputs);
        middleKeyValue.setKey(middleData);
        MathMatrix middleError = factory.makeMatrix(rowSize, numberOfOutputs);
        middleKeyValue.setValue(middleError);

        MathMatrix outputData = factory.makeMatrix(rowSize, numberOfOutputs);
        outputKeyValue.setKey(outputData);
        MathMatrix innerError = factory.makeMatrix(rowSize, numberOfOutputs);
        outputKeyValue.setValue(innerError);
    }

    @Override
    public KeyValue<MathMatrix, MathMatrix> getInputKeyValue() {
        return inputKeyValue;
    }

    @Override
    public KeyValue<MathMatrix, MathMatrix> getMiddleKeyValue() {
        return middleKeyValue;
    }

    @Override
    public KeyValue<MathMatrix, MathMatrix> getOutputKeyValue() {
        return outputKeyValue;
    }

    @Override
    public void regularize() {
        for (Entry<String, ParameterConfigurator> term : configurators.entrySet()) {
            ParameterConfigurator configurator = term.getValue();
            float l1Regularization = configurator.getL1Regularization();
            float l2Regularization = configurator.getL2Regularization();
            MathMatrix parameter = parameters.get(term.getKey());
            MathMatrix gradient = gradients.get(term.getKey());

            if (l2Regularization > 0D && parameter != null && gradient != null) {
                // TODO 此处可以优化性能
                gradient.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                    int row = scalar.getRow();
                    int column = scalar.getColumn();
                    float value = scalar.getValue();
                    value = value + (parameter.getValue(row, column) * l2Regularization);
                    scalar.setValue(value);
                });
            }
            if (l1Regularization > 0D && parameter != null && gradient != null) {
                // TODO 此处可以优化性能
                gradient.iterateElement(MathCalculator.SERIAL, (scalar) -> {
                    int row = scalar.getRow();
                    int column = scalar.getColumn();
                    float value = scalar.getValue();
                    value = value + (FastMath.signum(parameter.getValue(row, column)) * l1Regularization);
                    scalar.setValue(value);
                });
            }
        }
    }

    @Override
    public Map<String, MathMatrix> getParameters() {
        return parameters;
    }

    @Override
    public Map<String, MathMatrix> getGradients() {
        return gradients;
    }

    @Override
    public ActivationFunction getFunction() {
        return function;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null) {
            return false;
        }
        if (getClass() != object.getClass()) {
            return false;
        } else {
            AbstractLayer that = (AbstractLayer) object;
            EqualsBuilder equal = new EqualsBuilder();
            equal.append(this.numberOfInputs, that.numberOfInputs);
            equal.append(this.numberOfOutputs, that.numberOfOutputs);
            equal.append(this.configurators, that.configurators);
            equal.append(this.parameters, that.parameters);
            equal.append(this.function, that.function);
            return equal.isEquals();
        }
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hash = new HashCodeBuilder();
        hash.append(numberOfInputs);
        hash.append(numberOfOutputs);
        hash.append(configurators);
        hash.append(parameters);
        hash.append(function);
        return hash.toHashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(numberOfInputs=" + numberOfInputs + ", numberOfOutputs=" + numberOfOutputs + ", configurators=" + configurators + ", parameters=" + parameters + ", function=" + function + ")";
    }

}
