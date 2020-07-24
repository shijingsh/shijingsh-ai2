package com.shijingsh.core.codec.protocolbufferx.converter;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Map.Entry;

import com.shijingsh.core.codec.exception.CodecConvertionException;
import com.shijingsh.core.codec.protocolbufferx.ProtocolReader;
import com.shijingsh.core.codec.protocolbufferx.ProtocolWriter;
import com.shijingsh.core.codec.specification.ClassDefinition;
import com.shijingsh.core.common.reflection.Specification;
import com.shijingsh.core.common.reflection.TypeUtility;
import com.shijingsh.core.utility.StringUtility;

/**
 * 映射转换器
 *
 * @author Birdy
 *
 */
public class MapConverter extends ProtocolConverter<Map<Object, Object>> {

    /** 0000 0000(Null标记) */
    private static final byte NULL_MARK = (byte) 0x00;

    /** 0000 0001(显式标记) */
    private static final byte EXPLICIT_MARK = (byte) 0x01;

    /** 0000 0002(隐式标记) */
    private static final byte IMPLICIT_MARK = (byte) 0x02;

    /** 0000 0003(引用标记) */
    private static final byte REFERENCE_MARK = (byte) 0x03;

    @Override
    public Map<Object, Object> readValueFrom(ProtocolReader context, Type type, ClassDefinition definition) throws Exception {
        InputStream in = context.getInputStream();
        byte information = (byte) in.read();
        byte mark = getMark(information);
        if (mark == NULL_MARK) {
            return null;
        }
        if (mark == EXPLICIT_MARK) {
            int size = NumberConverter.readNumber(in).intValue();
            // int code = NumberConverter.readNumber(in).intValue();
            // definition = context.getClassDefinition(code);
            Map map = (Map) definition.getInstance();
            context.putMapValue(map);
            ProtocolConverter converter = context.getProtocolConverter(Specification.TYPE);

            // Type keyType = (Type) converter.readValueFrom(context,
            // Type.class, null);
            // Type valueType = (Type) converter.readValueFrom(context,
            // Type.class, null);

            ParameterizedType parameterizedType = (ParameterizedType) type;
            Type[] types = parameterizedType.getActualTypeArguments();
            Type keyType = types[0];
            Type valueType = types[1];

            ProtocolConverter keyConverter = context.getProtocolConverter(Specification.getSpecification(keyType));
            ProtocolConverter valueConverter = context.getProtocolConverter(Specification.getSpecification(valueType));
            ClassDefinition keyDefinition = context.getClassDefinition(TypeUtility.getRawType(keyType, null));
            ClassDefinition valueDefinition = context.getClassDefinition(TypeUtility.getRawType(valueType, null));
            for (int index = 0; index < size; index++) {
                Object key = keyConverter.readValueFrom(context, keyType, keyDefinition);
                Object value = valueConverter.readValueFrom(context, valueType, valueDefinition);
                map.put(key, value);
            }
            return map;
        } else if (mark == IMPLICIT_MARK) {
            int size = NumberConverter.readNumber(in).intValue();
            int code = NumberConverter.readNumber(in).intValue();
            definition = context.getClassDefinition(code);
            Map map = (Map) definition.getInstance();
            context.putMapValue(map);
            for (int index = 0; index < size; index++) {
                code = NumberConverter.readNumber(in).intValue();
                definition = context.getClassDefinition(code);
                Type keyType = definition.getType();
                ProtocolConverter keyConverter = context.getProtocolConverter(definition.getSpecification());
                code = NumberConverter.readNumber(in).intValue();
                definition = context.getClassDefinition(code);
                Type valueType = definition.getType();
                ProtocolConverter valueConverter = context.getProtocolConverter(definition.getSpecification());
                ClassDefinition keyDefinition = context.getClassDefinition(TypeUtility.getRawType(keyType, null));
                ClassDefinition valueDefinition = context.getClassDefinition(TypeUtility.getRawType(valueType, null));
                Object key = keyConverter.readValueFrom(context, keyType, keyDefinition);
                Object value = valueConverter.readValueFrom(context, valueType, valueDefinition);
                map.put(key, value);
            }
            return map;
        } else if (mark == REFERENCE_MARK) {
            int reference = NumberConverter.readNumber(in).intValue();
            Map map = (Map) context.getMapValue(reference);
            return map;
        }
        String message = StringUtility.format("类型码[{}]没有对应标记码[{}]", type, mark);
        throw new CodecConvertionException(message);
    }

    @Override
    public void writeValueTo(ProtocolWriter context, Type type, ClassDefinition definition, Map<Object, Object> value) throws Exception {
        OutputStream out = context.getOutputStream();
        byte information = ClassDefinition.getCode(Specification.MAP);
        if (value == null) {
            out.write(information);
            return;
        }
        int reference = context.getMapIndex(value);
        if (reference != -1) {
            information |= REFERENCE_MARK;
            out.write(information);
            NumberConverter.writeNumber(out, reference);
        } else {
            if (type instanceof Class) {
                information |= IMPLICIT_MARK;
                context.putMapValue(value);
                out.write(information);
                int size = value.size();
                NumberConverter.writeNumber(out, size);
                int code = definition.getCode();
                NumberConverter.writeNumber(out, code);
                for (Entry<Object, Object> keyValue : value.entrySet()) {
                    ClassDefinition keyDefinition = context.getClassDefinition(keyValue.getKey().getClass());
                    NumberConverter.writeNumber(out, keyDefinition.getCode());
                    ProtocolConverter keyConverter = context.getProtocolConverter(keyDefinition.getSpecification());
                    ClassDefinition valueDefinition = context.getClassDefinition(keyValue.getValue() == null ? void.class : keyValue.getValue().getClass());
                    NumberConverter.writeNumber(out, valueDefinition.getCode());
                    ProtocolConverter valueConverter = context.getProtocolConverter(valueDefinition.getSpecification());
                    keyConverter.writeValueTo(context, keyValue.getKey().getClass(), keyDefinition, keyValue.getKey());
                    valueConverter.writeValueTo(context, keyValue.getValue() == null ? void.class : keyValue.getValue().getClass(), valueDefinition, keyValue.getValue());
                }
            } else {
                information |= EXPLICIT_MARK;
                context.putMapValue(value);
                out.write(information);
                int size = value.size();
                NumberConverter.writeNumber(out, size);
                definition = context.getClassDefinition(value.getClass());
                // int code = definition.getCode();
                // NumberConverter.writeNumber(out, code);
                ParameterizedType parameterizedType = (ParameterizedType) type;
                Type[] types = parameterizedType.getActualTypeArguments();
                Type keyType = types[0];
                Type valueType = types[1];
                // ProtocolConverter converter =
                // context.getProtocolConverter(CodecSpecification.TYPE);
                // converter.writeValueTo(context, Type.class, null, keyType);
                // converter.writeValueTo(context, Type.class, null, valueType);
                ProtocolConverter keyConverter = context.getProtocolConverter(Specification.getSpecification(keyType));
                ProtocolConverter valueConverter = context.getProtocolConverter(Specification.getSpecification(valueType));
                ClassDefinition keyDefinition = context.getClassDefinition(TypeUtility.getRawType(keyType, null));
                ClassDefinition valueDefinition = context.getClassDefinition(TypeUtility.getRawType(valueType, null));
                for (Entry<Object, Object> keyValue : value.entrySet()) {
                    keyConverter.writeValueTo(context, keyType, keyDefinition, keyValue.getKey());
                    valueConverter.writeValueTo(context, valueType, valueDefinition, keyValue.getValue());
                }
            }
        }
    }

}
