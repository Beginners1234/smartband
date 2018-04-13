package in.iitd.assistech.smartband;

import android.bluetooth.BluetoothAdapter;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.sounds.ClassifySound;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import static com.sounds.ClassifySound.numOutput;
import static in.iitd.assistech.smartband.HelperFunctions.getClassifyProb;
import static in.iitd.assistech.smartband.MainActivity.adapter;
import static java.lang.Thread.sleep;

/**
 * Created by nikhil on 12/4/18.
 */

/**
 * ----------For Sound Processing and stuff----------
 * All variables and functions are static
 **/

public class SoundProcessing {

    /***Variables for audiorecord*/
    private static MediaRecorder mRecorder = null;
    public static final int RECORD_TIME_DURATION = 300; //0.5 seconds
    private static final int RECORDER_SAMPLERATE = ClassifySound.RECORDER_SAMPLERATE;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    static int BufferElements2Rec = RECORDER_SAMPLERATE * RECORD_TIME_DURATION/1000; // number of 16 bits for 3 seconds


    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "SmartBand";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";

    private static AudioRecord recorder = null;
    private static int bufferSize = 0;
    private static boolean isRecording = false;
    public static Boolean isServiceRecordingSound = false;
    public static Boolean isActivityRecordingSound = false;
    private static Thread recordingThread = null;
    private static int num_history = 2;
    static int hist_count = 0;
    private static double[][] history_prob = new double[num_history][numOutput];

    private static int MaxAudioRecordTime = 5;
    private static String TAG = "Sound Processing";
    private static BluetoothAdapter mBluetoothAdapter;

    static{
        bufferSize = AudioRecord.getMinBufferSize
                (RECORDER_SAMPLERATE,RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING)*3;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    private static synchronized void checkIfRecodringOn(boolean requestFromService){
        if(isServiceRecordingSound || isActivityRecordingSound){
            Log.d(TAG, "already sound recoding in progress");
            return;
        }
        if(requestFromService){
            isServiceRecordingSound = true;
        }else{
            isActivityRecordingSound = true;
        }
    }

    public static void startRecording(int indicator, final boolean requestFromService) {

        checkIfRecodringOn(requestFromService);

        if((requestFromService && !isServiceRecordingSound) || (!requestFromService && !isActivityRecordingSound)){
            return;
        }

        Log.d(TAG, "Sound Recording started");

        int bufferSizeinBytes = 0;
        int BytesPerElement = 2; // 2 bytes in 16bit format
        if (indicator == 1) bufferSizeinBytes = BufferElements2Rec * BytesPerElement;
        if (indicator == 2) bufferSizeinBytes = bufferSize;
        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                bufferSizeinBytes);//TODO Uncomment this for processing
        int i = recorder.getState();
        if (i == 1)
            recorder.startRecording();

        isRecording = true;
        if (indicator == 1) {
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //TODO Uncomment recorder bufferSize above for processing
                    readAudioAndProcess(requestFromService);
//                    writeAudioDataToFile();
                }
            }, "AudioRecorder Thread");

            recordingThread.start();
            if(requestFromService){
                stopRecordingThread();
            }
        }
        if (indicator == 2) {
            recordingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "Recording started");
                    //TODO Uncomment recorder bufferSize above for processing
                    writeAudioDataToFile();
                }
            }, "AudioRecorder Thread");

            recordingThread.start();
        }
    }

    private static void stopRecordingThread(){
        Thread stopThread = new Thread(new Runnable() {
            @Override
            public void run() {
                //  sleep for sometime if you could not connect to the bluetooth device
                try {
                    sleep(MaxAudioRecordTime);
                }catch (Exception ex){
                    Log.d(TAG, "could not sleep");
                }
                stopRecording();
            }
        }, "AudioRecorder Stop Thread");
        stopThread.start();
    }

    private static void readAudioAndProcess(final boolean requestFromService) {
        short[] data = new short[BufferElements2Rec];

        //@link https://stackoverflow.com/questions/40459490/processing-in-audiorecord-thread
        HandlerThread myHandlerThread = new HandlerThread("my-handler-thread");
        myHandlerThread.start();

        final Handler myHandler = new Handler(myHandlerThread.getLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                Log.e(TAG, "handleMessage");
                short[] temp_sound = (short[]) msg.obj;
                double[] classifyProb = getClassifyProb(temp_sound);
                for (int i = 0; i < classifyProb.length; i++) {
                    history_prob[hist_count][i] = classifyProb[i];
                }
                hist_count++;
                int numNan = 0;
                if (hist_count == num_history) {
                    hist_count = 0;
                    double[] mean_classifyProb = new double[classifyProb.length];
                    for (int i = 0; i < classifyProb.length; i++) {
                        int num_NotNan = 0;
                        double sum = 0;
                        for (int j = 0; j < history_prob.length; j++) {
                            if (history_prob[j][i] != Double.NaN) {
                                sum += history_prob[j][i];
                                num_NotNan++;
                            }
                        }
                        mean_classifyProb[i] = sum / num_NotNan;
                        numNan = num_history - num_NotNan;
                    }
                    Log.e(TAG, "Num Nan : " + numNan);
                    if (numNan < num_history / 2)
                        setProbOut(mean_classifyProb, requestFromService);
                }
                return true;
            }
        });

        int read = 0;
        while (isRecording) {
            read = recorder.read(data, 0, BufferElements2Rec);
            if (read > 0) {
            }
            if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                Message message = myHandler.obtainMessage();
                message.obj = data;
                message.arg1 = read;
                message.sendToTarget();
            }
        }
    }

    static Handler uiHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            double[] prob_sound = (double[]) msg.obj;
            boolean[] notifState = adapter.getInitialNotifListState();
            adapter.editTab2Text(prob_sound, notifState);
        }
    };

    private static void setProbOut(double[] outProb, boolean requestFromService){
        //send msg to edit value of prob on screen
        //Launch dialog interface;
        if(!requestFromService) {
            Message message = uiHandler.obtainMessage();
            message.obj = outProb;
            message.sendToTarget();
        }
        Log.e(TAG, "Prob Ambient = " + Double.toString(outProb[2]));
        if((1.0-outProb[2])>0.6){
            int idx = 1;
            if(outProb[1]<outProb[0]) idx = 0;

            // send sound label to bluetooth device
            BluetoothService.getInstance().sendSoundLabelToDevice(idx);

            if(requestFromService){
                stopRecording();
            }

            // Display sound detection results if MainActivity is active
            // Else show notification
            if(MainActivity.isRunning()) {
                MainActivity.getInstance().showDialog(MainActivity.getInstance(), idx, outProb);
            }else {
                BluetoothService.getInstance().showSoundResultNotification(idx);
            }

            // reset variables to mark end of recording
            isServiceRecordingSound = false;
            isActivityRecordingSound =false;
        }
    }

    private static void writeAudioDataToFile() {
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;

        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;
        if (null != os) {
            while (isRecording) {
                read = recorder.read(data, 0, bufferSize);
                if (read > 0) {
                }

                if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized static void stopRecording() {
        if (null != recorder) {
            isRecording = false;

            int i = recorder.getState();
            if (i == 1)
                recorder.stop();
            recorder.release();

            recorder = null;
            recordingThread = null;
        }
    }

    public static void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    public static synchronized String getFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }
        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() +
                AUDIO_RECORDER_FILE_EXT_WAV);
    }

    public static String getTempFilename() {
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if (!file.exists()) {
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);

        if (tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }

    public static void copyWaveFile(String inFilename,String outFilename){
        FileInputStream in = null;
        FileOutputStream out = null;
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 1;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

//            AppLog.logString("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1) {
                out.write(data);
            }

            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';  // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';  // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;  // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;  // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8);  // block align
        header[33] = 0;
        header[34] = RECORDER_BPP;  // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    /**---------------------------------------**/

    /**---------------------------------------**/

}
