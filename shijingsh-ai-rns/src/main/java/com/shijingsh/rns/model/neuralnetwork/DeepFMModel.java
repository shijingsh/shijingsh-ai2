package com.shijingsh.rns.model.neuralnetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.shijingsh.rns.data.processor.AllFeatureDataSorter;
import com.shijingsh.rns.data.processor.QualityFeatureDataSplitter;
import org.nd4j.linalg.factory.Nd4j;

import com.shijingsh.ai.data.DataInstance;
import com.shijingsh.ai.data.DataModule;
import com.shijingsh.ai.data.DataSpace;
import com.shijingsh.ai.data.processor.DataSorter;
import com.shijingsh.ai.data.processor.DataSplitter;
import com.shijingsh.ai.math.structure.DenseCache;
import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.MathCalculator;
import com.shijingsh.ai.math.structure.matrix.DenseMatrix;
import com.shijingsh.ai.math.structure.vector.SparseVector;
import com.shijingsh.ai.model.neuralnetwork.Graph;
import com.shijingsh.ai.model.neuralnetwork.GraphConfigurator;
import com.shijingsh.ai.model.neuralnetwork.activation.IdentityActivationFunction;
import com.shijingsh.ai.model.neuralnetwork.activation.ReLUActivationFunction;
import com.shijingsh.ai.model.neuralnetwork.activation.SigmoidActivationFunction;
import com.shijingsh.ai.model.neuralnetwork.layer.EmbedLayer;
import com.shijingsh.ai.model.neuralnetwork.layer.Layer;
import com.shijingsh.ai.model.neuralnetwork.layer.ParameterConfigurator;
import com.shijingsh.ai.model.neuralnetwork.layer.WeightLayer;
import com.shijingsh.ai.model.neuralnetwork.learn.SgdLearner;
import com.shijingsh.ai.model.neuralnetwork.loss.BinaryXENTLossFunction;
import com.shijingsh.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.shijingsh.ai.model.neuralnetwork.optimization.StochasticGradientOptimizer;
import com.shijingsh.ai.model.neuralnetwork.parameter.NormalParameterFactory;
import com.shijingsh.ai.model.neuralnetwork.schedule.ConstantSchedule;
import com.shijingsh.ai.model.neuralnetwork.schedule.Schedule;
import com.shijingsh.ai.model.neuralnetwork.vertex.LayerVertex;
import com.shijingsh.ai.model.neuralnetwork.vertex.accumulation.InnerProductVertex;
import com.shijingsh.ai.model.neuralnetwork.vertex.transformation.HorizontalAttachVertex;
import com.shijingsh.core.utility.Configurator;
import com.shijingsh.core.utility.KeyValue;
import com.shijingsh.core.utility.RandomUtility;
import com.shijingsh.rns.data.processor.AllFeatureDataSorter;
import com.shijingsh.rns.data.processor.QualityFeatureDataSplitter;
import com.shijingsh.rns.model.EpocheModel;

/**
 *
 * DeepFM推荐器
 *
 * <pre>
 * DeepFM: A Factorization-Machine based Neural Network for CTR Prediction
 * </pre>
 *
 * @author Birdy
 *
 */
public class DeepFMModel extends EpocheModel {

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
     * 所有维度的特征总数
     */
    private int numberOfFeatures;

    /**
     * the data structure that stores the training data
     */
    protected DenseMatrix[] inputData;

    /**
     * the data structure that stores the predicted data
     */
    protected DenseMatrix outputData;

    /**
     * 计算图
     */
    protected Graph graph;

    protected int[] dimensionSizes;

    protected DataModule marker;

    @Override
    public void prepare(Configurator configuration, DataModule model, DataSpace space) {
        super.prepare(configuration, model, space);
        learnRatio = configuration.getFloat("recommender.iterator.learnrate");
        momentum = configuration.getFloat("recommender.iterator.momentum");
        weightRegularization = configuration.getFloat("recommender.weight.regularization");
        this.marker = model;

        // TODO 此处需要重构,外部索引与内部索引的映射转换
        dimensionSizes = new int[model.getQualityOrder()];
        for (int orderIndex = 0, orderSize = model.getQualityOrder(); orderIndex < orderSize; orderIndex++) {
            Entry<Integer, KeyValue<String, Boolean>> term = model.getOuterKeyValue(orderIndex);
            dimensionSizes[model.getQualityInner(term.getValue().getKey())] = space.getQualityAttribute(term.getValue().getKey()).getSize();
        }
    }

    protected Graph getComputationGraph(int[] dimensionSizes) {
        Schedule schedule = new ConstantSchedule(learnRatio);
        GraphConfigurator configurator = new GraphConfigurator();
        Map<String, ParameterConfigurator> configurators = new HashMap<>();
        Nd4j.getRandom().setSeed(6L);
        ParameterConfigurator parameter = new ParameterConfigurator(weightRegularization, 0F, new NormalParameterFactory());
        configurators.put(WeightLayer.WEIGHT_KEY, parameter);
        configurators.put(WeightLayer.BIAS_KEY, new ParameterConfigurator(0F, 0F));
        MathCache factory = new DenseCache();

        // 构建Embed节点
        // TODO 应该调整为配置项.
        int numberOfFactors = 10;
        // TODO Embed只支持输入的column为1.
        String[] embedVertexNames = new String[dimensionSizes.length];
        for (int fieldIndex = 0; fieldIndex < dimensionSizes.length; fieldIndex++) {
            embedVertexNames[fieldIndex] = "Embed" + fieldIndex;
            Layer embedLayer = new EmbedLayer(dimensionSizes[fieldIndex], numberOfFactors, factory, configurators, new IdentityActivationFunction());
            configurator.connect(new LayerVertex(embedVertexNames[fieldIndex], factory, embedLayer, new SgdLearner(schedule), new IgnoreNormalizer()));
        }

        // 构建因子分解机部分
        // 构建FM Plus节点(实际就是FM的输入)
        numberOfFeatures = 0;
        for (int fieldIndex = 0; fieldIndex < dimensionSizes.length; fieldIndex++) {
            numberOfFeatures += dimensionSizes[fieldIndex];
        }
        // TODO 注意,由于EmbedLayer不支持与其它Layer共享输入,所以FM Plus节点构建自己的One Hot输入.
        Layer fmLayer = new FMLayer(dimensionSizes, numberOfFeatures, 1, factory, configurators, new IdentityActivationFunction());
        configurator.connect(new LayerVertex("FMPlus", factory, fmLayer, new SgdLearner(schedule), new IgnoreNormalizer()));

        // 构建FM Product节点
        // 注意:节点数量是(n*(n-1)/2)),n为Embed节点数量
        String[] productVertexNames = new String[dimensionSizes.length * (dimensionSizes.length - 1) / 2];
        int productIndex = 0;
        for (int outterFieldIndex = 0; outterFieldIndex < dimensionSizes.length; outterFieldIndex++) {
            for (int innerFieldIndex = outterFieldIndex + 1; innerFieldIndex < dimensionSizes.length; innerFieldIndex++) {
                productVertexNames[productIndex] = "FMProduct" + outterFieldIndex + ":" + innerFieldIndex;
                String left = embedVertexNames[outterFieldIndex];
                String right = embedVertexNames[innerFieldIndex];
                configurator.connect(new InnerProductVertex(productVertexNames[productIndex], factory), left, right);
                productIndex++;
            }
        }

        // 构建FM Sum节点(实际就是FM的输出)
        String[] names = new String[productVertexNames.length + 2];
        System.arraycopy(productVertexNames, 0, names, 0, productVertexNames.length);
        names[productVertexNames.length] = "FMPlus";
        // configurator.connect(new SumVertex("FMOutput"), names);

        // 构建多层网络部分
        // 构建Net Input节点
        // TODO 调整为支持输入(连续域)Dense Field.
        // TODO 应该调整为配置项.
        int numberOfHiddens = 20;
        configurator.connect(new HorizontalAttachVertex("EmbedStack", factory), embedVertexNames);
        Layer netLayer = new WeightLayer(dimensionSizes.length * numberOfFactors, numberOfHiddens, factory, configurators, new ReLUActivationFunction());
        configurator.connect(new LayerVertex("NetInput", factory, netLayer, new SgdLearner(schedule), new IgnoreNormalizer()), "EmbedStack");

        // TODO 应该调整为配置项.
        int numberOfLayers = 5;
        String currentLayer = "NetInput";
        for (int layerIndex = 0; layerIndex < numberOfLayers; layerIndex++) {
            Layer hiddenLayer = new WeightLayer(numberOfHiddens, numberOfHiddens, factory, configurators, new ReLUActivationFunction());
            configurator.connect(new LayerVertex("NetHidden" + layerIndex, factory, hiddenLayer, new SgdLearner(schedule), new IgnoreNormalizer()), currentLayer);
            currentLayer = "NetHidden" + layerIndex;
        }
        names[productVertexNames.length + 1] = currentLayer;

        // 构建Deep Output节点
        configurator.connect(new HorizontalAttachVertex("DeepStack", factory), names);
        Layer deepLayer = new WeightLayer(productVertexNames.length + 1 + numberOfHiddens, 1, factory, configurators, new SigmoidActivationFunction());
        configurator.connect(new LayerVertex("DeepOutput", factory, deepLayer, new SgdLearner(schedule), new IgnoreNormalizer()), "DeepStack");

        Graph graph = new Graph(configurator, new StochasticGradientOptimizer(), new BinaryXENTLossFunction(false));
        return graph;
    }

    @Override
    protected void doPractice() {
        DataSplitter splitter = new QualityFeatureDataSplitter(userDimension);
        DataModule[] models = splitter.split(marker, userSize);
        DataSorter sorter = new AllFeatureDataSorter();
        for (int index = 0; index < userSize; index++) {
            models[index] = sorter.sort(models[index]);
        }

        DataInstance instance;

        int[] positiveKeys = new int[dimensionSizes.length], negativeKeys = new int[dimensionSizes.length];

        graph = getComputationGraph(dimensionSizes);

        for (int epocheIndex = 0; epocheIndex < epocheSize; epocheIndex++) {
            totalError = 0F;

            // TODO 应该调整为配置项.
            int batchSize = 2000;
            inputData = new DenseMatrix[dimensionSizes.length + 1];
            inputData[dimensionSizes.length] = DenseMatrix.valueOf(batchSize, dimensionSizes.length);
            for (int index = 0; index < dimensionSizes.length; index++) {
                inputData[index] = DenseMatrix.valueOf(batchSize, 1);
            }
            DenseMatrix labelData = DenseMatrix.valueOf(batchSize, 1);

            for (int batchIndex = 0; batchIndex < batchSize;) {
                // 随机用户
                int userIndex = RandomUtility.randomInteger(userSize);
                SparseVector userVector = scoreMatrix.getRowVector(userIndex);
                if (userVector.getElementSize() == 0 || userVector.getElementSize() == itemSize) {
                    continue;
                }

                DataModule module = models[userIndex];
                instance = module.getInstance(0);
                // 获取正样本
                int positivePosition = RandomUtility.randomInteger(module.getSize());
                instance.setCursor(positivePosition);
                for (int index = 0; index < positiveKeys.length; index++) {
                    positiveKeys[index] = instance.getQualityFeature(index);
                }

                // 获取负样本
                int negativeItemIndex = RandomUtility.randomInteger(itemSize - userVector.getElementSize());
                for (int position = 0, size = userVector.getElementSize(); position < size; position++) {
                    if (negativeItemIndex >= userVector.getIndex(position)) {
                        negativeItemIndex++;
                        continue;
                    }
                    break;
                }
                // TODO 注意,此处为了故意制造负面特征.
                int negativePosition = RandomUtility.randomInteger(module.getSize());
                instance.setCursor(negativePosition);
                for (int index = 0; index < negativeKeys.length; index++) {
                    negativeKeys[index] = instance.getQualityFeature(index);
                }
                negativeKeys[itemDimension] = negativeItemIndex;

                for (int dimension = 0; dimension < dimensionSizes.length; dimension++) {
                    // inputData[dimension].putScalar(batchIndex, 0,
                    // positiveKeys[dimension]);
                    inputData[dimensionSizes.length].setValue(batchIndex, dimension, positiveKeys[dimension]);
                    inputData[dimension].setValue(batchIndex, 0, positiveKeys[dimension]);
                }
                labelData.setValue(batchIndex, 0, 1);
                batchIndex++;

                for (int dimension = 0; dimension < dimensionSizes.length; dimension++) {
                    // inputData[dimension].putScalar(batchIndex, 0,
                    // negativeKeys[dimension]);
                    inputData[dimensionSizes.length].setValue(batchIndex, dimension, negativeKeys[dimension]);
                    inputData[dimension].setValue(batchIndex, 0, negativeKeys[dimension]);
                }
                labelData.setValue(batchIndex, 0, 0);
                batchIndex++;
            }
            totalError = graph.practice(100, inputData, new DenseMatrix[] { labelData });

            DenseMatrix[] data = new DenseMatrix[inputData.length];
            DenseMatrix label = DenseMatrix.valueOf(10, 1);
            for (int index = 0; index < data.length; index++) {
                DenseMatrix input = inputData[index];
                data[index] = DenseMatrix.valueOf(10, input.getColumnSize());
                data[index].iterateElement(MathCalculator.SERIAL, (scalar) -> {
                    scalar.setValue(input.getValue(scalar.getRow(), scalar.getColumn()));
                });
            }
            graph.predict(data, new DenseMatrix[] { label });
            System.out.println(label);

            if (isConverged(epocheIndex) && isConverged) {
                break;
            }
            currentError = totalError;
        }

        inputData[dimensionSizes.length] = DenseMatrix.valueOf(userSize, dimensionSizes.length);
        for (int index = 0; index < dimensionSizes.length; index++) {
            inputData[index] = DenseMatrix.valueOf(userSize, 1);
        }

        for (int userIndex = 0; userIndex < userSize; userIndex++) {
            DataModule model = models[userIndex];
            if (model.getSize() > 0) {
                instance = model.getInstance(model.getSize() - 1);
                for (int dimension = 0; dimension < dimensionSizes.length; dimension++) {
                    if (dimension != itemDimension) {
                        int feature = instance.getQualityFeature(dimension);
                        // inputData[dimension].putScalar(userIndex, 0,
                        // keys[dimension]);
                        inputData[dimensionSizes.length].setValue(userIndex, dimension, feature);
                        inputData[dimension].setValue(userIndex, 0, feature);
                    }
                }
            } else {
                inputData[dimensionSizes.length].setValue(userIndex, userDimension, userIndex);
                inputData[userDimension].setValue(userIndex, 0, userIndex);
            }
        }

        DenseMatrix labelData = DenseMatrix.valueOf(userSize, 1);
        outputData = DenseMatrix.valueOf(userSize, itemSize);

        for (int itemIndex = 0; itemIndex < itemSize; itemIndex++) {
            inputData[dimensionSizes.length].getColumnVector(itemDimension).setValues(itemIndex);
            inputData[itemDimension].setValues(itemIndex);
            graph.predict(inputData, new DenseMatrix[] { labelData });
            outputData.getColumnVector(itemIndex).iterateElement(MathCalculator.SERIAL, (scalar) -> {
                scalar.setValue(labelData.getValue(scalar.getIndex(), 0));
            });
        }
    }

    @Override
    public void predict(DataInstance instance) {
        int userIndex = instance.getQualityFeature(userDimension);
        int itemIndex = instance.getQualityFeature(itemDimension);
        float value = outputData.getValue(userIndex, itemIndex);
        instance.setQuantityMark(value);
    }

}
