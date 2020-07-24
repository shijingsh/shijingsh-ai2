package com.shijingsh.rns.model.benchmark.rating;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.rns.model.AbstractModel;

/**
 *
 * Item Average推荐器
 *
 * <pre>
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "itemDimension", "itemMeans" })
public class ItemAverageModel extends AbstractModel {

    /** 物品平均分数 */
    private float[] itemMeans;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        itemMeans = new float[itemSize];
    }

    @Override
    protected void doPractice() {
        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            SparseVector itemVector = scoreMatrix.getColumnVector(itemIndex);
            itemMeans[itemIndex] = itemVector.getElementSize() == 0 ? meanScore : itemVector.getSum(false) / itemVector.getElementSize();
        }
    }

    @Override
    public void predict(DataInstance instance) {
        int itemIndex = instance.getQualityFeature(itemDimension);
        instance.setQuantityMark(itemMeans[itemIndex]);
    }

}
