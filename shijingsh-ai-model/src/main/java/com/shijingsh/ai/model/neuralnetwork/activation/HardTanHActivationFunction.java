package com.shijingsh.ai.model.neuralnetwork.activation;

import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.vector.MathVector;

/**
 * HardTanH激活函数
 *
 * <pre>
 * 参考Deeplearning4j团队
 *
 * f(x) =
 * |1, if x > 1
 * |-1, if x < -1
 * |x, otherwise
 * </pre>
 *
 * @author Birdy
 *
 */
public class HardTanHActivationFunction implements ActivationFunction {

    @Override
    public void forward(MathMatrix input, MathMatrix output) {
        output.iterateElement(MathCalculator.PARALLEL, (scalar) -> {
            int row = scalar.getRow();
            int column = scalar.getColumn();
            float value = input.getValue(row, column);
            value = (value < -1F ? -1F : value > 1F ? 1F : value);
            scalar.setValue(value);
        });
    }

    @Override
    public void forward(MathVector input, MathVector output) {
        output.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            float value = input.getValue(index);
            value = (value < -1F ? -1F : value > 1F ? 1F : value);
            scalar.setValue(value);
        });
    }

    @Override
    public void backward(MathMatrix input, MathMatrix error, MathMatrix output) {
        output.iterateElement(MathCalculator.PARALLEL, (scalar) -> {
            int row = scalar.getRow();
            int column = scalar.getColumn();
            float value = input.getValue(row, column);
            value = ((value >= -1F && value <= 1F) ? 1F : 0F);
            value *= error.getValue(row, column);
            scalar.setValue(value);
        });
    }

    @Override
    public void backward(MathVector input, MathVector error, MathVector output) {
        output.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            float value = input.getValue(index);
            value = ((value >= -1F && value <= 1F) ? 1F : 0F);
            value *= error.getValue(index);
            scalar.setValue(value);
        });
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
            return true;
        }
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "HardTanHActivationFunction()";
    }

}
