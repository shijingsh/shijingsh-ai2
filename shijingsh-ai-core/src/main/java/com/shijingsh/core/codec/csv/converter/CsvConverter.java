package com.shijingsh.core.codec.csv.converter;

import java.io.IOException;
import java.lang.reflect.Type;

import com.shijingsh.core.codec.csv.CsvReader;
import com.shijingsh.core.codec.csv.CsvWriter;

/**
 * CSV转换器
 *
 * @author Birdy
 *
 * @param <T>
 */
public interface CsvConverter<T> {

    /**
     * 从指定上下文读取内容
     *
     * @param context
     * @return
     * @throws IOException
     */
    T readValueFrom(CsvReader context, Type type) throws Exception;

    /**
     * 将指定内容写到上下文
     *
     * @param context
     * @param value
     * @throws IOException
     */
    void writeValueTo(CsvWriter context, Type type, T value) throws Exception;

}
