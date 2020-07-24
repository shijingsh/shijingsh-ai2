package com.shijingsh.rns.model.neuralnetwork;

import java.util.HashMap;
import java.util.Map;

import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.Nd4jCache;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.math.structure.matrix.MatrixScalar;
import com.shijingsh.ai.math.structure.matrix.Nd4jMatrix;
import com.shijingsh.ai.model.neuralnetwork.Graph;
import com.shijingsh.ai.model.neuralnetwork.GraphConfigurator;
import com.shijingsh.ai.model.neuralnetwork.activation.IdentityActivationFunction;
import com.shijingsh.ai.model.neuralnetwork.activation.SigmoidActivationFunction;
import com.shijingsh.ai.model.neuralnetwork.layer.Layer;
import com.shijingsh.ai.model.neuralnetwork.layer.ParameterConfigurator;
import com.shijingsh.ai.model.neuralnetwork.layer.WeightLayer;
import com.shijingsh.ai.model.neuralnetwork.learn.NesterovLearner;
import com.shijingsh.ai.model.neuralnetwork.loss.MSELossFunction;
import com.shijingsh.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.shijingsh.ai.model.neuralnetwork.optimization.StochasticGradientOptimizer;
import com.shijingsh.ai.model.neuralnetwork.parameter.XavierParameterFactory;
import com.shijingsh.ai.model.neuralnetwork.schedule.ConstantSchedule;
import com.shijingsh.ai.model.neuralnetwork.vertex.LayerVertex;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.model.EpocheModel;

/**
 *
 * CDAE推荐器
 *
 * <pre>
 * Collaborative Denoising Auto-Encoders for Top-N Recommender Systems
 * 参考LibRec团队
 * </pre>
 *
 * @author Birdy
 *
 */
public class CDAEModel extends EpocheModel {

    /**
     * the dimension of input units
     */
    protected int inputDimension;

    /**
     * the dimension of hidden units
     */
    protected int hiddenDimension;

    /**
     * the activation function of the hidden layer in the neural network
     */
    protected String hiddenActivation;

    /**
     * the activation function of the output layer in the neural network
     */
    protected String outputActivation;

    /**
     * the learning rate of the optimization algorithm
     */
    protected float learnRatio;

    /**
     * the momentum of the optimization algorithm
     */
    protected float momentum;

    /**
     * the regularization coefficient of the weights in the neural network
     */
    protected float weightRegularization;

    /**
     * the data structure that stores the training data
     */
    protected Nd4jMatrix inputData;

    protected Nd4jMatrix labelData;

    /**
     * the data structure that stores the predicted data
     */
    protected Nd4jMatrix outputData;

    protected Graph network;

    /**
     * the threshold to binarize the rating
     */
    private float binarie;

    protected int getInputDimension() {
        return itemSize;
    }

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        inputDimension = getInputDimension();
        hiddenDimension = configuration.getInteger("recommender.hidden.dimension");
        hiddenActivation = configuration.getString("recommender.hidden.activation");
        outputActivation = configuration.getString("recommender.output.activation");
        learnRatio = configuration.getFloat("recommender.iterator.learnrate");
        momentum = configuration.getFloat("recommender.iterator.momentum");
        weightRegularization = configuration.getFloat("recommender.weight.regularization");
        binarie = configuration.getFloat("recommender.binarize.threshold");
        // transform the sparse matrix to INDArray
        // the sparse training matrix has been binarized

        INDArray array = Nd4j.create(userSize, itemSize);
        inputData = new Nd4jMatrix(array);

        array = Nd4j.create(userSize, itemSize);
        labelData = new Nd4jMatrix(array);
        for (MatrixScalar term : scoreMatrix) {
            labelData.setValue(term.getRow(), term.getColumn(), 1F);
        }

        array = Nd4j.create(userSize, itemSize);
        outputData = new Nd4jMatrix(array);
    }

    protected Graph getComputationGraph() {
        GraphConfigurator configurator = new GraphConfigurator();
        Map<String, ParameterConfigurator> configurators = new HashMap<>();
        Nd4j.getRandom().setSeed(6L);
        ParameterConfigurator parameterConfigurator = new ParameterConfigurator(0F, weightRegularization, new XavierParameterFactory());
        configurators.put(CDAELayer.WEIGHT_KEY, parameterConfigurator);
        configurators.put(CDAELayer.BIAS_KEY, new ParameterConfigurator(0F, 0F));
        configurators.put(CDAELayer.USER_KEY, parameterConfigurator);
        MathCache factory = new Nd4jCache();
        Layer cdaeLayer = new CDAELayer(userSize, itemSize, hiddenDimension, factory, configurators, new SigmoidActivationFunction());
        Layer outputLayer = new WeightLayer(hiddenDimension, itemSize, factory, configurators, new IdentityActivationFunction());

        configurator.connect(new LayerVertex("cdae", factory, cdaeLayer, new NesterovLearner(new ConstantSchedule(momentum), new ConstantSchedule(learnRatio)), new IgnoreNormalizer()));
        configurator.connect(new LayerVertex("output", factory, outputLayer, new NesterovLearner(new ConstantSchedule(momentum), new ConstantSchedule(learnRatio)), new IgnoreNormalizer()), "cdae");

        Graph graph = new Graph(configurator, new StochasticGradientOptimizer(), new MSELossFunction());
        return graph;
    }

    @Override
    protected void doPractice() {
        Graph graph = getComputationGraph();
        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            inputData.getArray().assign(labelData.getArray());
            for (MatrixScalar term : scoreMatrix) {
                if (RandomUtility.randomFloat(1F) < 0.2F) {
                    inputData.setValue(term.getRow(), term.getColumn(), 0F);
                }
            }
            totalError = graph.practice(1, new MathMatrix[] { inputData }, new MathMatrix[] { labelData });
            if (isConverged(epocheIndex) && isConverged) {
                break;
            }
            currentError = totalError;
        }
        graph.predict(new MathMatrix[] { labelData }, new MathMatrix[] { outputData });
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        instance.setQuantityMark(outputData.getValue(userIndex, itemIndex));
    }

}
