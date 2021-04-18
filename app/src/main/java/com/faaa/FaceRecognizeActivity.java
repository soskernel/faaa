package com.faaa;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

public class FaceRecognizeActivity extends CommonActivity {
    private static final String TAG = FaceRecognizeActivity.class.getSimpleName();

    protected TextView tvModelName;
    protected TextView tvInferenceTime;
    protected ImageView ivInputImage;
    protected TextView tvTop1Result;
    protected TextView tvTop2Result;
    protected TextView tvTop3Result;

    MTCNN mtcnn;

    // model config
    protected String modelPath = "";
    protected String labelPath = "";
    protected String imagePath = "";
    protected Boolean enableRGBColorFormat = true;
    protected long[] inputShape = new long[]{};
    protected float[] inputMean = new float[]{};
    protected float[] inputStd = new float[]{};

    protected FaceRecognizePredictor predictor = new FaceRecognizePredictor();

    /*
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data==null) {
            return;
        }
        try {
            Bitmap tmp_image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
            if (tmp_image != null && predictor.isLoaded()) {
                onImageChanged(tmp_image);
            }
        }catch (Exception e){
            Log.d("MainActivity","[*]"+e);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mtcnn=new MTCNN(getAssets());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_recognize);

        tvModelName = findViewById(R.id.tv_model_name);
        tvInferenceTime = findViewById(R.id.tv_inference_time);
        ivInputImage = findViewById(R.id.iv_input_image);
        tvTop1Result = findViewById(R.id.tv_top1_result);
        tvTop2Result = findViewById(R.id.tv_top2_result);
        tvTop3Result = findViewById(R.id.tv_top3_result);
        ivInputImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(Intent.ACTION_PICK,null);
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*");
                startActivityForResult(intent,OPEN_GALLERY_REQUEST_CODE);
            }
        });

/*        ivInputImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(Intent.ACTION_PICK,null);
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*");
                startActivityForResult(intent, 0x1);
            }
        });*/
    }

    @Override
    public boolean onLoadModel() {
        return super.onLoadModel() && predictor.init(FaceRecognizeActivity.this,
                modelPath,
                labelPath,
                enableRGBColorFormat,
                inputShape,
                inputMean,
                inputStd);
    }

    @Override
    public boolean onRunModel() {
        return super.onRunModel() && predictor.isLoaded() && predictor.runModel();
    }

    @Override
    public void onLoadModelSuccessed() {
        super.onLoadModelSuccessed();
        // load test image from path and run model
        try {
            if (imagePath.isEmpty()) {
                return;
            }
            Bitmap image = null;
            // read test image file from custom path if the first character of mode path is '/', otherwise read test
            // image file from assets
            if (!imagePath.substring(0, 1).equals("/")) {
                InputStream imageStream = getAssets().open(imagePath);
                image = BitmapFactory.decodeStream(imageStream);
            } else {
                if (!new File(imagePath).exists()) {
                    return;
                }
                image = BitmapFactory.decodeFile(imagePath);
            }
            if (image != null && predictor.isLoaded()) {
                Vector<Box> boxes=mtcnn.detectFaces(image,40);
                if (boxes.size() > 0) {
                    Box box = boxes.get(0);
                    Bitmap face = Bitmap.createBitmap(image, box.left(), box.top(), box.right() - box.left(), box.bottom() - box.top());
                    predictor.setInputImage(face);
                    predictor.setOutputImage(image);
                }
                else {
                    predictor.setInputImage(null);
                    predictor.setOutputImage(image);
                }
                runModel();
            }
        } catch (IOException e) {
            Toast.makeText(FaceRecognizeActivity.this, "Load image failed!", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    @Override
    public void onLoadModelFailed() {
        super.onLoadModelFailed();
    }

    @Override
    public void onRunModelSuccessed() {
        super.onRunModelSuccessed();
        // obtain results and update UI
        //tvModelName.setText("Model: " + predictor.modelName());
        tvInferenceTime.setText("Inference time: " + predictor.inferenceTime() + " ms");
        Bitmap inputImage = predictor.inputImage();
        Bitmap outputImage = predictor.outputImage();
        if (outputImage != null) {
            ivInputImage.setImageBitmap(outputImage);
        }
        tvTop1Result.setText(predictor.top1Result());
        //tvTop2Result.setText(predictor.top2Result());
        //tvTop3Result.setText(predictor.top3Result());
    }

    @Override
    public void onRunModelFailed() {
        super.onRunModelFailed();
    }


    @Override
    public void onImageChanged(Bitmap image) {
        super.onImageChanged(image);
        // rerun model if users pick test image from gallery or camera
        if (image != null && predictor.isLoaded()) {

            Vector<Box> boxes=mtcnn.detectFaces(image,40);
            if (boxes.size() > 0) {
                int max_area = -1;
                int max_area_index = -1;
                for(int i = 0;i < boxes.size();++i){
                    int area = boxes.get(i).area();
                    if(area > max_area) {
                        max_area = area;
                        max_area_index = i;
                    }
                }
                Box box = boxes.get(max_area_index);
                Bitmap face = Bitmap.createBitmap(image, box.left(), box.top(), box.right() - box.left(), box.bottom() - box.top());
                predictor.setInputImage(face);
                predictor.setOutputImage(image);

            }
            else {
                predictor.setInputImage(null);
                predictor.setOutputImage(null);
            }
            runModel();
        }
    }

    public void onSettingsClicked() {
        super.onSettingsClicked();
        startActivity(new Intent(FaceRecognizeActivity.this, FaceRecognizeSettingsActivity.class));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isLoaded = predictor.isLoaded();
        menu.findItem(R.id.open_gallery).setEnabled(isLoaded);
        menu.findItem(R.id.take_photo).setEnabled(isLoaded);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        boolean settingsChanged = false;
        String model_path = sharedPreferences.getString(getString(R.string.FRS_MODEL_PATH_KEY),
                getString(R.string.FRS_MODEL_PATH_DEFAULT));
        String label_path = sharedPreferences.getString(getString(R.string.FRS_LABEL_PATH_KEY),
                getString(R.string.FRS_LABEL_PATH_DEFAULT));
        String image_path = sharedPreferences.getString(getString(R.string.FRS_IMAGE_PATH_KEY),
                getString(R.string.FRS_IMAGE_PATH_DEFAULT));
        settingsChanged |= !model_path.equalsIgnoreCase(modelPath);
        settingsChanged |= !label_path.equalsIgnoreCase(labelPath);
        settingsChanged |= !image_path.equalsIgnoreCase(imagePath);
        Boolean enable_rgb_color_format =
                sharedPreferences.getBoolean(getString(R.string.FRS_ENABLE_RGB_COLOR_FORMAT_KEY),
                        Boolean.parseBoolean(getString(R.string.FRS_ENABLE_RGB_COLOR_FORMAT_DEFAULT)));
        settingsChanged |= enable_rgb_color_format != enableRGBColorFormat;
        long[] input_shape =
                Utils.parseLongsFromString(sharedPreferences.getString(getString(R.string.FRS_INPUT_SHAPE_KEY),
                        getString(R.string.FRS_INPUT_SHAPE_DEFAULT)), ",");
        float[] input_mean =
                Utils.parseFloatsFromString(sharedPreferences.getString(getString(R.string.FRS_INPUT_MEAN_KEY),
                        getString(R.string.FRS_INPUT_MEAN_DEFAULT)), ",");
        float[] input_std =
                Utils.parseFloatsFromString(sharedPreferences.getString(getString(R.string.FRS_INPUT_STD_KEY)
                        , getString(R.string.FRS_INPUT_STD_DEFAULT)), ",");
        settingsChanged |= input_shape.length != inputShape.length;
        settingsChanged |= input_mean.length != inputMean.length;
        settingsChanged |= input_std.length != inputStd.length;
        if (!settingsChanged) {
            for (int i = 0; i < input_shape.length; i++) {
                settingsChanged |= input_shape[i] != inputShape[i];
            }
            for (int i = 0; i < input_mean.length; i++) {
                settingsChanged |= input_mean[i] != inputMean[i];
            }
            for (int i = 0; i < input_std.length; i++) {
                settingsChanged |= input_std[i] != inputStd[i];
            }
        }
        if (settingsChanged) {
            modelPath = model_path;
            labelPath = label_path;
            imagePath = image_path;
            enableRGBColorFormat = enable_rgb_color_format;
            inputShape = input_shape;
            inputMean = input_mean;
            inputStd = input_std;
            // reload model if configure has been changed
            loadModel();
        }
    }


    @Override
    protected void onDestroy() {
        if (predictor != null) {
            predictor.releaseModel();
        }
        super.onDestroy();
    }
}
