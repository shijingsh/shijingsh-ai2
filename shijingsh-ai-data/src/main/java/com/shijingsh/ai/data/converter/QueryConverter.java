package com.shijingsh.ai.data.converter;

import com.shijingsh.ai.data.*;
import com.shijingsh.ai.data.attribute.QualityAttribute;
import com.shijingsh.ai.data.attribute.QuantityAttribute;
import com.shijingsh.core.common.conversion.csv.ConversionUtility;
import com.shijingsh.core.utility.KeyValue;
import it.unimi.dsi.fastutil.ints.Int2FloatRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2FloatSortedMap;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import org.hibernate.HibernateException;
import org.hibernate.ScrollableResults;

import java.util.Collection;
import java.util.Map.Entry;

/**
 * 行式转换器
 *
 * @author Birdy
 *
 */
public class QueryConverter extends AbstractConverter<ScrollableResults> {

    protected QueryConverter(Collection<QualityAttribute> qualityAttributes, Collection<QuantityAttribute> quantityAttributes) {
        super(qualityAttributes, quantityAttributes);
    }

    @Override
    public int convert(DataModule module, ScrollableResults iterator) {
        int count = 0;
        Int2IntSortedMap qualityFeatures = new Int2IntRBTreeMap();
        Int2FloatSortedMap quantityFeatures = new Int2FloatRBTreeMap();
        int size = module.getQualityOrder() + module.getQuantityOrder();
        try {
            while (iterator.next()) {
                for (int index = 0; index < size; index++) {
                    Object data = iterator.get(index);
                    if (data == null) {
                        continue;
                    }
                    Entry<Integer, KeyValue<String, Boolean>> term = module.getOuterKeyValue(index);
                    KeyValue<String, Boolean> keyValue = term.getValue();
                    if (keyValue.getValue()) {
                        QualityAttribute attribute = qualityAttributes.get(keyValue.getKey());
                        data = ConversionUtility.convert(data, attribute.getType());
                        int feature = attribute.convertData((Comparable) data);
                        qualityFeatures.put(module.getQualityInner(keyValue.getKey()) + index - term.getKey(), feature);
                    } else {
                        QuantityAttribute attribute = quantityAttributes.get(keyValue.getKey());
                        data = ConversionUtility.convert(data, attribute.getType());
                        float feature = attribute.convertData((Number) data);
                        quantityFeatures.put(module.getQuantityInner(keyValue.getKey()) + index - term.getKey(), feature);
                    }
                }
                module.associateInstance(qualityFeatures, quantityFeatures);
                qualityFeatures.clear();
                quantityFeatures.clear();
                count++;
            }
        } catch (HibernateException exception) {
            // TODO 处理日志.
            throw new RuntimeException(exception);
        }
        return count;
    }

}
