package com.shijingsh.ai.data.converter;

import com.shijingsh.ai.data.*;

/**
 * 数据转换器
 *
 * @author Birdy
 *
 */
public interface DataConverter<T> {

    /**
     * 转换
     *
     * <pre>
     * 通过迭代器将指定数据转换到模块
     * </pre>
     *
     * @param module
     * @param iterator
     * @return
     */
    int convert(DataModule module, T iterator);

}
