package in.iitd.assistech.smartband;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;

/**
 * Created by nikhil on 17/3/18.
 */

public class ResultAlertDialog extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("your title");
        alertDialog.setMessage("your message");
        alertDialog.setIcon(R.drawable.notif_icon);

        alertDialog.show();
    }
}
