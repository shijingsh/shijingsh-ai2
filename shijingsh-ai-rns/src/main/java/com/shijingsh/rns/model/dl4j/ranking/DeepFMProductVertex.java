package com.shijingsh.rns.model.dl4j.ranking;

import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.api.MaskState;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.graph.vertex.BaseGraphVertex;
import org.deeplearning4j.nn.graph.vertex.VertexIndices;
import org.deeplearning4j.nn.workspace.ArrayType;
import org.deeplearning4j.nn.workspace.LayerWorkspaceMgr;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.impl.transforms.Or;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.primitives.Pair;

/**
 *
 * DeepFM Product节点
 *
 * <pre>
 * DeepFM: A Factorization-Machine based Neural Network for CTR Prediction
 * </pre>
 *
 * @author Birdy
 *
 */
//TODO 存档,以后需要基于DL4J重构.
@Deprecated
public class DeepFMProductVertex extends BaseGraphVertex {

    public DeepFMProductVertex(ComputationGraph graph, String name, int vertexIndex) {
        this(graph, name, vertexIndex, null, null);
    }

    public DeepFMProductVertex(ComputationGraph graph, String name, int vertexIndex, VertexIndices[] inputVertices, VertexIndices[] outputVertices) {
        super(graph, name, vertexIndex, inputVertices, outputVertices);
    }

    @Override
    public boolean hasLayer() {
        return false;
    }

    @Override
    public boolean isOutputVertex() {
        return false;
    }

    @Override
    public Layer getLayer() {
        return null;
    }

    @Override
    public INDArray doForward(boolean training, LayerWorkspaceMgr workspaceMgr) {
        if (!canDoForward()) {
            throw new IllegalStateException("Cannot do forward pass: inputs not set");
        }
        // inputs[index] => {batchSize, numberOfEmbeds}
        INDArray left = inputs[0];
        INDArray right = inputs[1];
        long size = inputs[0].shape()[0];
        INDArray value = workspaceMgr.createUninitialized(ArrayType.ACTIVATIONS, size);
        // 求两个行向量的点积
        for (int index = 0; index < size; index++) {
            INDArray product = left.getRow(index).mmul(right.getRow(index).transpose());
            value.put(index, product);
        }
        // outputs[index] => {batchSize, 1}
        return Shape.newShapeNoCopy(value, new long[] { value.length(), 1L }, value.ordering() == 'f');
    }

    @Override
    public Pair<Gradient, INDArray[]> doBackward(boolean tbptt, LayerWorkspaceMgr workspaceMgr) {
        if (!canDoBackward()) {
            throw new IllegalStateException("Cannot do backward pass: errors not set");
        }

        // epsilons[index] => {batchSize, numberOfEmbeds}
        INDArray[] epsilons = new INDArray[inputs.length];
        epsilons[0] = workspaceMgr.dup(ArrayType.ACTIVATION_GRAD, inputs[0]);
        epsilons[1] = workspaceMgr.dup(ArrayType.ACTIVATION_GRAD, inputs[1]);
        // epsilon => {batchSize, 1}
        // inputs[index] => {batchSize, numberOfEmbeds}
        // TODO 如何通过inputs[index]与epsilon求导epsilons[index]
        INDArray left = inputs[0];
        INDArray right = inputs[1];
        for (int index = 0; index < epsilon.rows(); index++) {
            epsilons[0].putRow(index, right.getRow(index).transpose().mmul(epsilon.getRow(index)).transpose());
            epsilons[1].putRow(index, left.getRow(index).transpose().mmul(epsilon.getRow(index)).transpose());
        }
        return new Pair<>(null, epsilons);
    }

    @Override
    public void setBackpropGradientsViewArray(INDArray backpropGradientsViewArray) {
        if (backpropGradientsViewArray != null)
            throw new RuntimeException("Vertex does not have gradients; gradients view array cannot be set here");
    }

    @Override
    public Pair<INDArray, MaskState> feedForwardMaskArrays(INDArray[] maskArrays, MaskState currentMaskState, int minibatchSize) {
        if (maskArrays == null) {
            return new Pair<>(null, currentMaskState);
        }

        // Most common case: all or none.
        // If there's only *some* mask arrays: assume the others (missing) are
        // equivalent to all 1s
        // And for handling multiple masks: best strategy seems to be an OR
        // operation
        // i.e., output is 1 if any of the input are 1s
        // Which means: if any masks are missing, output null (equivalent to no
        // mask, or all steps present)
        // Otherwise do an element-wise OR operation

        for (INDArray mask : maskArrays) {
            if (mask == null) {
                return new Pair<>(null, currentMaskState);
            }
        }

        // At this point: all present. Do OR operation
        if (maskArrays.length == 1) {
            return new Pair<>(maskArrays[0], currentMaskState);
        } else {
            INDArray mask = maskArrays[0].dup(maskArrays[0].ordering());
            for (int index = 1; index < maskArrays.length; index++) {
                Nd4j.getExecutioner().exec(new Or(maskArrays[index], mask, mask));
            }
            return new Pair<>(mask, currentMaskState);
        }
    }

    @Override
    public String toString() {
        return "DeepFMProductVertex(id=" + this.getVertexIndex() + ",name=\"" + this.getVertexName() + "\")";
    }

}
