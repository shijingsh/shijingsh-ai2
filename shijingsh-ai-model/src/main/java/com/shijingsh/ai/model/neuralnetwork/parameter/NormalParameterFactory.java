package com.shijingsh.ai.model.neuralnetwork.parameter;

import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import org.apache.commons.math3.util.FastMath;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;

public class NormalParameterFactory implements ParameterFactory {

    @Override
    public void setValues(MathMatrix matrix) {
        INDArray ndArray = Nd4j.randn(new int[] { matrix.getRowSize(), matrix.getColumnSize() }).divi(FastMath.sqrt(matrix.getRowSize()));
        matrix.iterateElement(MathCalculator.SERIAL, (scalar) -> {
            scalar.setValue(ndArray.getFloat(scalar.getRow(), scalar.getColumn()));
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
        return "NormalParameterFactory()";
    }

}
