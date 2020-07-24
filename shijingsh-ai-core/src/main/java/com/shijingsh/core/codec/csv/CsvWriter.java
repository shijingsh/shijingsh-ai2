package com.shijingsh.core.codec.csv;

import java.io.OutputStream;
import java.io.OutputStreamWriter;

import org.apache.commons.csv.CSVPrinter;

import com.shijingsh.core.codec.csv.converter.CsvContext;
import com.shijingsh.core.codec.specification.CodecDefinition;
import com.shijingsh.core.utility.StringUtility;

/**
 * CSV写出器
 *
 * @author Birdy
 *
 */
public class CsvWriter extends CsvContext {

    private CSVPrinter outputStream;

    public CsvWriter(OutputStream outputStream, CodecDefinition definition) {
        super(definition);
        try {
            OutputStreamWriter buffer = new OutputStreamWriter(outputStream, StringUtility.CHARSET);
            this.outputStream = new CSVPrinter(buffer, FORMAT);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public CSVPrinter getOutputStream() {
        return outputStream;
    }

}
