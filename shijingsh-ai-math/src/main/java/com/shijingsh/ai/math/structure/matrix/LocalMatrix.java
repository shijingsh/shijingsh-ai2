package com.shijingsh.ai.math.structure.matrix;

import java.util.Iterator;
import java.util.concurrent.Semaphore;

import com.shijingsh.ai.math.structure.ScalarIterator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.math3.util.FastMath;

import com.shijingsh.ai.environment.EnvironmentContext;
import com.shijingsh.ai.math.structure.MathAccessor;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.ScalarIterator;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.ai.math.structure.vector.LocalVector;

/**
 * 区域矩阵
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "left", "right", "top", "bottom", "matrix", "rowSize", "columnSize", "elementSize", "knownSize", "unknownSize" })
public class LocalMatrix implements MathMatrix {

    /** inclusive */
    protected int left;

    /** exclusive */
    protected int right;

    /** inclusive */
    protected int top;

    /** exclusive */
    protected int bottom;

    protected MathMatrix matrix;

    protected int rowSize, columnSize;

    protected int elementSize, knownSize, unknownSize;

    private LocalMatrix() {
    }

    public LocalMatrix(MathMatrix matrix, int left, int right, int top, int bottom) {
        assert left < right;
        assert top < bottom;
        assert matrix.getColumnSize() >= left && matrix.getColumnSize() >= right;
        assert matrix.getRowSize() >= top && matrix.getRowSize() >= bottom;
        if (matrix.getKnownSize() != matrix.getElementSize()) {
            throw new IllegalArgumentException();
        }
        if (matrix.getUnknownSize() != 0) {
            throw new IllegalArgumentException();
        }

        if (matrix instanceof LocalMatrix) {
            LocalMatrix section = LocalMatrix.class.cast(matrix);
            this.left = left + section.left;
            this.right = right + section.left;
            this.top = top + section.top;
            this.bottom = bottom + section.top;
            this.matrix = section.matrix;
        } else {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
            this.matrix = matrix;
        }

        this.rowSize = bottom - top;
        this.columnSize = right - left;

        this.knownSize = rowSize * columnSize;
        this.unknownSize = 0;
        this.elementSize = knownSize;
    }

    @Override
    public int getElementSize() {
        return elementSize;
    }

    @Override
    public int getKnownSize() {
        return knownSize;
    }

    @Override
    public int getUnknownSize() {
        return unknownSize;
    }

    @Override
    public ScalarIterator<MatrixScalar> iterateElement(MathCalculator mode, MathAccessor<MatrixScalar>... accessors) {
        switch (mode) {
        case SERIAL: {
            LocalMatrixScalar scalar = new LocalMatrixScalar();
            for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
                for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                    int cursor = rowIndex * columnSize + columnIndex;
                    scalar.update(cursor);
                    for (MathAccessor<MatrixScalar> accessor : accessors) {
                        accessor.accessElement(scalar);
                    }
                }
            }
            return this;
        }
        default: {
            if (this.getColumnSize() <= this.getRowSize()) {
                int size = columnSize;
                EnvironmentContext context = EnvironmentContext.getContext();
                Semaphore semaphore = MathCalculator.getSemaphore();
                for (int index = 0; index < size; index++) {
                    int columnIndex = index;
                    context.doStructureByAny(index, () -> {
                        LocalMatrixScalar scalar = new LocalMatrixScalar();
                        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
                            int cursor = rowIndex * columnSize + columnIndex;
                            scalar.update(cursor);
                            for (MathAccessor<MatrixScalar> accessor : accessors) {
                                accessor.accessElement(scalar);
                            }
                        }
                        semaphore.release();
                    });
                }
                try {
                    semaphore.acquire(size);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
                return this;
            } else {
                int size = rowSize;
                EnvironmentContext context = EnvironmentContext.getContext();
                Semaphore semaphore = MathCalculator.getSemaphore();
                for (int index = 0; index < size; index++) {
                    int rowIndex = index;
                    context.doStructureByAny(index, () -> {
                        LocalMatrixScalar scalar = new LocalMatrixScalar();
                        for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                            int cursor = rowIndex * columnSize + columnIndex;
                            scalar.update(cursor);
                            for (MathAccessor<MatrixScalar> accessor : accessors) {
                                accessor.accessElement(scalar);
                            }
                        }
                        semaphore.release();
                    });
                }
                try {
                    semaphore.acquire(size);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
                return this;
            }
        }
        }
    }

    @Override
    public LocalMatrix setValues(float value) {
        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                matrix.setValue(rowIndex + top, columnIndex + left, value);
            }
        }
        return this;
    }

    @Override
    public LocalMatrix scaleValues(float value) {
        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                matrix.scaleValue(rowIndex + top, columnIndex + left, value);
            }
        }
        return this;
    }

    @Override
    public LocalMatrix shiftValues(float value) {
        for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
            for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                matrix.shiftValue(rowIndex + top, columnIndex + left, value);
            }
        }
        return this;
    }

    @Override
    public float getSum(boolean absolute) {
        float sum = 0F;
        if (absolute) {
            for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
                for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                    sum += FastMath.abs(matrix.getValue(rowIndex + top, columnIndex + left));
                }
            }
        } else {
            for (int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
                for (int columnIndex = 0; columnIndex < columnSize; columnIndex++) {
                    sum += matrix.getValue(rowIndex + top, columnIndex + left);
                }
            }
        }
        return sum;
    }

    @Override
    public int getRowSize() {
        return rowSize;
    }

    @Override
    public int getColumnSize() {
        return columnSize;
    }

    @Override
    public MathVector getRowVector(int rowIndex) {
        return new LocalVector(matrix.getRowVector(rowIndex + top), left, right);
    }

    @Override
    public MathVector getColumnVector(int columnIndex) {
        return new LocalVector(matrix.getColumnVector(columnIndex + left), top, bottom);
    }

    @Override
    public boolean isIndexed() {
        return matrix.isIndexed();
    }

    @Override
    public float getValue(int rowIndex, int columnIndex) {
        return matrix.getValue(rowIndex + top, columnIndex + left);
    }

    @Override
    public void setValue(int rowIndex, int columnIndex, float value) {
        matrix.setValue(rowIndex + top, columnIndex + left, value);
    }

    @Override
    public void scaleValue(int rowIndex, int columnIndex, float value) {
        matrix.scaleValue(rowIndex + top, columnIndex + left, value);
    }

    @Override
    public void shiftValue(int rowIndex, int columnIndex, float value) {
        matrix.shiftValue(rowIndex + top, columnIndex + left, value);
    }

    protected MathMatrix getMatrix() {
        return matrix;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null)
            return false;
        if (getClass() != object.getClass())
            return false;
        LocalMatrix that = (LocalMatrix) object;
        EqualsBuilder equal = new EqualsBuilder();
        equal.append(this.matrix, that.matrix);
        equal.append(this.left, that.left);
        equal.append(this.right, that.right);
        equal.append(this.top, that.top);
        equal.append(this.bottom, that.bottom);
        return equal.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hash = new HashCodeBuilder();
        hash.append(matrix);
        hash.append(left);
        hash.append(right);
        hash.append(top);
        hash.append(bottom);
        return hash.toHashCode();
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder();
        buffer.append(matrix.toString());
        return buffer.toString();
    }

    @Override
    public Iterator<MatrixScalar> iterator() {
        return new LocalMatrixIterator();
    }

    private class LocalMatrixIterator implements Iterator<MatrixScalar> {

        private int cursor;

        private int size = rowSize * columnSize;

        private LocalMatrixScalar term = new LocalMatrixScalar();

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public MatrixScalar next() {
            term.update(cursor++);
            return term;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private class LocalMatrixScalar implements MatrixScalar {

        private int index, row, column;

        private void update(int index) {
            this.index = index;
            this.row = index / columnSize;
            this.column = index % columnSize;
        }

        @Override
        public int getRow() {
            return row;
        }

        @Override
        public int getColumn() {
            return column;
        }

        @Override
        public float getValue() {
            return matrix.getValue(row + top, column + left);
        }

        @Override
        public void scaleValue(float value) {
            matrix.scaleValue(row + top, column + left, value);
        }

        @Override
        public void setValue(float value) {
            matrix.setValue(row + top, column + left, value);
        }

        @Override
        public void shiftValue(float value) {
            matrix.shiftValue(row + top, column + left, value);
        }

    }

}
