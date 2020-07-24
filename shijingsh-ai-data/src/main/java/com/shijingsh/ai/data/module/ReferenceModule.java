package com.shijingsh.ai.data.module;

import java.util.Iterator;
import java.util.Map.Entry;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.IntegerArray;
import com.shijingsh.core.utility.KeyValue;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.IntegerArray;
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;

/**
 * 引用模块
 *
 * @author Birdy
 *
 */
public class ReferenceModule implements DataModule {

    /** 引用 */
    private IntegerArray references;

    /** 模型 */
    private DataModule module;

    public ReferenceModule(DataModule module) {
        int size = module.getSize();
        this.references = new IntegerArray(size, size);
        for (int index = 0; index < size; index++) {
            this.references.associateData(index);
        }
        this.module = module;
    }

    public ReferenceModule(IntegerArray references, DataModule module) {
        this.references = references;
        this.module = module;
    }

    @Override
    public void associateInstance(Int2IntSortedMap qualityFeatures, Int2FloatSortedMap quantityFeatures, int qualityMark, float quantityMark, float weight) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DataInstance getInstance(int cursor) {
        return new ReferenceInstance(cursor, references, module);
    }

    @Override
    public int getSize() {
        return references.getSize();
    }

    @Override
    public Entry<Integer, KeyValue<String, Boolean>> getOuterKeyValue(int index) {
        return module.getOuterKeyValue(index);
    }

    @Override
    public int getQualityInner(String name) {
        return module.getQualityInner(name);
    }

    @Override
    public int getQuantityInner(String name) {
        return module.getQuantityInner(name);
    }

    @Override
    public int getQualityOrder() {
        return module.getQualityOrder();
    }

    @Override
    public int getQuantityOrder() {
        return module.getQuantityOrder();
    }

    @Override
    public Iterator<DataInstance> iterator() {
        return new ReferenceModuleIterator();
    }

    public DataModule getModule() {
        return module;
    }

    public IntegerArray getReference() {
        return references;
    }

    private class ReferenceModuleIterator implements Iterator<DataInstance> {

        private int cursor, size = ReferenceModule.this.getSize();

        private DataInstance term = cursor < size ? new ReferenceInstance(cursor, ReferenceModule.this.references, ReferenceModule.this.module) : null;

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
