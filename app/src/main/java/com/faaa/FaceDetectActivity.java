package com.faaa;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.Bundle;
import android.preference.PreferenceManager;

//import android.support.annotation.NonNull;
import androidx.annotation.NonNull;

//import android.support.v4.app.ActivityCompat;
import androidx.core.app.ActivityCompat;

//import android.support.v4.content.ContextCompat;
import androidx.core.content.ContextCompat;

//import android.support.v7.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;

import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.provider.MediaStore;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.io.InputStream;
import java.io.IOException;

public class FaceDetectActivity extends AppCompatActivity {
    private static final String TAG = FaceDetectActivity.class.getSimpleName();

    public static final int OPEN_GALLERY_REQUEST_CODE = 0;
    public static final int TAKE_PHOTO_REQUEST_CODE = 1;

    public static final int REQUEST_LOAD_MODEL = 0;
    public static final int REQUEST_RUN_MODEL = 1;
    public static final int RESPONSE_LOAD_MODEL_SUCCESSED = 0;
    public static final int RESPONSE_LOAD_MODEL_FAILED = 1;
    public static final int RESPONSE_RUN_MODEL_SUCCESSED = 2;
    public static final int RESPONSE_RUN_MODEL_FAILED = 3;


    protected TextView tvModelName;
    protected TextView tvInferenceTime;
    protected ImageView ivInputImage;
    protected TextView tvOutputResult;

    Bitmap bitmap;
    ImageView imageView;
    MTCNN mtcnn;

    private  Bitmap readFromAssets(String filename){
        Bitmap bitmap;
        AssetManager asm=getAssets();
        try {
            InputStream is=asm.open(filename);
            bitmap= BitmapFactory.decodeStream(is);
            is.close();
        } catch (IOException e) {
            Log.e("MainActivity","[*]failed to open "+filename);
            e.printStackTrace();
            return null;
        }
        return Utils.copyBitmap(bitmap); //返回mutable的image
    }
    public void processImage(){
        Bitmap bm= Utils.copyBitmap(bitmap);
        try {
            Vector<Box> boxes=mtcnn.detectFaces(bm,40);
            for (int i=0;i<boxes.size();i++){
                Utils.drawRect(bm,boxes.get(i).transform2Rect());
                Utils.drawPoints(bm,boxes.get(i).landmark);
            }
            Utils.drawText(bm,boxes.size());
            ivInputImage.setImageBitmap(bm);

        }catch (Exception e){
            Log.e(TAG,"[*]detect false:"+e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data==null)return;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), data.getData());
            ExifInterface open_exif = null;
            open_exif = new ExifInterface(this.getContentResolver().openInputStream(data.getData()));
            int open_orientation = open_exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            bitmap = Utils.rotateBitmap(bitmap, open_orientation);
            processImage();
        }catch (Exception e){
            Log.d("MainActivity","[*]"+e);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public void myMain(){
        imageView =(ImageView)findViewById(R.id.iv_input_image);

        bitmap=readFromAssets("face_detection/images/新垣结衣.jpg");
        mtcnn=new MTCNN(getAssets());
        processImage();
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent= new Intent(Intent.ACTION_PICK,null);
                intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*");
                startActivityForResult(intent, 0x1);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_action_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
            case R.id.open_gallery:
                if (requestAllPermissions()) {
                    openGallery();
                }
                break;
            case R.id.take_photo:
                if (requestAllPermissions()) {
                    takePhoto();
                }
                break;
            case R.id.settings:
                if (requestAllPermissions()) {
                    // make sure we have SDCard r&w permissions to load model from SDCard
                    //onSettingsClicked();
                }
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] != PackageManager.PERMISSION_GRANTED || grantResults[1] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean requestAllPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.CAMERA},
                    0);
            return false;
        }
        return true;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, null);
        intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
        startActivityForResult(intent, OPEN_GALLERY_REQUEST_CODE);
    }

    private void takePhoto() {
        Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePhotoIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePhotoIntent, TAKE_PHOTO_REQUEST_CODE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_detect);

        tvModelName = findViewById(R.id.tv_model_name);
        ivInputImage = findViewById(R.id.iv_input_image);
        tvInferenceTime = findViewById(R.id.tv_inference_time);
        tvOutputResult = findViewById(R.id.tv_output_result);
        tvOutputResult.setMovementMethod(ScrollingMovementMethod.getInstance());

        myMain();
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //boolean isLoaded = predictor.isLoaded();
        //menu.findItem(R.id.open_gallery).setEnabled(isLoaded);
        //menu.findItem(R.id.take_photo).setEnabled(isLoaded);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        /*if (predictor != null) {
            predictor.releaseModel();
        }*/
        super.onDestroy();
    }
}
