package com.shijingsh.ai.model.neuralnetwork.vertex;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.matrix.ColumnGlobalMatrix;
import com.shijingsh.ai.math.structure.matrix.LocalMatrix;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.shijingsh.ai.model.neuralnetwork.normalization.Normalizer;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.matrix.ColumnGlobalMatrix;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.model.neuralnetwork.layer.Layer;
import com.shijingsh.ai.model.neuralnetwork.learn.IgnoreLearner;
import com.shijingsh.ai.model.neuralnetwork.learn.Learner;
import com.shijingsh.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.shijingsh.ai.model.neuralnetwork.normalization.Normalizer;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.ai.math.structure.matrix.LocalMatrix;
import com.shijingsh.core.utility.KeyValue;

/**
 * 共享节点
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "vertexName", "factory", "layer", "learner", "normalizer", "numberOfShares" })
public class ShareVertex extends LayerVertex {

    private int numberOfShares;

    private KeyValue<MathMatrix, MathMatrix> middleKeyValue;

    private Map<String, MathMatrix> vertexGradients;

    private MathMatrix inputGlobalData;
    private MathMatrix middleGlobalData;
    private MathMatrix outputGlobalData;
    private MathMatrix outterGlobalError;
    private MathMatrix middleGlobalError;
    private MathMatrix innerGlobalError;

    private MathMatrix[] inputLocalDatas;
    private MathMatrix[] middleLocalDatas;
    private MathMatrix[] outputLocalDatas;
    private MathMatrix[] outterLocalErrors;
    private MathMatrix[] middleLocalErrors;
    private MathMatrix[] innerLocalErrors;

    protected ShareVertex() {
    }

    public ShareVertex(String name, MathCache factory, int numberOfShares, Layer layer) {
        this(name, factory, numberOfShares, layer, new IgnoreLearner(), new IgnoreNormalizer());
    }

    public ShareVertex(String name, MathCache factory, int numberOfShares, Layer layer, Learner learner, Normalizer normalizer) {
        super(name, factory, layer, learner, normalizer);
        this.numberOfShares = numberOfShares;
        this.vertexGradients = new HashMap<>(layer.getGradients());
        for (Entry<String, MathMatrix> term : vertexGradients.entrySet()) {
            MathMatrix matrix = term.getValue();
            matrix = factory.makeMatrix(matrix.getRowSize(), matrix.getColumnSize());
            term.setValue(matrix);
        }
    }

    @Override
    public void doCache(KeyValue<MathMatrix, MathMatrix>... samples) {
        // 检查样本
        if (samples.length == 0) {
            throw new IllegalArgumentException();
        }

        this.inputKeyValues = samples;
        this.outputKeyValue = new KeyValue<>(null, null);
        this.middleKeyValue = new KeyValue<>(null, null);

        inputLocalDatas = new MathMatrix[numberOfShares];
        middleLocalDatas = new MathMatrix[numberOfShares];
        outputLocalDatas = new MathMatrix[numberOfShares];
        outterLocalErrors = new MathMatrix[numberOfShares];
        middleLocalErrors = new MathMatrix[numberOfShares];
        innerLocalErrors = new MathMatrix[numberOfShares];

        for (int shareIndex = 0; shareIndex < numberOfShares; shareIndex++) {
            // 输入部分
            MathMatrix key = inputKeyValues[0].getKey();
            MathMatrix value = inputKeyValues[0].getValue();
            int from = shareIndex * key.getColumnSize() / numberOfShares;
            int to = from + key.getColumnSize() / numberOfShares;

            if (key instanceof ColumnGlobalMatrix) {
                key = ColumnGlobalMatrix.detachOf(ColumnGlobalMatrix.class.cast(key), from, to);
                if (value != null) {
                    value = ColumnGlobalMatrix.detachOf(ColumnGlobalMatrix.class.cast(value), from, to);
                }
            } else {
                key = new LocalMatrix(key, from, to, 0, key.getRowSize());
                if (value != null) {
                    value = new LocalMatrix(value, from, to, 0, key.getRowSize());
                }
            }

            KeyValue<MathMatrix, MathMatrix> keyValue = new KeyValue<>(key, value);
            layer.doCache(factory, keyValue);

            keyValue = layer.getInputKeyValue();
            inputLocalDatas[shareIndex] = keyValue.getKey();
            outterLocalErrors[shareIndex] = keyValue.getValue();
            keyValue = layer.getMiddleKeyValue();
            middleLocalDatas[shareIndex] = keyValue.getKey();
            middleLocalErrors[shareIndex] = keyValue.getValue();
            keyValue = layer.getOutputKeyValue();
            outputLocalDatas[shareIndex] = keyValue.getKey();
            innerLocalErrors[shareIndex] = keyValue.getValue();
        }

        inputGlobalData = ColumnGlobalMatrix.attachOf(inputLocalDatas);
        middleGlobalData = ColumnGlobalMatrix.attachOf(middleLocalDatas);
        outputGlobalData = ColumnGlobalMatrix.attachOf(outputLocalDatas);
        if (inputKeyValues[0].getValue() != null) {
            outterGlobalError = ColumnGlobalMatrix.attachOf(outterLocalErrors);
        }
        middleGlobalError = ColumnGlobalMatrix.attachOf(middleLocalErrors);
        innerGlobalError = ColumnGlobalMatrix.attachOf(innerLocalErrors);

        // 中间部分
        middleKeyValue.setKey(middleGlobalData);
        middleKeyValue.setValue(middleGlobalError);

        // 输出部分
        outputKeyValue.setKey(outputGlobalData);
        outputKeyValue.setValue(innerGlobalError);

        learner.doCache(layer.getGradients());
        epoch++;
        iteration = 0;
    }

    @Override
    public void doForward() {
        for (int shareIndex = 0; shareIndex < numberOfShares; shareIndex++) {
            MathMatrix inputLocalData = inputLocalDatas[shareIndex];
            MathMatrix middleLocalData = middleLocalDatas[shareIndex];
            MathMatrix outputLocalData = outputLocalDatas[shareIndex];

            layer.getInputKeyValue().setKey(inputLocalData);
            layer.getMiddleKeyValue().setKey(middleLocalData);
            layer.getOutputKeyValue().setKey(outputLocalData);
            layer.doForward();
        }

        MathMatrix innerError = outputKeyValue.getValue();
        innerError.setValues(0F);
    }

    @Override
    public void doBackward() {
        for (Entry<String, MathMatrix> term : vertexGradients.entrySet()) {
            MathMatrix matrix = term.getValue();
            matrix.setValues(0F);
        }
        Map<String, MathMatrix> layerGradients = layer.getGradients();

        for (int shareIndex = 0; shareIndex < numberOfShares; shareIndex++) {
            MathMatrix inputLocalData = inputLocalDatas[shareIndex];
            MathMatrix middleLocalData = middleLocalDatas[shareIndex];
            MathMatrix outputLocalData = outputLocalDatas[shareIndex];
            MathMatrix outterLocalError = outterLocalErrors[shareIndex];
            MathMatrix innerLocalError = innerLocalErrors[shareIndex];

            layer.getInputKeyValue().setKey(inputLocalData);
            layer.getMiddleKeyValue().setKey(middleLocalData);
            layer.getOutputKeyValue().setKey(outputLocalData);
            layer.getInputKeyValue().setValue(outterLocalError);
            layer.getOutputKeyValue().setValue(innerLocalError);
            layer.doBackward();

            for (Entry<String, MathMatrix> term : vertexGradients.entrySet()) {
                MathMatrix vertexGradient = term.getValue();
                MathMatrix layerGradient = layerGradients.get(term.getKey());
                vertexGradient.addMatrix(layerGradient, false);
            }
        }
        float scale = 1F / numberOfShares;
        for (Entry<String, MathMatrix> term : layerGradients.entrySet()) {
            MathMatrix vertexGradient = vertexGradients.get(term.getKey());
            vertexGradient.scaleValues(scale);
            MathMatrix layerGradient = term.getValue();
            layerGradient.copyMatrix(vertexGradient, false);
        }
        // TODO 执行标准器(标准化)
        normalizer.normalize(layerGradients);
        // 执行学习器(自适应学习率)
        learner.learn(layerGradients, iteration++, epoch);
    }

    public Layer getLayer() {
        return layer;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null) {
            return false;
        }
        if (getClass() != object.getClass()) {
            return false;
        } else {
            ShareVertex that = (ShareVertex) object;
            EqualsBuilder equal = new EqualsBuilder();
            equal.append(this.vertexName, that.vertexName);
            equal.append(this.layer, that.layer);
            equal.append(this.learner, that.learner);
            equal.append(this.normalizer, that.normalizer);
            equal.append(this.numberOfShares, that.numberOfShares);
            return equal.isEquals();
        }
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hash = new HashCodeBuilder();
        hash.append(vertexName);
        hash.append(layer);
        hash.append(learner);
        hash.append(normalizer);
        hash.append(numberOfShares);
        return hash.toHashCode();
    }

    @Override
    public String toString() {
        return "ShareVertex(name=" + vertexName + ")";
    }

}
