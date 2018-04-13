package in.iitd.assistech.smartband;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import com.sounds.ClassifySound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

import static android.app.ProgressDialog.show;
import static com.sounds.ClassifySound.M;
import static com.sounds.ClassifySound.numOutput;
import static in.iitd.assistech.smartband.HelperFunctions.getClassifyProb;
import static in.iitd.assistech.smartband.MainActivity.adapter;
import static java.lang.Thread.sleep;

/**
 * Created by nikhil on 20/2/18.
 */

public class BluetoothService extends Service {


    private static BluetoothAdapter mBluetoothAdapter;
    private ConnectThread connectThread;
    public static BluetoothDevice bluetoothDevice;

    private static final String TAG = "BluetoothService";
    private static final String DeviceName = "HC-05";
    private static final int NOTIFICATION_ID_START = 1;
    private static final int NOTIFICATION_ID_RESULT = 1;

    /*
    * Variables to maintain time difference between successive detection of loud sounds
    * */
    private long prevTimeSoundLabelSent;
    private final long timeDiffToBeMaintained = 10000;

    private static BluetoothService instance = null;

    // Bluetooth variables
    private BluetoothSocket mmSocket;
    private BluetoothDevice mmDevice;
    private InputStream mmInStream;
    private OutputStream mmOutStream;
    private byte[] mmBuffer; // mmBuffer store for the stream

    ProgressDialog dialogDetectThreadEnd, dialogDetectThreadStart;

    /*
    * Check if the instance of this bluetooth service is already created
    * */
    public static boolean isInstanceCreated() {
        return instance != null;
    }

    //Now the receiver which will receive this Intent
    public class ActionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {

            String action=intent.getStringExtra("action");

            Toast.makeText(context,action,Toast.LENGTH_SHORT).show();

            if(action.equals("Correct Detection")){
                // TODO: send data to server
            }
            else if(action.equals("Wrong Detection")){
                // TODO: send data to server

            }
            //This is used to close the notification tray
            Intent it = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(it);
        }

    }

    static{
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /*
    * Check if the instance of this bluetooth service is already created
    * */
    public static BluetoothService getInstance() {
        return instance;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        prevTimeSoundLabelSent = 0;
        instance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(MainActivity.isRunning()) {
            dialogDetectThreadStart = ProgressDialog.show(MainActivity.getInstance(), "",
                    "Starting Bluetooth Service Please wait...", true);
        }

        bluetoothDevice = searchDevice();

        // TODO: if could not find bluetooth device
        if(bluetoothDevice == null){
            showNotificationMsg("Could not find Bluetooth device, Please Pair the device and the restart the Bluetooth service");
            return Service.START_STICKY;
        }

        // create the connection thread
        connectThread = new ConnectThread(bluetoothDevice);
        connectThread.start();

        return Service.START_STICKY;
    }

    public static BluetoothDevice searchDevice(){

        // Check if bluetooth is enabled
        if (!mBluetoothAdapter.isEnabled()) {
            // bluetooth is disabled
            return null;
        }

        // Search for the bluetooth Device
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

        // Cancel discovery because it otherwise slows down the connection.
        mBluetoothAdapter.cancelDiscovery();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName(); // Name
                if(deviceName.equals(DeviceName)){
                    // connect to this device
                    Log.d(TAG, DeviceName+" detected");

                    return device;
                }
            }
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        dismissDialog(dialogDetectThreadStart);
        instance = null;
    }

    private void dismissDialog(ProgressDialog dialog){
        if(dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public boolean stopBluetoothService(){
        dialogDetectThreadEnd = ProgressDialog.show(MainActivity.getInstance(), "",
                "Closing Bluetooth Service Please wait...", true);
        if(SoundProcessing.isServiceRecordingSound){
            SoundProcessing.stopRecording();
        }
        connectThread.cancel();
        return true;
    }


    /*
    * Call this function after a loud sound has been detected, and we want to know the label of the sound
    * */
    private void detectSound() {

        // Send sound label only if the prev time the sound label sent is atleast the value "timeDiffToBeMaintained"
        long time = System.currentTimeMillis();
        if(time - prevTimeSoundLabelSent > timeDiffToBeMaintained) {
            prevTimeSoundLabelSent = time;
            showSoundDetectionStartNotification();
            SoundProcessing.startRecording(1, true);
        }
    }

    /*
    * This functions send the sound label (input parameter) to the smart device
    * */
    public boolean sendSoundLabelToDevice(int soundLabel){
        try {
            mmOutStream.write(soundLabel);
            Log.d(TAG, "Data written to the bluetooth device - " + soundLabel);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred when sending data", e);
            return false;
        }
        return true;
    }

    public void showSoundResultNotification(int idx){
        // Start MainActivity if the notification is clicked on
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("soundDetected", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);

        //This is the intent of PendingIntent
        Intent intentAction = new Intent(this,ActionReceiver.class);
        intentAction.putExtra("action","wrong");
        intentAction.putExtra("action","correct");
        PendingIntent pendingIntentAction = PendingIntent.getBroadcast(this,1,intentAction,PendingIntent.FLAG_UPDATE_CURRENT);;

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle("Smart Band")
                .setContentText("Sound Detected - " + idx)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.cross_icon, "Wrong Detection", pendingIntentAction)
                .addAction(R.drawable.common_google_signin_btn_icon_light, "Correct Detection", pendingIntentAction)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(new long[] { 1000, 1000});

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID_RESULT, mBuilder.build());
    }

    public void showSoundDetectionStartNotification(){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle("Smart Band")
                .setContentText("Bluetooth Service - Started Detecting Sound")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID_START, mBuilder.build());
    }

    public void showNotificationMsg(String msg){
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.notif_icon)
                .setContentTitle("Smart Band")
                .setContentText(msg)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

        // notificationId is a unique int for each notification that you must define
        notificationManager.notify(NOTIFICATION_ID_START, mBuilder.build());
    }


    /*
    * This class is private to Bluetooth Service
    * This thread deals with the bluetooth variables connecting to the device
    * */
    private class ConnectThread extends Thread {

        private int DelayDetectionOfDevice = 100;
        private UUID MY_UUID;
        private boolean isInterrputed;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            MY_UUID = mmDevice.getUuids()[0].getUuid();
            isInterrputed = false;
        }

        public void run() {
            dismissDialog(dialogDetectThreadStart);

            while(!isInterrputed) {

                mmSocket = null;

                // loop till you connect to the required device
                while(!isInterrputed) {

                    try {
                        // Get a BluetoothSocket to connect with the given BluetoothDevice.
                        mmSocket = mmDevice.createRfcommSocketToServiceRecord(MY_UUID);
                        Log.d(TAG, "Socket created");

                        // Connect to the remote device through the socket. This call blocks
                        // until it succeeds or throws an exception.
                        try {
                            mmSocket.connect();
                            break;
                        } catch (IOException connectException) {
                            // Unable to connect; close the socket and return.
                            Log.d(TAG, "could not connect to the socket");
                            try {
                                mmSocket.close();
                            } catch (IOException closeException) {
                                Log.e(TAG, "Could not close the client socket", closeException);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Socket's create() method failed", e);
                    }

                    //  sleep for sometime if you could not connect to the bluetooth device
                    try {
                        sleep(DelayDetectionOfDevice);
                    }catch (Exception ex){
                        Log.d(TAG, "could not sleep");
                    }
                }

                if(isInterrputed) {
                    break;
                }

                // The connection attempt succeeded.
                Log.d(TAG, "Connected to socket");

                // Get the input and output streams; using temp objects because
                // member streams are final.
                mmInStream = null;
                mmOutStream = null;

                try {
                    mmInStream = mmSocket.getInputStream();
                    mmOutStream = mmSocket.getOutputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating input stream", e);
                }

                // read buffer
                mmBuffer = new byte[1024];
                int numBytes; // bytes returned from read()

                Log.d(TAG, "Started Parsing data");

                // Keep listening to the InputStream until an exception occurs.
                while (!isInterrputed) {
                    try {
                        // Read from the InputStream.
                        numBytes = mmInStream.read(mmBuffer);
                        String inbuf = new String(mmBuffer).substring(0, numBytes).toLowerCase();
                        Log.d(TAG, inbuf);

                        // TODO: refine the detection process
                        CharSequence checkSeq = "l";
                        if (inbuf.contains(checkSeq)) {
                            Log.d(TAG, "Loud sound detected");

                            // get the sound label from the app
                            detectSound();
                        }

                    } catch (IOException e) {
                        Log.d(TAG, "Input stream was disconnected", e);
                        closeAllSockets();
                        break;
                    }
                }
            }
            Log.d(TAG, "Connect Thread Run Completed");

            dismissDialog(dialogDetectThreadEnd);
            stopSelf();
        }

        public void cancel(){
            isInterrputed = true;
            closeAllSockets();
        }

        private void closeAllSockets(){
            try {
                if(mmSocket!=null) {
                    mmSocket.close();
                }
                if(mmInStream!=null){
                    mmInStream.close();
                }
                if(mmOutStream!=null){
                    mmOutStream.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Could not close the client socket", e);
            }
        }
    }


}

