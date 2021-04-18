package com.faaa;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.baidu.paddle.lite.Tensor;

import java.io.InputStream;
import java.util.Date;
import java.util.Vector;

import static android.graphics.Color.*;

public class FaceRecognizePredictor extends Predictor {
    private static final String TAG = FaceRecognizePredictor.class.getSimpleName();
    protected Vector<String> wordLabels = new Vector<String>();
    protected Boolean enableRGBColorFormat = true;
    protected long[] inputShape = new long[]{1, 3, 112, 112};
    protected long[] outputShape = new long[]{1, 3, 1200, 825};
    protected float[] inputMean = new float[]{127.5f, 127.5f, 127.5f};
    protected float[] inputStd = new float[]{128.0f, 128.0f, 128.0f};
    protected Bitmap inputImage = null;
    protected Bitmap outputImage = null;

    protected String top1Result = "";
    protected String top2Result = "";
    protected String top3Result = "";
    protected float preprocessTime = 0;
    protected float postprocessTime = 0;
    protected float[][] lableFeatures = new float[50][512];

    public FaceRecognizePredictor() {
        super();
    }

    public boolean init(Context appCtx, String modelPath, String labelPath, Boolean enableRGBColorFormat,
                        long[] inputShape, float[] inputMean,
                        float[] inputStd) {
        if (inputShape.length != 4) {
            Log.i(TAG, "size of input shape should be: 4");
            return false;
        }
        if (inputMean.length != inputShape[1]) {
            Log.i(TAG, "size of input mean should be: " + Long.toString(inputShape[1]));
            return false;
        }
        if (inputStd.length != inputShape[1]) {
            Log.i(TAG, "size of input std should be: " + Long.toString(inputShape[1]));
            return false;
        }
        if (inputShape[0] != 1) {
            Log.i(TAG, "only one batch is supported in the image classification demo, you can use any batch size in " +
                    "your Apps!");
            return false;
        }
        if (inputShape[1] != 1 && inputShape[1] != 3) {
            Log.i(TAG, "only one/three channels are supported in the image classification demo, you can use any " +
                    "channel size in your Apps!");
            return false;
        }
        super.init(appCtx, modelPath);
        if (!super.isLoaded()) {
            return false;
        }
        isLoaded &= loadLabel(labelPath);
        this.enableRGBColorFormat = enableRGBColorFormat;
        this.inputShape = inputShape;
        this.inputMean = inputMean;
        this.inputStd = inputStd;
        return isLoaded;
    }

    protected boolean loadLabel(String labelPath) {
        wordLabels.clear();
        // load word labels from file
        try {
            InputStream assetsInputStream = appCtx.getAssets().open(labelPath);
            int available = assetsInputStream.available();
            byte[] lines = new byte[available];
            assetsInputStream.read(lines);
            assetsInputStream.close();
            String words = new String(lines);
            String[] contents = words.split("\n");
            int i=0;
            for (String content : contents) {
                String[] labels = content.split(" ");
                wordLabels.add(labels[0]);
                Log.i(TAG,wordLabels.elementAt(i));
                String[] features = labels[1].split(",");
                for(int j=0;j<512;j++){
                    lableFeatures[i][j] =  Float.parseFloat(features[j]);
                }
                i = i + 1;
            }
            Log.i(TAG, "word label size: " + wordLabels.size());
        } catch (Exception e) {
            Log.e(TAG, "EEEEEE" + e.getMessage());
            return false;
        }
        return true;
    }

    public Tensor getInput(int idx) {
        return super.getInput(idx);
    }

    public Tensor getOutput(int idx) {
        return super.getOutput(idx);
    }

    public boolean runModel(Bitmap image) {
        setInputImage(image);
        return runModel();
    }

    public boolean runModel() {
        if (inputImage == null) {
            return false;
        }

        // set input shape
        Tensor inputTensor = getInput(0);
        inputTensor.resize(inputShape);

        // pre-process image, and feed input tensor with pre-processed data
        Date start = new Date();
        int channels = (int) inputShape[1];
        int width = (int) inputShape[3];
        int height = (int) inputShape[2];
        float[] inputData = new float[channels * width * height];
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pixel = inputImage.getPixel(j, i);
                float r = (float) red(pixel);
                float g = (float) green(pixel);
                float b = (float) blue(pixel);
                if (channels == 3) {
                    float ch0 = enableRGBColorFormat ? r : b;
                    float ch1 = g;
                    float ch2 = enableRGBColorFormat ? b : r;
                    int idx0 = i * width + j;
                    int idx1 = idx0 + width * height;
                    int idx2 = idx1 + width * height;
                    inputData[idx0] = (ch0 - inputMean[0]) / inputStd[0];
                    inputData[idx1] = (ch1 - inputMean[1]) / inputStd[1];
                    inputData[idx2] = (ch2 - inputMean[2]) / inputStd[2];
                } else { // channels = 1
                    float gray = (b + g + r) / 3.0f;
                    gray = gray - inputMean[0];
                    gray = gray / inputStd[0];
                    inputData[i * width + j] = gray;
                }
            }
        }
        inputTensor.setData(inputData);
        Date end = new Date();
        preprocessTime = (float) (end.getTime() - start.getTime());

        // inference
        super.runModel();

        // fetch output tensor
        Tensor outputTensor = getOutput(0);

        // post-process
        start = new Date();
        long outputShape[] = outputTensor.shape();
        long outputSize = 1;
        for (long s : outputShape) {
            outputSize *= s;
        }

        float feature[] = new float[512];
        float s=0;
        for(int i=0;i<outputSize;i++){
            float tmp = outputTensor.getFloatData()[i];
            s += tmp*tmp;
            feature[i] = tmp;
        }

        for(int i=0;i<outputSize;i++){
            feature[i] = feature[i]/s;
        }
        float scores[] = new float[50];

        for(int i=0;i<50;i++) {
            scores[i] = 0;
        }

        for(int i=0;i<50;i++) {
            float tmp = 0;
            for(int j=0;j<512;j++) {
                tmp = tmp + feature[j]*lableFeatures[i][j];
            }
            scores[i] = tmp;
        }

        float top1_score = -1024;
        int top1_index =0;
        for(int i=0;i<50;i++){
            if (scores[i] > top1_score) {
                top1_score = scores[i];
                top1_index = i;
            }
        }

        end = new Date();
        postprocessTime = (float) (end.getTime() - start.getTime());

        top1Result = "Top1: " + wordLabels.get(top1_index) + " - " + String.format("%.3f", scores[top1_index]*10+0.2);
        top2Result = "";
        top3Result = "";
        Log.i(TAG,top1Result());
        return true;
    }

    public Bitmap inputImage() {
        return inputImage;
    }

    public Bitmap outputImage() {
        return outputImage;
    }

    public String top1Result() {
        return top1Result;
    }

    public String top2Result() {
        return top2Result;
    }

    public String top3Result() {
        return top3Result;
    }

    public float preprocessTime() {
        return preprocessTime;
    }

    public float postprocessTime() {
        return postprocessTime;
    }

    public void setInputImage(Bitmap image) {
        if (image == null) {
            return;
        }
        // scale image to the size of input tensor
        Bitmap rgbaImage = image.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap scaleImage = Bitmap.createScaledBitmap(rgbaImage, (int) inputShape[3], (int) inputShape[2], true);
        this.inputImage = scaleImage;
    }

    public void setOutputImage(Bitmap image) {
        if (image == null) {
            return;
        }
        // scale image to the size of input tensor
        Bitmap rgbaImage = image.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap scaleImage = Bitmap.createScaledBitmap(rgbaImage, (int) outputShape[3], (int) outputShape[2], true);
        this.outputImage = scaleImage;
    }
}
