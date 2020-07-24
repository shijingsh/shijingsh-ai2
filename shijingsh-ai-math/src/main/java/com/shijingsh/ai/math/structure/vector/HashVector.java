package com.shijingsh.ai.math.structure.vector;

import java.util.Iterator;
import java.util.WeakHashMap;
import java.util.concurrent.Semaphore;

import com.shijingsh.ai.math.structure.ScalarIterator;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.math3.util.FastMath;

import com.shijingsh.ai.environment.EnvironmentContext;
import com.shijingsh.ai.math.structure.MathAccessor;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.MathMonitor;
import com.shijingsh.ai.math.structure.ScalarIterator;
import com.shijingsh.ai.modem.ModemCycle;
import com.shijingsh.ai.modem.ModemDefinition;

import it.unimi.dsi.fastutil.longs.Long2FloatMap.Entry;
import it.unimi.dsi.fastutil.longs.Long2FloatSortedMap;

/**
 * 随机向量
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "shift", "capacity", "clazz", "keys", "values" })
public class HashVector implements MathVector, ModemCycle {

    private long shift;

    private int capacity;

    private String clazz;

    private long[] keys;

    private float[] values;

    private transient Long2FloatSortedMap keyValues;

    private transient WeakHashMap<MathMonitor<VectorScalar>, Object> monitors = new WeakHashMap<>();

    HashVector() {
    }

    public HashVector(long shift, int capacity, Long2FloatSortedMap data) {
        data.defaultReturnValue(Float.NaN);
        assert shift >= 0;
        if (!data.isEmpty()) {
            assert data.firstLongKey() - shift >= 0;
            assert data.lastLongKey() - shift < capacity;
        }
        this.shift = shift;
        this.capacity = capacity;
        this.keyValues = data;
    }

    @Override
    public int getDimensionSize() {
        return keyValues.size();
    }

    @Override
    public int getElementSize() {
        return keyValues.size();
    }

    @Override
    public int getKnownSize() {
        return getElementSize();
    }

    @Override
    public int getUnknownSize() {
        return capacity - getElementSize();
    }

    @Override
    public ScalarIterator<VectorScalar> iterateElement(MathCalculator mode, MathAccessor<VectorScalar>... accessors) {
        switch (mode) {
        case SERIAL: {
            RandomVectorScalar scalar = new RandomVectorScalar();
            for (Entry element : keyValues.long2FloatEntrySet()) {
                scalar.update(element);
                for (MathAccessor<VectorScalar> accessor : accessors) {
                    accessor.accessElement(scalar);
                }
            }
            return this;
        }
        default: {
            EnvironmentContext context = EnvironmentContext.getContext();
            Semaphore semaphore = MathCalculator.getSemaphore();
            for (Entry element : keyValues.long2FloatEntrySet()) {
                RandomVectorScalar scalar = new RandomVectorScalar();
                scalar.update(element);
                context.doStructureByAny((int) element.getLongKey(), () -> {
                    for (MathAccessor<VectorScalar> accessor : accessors) {
                        accessor.accessElement(scalar);
                    }
                    semaphore.release();
                });
            }
            try {
                semaphore.acquire(keyValues.size());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            return this;
        }
        }
    }

    @Override
    public HashVector setValues(float value) {
        if (Float.isNaN(value)) {
            int oldElementSize = keyValues.size();
            int oldKnownSize = getKnownSize();
            int oldUnknownSize = getUnknownSize();
            keyValues.clear();
            int newElementSize = 0;
            int newKnownSize = 0;
            int newUnknownSize = capacity;
            if (oldElementSize != newElementSize) {
                for (MathMonitor<VectorScalar> monitor : monitors.keySet()) {
                    monitor.notifySizeChanged(this, oldElementSize, newElementSize, oldKnownSize, newKnownSize, oldUnknownSize, newUnknownSize);
                }
            }
        } else {
            for (Entry term : keyValues.long2FloatEntrySet()) {
                term.setValue(value);
            }
        }
        return this;
    }

    @Override
    public HashVector scaleValues(float value) {
        for (Entry term : keyValues.long2FloatEntrySet()) {
            term.setValue(term.getFloatValue() * value);
        }
        return this;
    }

    @Override
    public HashVector shiftValues(float value) {
        for (Entry term : keyValues.long2FloatEntrySet()) {
            term.setValue(term.getFloatValue() + value);
        }
        return this;
    }

    @Override
    public float getSum(boolean absolute) {
        float sum = 0F;
        if (absolute) {
            for (Entry term : keyValues.long2FloatEntrySet()) {
                sum += FastMath.abs(term.getFloatValue());
            }
        } else {
            for (Entry term : keyValues.long2FloatEntrySet()) {
                sum += term.getFloatValue();
            }
        }
        return sum;
    }

    @Override
    public void attachMonitor(MathMonitor<VectorScalar> monitor) {
        monitors.put(monitor, null);
    }

    @Override
    public void detachMonitor(MathMonitor<VectorScalar> monitor) {
        monitors.remove(monitor);
    }

    @Override
    public boolean isConstant() {
        return false;
    }

    @Override
    public int getIndex(int position) {
        return position;
    }

    @Override
    public float getValue(int position) {
        return keyValues.get(position + shift);
    }

    @Override
    public void setValue(int position, float value) {
        assert position >= 0 && position < capacity;
        int oldElementSize = keyValues.size();
        int oldKnownSize = getKnownSize();
        int oldUnknownSize = getUnknownSize();
        if (Float.isNaN(value)) {
            keyValues.remove(position + shift);
        } else {
            keyValues.put(position + shift, value);
        }
        int newElementSize = keyValues.size();
        int newKnownSize = getKnownSize();
        int newUnknownSize = getUnknownSize();
        if (oldElementSize != newElementSize) {
            for (MathMonitor<VectorScalar> monitor : monitors.keySet()) {
                monitor.notifySizeChanged(this, oldElementSize, newElementSize, oldKnownSize, newKnownSize, oldUnknownSize, newUnknownSize);
            }
        }
    }

    @Override
    public void scaleValue(int position, float value) {
        assert position >= 0 && position < capacity;
        value = keyValues.get(position + shift) * value;
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException();
        } else {
            keyValues.put(position + shift, value);
        }
    }

    @Override
    public void shiftValue(int position, float value) {
        assert position >= 0 && position < capacity;
        value = keyValues.get(position + shift) + value;
        if (Float.isNaN(value)) {
            throw new IllegalArgumentException();
        } else {
            keyValues.put(position + shift, value);
        }
    }

    @Override
    public void beforeSave() {
        clazz = keyValues.getClass().getName();
        int index = 0;
        keys = new long[keyValues.size()];
        values = new float[keyValues.size()];
        for (Entry term : keyValues.long2FloatEntrySet()) {
            keys[index] = term.getLongKey();
            values[index] = term.getFloatValue();
            index++;
        }
    }

    @Override
    public void afterLoad() {
        try {
            keyValues = (Long2FloatSortedMap) Class.forName(clazz).newInstance();
            for (int index = 0, size = keys.length; index < size; index++) {
                keyValues.put(keys[index], values[index]);
            }
            clazz = null;
            keys = null;
            values = null;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null)
            return false;
        if (getClass() != object.getClass())
            return false;
        HashVector that = (HashVector) object;
        EqualsBuilder equal = new EqualsBuilder();
        equal.append(this.capacity, that.capacity);
        equal.append(this.keyValues, that.keyValues);
        return equal.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hash = new HashCodeBuilder();
        hash.append(capacity);
        hash.append(keyValues);
        return hash.toHashCode();
    }

    @Override
    public String toString() {
        return keyValues.toString();
    }

    @Override
    public Iterator<VectorScalar> iterator() {
        return new RandomVectorIterator();
    }

    private class RandomVectorIterator implements Iterator<VectorScalar> {

        private Iterator<Entry> iterator = keyValues.long2FloatEntrySet().iterator();

        private final RandomVectorScalar term = new RandomVectorScalar();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public VectorScalar next() {
            term.update(iterator.next());
            return term;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private class RandomVectorScalar implements VectorScalar {

        private Entry element;

        private void update(Entry element) {
            this.element = element;
        }

        @Override
        public int getIndex() {
            return (int) (element.getLongKey() - shift);
        }

        @Override
        public float getValue() {
            return element.getFloatValue();
        }

        @Override
        public void scaleValue(float value) {
            element.setValue(element.getFloatValue() * value);
        }

        @Override
        public void setValue(float value) {
            if (Float.isNaN(value)) {
                int oldElementSize = keyValues.size();
                int oldKnownSize = getKnownSize();
                int oldUnknownSize = getUnknownSize();
                int newElementSize = oldElementSize - 1;
                int newKnownSize = oldKnownSize - 1;
                int newUnknownSize = oldUnknownSize + 1;
                keyValues.remove(element.getLongKey());
                for (MathMonitor<VectorScalar> monitor : monitors.keySet()) {
                    monitor.notifySizeChanged(HashVector.this, oldElementSize, newElementSize, oldKnownSize, newKnownSize, oldUnknownSize, newUnknownSize);
                }
            } else {
                element.setValue(value);
            }
        }

        @Override
        public void shiftValue(float value) {
            element.setValue(element.getFloatValue() + value);
        }

    }

}
