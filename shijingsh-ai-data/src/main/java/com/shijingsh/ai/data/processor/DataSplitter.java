package com.shijingsh.ai.data.processor;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.IntegerArray;
import com.shijingsh.ai.data.module.ReferenceModule;

/**
 * 数据划分器
 *
 * @author Birdy
 *
 */
public interface DataSplitter {

    /**
     * 划分
     *
     * @param instance
     * @return
     */
    int split(DataInstance instance);

    /**
     * 划分
     *
     * @param module
     * @param number
     * @return
     */
    default ReferenceModule[] split(DataModule module, int number) {
        int size = module.getSize();
        int maximum = size;
        int minimum = 1000;
        if (maximum < minimum) {
            maximum = minimum;
        }
        IntegerArray[] references = new IntegerArray[number];
        for (int index = 0; index < number; index++) {
            references[index] = new IntegerArray(minimum, maximum);
        }
        int cursor = 0;
        DataInstance instance = module.getInstance(cursor);
        while (cursor < size) {
            instance.setCursor(cursor);
            references[split(instance) % number].associateData(cursor);
            cursor++;
        }
        ReferenceModule[] modules = new ReferenceModule[number];
        for (int index = 0; index < number; index++) {
            modules[index] = new ReferenceModule(references[index], module);
        }
        return modules;
    }

}
