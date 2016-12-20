package com.example.weis.cv_grabber;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
import static android.provider.MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    /* stupid android 6.0 permission management sucks */
    int MY_PERMISSION_REQUEST = 1;

    int _framerate = 10;
    long _ts_lastframe = 0;
    static String _last_fname = "";

    boolean recording = false;
    private Handler handler = new Handler();
    FileOutputStream fileos;
    XmlSerializer serializer = Xml.newSerializer();
    static File _mediaStorageDir; // sequence directory

    android.hardware.Camera.Size p_picture_size;
    Camera.Parameters parameters;

    TextView textview_coords;
    LocationManager mLocationManager;
    Criteria criteria = new Criteria();
    String bestProvider;
    android.location.Location _loc;
    boolean _pic_returned = true;

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            if (_pic_returned == true) { // check if picture-take-callback has returned already
                if (System.currentTimeMillis() - _ts_lastframe >= (1. / _framerate) * 1000.) {
                    Log.d("Timecalc", "Diff: " + (System.currentTimeMillis() - _ts_lastframe));
                    _pic_returned = false;
                    try {
                        mCamera.takePicture(null, null, mPicture);
                    }catch(Exception e){
                        Log.e("TakePicture", "Exception: ", e);
                    }
                    bestProvider = mLocationManager.getBestProvider(criteria, false);
                    _loc = mLocationManager.getLastKnownLocation(bestProvider);

                    textview_coords.setText("Coordinates: " + _loc.getLatitude() + ", " + _loc.getLongitude() + ", Acc:" + _loc.getAccuracy());
                    try {
                        serializer.startTag(null, "Frame");
                        serializer.attribute(null, "uri", _last_fname);
                        serializer.attribute(null, "lat", ""+_loc.getLatitude());
                        serializer.attribute(null, "lon", ""+_loc.getLongitude());
                        serializer.attribute(null, "acc", ""+ _loc.getAccuracy());
                        serializer.endTag(null, "Frame");
                        serializer.flush();
                    }catch(IOException e){
                        Log.e("serializer", "IOException: " + e);
                    }
                    _ts_lastframe = System.currentTimeMillis();
                }
            }
            handler.postDelayed(runnable, 1);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("onResume", "---------------------------- startMain");
        startMain();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            Log.d("onRequestPermisstion", "---------------------------- startMain");
            startMain();
        }
    };

    final LocationListener locationListener = new LocationListener() {
        public void onLocationChanged(Location location) {}
        public void onProviderDisabled(String provider){}
        public void onProviderEnabled(String provider){ }
        public void onStatusChanged(String provider, int status, Bundle extras){ }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final Button settingsButton = (Button) findViewById(R.id.button_settings);
        settingsButton.setOnClickListener(
                new android.view.View.OnClickListener(){
                    @Override
                    public void onClick(View v){
                        Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                        if (mCamera != null) {
                            mCamera.stopPreview();
                            mCamera.release();
                            mCamera = null;
                        }
                        startActivity(intent);
                    }
                }
        );

        final Button captureButton = (Button) findViewById(R.id.button_capture);
        captureButton.setBackgroundColor(Color.GREEN);

        textview_coords = (TextView) findViewById(R.id.textview_coords);

        mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        bestProvider = mLocationManager.getBestProvider(criteria, false);
        mLocationManager.requestLocationUpdates(bestProvider, 10,10, locationListener);


        captureButton.setOnClickListener(
                new android.view.View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!recording) {
                            // get an image from the camera
                            try {
                                setSequenceDirectory();
                                fileos = new FileOutputStream(getOutputMediaFile("xml"));
                                serializer.setOutput(fileos, "UTF-8");
                                serializer.startDocument(null, Boolean.valueOf(true));
                                serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
                                serializer.startTag(null, "sequence");
                            }catch(FileNotFoundException e){
                                Log.e("fileos", "Exception: file not found");
                            }catch(IOException e){
                                Log.e("serializer", "IOException: " + e);
                            }

                            handler.postDelayed(runnable, 100);
                            recording = true;
                            captureButton.setBackgroundColor(Color.RED);
                        }else{
                            handler.removeCallbacks(runnable);
                            recording = false;
                            try {
                                serializer.startTag(null, "sequence");
                                serializer.flush();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            try {
                                fileos.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            captureButton.setBackgroundColor(Color.GREEN);
                        }
                    }
                }
        );


        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Log.d("main", "Requesting permissions");

            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE }, MY_PERMISSION_REQUEST);
        }else {
            Log.d("main", "Already have permission, not asking");
            Log.d("gotPermissions", "---------------------------- startMain");
            startMain();
        }
    }

    /* start camera preview and enable button */
    private void startMain(){
        mCamera = getCameraInstance();

        parameters = mCamera.getParameters();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String selected_res = prefs.getString("pref_resolutions", ""); // this gives the value
        Log.d("---", "Selected resolution index was: |" + selected_res + "|" );
        if (selected_res != "") {
            final List<android.hardware.Camera.Size> sizes = parameters.getSupportedPictureSizes();
            Integer width = sizes.get(Integer.parseInt(selected_res)).width;
            Integer height = sizes.get(Integer.parseInt(selected_res)).height;
            parameters.setPictureSize(width, height);
            mCamera.setParameters(parameters);
            Log.d("Settings", "Set image resolution to " + width + "x" + height);
        }

        String selected_fr = prefs.getString("pref_framerates", "");
        if (selected_fr != ""){
            _framerate = Integer.parseInt(selected_fr);
            Log.d("Settings", "Set framerate to " + _framerate);
        }

        // Create Preview view and set it as the content of our activity.
        // this HAS to be done in order to able to take pictures (WTF?!)
        mPreview = new CameraPreview(this, mCamera);
        RelativeLayout preview = (RelativeLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        /*********************************** GPS ************************/
        mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        //mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 10, mLocationListener);

    }


    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile("jpg");
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
                _pic_returned = true;
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
            /* restart the picture-taking from here,
             * b/c we have to wait for the callback to finish
             */
        }
    };

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    private Camera mCamera;
    private CameraPreview mPreview;

    private static void setSequenceDirectory(){
        long timeStamp = System.currentTimeMillis();
        _mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "multisensorgrabber_" + timeStamp);
        if (! _mediaStorageDir.exists()){
            if (! _mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
            }
        }
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile(String ending){
        //String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        long timeStamp = System.currentTimeMillis();
        File mediaFile;
        if (ending == "jpg"){
            mediaFile = new File(_mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
            _last_fname = mediaFile.getAbsolutePath();
        } else if(ending == "xml") {
            mediaFile = new File(_mediaStorageDir.getPath() + File.separator +
                    timeStamp + ".xml");
        } else {
            return null;
        }

        return mediaFile;
    }


}
