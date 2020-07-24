package com.shijingsh.rns.model.extend.ranking;

import java.util.Iterator;
import java.util.concurrent.Semaphore;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.environment.EnvironmentContext;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.math.structure.vector.VectorScalar;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.rns.model.AbstractModel;
import com.shijingsh.rns.model.exception.ModelException;

/**
 *
 * Association Rule推荐器
 *
 * <pre>
 * A Recommendation Algorithm Using Multi-Level Association Rules
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class AssociationRuleModel extends AbstractModel {

    /**
     * confidence matrix of association rules
     */
    private DenseMatrix associationMatrix;

    /**
     * setup
     *
     * @throws ModelException if error occurs
     */
    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        associationMatrix = DenseMatrix.valueOf(itemSize, itemSize);
    }

    @Override
    protected void doPractice() {
        EnvironmentContext context = EnvironmentContext.getContext();
        Semaphore semaphore = new Semaphore(0);
        // simple rule: X => Y, given that each user vector is regarded as a
        // transaction
        for (int leftItemIndex = 0; leftItemIndex < itemSize; leftItemIndex++) {
            // all transactions for item itemIdx
            SparseVector leftVector = scoreMatrix.getColumnVector(leftItemIndex);
            for (int rightItemIndex = leftItemIndex + 1; rightItemIndex < itemSize; rightItemIndex++) {
                SparseVector rightVector = scoreMatrix.getColumnVector(rightItemIndex);
                int leftIndex = leftItemIndex;
                int rightIndex = rightItemIndex;
                context.doAlgorithmByAny(leftItemIndex * rightItemIndex, () -> {
                    int leftCursor = 0, rightCursor = 0, leftSize = leftVector.getElementSize(), rightSize = rightVector.getElementSize();
                    if (leftSize != 0 && rightSize != 0) {
                        // compute confidence where containing item assoItemIdx
                        // among
                        // userRatingsVector
                        int count = 0;
                        Iterator<VectorScalar> leftIterator = leftVector.iterator();
                        Iterator<VectorScalar> rightIterator = rightVector.iterator();
                        VectorScalar leftTerm = leftIterator.next();
                        VectorScalar rightTerm = rightIterator.next();
                        // 判断两个有序数组中是否存在相同的数字
                        while (leftCursor < leftSize && rightCursor < rightSize) {
                            if (leftTerm.getIndex() == rightTerm.getIndex()) {
                                count++;
                                if (leftIterator.hasNext()) {
                                    leftTerm = leftIterator.next();
                                }
                                if (rightIterator.hasNext()) {
                                    rightTerm = rightIterator.next();
                                }
                                leftCursor++;
                                rightCursor++;
                            } else if (leftTerm.getIndex() > rightTerm.getIndex()) {
                                if (rightIterator.hasNext()) {
                                    rightTerm = rightIterator.next();
                                }
                                rightCursor++;
                            } else if (leftTerm.getIndex() < rightTerm.getIndex()) {
                                if (leftIterator.hasNext()) {
                                    leftTerm = leftIterator.next();
                                }
                                leftCursor++;
                            }
                        }
                        float leftValue = (count + 0F) / leftVector.getElementSize();
                        float rightValue = (count + 0F) / rightVector.getElementSize();
                        associationMatrix.setValue(leftIndex, rightIndex, leftValue);
                        associationMatrix.setValue(rightIndex, leftIndex, rightValue);
                    }
                    semaphore.release();
                });
            }
            try {
                semaphore.acquire(itemSize - leftItemIndex - 1);
            } catch (Exception exception) {
                throw new ModelException(exception);
            }
        }
    }

    /**
     * predict a specific rating for user userIdx on item itemIdx.
     *
     * @param userIndex user index
     * @param itemIndex item index
     * @return predictive rating for user userIdx on item itemIdx
     * @throws ModelException if error occurs
     */
    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        float value = 0F;
        for (VectorScalar term : scoreMatrix.getRowVector(userIndex)) {
            int associationIndex = term.getIndex();
            float association = associationMatrix.getValue(associationIndex, itemIndex);
            double score = term.getValue();
            value += score * association;
        }
        instance.setQuantityMark(value);
    }

}
