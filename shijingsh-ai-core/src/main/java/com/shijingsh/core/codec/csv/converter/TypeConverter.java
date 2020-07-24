package com.shijingsh.core.codec.csv.converter;

import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;

import org.apache.commons.csv.CSVPrinter;

import com.shijingsh.core.codec.csv.CsvReader;
import com.shijingsh.core.codec.csv.CsvWriter;
import com.shijingsh.core.codec.exception.CodecConvertionException;
import com.shijingsh.core.codec.specification.ClassDefinition;
import com.shijingsh.core.common.reflection.TypeUtility;
import com.shijingsh.core.utility.StringUtility;

/**
 * 类型转换器
 *
 * @author Birdy
 *
 */
public class TypeConverter implements CsvConverter<Type> {

    @Override
    public Type readValueFrom(CsvReader context, Type type) throws Exception {
        Iterator<String> in = context.getInputStream();
        String check = in.next();
        if (StringUtility.isEmpty(check)) {
            return null;
        }
        int code = Integer.valueOf(check);
        ClassDefinition definition = context.getClassDefinition(code);
        if (definition.getType() == Class.class) {
            code = Integer.valueOf(in.next());
            definition = context.getClassDefinition(code);
            return definition.getType();
        } else if (definition.getType() == GenericArrayType.class) {
            if (type == Class.class) {
                type = readValueFrom(context, type);
                Class<?> clazz = Class.class.cast(type);
                return Array.newInstance(clazz, 0).getClass();
            } else {
                type = readValueFrom(context, type);
                return TypeUtility.genericArrayType(type);
            }
        } else if (definition.getType() == ParameterizedType.class) {
            code = Integer.valueOf(in.next());
            definition = context.getClassDefinition(code);
            Integer length = Integer.valueOf(in.next());
            Type[] types = new Type[length];
            for (int index = 0; index < length; index++) {
                types[index] = readValueFrom(context, type);
            }
            return TypeUtility.parameterize(definition.getType(), types);
        } else {
            throw new CodecConvertionException();
        }
    }

    @Override
    public void writeValueTo(CsvWriter context, Type type, Type value) throws Exception {
        CSVPrinter out = context.getOutputStream();
        if (value == null) {
            out.print(StringUtility.EMPTY);
            return;
        }
        if (value instanceof Class) {
            Class<?> clazz = TypeUtility.getRawType(value, null);
            if (clazz.isArray()) {
                ClassDefinition definition = context.getClassDefinition(GenericArrayType.class);
                out.print(definition.getCode());
                value = TypeUtility.getArrayComponentType(value);
                writeValueTo(context, value.getClass(), value);
            } else {
                ClassDefinition definition = context.getClassDefinition(Class.class);
                out.print(definition.getCode());
                definition = context.getClassDefinition(clazz);
                out.print(definition.getCode());
            }
        } else if (value instanceof GenericArrayType) {
            ClassDefinition definition = context.getClassDefinition(GenericArrayType.class);
            out.print(definition.getCode());
            value = TypeUtility.getArrayComponentType(value);
            writeValueTo(context, value.getClass(), value);
        } else if (value instanceof ParameterizedType) {
            ClassDefinition definition = context.getClassDefinition(ParameterizedType.class);
            out.print(definition.getCode());
            Class<?> clazz = TypeUtility.getRawType(value, null);
            definition = context.getClassDefinition(clazz);
            out.print(definition.getCode());
            ParameterizedType parameterizedType = (ParameterizedType) value;
            Type[] types = parameterizedType.getActualTypeArguments();
            out.print(types.length);
            for (int index = 0; index < types.length; index++) {
                writeValueTo(context, types[index].getClass(), types[index]);
            }
        } else {
            throw new CodecConvertionException();
        }
    }
}
