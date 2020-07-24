package com.shijingsh.core.codec.protocolbufferx;

import java.io.InputStream;

import com.shijingsh.core.codec.protocolbufferx.converter.ProtocolContext;
import com.shijingsh.core.codec.specification.CodecDefinition;

/**
 * 协议读入器
 *
 * <pre>
 * 每次解码都必须使用
 * </pre>
 *
 * @author Birdy
 */
public class ProtocolReader extends ProtocolContext {

    private InputStream inputStream;

    public ProtocolReader(InputStream inputStream, CodecDefinition definition) {
        super(definition);
        this.inputStream = inputStream;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

}
