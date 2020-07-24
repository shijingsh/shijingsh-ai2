package com.shijingsh.ai.model.neuralnetwork.vertex;

import java.util.Map;

import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.shijingsh.ai.model.neuralnetwork.normalization.Normalizer;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.shijingsh.ai.math.structure.MathCache;
import com.shijingsh.ai.math.structure.matrix.MathMatrix;
import com.shijingsh.ai.model.neuralnetwork.layer.Layer;
import com.shijingsh.ai.model.neuralnetwork.learn.IgnoreLearner;
import com.shijingsh.ai.model.neuralnetwork.learn.Learner;
import com.shijingsh.ai.model.neuralnetwork.normalization.IgnoreNormalizer;
import com.shijingsh.ai.model.neuralnetwork.normalization.Normalizer;
import com.shijingsh.ai.modem.ModemDefinition;
import com.shijingsh.core.utility.KeyValue;

/**
 * 层节点
 *
 * @author Birdy
 *
 */
@ModemDefinition(value = { "vertexName", "factory", "layer", "learner", "normalizer" })
public class LayerVertex extends AbstractVertex {

    protected Layer layer;

    // 梯度相关(自适应学习率)
    protected Learner learner;

    // 梯度相关(归一化)
    protected Normalizer normalizer;

    protected int epoch, iteration;

    protected LayerVertex() {
    }

    public LayerVertex(String name, MathCache factory, Layer layer) {
        this(name, factory, layer, new IgnoreLearner(), new IgnoreNormalizer());
    }

    public LayerVertex(String name, MathCache factory, Layer layer, Learner learner, Normalizer normalizer) {
        super(name, factory);
        this.layer = layer;
        this.learner = learner;
        this.normalizer = normalizer;
        this.epoch = 0;
        this.iteration = 0;
    }

    @Override
    public void doCache(KeyValue<MathMatrix, MathMatrix>... samples) {
        layer.doCache(factory, samples[0]);
        learner.doCache(layer.getGradients());
        inputKeyValues = new KeyValue[] { layer.getInputKeyValue() };
        outputKeyValue = layer.getOutputKeyValue();

        epoch++;
        iteration = 0;
    }

    @Override
    public void doForward() {
        layer.doForward();
    }

    @Override
    public void doBackward() {
        layer.doBackward();
        Map<String, MathMatrix> gradients = layer.getGradients();
        // TODO 执行标准器(标准化)
        normalizer.normalize(gradients);
        // 执行学习器(自适应学习率)
        learner.learn(gradients, iteration++, epoch);
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
            LayerVertex that = (LayerVertex) object;
            EqualsBuilder equal = new EqualsBuilder();
            equal.append(this.vertexName, that.vertexName);
            equal.append(this.layer, that.layer);
            equal.append(this.learner, that.learner);
            equal.append(this.normalizer, that.normalizer);
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
        return hash.toHashCode();
    }

    @Override
    public String toString() {
        return "LayerVertex(name=" + vertexName + ")";
    }

}
