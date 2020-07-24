package com.shijingsh.ai.data.attribute;

import com.shijingsh.ai.data.DataAttribute;
import com.shijingsh.ai.data.DataAttribute;

/**
 * 连续属性
 *
 * @author Birdy
 *
 * @param <T>
 */
public interface QuantityAttribute<T extends Number> extends DataAttribute<T> {

    /**
     * 转换属性值
     *
     * @param data
     * @return
     */
    float convertData(T data);

    float getMaximum();

    float getMinimum();

    // TODO 准备重构为基于Flaot2IntMap计算中位数
//	float getMedian();

}
