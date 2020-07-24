package com.shijingsh.rns.model.benchmark.rating;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.rns.model.AbstractModel;

/**
 *
 * Global Average推荐器
 *
 * <pre>
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "meanOfScore" })
public class GlobalAverageModel extends AbstractModel {

    @Override
    protected void doPractice() {
    }

    @Override
    public void predict(DataInstance instance) {
        instance.setQuantityMark(meanScore);
    }

}
