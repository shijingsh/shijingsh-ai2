package com.shijingsh.ai.math.structure.matrix;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

import com.shijingsh.ai.environment.EnvironmentContext;
import com.shijingsh.ai.math.structure.MathAccessor;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.ScalarIterator;
import com.shijingsh.ai.math.structure.vector.ArrayVector;
import com.shijingsh.ai.math.structure.vector.MathVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.ai.math.structure.ScalarIterator;

public class RowArrayMatrix extends ArrayMatrix {

    @Override
    public ScalarIterator<MatrixScalar> iterateElement(MathCalculator mode, MathAccessor<MatrixScalar>... accessors) {
        switch (mode) {
        case SERIAL: {
            ArrayMatrixScalar scalar = new ArrayMatrixScalar();
            for (int cursor = 0; cursor < rowSize; cursor++) {
                int rowIndex = cursor;
                ArrayVector vector = vectors[rowIndex];
                for (VectorScalar term : vector) {
                    scalar.update(term, rowIndex, term.getIndex());
                    for (MathAccessor<MatrixScalar> accessor : accessors) {
                        accessor.accessElement(scalar);
                    }
                }
            }
            return this;
        }
        default: {
            EnvironmentContext context = EnvironmentContext.getContext();
            Semaphore semaphore = MathCalculator.getSemaphore();
            for (int cursor = 0; cursor < rowSize; cursor++) {
                int rowIndex = cursor;
                context.doStructureByAny(cursor, () -> {
                    ArrayMatrixScalar scalar = new ArrayMatrixScalar();
                    ArrayVector vector = vectors[rowIndex];
                    for (VectorScalar term : vector) {
                        scalar.update(term, rowIndex, term.getIndex());
                        for (MathAccessor<MatrixScalar> accessor : accessors) {
                            accessor.accessElement(scalar);
                        }
                    }
                    semaphore.release();
                });
            }
            try {
                semaphore.acquire(rowSize);
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return this;
        }
        }
    }

    @Override
    public ArrayVector getRowVector(int rowIndex) {
        return vectors[rowIndex];
    }

    @Override
    public ArrayVector getColumnVector(int columnIndex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MathMatrix addMatrix(MathMatrix matrix, boolean transpose) {
        for (int index = 0, size = getRowSize(); index < size; index++) {
            getRowVector(index).addVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
        }
        return this;
    }

    @Override
    public MathMatrix subtractMatrix(MathMatrix matrix, boolean transpose) {
        for (int index = 0, size = getRowSize(); index < size; index++) {
            getRowVector(index).subtractVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
        }
        return this;
    }

    @Override
    public MathMatrix multiplyMatrix(MathMatrix matrix, boolean transpose) {
        for (int index = 0, size = getRowSize(); index < size; index++) {
            getRowVector(index).multiplyVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
        }
        return this;
    }

    @Override
    public MathMatrix divideMatrix(MathMatrix matrix, boolean transpose) {
        for (int index = 0, size = getRowSize(); index < size; index++) {
            getRowVector(index).divideVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
        }
        return this;
    }

    @Override
    public MathMatrix copyMatrix(MathMatrix matrix, boolean transpose) {
        for (int index = 0, size = getRowSize(); index < size; index++) {
            getRowVector(index).copyVector(transpose ? matrix.getColumnVector(index) : matrix.getRowVector(index));
        }
        return this;
    }

    @Override
    public MathMatrix dotProduct(MathMatrix leftMatrix, boolean leftTranspose, MathMatrix rightMatrix, boolean rightTranspose, MathCalculator mode) {
        // TODO 此处可以考虑性能优化.
        // TODO 可能触发元素变更.
        switch (mode) {
        case SERIAL: {
            for (MatrixScalar term : this) {
                int rowIndex = term.getRow();
                int columnIndex = term.getColumn();
                MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
                MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
                term.dotProduct(leftVector, rightVector);
            }
            return this;
        }
        default: {
            int size = this.getRowSize();
            EnvironmentContext context = EnvironmentContext.getContext();
            CountDownLatch latch = new CountDownLatch(size);
            for (int index = 0; index < size; index++) {
                int rowIndex = index;
                MathVector rowVector = this.getRowVector(index);
                context.doStructureByAny(index, () -> {
                    for (VectorScalar term : rowVector) {
                        int columnIndex = term.getIndex();
                        MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
                        MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
                        term.dotProduct(leftVector, rightVector);
                    }
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return this;
        }
        }
    }

    @Override
    public MathMatrix dotProduct(MathVector rowVector, MathVector columnVector, MathCalculator mode) {
        // TODO 可能触发元素变更.
        switch (mode) {
        case SERIAL: {
            for (VectorScalar term : rowVector) {
                float rowValue = term.getValue();
                MathVector leftVector = this.getRowVector(term.getIndex());
                MathVector rightVector = columnVector;
                int leftCursor = 0, rightCursor = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
                if (leftSize != 0 && rightSize != 0) {
                    Iterator<VectorScalar> leftIterator = leftVector.iterator();
                    Iterator<VectorScalar> rightIterator = rightVector.iterator();
                    VectorScalar leftTerm = leftIterator.next();
                    VectorScalar rightTerm = rightIterator.next();
                    // 判断两个有序数组中是否存在相同的数字
                    while (leftCursor < leftSize && rightCursor < rightSize) {
                        if (leftTerm.getIndex() == rightTerm.getIndex()) {
                            leftTerm.setValue(rowValue * rightTerm.getValue());
                            if (leftIterator.hasNext()) {
                                leftTerm = leftIterator.next();
                            }
                            if (rightIterator.hasNext()) {
                                rightTerm = rightIterator.next();
                            }
                            leftCursor++;
                            rightCursor++;
                        } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                            if (rightIterator.hasNext()) {
                                rightTerm = rightIterator.next();
                            }
                            rightCursor++;
                        } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                            if (leftIterator.hasNext()) {
                                leftTerm = leftIterator.next();
                            }
                            leftCursor++;
                        }
                    }
                }
            }
            return this;
        }
        default: {
            int size = rowVector.getElementSize();
            EnvironmentContext context = EnvironmentContext.getContext();
            CountDownLatch latch = new CountDownLatch(size);
            for (VectorScalar term : rowVector) {
                float rowValue = term.getValue();
                MathVector leftVector = this.getRowVector(term.getIndex());
                MathVector rightVector = columnVector;
                context.doStructureByAny(term.getIndex(), () -> {
                    int leftCursor = 0, rightCursor = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
                    if (leftSize != 0 && rightSize != 0) {
                        Iterator<VectorScalar> leftIterator = leftVector.iterator();
                        Iterator<VectorScalar> rightIterator = rightVector.iterator();
                        VectorScalar leftTerm = leftIterator.next();
                        VectorScalar rightTerm = rightIterator.next();
                        // 判断两个有序数组中是否存在相同的数字
                        while (leftCursor < leftSize && rightCursor < rightSize) {
                            if (leftTerm.getIndex() == rightTerm.getIndex()) {
                                leftTerm.setValue(rowValue * rightTerm.getValue());
                                if (leftIterator.hasNext()) {
                                    leftTerm = leftIterator.next();
                                }
                                if (rightIterator.hasNext()) {
                                    rightTerm = rightIterator.next();
                                }
                                leftCursor++;
                                rightCursor++;
                            } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                                if (rightIterator.hasNext()) {
                                    rightTerm = rightIterator.next();
                                }
                                rightCursor++;
                            } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                                if (leftIterator.hasNext()) {
                                    leftTerm = leftIterator.next();
                                }
                                leftCursor++;
                            }
                        }
                    }
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return this;
        }
        }
    }

    @Override
    @Deprecated
    // TODO 准备与dotProduct整合
    public MathMatrix accumulateProduct(MathMatrix leftMatrix, boolean leftTranspose, MathMatrix rightMatrix, boolean rightTranspose, MathCalculator mode) {
        // TODO 此处可以考虑性能优化.
        // TODO 可能触发元素变更.
        switch (mode) {
        case SERIAL: {
            for (MatrixScalar term : this) {
                int rowIndex = term.getRow();
                int columnIndex = term.getColumn();
                MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
                MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
                term.accumulateProduct(leftVector, rightVector);
            }
            return this;
        }
        default: {
            int size = this.getRowSize();
            EnvironmentContext context = EnvironmentContext.getContext();
            CountDownLatch latch = new CountDownLatch(size);
            for (int index = 0; index < size; index++) {
                int rowIndex = index;
                MathVector rowVector = this.getRowVector(index);
                context.doStructureByAny(index, () -> {
                    for (VectorScalar term : rowVector) {
                        int columnIndex = term.getIndex();
                        MathVector leftVector = leftTranspose ? leftMatrix.getColumnVector(rowIndex) : leftMatrix.getRowVector(rowIndex);
                        MathVector rightVector = rightTranspose ? rightMatrix.getRowVector(columnIndex) : rightMatrix.getColumnVector(columnIndex);
                        term.accumulateProduct(leftVector, rightVector);
                    }
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return this;
        }
        }
    }

    @Override
    @Deprecated
    // TODO 准备与dotProduct整合
    public MathMatrix accumulateProduct(MathVector rowVector, MathVector columnVector, MathCalculator mode) {
        // TODO 可能触发元素变更.
        switch (mode) {
        case SERIAL: {
            for (VectorScalar term : rowVector) {
                float rowValue = term.getValue();
                MathVector leftVector = this.getRowVector(term.getIndex());
                MathVector rightVector = columnVector;
                int leftCursor = 0, rightCursor = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
                if (leftSize != 0 && rightSize != 0) {
                    Iterator<VectorScalar> leftIterator = leftVector.iterator();
                    Iterator<VectorScalar> rightIterator = rightVector.iterator();
                    VectorScalar leftTerm = leftIterator.next();
                    VectorScalar rightTerm = rightIterator.next();
                    // 判断两个有序数组中是否存在相同的数字
                    while (leftCursor < leftSize && rightCursor < rightSize) {
                        if (leftTerm.getIndex() == rightTerm.getIndex()) {
                            leftTerm.shiftValue(rowValue * rightTerm.getValue());
                            if (leftIterator.hasNext()) {
                                leftTerm = leftIterator.next();
                            }
                            if (rightIterator.hasNext()) {
                                rightTerm = rightIterator.next();
                            }
                            leftCursor++;
                            rightCursor++;
                        } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                            if (rightIterator.hasNext()) {
                                rightTerm = rightIterator.next();
                            }
                            rightCursor++;
                        } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                            if (leftIterator.hasNext()) {
                                leftTerm = leftIterator.next();
                            }
                            leftCursor++;
                        }
                    }
                }
            }
            return this;
        }
        default: {
            int size = rowVector.getElementSize();
            EnvironmentContext context = EnvironmentContext.getContext();
            CountDownLatch latch = new CountDownLatch(size);
            for (VectorScalar term : rowVector) {
                float rowValue = term.getValue();
                MathVector leftVector = this.getRowVector(term.getIndex());
                MathVector rightVector = columnVector;
                context.doStructureByAny(term.getIndex(), () -> {
                    int leftCursor = 0, rightCursor = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
                    if (leftSize != 0 && rightSize != 0) {
                        Iterator<VectorScalar> leftIterator = leftVector.iterator();
                        Iterator<VectorScalar> rightIterator = rightVector.iterator();
                        VectorScalar leftTerm = leftIterator.next();
                        VectorScalar rightTerm = rightIterator.next();
                        // 判断两个有序数组中是否存在相同的数字
                        while (leftCursor < leftSize && rightCursor < rightSize) {
                            if (leftTerm.getIndex() == rightTerm.getIndex()) {
                                leftTerm.shiftValue(rowValue * rightTerm.getValue());
                                if (leftIterator.hasNext()) {
                                    leftTerm = leftIterator.next();
                                }
                                if (rightIterator.hasNext()) {
                                    rightTerm = rightIterator.next();
                                }
                                leftCursor++;
                                rightCursor++;
                            } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                                if (rightIterator.hasNext()) {
                                    rightTerm = rightIterator.next();
                                }
                                rightCursor++;
                            } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                                if (leftIterator.hasNext()) {
                                    leftTerm = leftIterator.next();
                                }
                                leftCursor++;
                            }
                        }
                    }
                    latch.countDown();
                });
            }
            try {
                latch.await();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return this;
        }
        }
    }

    @Override
    public Iterator<MatrixScalar> iterator() {
        return new RowArrayMatrixIterator();
    }

    private class RowArrayMatrixIterator implements Iterator<MatrixScalar> {

        private int index;

        private int current = vectors[index].getElementSize();

        private int cursor;

        private int size = elementSize;

        private Iterator<VectorScalar> iterator = vectors[index].iterator();

        private ArrayMatrixScalar term = new ArrayMatrixScalar();

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public MatrixScalar next() {
            if (cursor++ < current) {
                VectorScalar scalar = iterator.next();
                term.update(scalar, index, scalar.getIndex());
            }
            if (cursor == current && current != size) {
                current += vectors[++index].getElementSize();
                iterator = vectors[index].iterator();
            }
            return term;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    public static RowArrayMatrix valueOf(int columnSize, ArrayVector... components) {
        assert components.length != 0;
        RowArrayMatrix instance = new RowArrayMatrix();
        for (ArrayVector vector : components) {
            assert columnSize >= vector.getKnownSize() + vector.getUnknownSize();
            vector.attachMonitor(instance);
            instance.elementSize += vector.getElementSize();
            instance.knownSize += vector.getKnownSize();
            instance.unknownSize += vector.getUnknownSize();
        }
        instance.rowSize = components.length;
        instance.columnSize = columnSize;
        instance.vectors = components;
        return instance;
    }

}
