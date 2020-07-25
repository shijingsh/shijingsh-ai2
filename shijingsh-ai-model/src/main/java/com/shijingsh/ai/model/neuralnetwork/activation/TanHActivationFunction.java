package com.shijingsh.ai.model.neuralnetwork.activation;

import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.vector.MathVector;
import org.apache.commons.math3.util.FastMath;

/**
 * TanH激活函数
 *
 * <pre>
 * 参考Deeplearning4j团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class TanHActivationFunction implements ActivationFunction {

    @Override
    public void forward(MathMatrix input, MathMatrix output) {
        if (false) {
/*            INDArray inputArray = Nd4jMatrix.class.cast(input).getArray();
            INDArray outputArray = Nd4jMatrix.class.cast(output).getArray();
            Nd4j.getExecutioner().execAndReturn(new Tanh(inputArray, outputArray));*/
        } else {
            output.iterateElement(MathCalculator.PARALLEL, (scalar) -> {
                int row = scalar.getRow();
                int column = scalar.getColumn();
                float value = input.getValue(row, column);
                value = (float) FastMath.tanh(value);
                scalar.setValue(value);
            });
        }
    }

    @Override
    public void forward(MathVector input, MathVector output) {
        output.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            float value = input.getValue(index);
            value = (float) FastMath.tanh(value);
            scalar.setValue(value);
        });
    }

    @Override
    public void backward(MathMatrix input, MathMatrix error, MathMatrix output) {
        if (false) {
/*            INDArray inputArray = Nd4jMatrix.class.cast(input).getArray();
            INDArray outputArray = Nd4jMatrix.class.cast(output).getArray();
            INDArray errorArray = Nd4jMatrix.class.cast(error).getArray();
            Nd4j.getExecutioner().execAndReturn(new TanhDerivative(inputArray, outputArray));
            outputArray.muli(errorArray);*/
        } else {
            output.iterateElement(MathCalculator.PARALLEL, (scalar) -> {
                int row = scalar.getRow();
                int column = scalar.getColumn();
                float value = input.getValue(row, column);
                value = (float) FastMath.tanh(value);
                value = 1F - value * value;
                value *= error.getValue(row, column);
                scalar.setValue(value);
            });
        }
    }

    @Override
    public void backward(MathVector input, MathVector error, MathVector output) {
        output.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            int index = scalar.getIndex();
            float value = input.getValue(index);
            value = (float) FastMath.tanh(value);
            value = 1F - value * value;
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
        return "TanHActivationFunction()";
    }

}
