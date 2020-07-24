
package com.shijingsh.ai.jsat.clustering.evaluation;

import com.shijingsh.ai.jsat.DataSet;
import com.shijingsh.ai.jsat.clustering.ClustererBase;
import com.shijingsh.ai.jsat.DataSet;
import com.shijingsh.ai.jsat.clustering.ClustererBase;

/**
 * Base implementation for one of the methods in {@link ClusterEvaluation} to
 * make life easier.
 *
 * @author Edward Raff
 */
abstract public class ClusterEvaluationBase implements ClusterEvaluation {

    @Override
    public double evaluate(int[] designations, DataSet dataSet) {
        return evaluate(ClustererBase.createClusterListFromAssignmentArray(designations, dataSet));
    }

    @Override
    public abstract ClusterEvaluation clone();
}
