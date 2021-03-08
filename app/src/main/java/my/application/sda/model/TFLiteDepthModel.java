package my.application.sda.model;

import android.content.Context;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class TFLiteDepthModel {

    private Interpreter interpreter;
    private String mlName;
    final public int depthWidth = 640;
    final public int depthHeight = 384;

    private ByteBuffer inference;

    public TFLiteDepthModel(String mlName){
        this.mlName = mlName;

        inference = ByteBuffer.allocateDirect(depthWidth*depthHeight* DataType.FLOAT32.byteSize());
        inference.order(ByteOrder.nativeOrder());
    }

    public void init(Context context) throws IOException {
        Interpreter.Options tfliteOptions = (new Interpreter.Options());
        CompatibilityList compatList = new CompatibilityList();

        if(compatList.isDelegateSupportedOnThisDevice()){
            // if the device has a supported GPU, add the GPU delegate
            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
            tfliteOptions.addDelegate(gpuDelegate);
        } else {
            // if the GPU is not supported, run on 4 threads
            tfliteOptions.setNumThreads(4);
        }
        
        MappedByteBuffer tfliteModel = null;
        tfliteModel = FileUtil.loadMappedFile(context, mlName);

        // create tflite interpreter
        interpreter = new Interpreter(tfliteModel, tfliteOptions);
    }

    public ByteBuffer doInference(ByteBuffer input){
        inference.rewind();

        Object[] inputArray = new Object[interpreter.getInputTensorCount()];
        int iIndex = interpreter.getInputIndex("im0");
        inputArray[interpreter.getInputIndex("im0")] = input;

        Map<Integer, Object> outputMap = new HashMap<>();
        //int oIndex = interpreter.getOutputIndex("decoder/half/resize/ResizeBilinear");
        outputMap.put(0, inference);

        interpreter.runForMultipleInputsOutputs(inputArray, outputMap);

        inference.rewind();

        return inference;
    }

}
