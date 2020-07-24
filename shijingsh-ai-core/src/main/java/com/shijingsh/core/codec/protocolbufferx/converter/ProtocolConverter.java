package com.shijingsh.core.codec.protocolbufferx.converter;

import java.lang.reflect.Type;

import com.shijingsh.core.codec.protocolbufferx.ProtocolReader;
import com.shijingsh.core.codec.protocolbufferx.ProtocolWriter;
import com.shijingsh.core.codec.specification.ClassDefinition;
import com.shijingsh.core.common.reflection.Specification;

/**
 * 协议转换器
 *
 * <pre>
 * 参考ProtocolBuffer协议与ASF3协议
 * </pre>
 *
 * @author Birdy
 *
 * @param <T>
 */
public abstract class ProtocolConverter<T> {

    /** 1111 0000(类型掩码) */
    public static final byte TYPE_MASK = (byte) 0xF0;

    /** 0000 1111(标记掩码) */
    public static final byte MARK_MASK = (byte) 0x0F;

    /**
     * 通过指定字节数据获取类型码
     *
     * @param data
     * @return
     */
    public static byte getType(byte data) {
        byte code = (byte) (data & TYPE_MASK);
        return code;
    }

    /**
     * 通过指定字节数据获取标记码
     *
     * @param data
     * @return
     */
    public static byte getMark(byte data) {
        byte mark = (byte) (data & MARK_MASK);
        return mark;
    }

    /**
     * 从指定上下文读取内容
     *
     * @param context
     * @param information
     * @return
     */
    abstract public T readValueFrom(ProtocolReader context, Type type, ClassDefinition definition) throws Exception;

    /**
     * 将指定内容写到上下文
     *
     * @param context
     * @param value
     * @throws Exception
     */
    abstract public void writeValueTo(ProtocolWriter context, Type type, ClassDefinition definition, T value) throws Exception;

}
