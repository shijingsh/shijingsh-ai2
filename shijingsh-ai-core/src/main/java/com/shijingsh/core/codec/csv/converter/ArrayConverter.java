package com.shijingsh.core.codec.csv.converter;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.Iterator;

import org.apache.commons.csv.CSVPrinter;

import com.shijingsh.core.codec.csv.CsvReader;
import com.shijingsh.core.codec.csv.CsvWriter;
import com.shijingsh.core.common.reflection.Specification;
import com.shijingsh.core.common.reflection.TypeUtility;
import com.shijingsh.core.utility.StringUtility;

/**
 * 数组转换器
 *
 * @author Birdy
 *
 */
public class ArrayConverter implements CsvConverter<Object> {

    @Override
    public Object readValueFrom(CsvReader context, Type type) throws Exception {
        // TODO 处理null
        Iterator<String> in = context.getInputStream();
        String check = in.next();
        if (StringUtility.isEmpty(check)) {
            return null;
        }
        int length = Integer.valueOf(check);
        Class<?> componentClass = null;
        Type componentType = null;
        if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = GenericArrayType.class.cast(type);
            componentType = genericArrayType.getGenericComponentType();
            componentClass = TypeUtility.getRawType(componentType, null);
        } else {
            Class<?> clazz = TypeUtility.getRawType(type, null);
            componentType = clazz.getComponentType();
            componentClass = clazz.getComponentType();
        }
        Object array = Array.newInstance(componentClass, length);
        Specification specification = Specification.getSpecification(componentClass);
        CsvConverter converter = context.getCsvConverter(specification);
        for (int index = 0; index < length; index++) {
            Object element = converter.readValueFrom(context, componentType);
            Array.set(array, index, element);
        }
        return array;
    }

    @Override
    public void writeValueTo(CsvWriter context, Type type, Object value) throws Exception {
        // TODO 处理null
        CSVPrinter out = context.getOutputStream();
        if (value == null) {
            out.print(StringUtility.EMPTY);
            return;
        }
        Class<?> componentClass = null;
        Type componentType = null;
        if (type instanceof GenericArrayType) {
            GenericArrayType genericArrayType = GenericArrayType.class.cast(type);
            componentType = genericArrayType.getGenericComponentType();
            componentClass = TypeUtility.getRawType(componentType, null);
        } else {
            Class<?> clazz = TypeUtility.getRawType(type, null);
            componentType = clazz.getComponentType();
            componentClass = clazz.getComponentType();
        }
        int length = Array.getLength(value);
        out.print(length);
        Specification specification = Specification.getSpecification(componentClass);
        CsvConverter converter = context.getCsvConverter(specification);
        for (int index = 0; index < length; index++) {
            Object element = Array.get(value, index);
            converter.writeValueTo(context, componentType, element);
        }
    }

}
