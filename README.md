# Camera App with Roboflow Food Classification

This Android app allows you to take photos with your device camera and classify food items using Roboflow's AI-powered API. The classification results are displayed in an intuitive popup dialog.

## Features

### 🍽️ Food Classification
- **Real-time Camera Preview**: Live camera feed using Camera2 API
- **High-Quality Image Capture**: Captures images with proper orientation handling
- **AI-Powered Classification**: Uses Roboflow's food classification model
- **Smart Upload Methods**: Automatically tries multipart upload, falls back to base64 if needed
- **User-Friendly Results**: Displays classification results in a clear popup dialog

### 📱 App Features
- **Modern UI**: Clean, intuitive interface with progress indicators
- **Error Handling**: Comprehensive error handling with helpful user messages
- **Offline-Ready**: Camera functionality works without internet (classification requires internet)
- **Responsive Design**: Adapts to different screen sizes and orientations

## Configuration

### How to Use Your Own Model
1. Create or find a food classification model on [Roboflow](https://roboflow.com)
2. Deploy your model and get the API endpoint
3. Update the `MODEL_ENDPOINT` constant in `MainActivity.java`
4. Optionally update the `ROBOFLOW_API_KEY` if using a different account

## App Usage

### 📸 Taking Photos
1. Launch the app and grant camera permissions
2. Point your camera at food items
3. Tap "Take Picture" to capture an image
4. The captured image will appear as a thumbnail

### 🤖 Food Classification
1. After taking a photo, the "Classify Food" button becomes enabled
2. Tap "Classify Food" to send the image to Roboflow
3. A progress indicator shows while processing
4. Classification results appear in a popup dialog with:
   - Detected food items with confidence scores
   - Multiple predictions (if applicable)
   - Option to take another photo or close

## Technical Details

### 🔧 Architecture
- **Camera2 API**: Modern camera implementation with full control
- **OkHttp**: Robust HTTP client for API calls
- **Gson**: JSON parsing for API responses
- **Material Design**: Following Android design guidelines

### 🌐 Network Handling
- **Dual Upload Methods**: Tries multipart form data first, falls back to base64
- **Automatic Retry**: Intelligently retries with different formats on certain errors
- **Comprehensive Error Handling**: User-friendly error messages for different scenarios

### 📊 Supported Response Formats
The app handles multiple Roboflow response formats:
- **Object Detection**: Multiple predictions with confidence scores
- **Classification**: Single top prediction with confidence
- **Multi-label**: Multiple predicted classes

## Permissions Required

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## Build and Run

### Prerequisites
- Android Studio Arctic Fox or later
- Android device/emulator with API level 24+ (Android 7.0)
- Camera-enabled device for best experience

### Steps
1. Clone or download this project
2. Open in Android Studio
3. Update Roboflow configuration if needed
4. Build and run on your device/emulator
5. Grant camera permissions when prompted
6. Start taking and classifying food photos!

## Troubleshooting

### Common Issues

#### 🔧 Camera Issues
- **Permission Denied**: Ensure camera permissions are granted in device settings
- **Camera Preview Not Working**: Check if device has a camera and try restarting the app

#### 🌐 Network Issues
- **Classification Fails**: Check internet connection and Roboflow API status
- **Timeout Errors**: Ensure stable internet connection
- **Authentication Errors**: Verify your Roboflow API key and model endpoint

#### 📱 App Issues
- **App Crashes**: Check device compatibility (API level 24+)
- **UI Issues**: Try different device orientations

### Debug Information
- All API responses are logged with tag "MainActivity"
- Use Android Studio's Logcat to view detailed error messages
- Enable network logging for detailed request/response inspection

## Future Enhancements

### Possible Improvements
- **Offline Caching**: Cache recent classifications for offline viewing
- **History**: Save classification history with timestamps
- **Nutrition Info**: Integrate with nutrition APIs for detailed food information
- **Multiple Photos**: Batch classification for multiple food items
- **Custom Models**: Easy switching between different food classification models
