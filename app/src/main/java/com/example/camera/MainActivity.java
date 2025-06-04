package com.example.camera;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final String ROBOFLOW_API_URL = "https://detect.roboflow.com";
    private static final String ROBOFLOW_API_KEY = "dTilJcee6cDC3qrVQKFH";
    private static final String MODEL_ENDPOINT = "/traditional-cake-8jfpu/1"; 
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");
    
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private TextureView textureView;
    private ImageView imageView;
    private Button takePictureButton;
    private Button classifyButton;
    private ProgressBar classifyProgressBar;
    
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private Bitmap captureBitmap;
    private OkHttpClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        textureView = findViewById(R.id.texture_view);
        imageView = findViewById(R.id.image_view_thumbnail);
        takePictureButton = findViewById(R.id.button_take_picture);
        classifyButton = findViewById(R.id.button_upload);
        classifyProgressBar = findViewById(R.id.upload_progress);
        
        // Initialize OkHttpClient for network requests
        client = new OkHttpClient();
        
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        
        takePictureButton.setOnClickListener(view -> takePicture());
        
        classifyButton.setOnClickListener(view -> uploadImage());
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            // Open the camera when texture is available
            openCamera();
        }
        
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform image to fit the changed size
        }
        
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
        }
        
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    private void openCamera() {
        Log.e(TAG, "Opening camera");
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = manager.getCameraIdList()[0]; // Use back camera by default
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), textureView.getWidth(), textureView.getHeight());
            
            // Check camera permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                return;
            }
            
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void createCameraPreview() {
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);
            
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            
            cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_LONG).show();
                finish();
            } else {
                openCamera();
            }
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }
    
    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void takePicture() {
        if (null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes = null;
            if (characteristics != null) {
                jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.JPEG);
            }
            
            int width = 640;
            int height = 480;
            if (jpegSizes != null && jpegSizes.length > 0) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }
            
            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        captureBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        
                        runOnUiThread(() -> {
                            imageView.setImageBitmap(captureBitmap);
                            imageView.setVisibility(View.VISIBLE);
                            // Enable classify button now that we have an image
                            classifyButton.setEnabled(true);
                            Toast.makeText(MainActivity.this, "Picture captured!", Toast.LENGTH_SHORT).show();
                        });
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
            };
            
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    createCameraPreview(); // Continue preview after capture
                }
            };
            
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    // Upload captured image to Roboflow for food classification
    private void uploadImage() {
        if (captureBitmap == null) {
            Toast.makeText(this, "No image to upload", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Show progress
        classifyProgressBar.setVisibility(View.VISIBLE);
        classifyButton.setEnabled(false);
        
        // Convert bitmap to byte array
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        captureBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();
        
        // Try both multipart and base64 approaches
        uploadImageMultipart(imageBytes);
    }
    
    // Method for multipart form upload (most common for Roboflow)
    private void uploadImageMultipart(byte[] imageBytes) {
        // Create RequestBody for the image
        RequestBody imageBody = RequestBody.create(imageBytes, MEDIA_TYPE_JPEG);
        
        // Create multipart body for Roboflow API
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "image.jpg", imageBody)
                .build();
        
        // Create request with Roboflow API endpoint and authentication
        String fullUrl = ROBOFLOW_API_URL + MODEL_ENDPOINT + "?api_key=" + ROBOFLOW_API_KEY;
        Request request = new Request.Builder()
                .url(fullUrl)
                .post(requestBody)
                .addHeader("Content-Type", "multipart/form-data")
                .build();
        
        executeClassificationRequest(request);
    }
    
    // Alternative method for base64 upload (backup approach)
    private void uploadImageBase64(byte[] imageBytes) {
        try {
            // Convert to base64
            String base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
            
            // Create JSON body with base64 image
            JsonObject jsonBody = new JsonObject();
            jsonBody.addProperty("image", base64Image);
            
            RequestBody requestBody = RequestBody.create(
                jsonBody.toString(), 
                MediaType.parse("application/json")
            );
            
            String fullUrl = ROBOFLOW_API_URL + MODEL_ENDPOINT + "?api_key=" + ROBOFLOW_API_KEY;
            Request request = new Request.Builder()
                    .url(fullUrl)
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build();
            
            executeClassificationRequest(request);
            
        } catch (Exception e) {
            Log.e(TAG, "Error creating base64 request", e);
            handleClassificationError(e.getMessage());
        }
    }
    
    // Common method to execute the classification request
    private void executeClassificationRequest(Request request) {
        // Execute the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                handleClassificationError(e.getMessage());
            }
            
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String responseBody = response.body().string();
                runOnUiThread(() -> {
                    classifyProgressBar.setVisibility(View.GONE);
                    classifyButton.setEnabled(true);
                    
                    if (response.isSuccessful()) {
                        // Parse and display the classification results
                        displayClassificationResults(responseBody);
                    } else {
                        Log.e(TAG, "Classification failed: " + responseBody);
                        
                        // If multipart failed, try base64 as fallback
                        if (response.code() == 400 || response.code() == 415) {
                            Log.d(TAG, "Trying base64 upload as fallback");
                            runOnUiThread(() -> {
                                classifyProgressBar.setVisibility(View.VISIBLE);
                                classifyButton.setEnabled(false);
                            });
                            
                            // Convert bitmap to bytes again for base64 upload
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            captureBitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                            byte[] imageBytes = byteArrayOutputStream.toByteArray();
                            uploadImageBase64(imageBytes);
                        } else {
                            Toast.makeText(MainActivity.this, 
                                    "Classification error: " + responseBody, 
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }
    
    // Helper method to handle classification errors
    private void handleClassificationError(String errorMessage) {
        runOnUiThread(() -> {
            classifyProgressBar.setVisibility(View.GONE);
            classifyButton.setEnabled(true);
            
            // Show error in popup dialog instead of toast
            showClassificationDialog("❌ Classification Failed", 
                    "Unable to classify the image.\n\nError: " + errorMessage + 
                    "\n\nPlease check your internet connection and try again.");
        });
    }
      // Helper method to display classification results in a popup dialog
    private void displayClassificationResults(String jsonResponse) {
        try {
            Gson gson = new Gson();
            JsonObject response = gson.fromJson(jsonResponse, JsonObject.class);
            
            // Log the full response for debugging
            Log.d(TAG, "Roboflow Response: " + jsonResponse);
            
            String title = "🍽️ Food Classification Results";
            String message = "";
            final double CONFIDENCE_THRESHOLD = 0.7; // Minimum confidence level
            
            // Handle different response formats based on Roboflow model type
            if (response.has("predictions")) {
                // Object detection/classification response
                JsonArray predictions = response.getAsJsonArray("predictions");
                if (predictions.size() > 0) {
                    StringBuilder resultsBuilder = new StringBuilder();
                    boolean hasHighConfidenceResults = false;
                    
                    // Filter predictions by confidence threshold
                    for (int i = 0; i < predictions.size(); i++) {
                        JsonObject prediction = predictions.get(i).getAsJsonObject();
                        double confidence = prediction.get("confidence").getAsDouble();
                        
                        if (confidence >= CONFIDENCE_THRESHOLD) {
                            if (!hasHighConfidenceResults) {
                                resultsBuilder.append("Detected food items:\n\n");
                                hasHighConfidenceResults = true;
                            }
                            
                            String className = prediction.get("class").getAsString();
                            resultsBuilder.append(String.format("• %s\n", formatFoodName(className)));
                            resultsBuilder.append(String.format("   Confidence: %.1f%%\n\n", confidence * 100));
                        }
                    }
                    
                    if (hasHighConfidenceResults) {
                        message = resultsBuilder.toString();
                    } else {
                        message = "No confident food detection found.\n\nThe model couldn't identify the food with sufficient confidence (70%+).\n\nTry taking another photo with:\n• Better lighting\n• Clearer view of the food\n• Different angle";
                        title = "⚠️ Low Confidence Detection";
                    }
                } else {
                    message = "No food detected in the image.\n\nTry taking another photo with better lighting or a different angle.";
                    title = "❌ No Food Detected";
                }
            } else if (response.has("top")) {
                // Classification response format
                String topClass = response.get("top").getAsString();
                double confidence = response.get("confidence").getAsDouble();
                
                if (confidence >= CONFIDENCE_THRESHOLD) {
                    message = String.format("Classified as: %s\n\nConfidence: %.1f%%", 
                            formatFoodName(topClass), confidence * 100);
                } else {
                    message = String.format("Low confidence detection: %s (%.1f%%)\n\nThe model couldn't identify the food with sufficient confidence (70%+).\n\nTry taking another photo with better lighting or a clearer view.", 
                            formatFoodName(topClass), confidence * 100);
                    title = "⚠️ Low Confidence Detection";
                }
            }else if (response.has("predicted_classes")) {
                // Another possible classification format
                JsonArray predictedClasses = response.getAsJsonArray("predicted_classes");
                if (predictedClasses.size() > 0) {
                    StringBuilder resultsBuilder = new StringBuilder();
                    resultsBuilder.append("Predicted food types:\n\n");
                    
                    for (int i = 0; i < Math.min(3, predictedClasses.size()); i++) {
                        String className = predictedClasses.get(i).getAsString();
                        resultsBuilder.append(String.format("• %s\n", formatFoodName(className)));
                    }
                    
                    message = resultsBuilder.toString();
                } else {
                    message = "No classification results available.\n\nPlease try again with a clearer image.";
                    title = "❌ Classification Failed";
                }
            } else {
                // Fallback - show formatted response
                message = "Classification completed successfully!\n\nRaw response data available in logs.";
                Log.i(TAG, "Raw classification response: " + jsonResponse);
            }
            
            // Show the popup dialog
            showClassificationDialog(title, message);
            
        } catch (Exception e) {
            Log.e(TAG, "Error parsing classification results", e);
            showClassificationDialog("⚠️ Error", 
                    "Classification completed but couldn't parse the results.\n\nPlease check the logs for more details.");
        }
    }
    
    // Helper method to format food names for better display
    private String formatFoodName(String foodName) {
        if (foodName == null || foodName.isEmpty()) {
            return "Unknown Food";
        }
        
        // Convert underscores to spaces and capitalize first letter of each word
        String formatted = foodName.replace("_", " ").replace("-", " ");
        String[] words = formatted.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > 0) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
                result.append(" ");
            }
        }
        
        return result.toString().trim();
    }
    
    // Method to show classification results in a popup dialog
    private void showClassificationDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton("Take Another Photo", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Reset the image view and prepare for another photo
                        imageView.setVisibility(View.GONE);
                        classifyButton.setEnabled(false);
                        captureBitmap = null;
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                })
                .setCancelable(true);
        
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    
    // Helper method to choose optimal preview size
    private Size chooseOptimalSize(Size[] choices, int width, int height) {
        List<Size> bigEnough = new ArrayList<>();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * height / width &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }
    
    // Compare sizes by area
    class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}