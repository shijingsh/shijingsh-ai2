package com.shijingsh.core.codec.protocolbufferx.converter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.shijingsh.core.codec.exception.CodecConvertionException;
import com.shijingsh.core.codec.protocolbufferx.ProtocolReader;
import com.shijingsh.core.codec.protocolbufferx.ProtocolWriter;
import com.shijingsh.core.codec.specification.ClassDefinition;
import com.shijingsh.core.common.reflection.Specification;
import com.shijingsh.core.common.reflection.TypeUtility;

/**
 * 类型转换器
 *
 * @author Birdy
 *
 */
public class TypeConverter extends ProtocolConverter<Type> {

    /** 0000 0000(Null标记) */
    private static final byte NULL_MARK = (byte) 0x00;

    /** 0000 0002(数组标记) */
    private static final byte ARRAY_MARK = (byte) 0x01;

    /** 0000 0001(类型标记) */
    private static final byte CLASS_MARK = (byte) 0x02;

    /** 0000 0003(泛型标记) */
    private static final byte GENERIC_MARK = (byte) 0x03;

    @Override
    public Type readValueFrom(ProtocolReader context, Type type, ClassDefinition definition) throws IOException {
        InputStream in = context.getInputStream();
        byte information = (byte) in.read();
        byte mark = getMark(information);
        if (mark == NULL_MARK) {
            return null;
        }
        if (mark == CLASS_MARK) {
            int code = NumberConverter.readNumber(in).intValue();
            definition = context.getClassDefinition(code);
            return definition.getType();
        } else if (mark == ARRAY_MARK) {
            if (type == Class.class) {
                type = readValueFrom(context, type, definition);
                Class<?> clazz = Class.class.cast(type);
                return Array.newInstance(clazz, 0).getClass();
            } else {
                type = readValueFrom(context, type, definition);
                return TypeUtility.genericArrayType(type);
            }
        } else if (mark == GENERIC_MARK) {
            int code = NumberConverter.readNumber(in).intValue();
            definition = context.getClassDefinition(code);
            int size = NumberConverter.readNumber(in).intValue();
            Type[] types = new Type[size];
            for (int index = 0; index < size; index++) {
                types[index] = readValueFrom(context, type, definition);
            }
            return TypeUtility.parameterize(definition.getType(), types);
        } else {
            throw new CodecConvertionException();
        }
    }

    @Override
    public void writeValueTo(ProtocolWriter context, Type type, ClassDefinition definition, Type value) throws IOException {
        OutputStream out = context.getOutputStream();
        byte information = ClassDefinition.getCode(Specification.TYPE);
        if (value == null) {
            out.write(information);
            return;
        }
        if (value instanceof Class) {
            Class<?> clazz = TypeUtility.getRawType(value, null);
            if (clazz.isArray()) {
                information |= ARRAY_MARK;
                out.write(information);
                value = TypeUtility.getArrayComponentType(value);
                writeValueTo(context, value.getClass(), definition, value);
            } else {
                information |= CLASS_MARK;
                out.write(information);
                definition = context.getClassDefinition(clazz);
                NumberConverter.writeNumber(out, definition.getCode());
            }
        } else if (value instanceof GenericArrayType) {
            information |= ARRAY_MARK;
            out.write(information);
            value = TypeUtility.getArrayComponentType(value);
            writeValueTo(context, value.getClass(), definition, value);
        } else if (value instanceof ParameterizedType) {
            information |= GENERIC_MARK;
            out.write(information);
            Class<?> clazz = TypeUtility.getRawType(value, null);
            definition = context.getClassDefinition(clazz);
            NumberConverter.writeNumber(out, definition.getCode());
            ParameterizedType parameterizedType = (ParameterizedType) value;
            Type[] types = parameterizedType.getActualTypeArguments();
            NumberConverter.writeNumber(out, types.length);
            for (int index = 0; index < types.length; index++) {
                writeValueTo(context, types[index].getClass(), definition, types[index]);
            }
        } else {
            throw new CodecConvertionException();
        }
    }

}
