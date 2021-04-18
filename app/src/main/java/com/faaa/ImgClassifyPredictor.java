package com.faaa;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import com.baidu.paddle.lite.Tensor;

import java.io.InputStream;
import java.util.Date;
import java.util.Vector;

import static android.graphics.Color.blue;
import static android.graphics.Color.green;
import static android.graphics.Color.red;

public class ImgClassifyPredictor extends Predictor {
    private static final String TAG = ImgClassifyPredictor.class.getSimpleName();
    protected Vector<String> wordLabels = new Vector<String>();
    protected Boolean enableRGBColorFormat = true;
    protected long[] inputShape = new long[]{1, 3, 224, 224};
    protected float[] inputMean = new float[]{0.485f, 0.456f, 0.406f};
    protected float[] inputStd = new float[]{0.229f, 0.224f, 0.225f};
    protected Bitmap inputImage = null;
    protected String top1Result = "";
    protected String top2Result = "";
    protected String top3Result = "";
    protected float preprocessTime = 0;
    protected float postprocessTime = 0;

    public ImgClassifyPredictor() {
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
            for (String content : contents) {
                int first_space_pos = content.indexOf(" ");
                if (first_space_pos >= 0 && first_space_pos < content.length()) {
                    wordLabels.add(content.substring(first_space_pos));
                }
            }
            Log.i(TAG, "word label size: " + wordLabels.size());
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
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
                float r = (float) red(pixel) / 255.0f;
                float g = (float) green(pixel) / 255.0f;
                float b = (float) blue(pixel) / 255.0f;
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
        int[] max_index = new int[3]; // top3 indices
        double[] max_num = new double[3]; // top3 scores
        for (int i = 0; i < outputSize; i++) {
            float tmp = outputTensor.getFloatData()[i];
            int tmp_index = i;
            for (int j = 0; j < 3; j++) {
                if (tmp > max_num[j]) {
                    tmp_index += max_index[j];
                    max_index[j] = tmp_index - max_index[j];
                    tmp_index -= max_index[j];
                    tmp += max_num[j];
                    max_num[j] = tmp - max_num[j];
                    tmp -= max_num[j];
                }
            }
        }
        end = new Date();
        postprocessTime = (float) (end.getTime() - start.getTime());

        if (wordLabels.size() > 0) {
            top1Result = "Top1: " + wordLabels.get(max_index[0]) + " - " + String.format("%.3f", max_num[0]);
            top2Result = "Top2: " + wordLabels.get(max_index[1]) + " - " + String.format("%.3f", max_num[1]);
            top3Result = "Top3: " + wordLabels.get(max_index[2]) + " - " + String.format("%.3f", max_num[2]);
        }
        return true;
    }

    public Bitmap inputImage() {
        return inputImage;
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
}