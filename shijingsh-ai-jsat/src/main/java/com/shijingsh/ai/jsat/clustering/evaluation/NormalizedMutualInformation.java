package com.shijingsh.ai.jsat.clustering.evaluation;

import java.util.List;

import com.shijingsh.ai.jsat.DataSet;
import com.shijingsh.ai.jsat.classifiers.ClassificationDataSet;
import com.shijingsh.ai.jsat.classifiers.DataPoint;

import com.shijingsh.ai.jsat.DataSet;
import com.shijingsh.ai.jsat.classifiers.ClassificationDataSet;
import com.shijingsh.ai.jsat.classifiers.DataPoint;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;

/**
 * Normalized Mutual Information (NMI) is a measure to evaluate a cluster based
 * on the true class labels for the data set. The NMI normally returns a value
 * in [0, 1], where 0 indicates the clustering appears random, and 1 indicate
 * the clusters perfectly match the class labels. To match the
 * {@link ClusterEvaluation} interface, the value returned by evaluate will be
 * 1.0-NMI . <br>
 * <b>NOTE:</b> Because the NMI needs to know the true class labels, only
 * {@link #evaluate(int[], DataSet) } will work, since it
 * provides the data set as an argument. The dataset given must be an instance
 * of {@link ClassificationDataSet}
 *
 * @author Edward Raff
 */
public class NormalizedMutualInformation implements ClusterEvaluation {

    @Override
    public double evaluate(int[] designations, DataSet dataSet) {
        if (!(dataSet instanceof ClassificationDataSet))
            throw new RuntimeException("NMI can only be calcuate for classification data sets");
        ClassificationDataSet cds = (ClassificationDataSet) dataSet;
        double nmiNumer = 0.0;
        double nmiC = 0.0;
        double nmiK = 0.0;

        DoubleArrayList kPriors = new DoubleArrayList();

        for (int i = 0; i < cds.size(); i++) {
            int ki = designations[i];
            if (ki < 0)// outlier, not clustered
                continue;
            while (kPriors.size() <= ki)
                kPriors.add(0.0);
            kPriors.set(ki, kPriors.getDouble(ki) + cds.getWeight(i));
        }

        double N = 0.0;
        for (int i = 0; i < kPriors.size(); i++)
            N += kPriors.getDouble(i);
        for (int i = 0; i < kPriors.size(); i++) {
            kPriors.set(i, kPriors.getDouble(i) / N);
            double pKi = kPriors.getDouble(i);
            if (pKi > 0)
                nmiK += -pKi * Math.log(pKi);
        }

        double[] cPriors = cds.getPriors();

        double[][] ck = new double[cPriors.length][kPriors.size()];

        for (int i = 0; i < cds.size(); i++) {
            int ci = cds.getDataPointCategory(i);
            int kj = designations[i];
            if (kj < 0)// outlier, ignore
                continue;

            ck[ci][kj] += cds.getWeight(i);
        }

        for (int i = 0; i < cPriors.length; i++) {
            double pCi = cPriors[i];
            if (pCi <= 0.0)
                continue;
            double logPCi = Math.log(pCi);
            for (int j = 0; j < kPriors.size(); j++) {
                double pKj = kPriors.getDouble(j);
                if (pKj <= 0.0)
                    continue;
                double pCiKj = ck[i][j] / N;
                if (pCiKj <= 0.0)
                    continue;
                nmiNumer += pCiKj * (Math.log(pCiKj) - Math.log(pKj) - logPCi);
            }
            nmiC += -pCi * logPCi;
        }

        return 1.0 - nmiNumer / ((nmiC + nmiK) / 2);
    }

    @Override
    public double naturalScore(double evaluate_score) {
        return -evaluate_score + 1;
    }

    @Override
    public double evaluate(List<List<DataPoint>> dataSets) {
        throw new UnsupportedOperationException("NMI requires the true data set" + " labels, call evaluate(int[] designations, DataSet dataSet)" + " instead");
    }

    @Override
    public NormalizedMutualInformation clone() {
        return new NormalizedMutualInformation();
    }

}
