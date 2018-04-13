package in.iitd.assistech.smartband;


import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import com.sounds.ClassifySound;

import java.io.IOException;

import static com.sounds.ClassifySound.numOutput;

public class HelperFunctions {

    public static double getDecibel(short[] sound){
        double decibel;
        long sum = 0;
        double avg;
        for(int i=0; i<sound.length; i++){
//            Log.e(TAG, Integer.toString(Math.abs(sound[i])));
            sum += Math.abs(sound[i]);
//            Log.e(TAG, Long.toString(sum));

        }
        avg= sum*1.0/sound.length;
        if(avg == 0) decibel=Double.NEGATIVE_INFINITY;
        else decibel = 20.0*Math.log10(32769.0/avg);
        return decibel;
    }

    public static double[] getClassifyProb (short[] temp_sound){

        double threshold = 40;
        double decibel = getDecibel(temp_sound); //HelperFunction
        double[] temp_prob = new double[numOutput];
        if(decibel > threshold){
            for(int i=0; i<temp_prob.length; i++){
                temp_prob[i] = Double.NaN;
            }
        } else{
            try{
                temp_prob = ClassifySound.getProb(temp_sound);
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return temp_prob;
    }


    /*//To get filename from user
    public synchronized void getInput()
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                final AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                alert.setTitle("Name of Sound");
                //customize alert dialog to allow desired input
                final EditText inputFileFP = new EditText(MainActivity.this);
                // Specify the type of input expected; this, for example, sets the input as a password, and will mask the text
                inputFileFP.setInputType(InputType.TYPE_CLASS_TEXT);
                alert.setView(inputFileFP);

                alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton)
                    {
                        filenameFP = inputFileFP.getText().toString();
                        notify();
                    }
                });
                alert.show();
            }
        });

//        return filenameFP;
    }
     */

}
