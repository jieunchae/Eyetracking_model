package com.example.eyetracking_model;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapRegionDecoder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.BoundingPoly;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.Landmark;
import com.google.api.services.vision.v1.model.Vertex;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static org.opencv.core.CvType.CV_8UC3;
import static org.opencv.imgproc.Imgproc.INTER_AREA;
import static org.opencv.imgproc.Imgproc.resize;

public class MainActivity extends AppCompatActivity {

    private static final String CLOUD_VISION_API_KEY = "AIzaSyBugnqMo-RDSONebHimaB-L3f7fZVsZpb4";
    public static final String FILE_NAME = "temp.jpg";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";
    private static final int MAX_LABEL_RESULTS = 10;
    private static final int MAX_DIMENSION = 1200;

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;
    public static Bitmap nowBit;

    private ImageView mMainImage;
    private CameraBridgeViewBase mOpenCvCameraView;

    private static final String TAG2 = "opencv";
    private Net net;
    public Mat img_right_resized = null;
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mMainImage = findViewById(R.id.testface);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Button Camera = findViewById(R.id.camera);
        Camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCamera();
            }
        });

        Button Gallery = findViewById(R.id.gallery);
        Gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGalleryChooser();
            }
        });

    }

    @Override
    public void onResume()
    {
        super.onResume();

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "onResume :: Internal OpenCV library not found.");
            //penCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_2_0, this, mLoaderCallback);
            //myText.setText("FAILURE");
        } else {
            Log.d(TAG, "onResum :: OpenCV library found inside package. Using it!");
            //mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
            //myText.setText("SUCCESS");
        }
    }


    private static String saveFile(String filename, Context context) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = context.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d( TAG, "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }

        return pathDir;

    }

    private static String copyFile(String filename, Context context) {
        String baseDir = Environment.getExternalStorageDirectory().getPath();
        String pathDir = baseDir + File.separator + filename;

        AssetManager assetManager = context.getAssets();

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            Log.d( TAG, "copyFile :: 다음 경로로 파일복사 "+ pathDir);
            inputStream = assetManager.open(filename);
            outputStream = new FileOutputStream(pathDir);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            inputStream = null;
            outputStream.flush();
            outputStream.close();
            outputStream = null;
        } catch (Exception e) {
            Log.d(TAG, "copyFile :: 파일 복사 중 예외 발생 "+e.toString() );
        }

        return pathDir;

    }

    public float abs(float x){
        if(x < 0) return -x;
        else return x;
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    //mOpenCvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    public void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Select a photo"),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    public void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            uploadImage(photoUri);
        }
    }

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler(){
        public void handleMessage(Message msg){
            mMainImage.setImageBitmap(nowBit);
        }
    };


    private Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                MAX_DIMENSION);

                callCloudVision(bitmap,uri);
                //mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    private Vision.Images.Annotate prepareAnnotationRequest(final Bitmap bitmap) throws IOException {
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        VisionRequestInitializer requestInitializer =
                new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                    /**
                     * We override this so we can inject important identifying fields into the HTTP
                     * headers. This enables use of a restricted cloud platform API key.
                     */
                    @Override
                    protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                            throws IOException {
                        super.initializeVisionRequest(visionRequest);

                        String packageName = getPackageName();
                        visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                        String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                        visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                    }
                };

        Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
        builder.setVisionRequestInitializer(requestInitializer);

        Vision vision = builder.build();

        BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                new BatchAnnotateImagesRequest();
        batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
            AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();

            // Add the image
            Image base64EncodedImage = new Image();
            // Convert the bitmap to a JPEG
            // Just in case it's a format that Android understands but Cloud Vision
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
            byte[] imageBytes = byteArrayOutputStream.toByteArray();

            // Base64 encode the JPEG
            base64EncodedImage.encodeContent(imageBytes);
            annotateImageRequest.setImage(base64EncodedImage);

            // add the features we want
            annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                Feature labelDetection = new Feature();
                labelDetection.setType("FACE_DETECTION");
                labelDetection.setMaxResults(MAX_LABEL_RESULTS);
                add(labelDetection);
            }});

            // Add the list of one thing to the request
            add(annotateImageRequest);
        }});

        Vision.Images.Annotate annotateRequest =
                vision.images().annotate(batchAnnotateImagesRequest);
        // Due to a bug: requests to Vision API containing large images fail when GZipped.
        annotateRequest.setDisableGZipContent(true);
        Log.d(TAG, "created Cloud Vision request object, sending request");

        return annotateRequest;
    }

    private class LableDetectionTask extends AsyncTask<Object, Void, String> {
        private final WeakReference<MainActivity> mActivityWeakReference;
        private Vision.Images.Annotate mRequest;
        private Uri mUri;


        LableDetectionTask(MainActivity activity, Vision.Images.Annotate annotate,Uri output) {
            mActivityWeakReference = new WeakReference<>(activity);
            mRequest = annotate;
            mUri = output;
        }

        @Override
        protected String doInBackground(Object... params) {
            try {
                Log.d(TAG, "created Cloud Vision request object, sending request");
                BatchAnnotateImagesResponse response = mRequest.execute();
                return convertResponseToString(response,mUri);

            } catch (GoogleJsonResponseException e) {
                Log.d(TAG, "failed to make API request because " + e.getContent());
            } catch (IOException e) {
                Log.d(TAG, "failed to make API request because of other IOException " +
                        e.getMessage());
            }
            return "Cloud Vision API request failed. Check logs for details.";
        }

        protected void onPostExecute(String result) {
            MainActivity activity = mActivityWeakReference.get();
            if (activity != null && !activity.isFinishing()) {
                TextView imageDetail = activity.findViewById(R.id.image_details);
                imageDetail.setText(result);
            }
        }
    }
    // private static String convertResponseToString(BatchAnnotateImagesResponse response) {
    //        StringBuilder message = new StringBuilder("I found these things:\n\n");
    //
    //        List<EntityAnnotation> labels = response.getResponses().get(0).getLabelAnnotations();
    //        if (labels != null) {
    //            for (EntityAnnotation label : labels) {
    //                message.append(String.format(Locale.US, "%.3f: %s", label.getScore(), label.getDescription()));
    //                message.append("\n");
    //            }
    //        } else {
    //            message.append("nothing");
    //        }
    //
    //        return message.toString();
    //    }

    private String convertResponseToString(BatchAnnotateImagesResponse response, Uri output) {
        StringBuilder message = new StringBuilder("I found these things:\n\n");

        List<AnnotateImageResponse> responses = response.getResponses();
        for (AnnotateImageResponse res : responses) {
            // For full list of available annotations, see http://g.co/cloud/vision/docs
            for (FaceAnnotation annotation : res.getFaceAnnotations()) {
                BoundingPoly temp = annotation.getFdBoundingPoly();
                //message.append(temp.values());

                List<Vertex> vertices = temp.getVertices();
                Bitmap newBit = Bitmap.createBitmap(nowBit, vertices.get(0).getX(), vertices.get(0).getY(),
                        vertices.get(1).getX() - vertices.get(0).getX(), vertices.get(2).getY() - vertices.get(0).getY());

                int width = nowBit.getWidth();
                int height = nowBit.getHeight();
                int [][] grid = new int[25][25];

                for(int i=(int)(vertices.get(0).getY()/height*25); i<=(int)(vertices.get(2).getY()/height*25); i++){
                    for(int j=(int)(vertices.get(0).getX()/width*25); j<=(int)(vertices.get(1).getX()/width*25); j++){
                        grid[i][j] = 1;
                    }
                }

                Mat img1 = new Mat();
                Utils.bitmapToMat(newBit, img1);
                Mat img1_resized = new Mat();
                Size scaleSize = new Size(224, 224);
                resize(img1, img1_resized, scaleSize, 0, 0, INTER_AREA);

                Bitmap newBit2 = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(img1_resized, newBit2);
                //nowBit = newBit2;
                //message.append("!!!!!!!!!!!!");
                //message.append(vertices.get(0).getX());
                //mMainImage.setImageBitmap(newBit);

                //left_eye
                float left_of_lefteyebrow_x = 0;
                float left_of_lefteyebrow_y = 0;
                float right_of_lefteyebrow_x = 0;
                float right_of_lefteyebrow_y = 0;
                float lefteye_x = 0;
                float lefteye_y = 0;


                //right_eye
                float left_of_righteyebrow_x = 0;
                float left_of_righteyebrow_y = 0;
                float right_of_righteyebrow_x = 0;
                float right_of_righteyebrow_y = 0;
                float righteye_x = 0;
                float righteye_y = 0;

                for(Landmark landmark : annotation.getLandmarks()){
                    switch (landmark.getType()){
                        case "LEFT_EYE":
                            lefteye_x = landmark.getPosition().getX();
                            lefteye_y = landmark.getPosition().getY();
                        case "LEFT_OF_LEFT_EYEBROW" :
                            left_of_lefteyebrow_x = landmark.getPosition().getX();
                            left_of_lefteyebrow_y = landmark.getPosition().getY();
                        case "RIGHT_OF_LEFT_EYEBROW" :
                            right_of_lefteyebrow_x = landmark.getPosition().getX();
                            right_of_lefteyebrow_y = landmark.getPosition().getY();
                        case "RIGHT_EYE":
                            righteye_x = landmark.getPosition().getX();
                            righteye_y = landmark.getPosition().getY();
                        case "LEFT_OF_RIGHT_EYEBROW" :
                            left_of_righteyebrow_x = landmark.getPosition().getX();
                            left_of_righteyebrow_y = landmark.getPosition().getY();
                        case "RIGHT_OF_RIGHT_EYEBROW" :
                            right_of_righteyebrow_x = landmark.getPosition().getX();
                            right_of_righteyebrow_y = landmark.getPosition().getY();
                    }
                }

                Bitmap leftbit = Bitmap.createBitmap(nowBit, (int)left_of_lefteyebrow_x,(int)left_of_lefteyebrow_y,
                        (int)((right_of_lefteyebrow_x-left_of_lefteyebrow_x)),
                        (int)(lefteye_y-left_of_lefteyebrow_y)*2);

                //message.append(String.valueOf(left_of_lefteyebrow_x));
                //message.append("   ");
                //message.append(String.valueOf(left_of_lefteyebrow_y));
                //message.append("   ");
                //message.append(String.valueOf(right_of_lefteyebrow_x-left_of_lefteyebrow_x));
                //message.append("   ");
                //message.append(String.valueOf((lefteye_y-left_of_lefteyebrow_y)*2));
                //message.append("   ");
                Mat img_left = new Mat();
                Utils.bitmapToMat(leftbit, img_left);
                Mat img_left_resized = new Mat();
                resize(img_left, img_left_resized, scaleSize, 0, 0, INTER_AREA);

                Bitmap newBit3 = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(img_left_resized, newBit3);
                //nowBit = newBit3;

                Bitmap rightbit = Bitmap.createBitmap(nowBit, (int)left_of_righteyebrow_x,(int)left_of_righteyebrow_y,
                        (int)((right_of_righteyebrow_x-left_of_righteyebrow_x)),
                        (int)((lefteye_y-left_of_lefteyebrow_y)*2));

                /*message.append(String.valueOf(left_of_righteyebrow_y));
                message.append("   ");
                message.append(String.valueOf(right_of_righteyebrow_y));
                message.append("   ");
                message.append(String.valueOf((right_of_righteyebrow_x-left_of_righteyebrow_x)));
                message.append("   ");*/

                Mat img_right = new Mat();
                Utils.bitmapToMat(rightbit, img_right);
                img_right_resized = new Mat();
                resize(img_right, img_right_resized, scaleSize);

                Bitmap newBit4 = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(img_right_resized, newBit4);
                //nowBit = newBit4;


                //String pro = copyFile("itracker_train_val.prototxt", this);
                //String caff = copyFile("itracker_iter_92000.caffemodel", this);
                net = Dnn.readNetFromCaffe("./storage/emulated/0/itracker_train_val.prototxt","./storage/emulated/0/itracker_iter_92000.caffemodel");
                //net.setInput(img_right_resized, "image_right");
                //net.setInput(img_left_resized, "image_left");
                //net.setInput(img1_resized, "image_face");
                //net.setInput(grid,"facegrid");
                String detections = net.forward().toString();

                message.append(detections);
                message.append("   ");


                Message msg = handler.obtainMessage();
                handler.sendMessage(msg);
            }
        }


        return message.toString();
    }

    private void callCloudVision(final Bitmap bitmap, Uri uri) {
        // Switch text to loading
        //mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        try {
            AsyncTask<Object, Void, String> faceDetectionTask = new LableDetectionTask(this, prepareAnnotationRequest(bitmap),uri);
            nowBit = bitmap;
            faceDetectionTask.execute(bitmap);
        } catch (IOException e) {
            Log.d(TAG, "failed to make API request because of other IOException " +
                    e.getMessage());
        }
    }
    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */

    //여기서부턴 퍼미션 관련 메소드
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 200;


    protected void onCameraPermissionGranted() {
        List<? extends CameraBridgeViewBase> cameraViews = getCameraViewList();
        if (cameraViews == null) {
            return;
        }
        for (CameraBridgeViewBase cameraBridgeViewBase: cameraViews) {
            if (cameraBridgeViewBase != null) {
                cameraBridgeViewBase.setCameraPermissionGranted();
            }
        }
    }

    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(mOpenCvCameraView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        boolean havePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(CAMERA) != PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
                havePermission = false;
            }
        }
        if (havePermission) {
            onCameraPermissionGranted();
        }
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED){
            onCameraPermissionGranted();
        }else{
            showDialogForPermission("앱을 실행하려면 퍼미션을 허가하셔야합니다.");
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @TargetApi(Build.VERSION_CODES.M)
    private void showDialogForPermission(String msg) {

        AlertDialog.Builder builder = new AlertDialog.Builder( MainActivity.this);
        builder.setTitle("알림");
        builder.setMessage(msg);
        builder.setCancelable(false);
        builder.setPositiveButton("예", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id){
                requestPermissions(new String[]{CAMERA, WRITE_EXTERNAL_STORAGE}, CAMERA_PERMISSION_REQUEST_CODE);
            }
        });
        builder.setNegativeButton("아니오", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface arg0, int arg1) {
                finish();
            }
        });
        builder.create().show();
    }

    public native String stringFromJNI();
}
