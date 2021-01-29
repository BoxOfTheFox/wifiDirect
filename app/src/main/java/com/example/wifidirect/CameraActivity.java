package com.example.wifidirect;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CameraActivity extends AppCompatActivity {

    private static final int CAMERA_CAPTURE_IMAGE_REQUEST_CODE = 100;

    public static final String KEY_IMAGE_STORAGE_PATH = "image_path";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    public static final int BITMAP_SAMPLE_SIZE = 8;

    public static final String GALLERY_DIRECTORY_NAME = "Kamera";

    public static final String IMAGE_EXTENSION = "jpg";
    public static final String VIDEO_EXTENSION = "mp4";

    private static String imageStoragePath;

    private TextView txtDescription;
    private ImageView imgPreview;
    private VideoView videoPreview;
    private Button btnCapturePicture;

    private String groupOwnerAddress;
    private String isGroupOwner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // sprawdz dostepnosc kamery
        if (!CameraUtils.isDeviceSupportCamera(getApplicationContext())) {
            Toast.makeText(getApplicationContext(),
                    "Brak wsparcia kamery",
                    Toast.LENGTH_LONG).show();
            finish();
        }

        txtDescription = findViewById(R.id.txt_desc);
        imgPreview = findViewById(R.id.imgPreview);
        videoPreview = findViewById(R.id.videoPreview);
        btnCapturePicture = findViewById(R.id.btnCapturePicture);

        btnCapturePicture.setOnClickListener(v -> {
            if (CameraUtils.checkPermissions(getApplicationContext())) {
                captureImage();
            } else {
                requestCameraPermission(MEDIA_TYPE_IMAGE);
            }
        });

        Intent intent = getIntent();
        String[] vals = intent.getStringExtra("IP").split(" ");
        groupOwnerAddress = vals[0];
        isGroupOwner = vals[1];
        Log.e(MainActivity.TAG,groupOwnerAddress + " " + isGroupOwner);

        restoreFromBundle(savedInstanceState);
    }

    private void restoreFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Log.e(MainActivity.TAG,"savedInstanceState is not null");
            if (savedInstanceState.containsKey(KEY_IMAGE_STORAGE_PATH)) {
                imageStoragePath = savedInstanceState.getString(KEY_IMAGE_STORAGE_PATH);
                if (!TextUtils.isEmpty(imageStoragePath)) {
                    if (imageStoragePath.substring(imageStoragePath.lastIndexOf(".")).equals("." + IMAGE_EXTENSION)) {
                        previewCapturedImage();
                    }
                }
            }
        }
    }

    /**
     * Ustalenie pozwolen
     */
    private void requestCameraPermission(final int type) {
        Dexter.withActivity(this)
                .withPermissions(Manifest.permission.CAMERA)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {

                            if (type == MEDIA_TYPE_IMAGE) {
                                captureImage();
                            }

                        } else if (report.isAnyPermissionPermanentlyDenied()) {
                            showPermissionsAlert();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }


    /**
     * W celu zrobienia zdjecia zostaje uruchomiona aplikacja aparatu
     */
    private void captureImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        File file = CameraUtils.getOutputMediaFile(MEDIA_TYPE_IMAGE, this);
        if (file != null) {
            imageStoragePath = file.getAbsolutePath();
        }

        Uri fileUri = CameraUtils.getOutputMediaFileUri(getApplicationContext(), file);

        intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);

        startActivityForResult(intent, CAMERA_CAPTURE_IMAGE_REQUEST_CODE);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(KEY_IMAGE_STORAGE_PATH, imageStoragePath);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        imageStoragePath = savedInstanceState.getString(KEY_IMAGE_STORAGE_PATH);
    }

    /**
     * Aktywnosc wywolywana po zamknieciu kamery
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_CAPTURE_IMAGE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                CameraUtils.refreshGallery(getApplicationContext(), imageStoragePath);

                previewCapturedImage();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(),
                        "Anulowano robienie zdjecia", Toast.LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(getApplicationContext(),
                        "Blad w trakcie robienia zdjecia", Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    /**
     * Pokaz obrazek
     */
    private void previewCapturedImage() {
        try {
            txtDescription.setVisibility(View.GONE);
            videoPreview.setVisibility(View.GONE);

            imgPreview.setVisibility(View.VISIBLE);

            Bitmap bitmap = CameraUtils.optimizeBitmap(BITMAP_SAMPLE_SIZE, imageStoragePath);

            imgPreview.setImageBitmap(bitmap);

        } catch (NullPointerException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG,"error in previewImage");
        }
        if (isGroupOwner.equals("true")){
            Executors.newSingleThreadScheduledExecutor().schedule(this::runServer,0,TimeUnit.MILLISECONDS);
        }else{
            Executors.newSingleThreadScheduledExecutor().schedule(this::runClient,0,TimeUnit.MILLISECONDS);
        }
    }

    private void showPermissionsAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Konieczne sa uprawnienia!")
                .setMessage("Kamera potrzebuje kilku uprawnien do poprawnego dzialania. Nadaj je w ustawieniach")
                .setPositiveButton("Idz do ustawien", (dialog, which) -> CameraUtils.openSettings(getApplicationContext()))
                .setNegativeButton("Anuluj", (dialog, which) -> {
                }).show();
    }

    private void runServer() {
        /*
         * Create a server socket and wait for client connections. This
         * call blocks until a connection is accepted from a client
         */
        Socket socket = null;
        byte[] buf = new byte[1024];
        int len;
        try {
            Log.e(MainActivity.TAG, "Server socket start in Camera");
            ServerSocket serverSocket = new ServerSocket(8888);
            socket = serverSocket.accept();
            Log.e(MainActivity.TAG, "Server socket success in Camera");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG, "Server error in Camera");
        }

        try {
            OutputStream outputStream = Objects.requireNonNull(socket).getOutputStream();
            ContentResolver cr = getContentResolver();
            InputStream inputStream = null;
            inputStream = cr.openInputStream(Uri.fromFile(new File(imageStoragePath)));
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG,"Error sending picture");
        }

        /*
         * Clean up any open sockets when done
         * transferring or if an exception occurred.
         */
        finally {
            if (socket != null) {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        //catch logic
                    }
                }
            }
        }

    }

    private void runClient() {
        Socket socket = new Socket();
        byte[] buf = new byte[1024];
        int len;

        try {
            Log.e(MainActivity.TAG, "Client socket start in Camera");
            socket.bind(null);
            socket.connect((new InetSocketAddress(InetAddress.getByName(groupOwnerAddress), 8888)));
            Log.e(MainActivity.TAG, "Client socket success in Camera");
        } catch (ConnectException exception) {
            Log.e(MainActivity.TAG, "No connection, retry in Camera");
            Executors.newSingleThreadScheduledExecutor().schedule(this::runClient, 1, TimeUnit.SECONDS);
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG, "Client error in Camera");
        }

        try {
            OutputStream outputStream = socket.getOutputStream();
            ContentResolver cr = getContentResolver();
            InputStream inputStream = null;
            inputStream = cr.openInputStream(Uri.fromFile(new File(imageStoragePath)));
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(MainActivity.TAG,"Error sending picture");
        }

        /*
         * Clean up any open sockets when done
         * transferring or if an exception occurred.
         */
        finally {
            if (socket != null) {
                if (socket.isConnected()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        //catch logic
                    }
                }
            }
        }

    }
}