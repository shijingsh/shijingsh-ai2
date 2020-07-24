package com.shijingsh.ai.data.module;

import java.util.Iterator;
import java.util.List;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.FloatArray;
import com.shijingsh.ai.data.IntegerArray;
import com.shijingsh.ai.data.exception.DataCapacityException;
import com.shijingsh.ai.data.exception.DataCursorException;
import com.shijingsh.ai.data.exception.DataException;
import com.shijingsh.core.utility.KeyValue;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.FloatArray;
import com.shijingsh.ai.data.IntegerArray;
import com.shijingsh.ai.data.exception.DataCapacityException;
import com.shijingsh.ai.data.exception.DataCursorException;
import com.shijingsh.ai.data.exception.DataException;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;

/**
 * 稠密模块
 *
 * @author Birdy
 *
 */
public class DenseModule extends AbstractModule {

    /** 离散特征 */
    private IntegerArray[] qualityValues;

    /** 连续特征 */
    private FloatArray[] quantityValues;

    public DenseModule(String name, List<KeyValue<KeyValue<String, Boolean>, Integer>> definition, int capacity) {
        super(name, definition, capacity);
        this.qualityValues = new IntegerArray[qualityOrder];
        for (int index = 0; index < qualityOrder; index++) {
            this.qualityValues[index] = new IntegerArray(1000, capacity);
        }
        this.quantityValues = new FloatArray[quantityOrder];
        for (int index = 0; index < quantityOrder; index++) {
            this.quantityValues[index] = new FloatArray(1000, capacity);
        }
    }

    IntegerArray[] getQualityValues() {
        return qualityValues;
    }

    FloatArray[] getQuantityValues() {
        return quantityValues;
    }

    @Override
    public void associateInstance(Int2IntSortedMap qualityFeatures, Int2FloatSortedMap quantityFeatures, int qualityMark, float quantityMark, float weight) {
        if (capacity == size) {
            throw new DataCapacityException();
        }
        if (!qualityFeatures.isEmpty() && (qualityFeatures.firstIntKey() < 0 || qualityFeatures.lastIntKey() >= qualityOrder)) {
            throw new DataException();
        }
        if (!quantityFeatures.isEmpty() && (quantityFeatures.firstIntKey() < 0 || quantityFeatures.lastIntKey() >= quantityOrder)) {
            throw new DataException();
        }
        assert qualityOrder == qualityFeatures.size();
        assert quantityOrder == quantityFeatures.size();
        for (Int2IntMap.Entry term : qualityFeatures.int2IntEntrySet()) {
            qualityValues[term.getIntKey()].associateData(term.getIntValue());
        }
        for (Int2FloatMap.Entry term : quantityFeatures.int2FloatEntrySet()) {
            quantityValues[term.getIntKey()].associateData(term.getFloatValue());
        }
        qualityMarks.associateData(qualityMark);
        quantityMarks.associateData(quantityMark);
        weights.associateData(weight);
        size++;
    }

    @Override
    public DataInstance getInstance(int cursor) {
        if (cursor < 0 && cursor >= size) {
            throw new DataCursorException();
        }
        return new DenseInstance(cursor, this);
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public Iterator<DataInstance> iterator() {
        return new DenseModuleIterator();
    }

    private class DenseModuleIterator implements Iterator<DataInstance> {

        private int cursor;

        private DataInstance term = cursor < size ? new DenseInstance(cursor, DenseModule.this) : null;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public DataInstance next() {
            term.setCursor(cursor++);
            return term;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

}
