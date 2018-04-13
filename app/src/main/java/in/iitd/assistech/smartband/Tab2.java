package in.iitd.assistech.smartband;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;
import java.util.SimpleTimeZone;
import java.util.UUID;

import de.hdodenhof.circleimageview.CircleImageView;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.os.ParcelFileDescriptor.MODE_APPEND;
import static com.facebook.FacebookSdk.getApplicationContext;

public class Tab2 extends Fragment implements View.OnClickListener{

    private static final String TAG = "TAB2";

    private View view;
    private TextView hornValue;
    private TextView barkValue;
    private TextView gunShotValue;
    private TextView ambientValue;
    private Button startFPButton;
    private Button stopFPButton;
    private Button soundRecordButton;
    private Button stopSoundRecord;
    private ImageButton startButton;
    private ImageButton stopButton;
    private ListView historyListView;
    private Button bleOnButton;
    private Button bleSendButton;
    private Button bleOffButton;
    TextView myLabel;

    // MAC address of remote Bluetooth device
    // Replace this with the address of your own module
    private final String address = "00:06:66:66:33:89";

    // The thread that does all the work
    BluetoothThread btt;

    // Handler for writing messages to the Bluetooth connection
    Handler writeHandler;


    private OnTabEvent mListener;

    public Tab2(){

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        view = inflater.inflate(R.layout.tab2, container, false);

        hornValue = (TextView)view.findViewById(R.id.hornValue);
        barkValue = (TextView)view.findViewById(R.id.barkValue);
        gunShotValue = (TextView)view.findViewById(R.id.gunShotValue);
        ambientValue = (TextView)view.findViewById(R.id.ambientValue);
        startFPButton = (Button)view.findViewById(R.id.startFPButton);
        stopFPButton = (Button)view.findViewById(R.id.stopFPButton);
        soundRecordButton = (Button)view.findViewById(R.id.startsoundRecord);
        stopSoundRecord = (Button)view.findViewById(R.id.stopSoundRecord);
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point screenSize = new Point();
        display.getRealSize(screenSize);
        int size = Math.min(screenSize.x, screenSize.y);
        int buttonSize = Math.round(size * 0.75f);

        bleSendButton = (Button) view.findViewById(R.id.bleSendButton);
        bleOnButton = (Button) view.findViewById(R.id.bleOnButton);
        bleOffButton = (Button) view.findViewById(R.id.bleOffButton);
        myLabel = (TextView) view.findViewById(R.id.myLabel);

        startButton = (ImageButton) view.findViewById(R.id.start_button);
        stopButton = (ImageButton) view.findViewById(R.id.stop_button);
//        historyListView = (ListView)view.findViewById(R.id.historyListView);

        startButton.setMaxWidth(buttonSize);
        startButton.setMaxHeight(buttonSize);
        stopButton.setMaxWidth(buttonSize);
        stopButton.setMaxHeight(buttonSize);

        startFPButton.setOnClickListener(this);
        startButton.setOnClickListener(this);
        stopFPButton.setOnClickListener(this);
        stopButton.setOnClickListener(this);
        soundRecordButton.setOnClickListener(this);
        stopSoundRecord.setOnClickListener(this);
        bleOffButton.setOnClickListener(this);
        bleOnButton.setOnClickListener(this);
        bleSendButton.setOnClickListener(this);

        return view;
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.startFPButton:
                //TODO
                mListener.onButtonClick("StartTargetFingerPrint");
                stopFPButton.setVisibility(View.VISIBLE);
                startFPButton.setVisibility(View.GONE);
                break;
            case R.id.stopFPButton:
                //TODO
                mListener.onButtonClick("StopTargetFingerPrint");
                stopFPButton.setVisibility(View.GONE);
                startFPButton.setVisibility(View.VISIBLE);
                break;

            case R.id.start_button:
                //TODO
                mListener.onButtonClick("MicReadButton");
                startButton.setVisibility(View.GONE);
                stopButton.setVisibility(View.VISIBLE);
                break;
            case R.id.stop_button:
                //TODO
                mListener.onButtonClick("StopRecordButton");
                startButton.setVisibility(View.VISIBLE);
                stopButton.setVisibility(View.GONE);
                break;
            case R.id.startsoundRecord:
                Log.e(TAG, "Record}");
                mListener.onButtonClick("SoundRecord");
                break;
            case R.id.stopSoundRecord:
                mListener.onButtonClick("StopRecord");
                break;
            case R.id.bleOnButton:
                connectButtonPressed();
                mListener.onButtonClick("TurnBluetoothOn");
                break;
            case R.id.bleSendButton:
                mListener.onButtonClick("SendBluetoothData");
                break;
            case R.id.bleOffButton:
                disconnectButtonPressed();
                mListener.onButtonClick("TurnBluetoothOff");
                break;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnTabEvent) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnTabEvent interface");
        }
    }


    public void editValue(double[] output, boolean[] notifState){
        String filename = "M12_bhavya_phone.csv";
        writeToExcel(filename, output);
        hornValue.setText(Double.toString(output[0]));
        barkValue.setText(Double.toString(output[1]));
//        gunShotValue.setText(Double.toString(output[]));
        ambientValue.setText(Double.toString(output[2]));
    }

    /**
     * Launch the Bluetooth thread.
     */
    public void connectButtonPressed() {
        Log.v(TAG, "Connect button pressed.");

        // Only one thread at a time
        if (btt != null) {
            Log.w(TAG, "Already connected!");
            return;
        }

        // Initialize the Bluetooth thread, passing in a MAC address
        // and a Handler that will receive incoming messages
        btt = new BluetoothThread(address, new Handler() {

            @Override
            public void handleMessage(Message message) {

                String s = (String) message.obj;

                // Do something with the message
                if (s.equals("CONNECTED")) {
                    myLabel.setText("Connected.");
                } else if (s.equals("DISCONNECTED")) {
                    myLabel.setText("Disconnected.");
                } else if (s.equals("CONNECTION FAILED")) {
                    myLabel.setText("Connection failed!");
                    btt = null;
                } else {
                    myLabel.setText(s);
                }
            }
        });

        // Get the handler that is used to send messages
        writeHandler = btt.getWriteHandler();

        // Run the thread
        btt.start();
        myLabel.setText("Connecting...");
    }

    /**
     * Kill the Bluetooth thread.
     */
    public void disconnectButtonPressed() {
        Log.v(TAG, "Disconnect button pressed.");

        if(btt != null) {
            btt.interrupt();
            btt = null;
        }
    }

    /**
     * Send a message using the Bluetooth thread's write handler.
     */
    public void writeButtonPressed(View v) {
        Log.v(TAG, "Write button pressed.");

        String data = myLabel.getText().toString();

        Message msg = Message.obtain();
        msg.obj = data;
        writeHandler.sendMessage(msg);
    }


    public void writeToExcel(String filename, double[] output){ //String detail
        File external = Environment.getExternalStorageDirectory();
        String sdcardPath = external.getPath() + "/SmartBand/";
        // to this path add a new directory path
        File file = new File(external, filename);
        try{
            if (!file.exists()){
                file.createNewFile();
            }
            FileOutputStream fos  = getContext().openFileOutput(filename, getContext().MODE_APPEND);
//                            Writer out = new BufferedWriter(new OutputStreamWriter(openFileOutput(file.getName(), MODE_APPEND)));

            android.text.format.DateFormat df = new android.text.format.DateFormat();
            Date date = new Date();

            FileWriter filewriter = new FileWriter(sdcardPath + filename, true);
            BufferedWriter out = new BufferedWriter(filewriter);
//            StringBuilder sb = new StringBuilder(dateString.length());
//            sb.append(dateString);
//            Log.e(TAG, "Time: " + date.toString());

            String data = date.toString() + ",";
//            data += "bark, ";
            for(int i=0; i<output.length; i++){
                data += output[i] + ",";
            }
            data += "\n";
//            data += detail + "\n";

            out.write(data);
            out.close();
            filewriter.close();

            fos.close();
        }catch(Exception e){
            Log.e(TAG, e.toString() + " FileOutputStream");
        }
    }

    public void simulateSoundRecordClick(){

        MainActivity.getInstance().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                startButton.performClick();
            }
        });
    }
}