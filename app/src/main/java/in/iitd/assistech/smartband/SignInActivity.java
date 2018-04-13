package in.iitd.assistech.smartband;

import android.app.LoaderManager;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FacebookAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserInfo;

import java.net.URL;

public class SignInActivity extends AppCompatActivity implements View.OnClickListener,
        GoogleApiClient.OnConnectionFailedListener{

    private static final String TAG = "SignInActivity";
    public static final int RC_SIGN_IN = 9001;

    private GoogleApiClient mGoogleApiClient;
    private CallbackManager mCallbackManager;
    public FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

        findViewById(R.id.google_sign_in_button).setOnClickListener(this);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        // [END config_signin]
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();

        // [START initialize_auth]
        mAuth = FirebaseAuth.getInstance();
        // [END initialize_auth]

        Log.d(TAG, "Before mCallBackManager");
        mCallbackManager = CallbackManager.Factory.create();
        LoginButton fbLoginButton = (LoginButton) findViewById(R.id.fb_login_button);
        //fbLoginButton.setFragment(this);
        fbLoginButton.setReadPermissions("email", "public_profile");
        Log.d(TAG, "After fbLoginButton");
        fbLoginButton.registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult loginResult) {
                Log.d(TAG, "facebook:onSuccess:" + loginResult);
                handleFacebookAccessToken(loginResult.getAccessToken());
//                FirebaseUser user = mAuth.getCurrentUser();
//                updateUI(user);
                finish();
            }

            @Override
            public void onCancel() {
                Log.d(TAG, "facebook:onCancel");
                // [START_EXCLUDE]
                //TODO: updateUI(null);
                // [END_EXCLUDE]
            }

            @Override
            public void onError(FacebookException error) {
                Log.d(TAG, "facebook:onError", error);
                // [START_EXCLUDE]
                //TODO: updateUI(null);
                Toast.makeText(SignInActivity.this, "Authentication Cancelled by User", Toast.LENGTH_SHORT).show();
                // [END_EXCLUDE]
            }
        });
        // [END initialize_fblogin]
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        switch (view.getId()){
            case R.id.google_sign_in_button:
                signIn();
                break;
//            case R.id.email_sign_in_button:
//                attemptLogin();
//                break;
        }
    }

    /**--------Added Extra For Google Sign In------**/
    // [START on_start_check_user]
    @Override
    @NonNull
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "Non null user, " + currentUser.getDisplayName() + ", " + currentUser.getEmail());
            updateUI(currentUser);
            finish();
        }

        /*if(currentUser.getProviderId() != "firebase"){
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }*/
        //TODO: updateUI(currentUser);
    }

    // [END on_start_check_user]
    // [START signin]
    private void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        //TODO: Edit below method
        Log.e(TAG, "SIGN in intent start");
        startActivityForResult(signInIntent, RC_SIGN_IN);
        Log.e(TAG, "SIGN in intent ends");
    }
    // [END signin]

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.e(TAG, "googleSignIn1");
        super.onActivityResult(requestCode, resultCode, data);
        Log.e(TAG, "googleSignIn2");

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Log.e(TAG, "googleSignIn");
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            if (result.isSuccess()) {
                // Google Sign In was successful, authenticate with Firebase
                GoogleSignInAccount account = result.getSignInAccount();
                firebaseAuthWithGoogle(account);
            } else {
                // Google Sign In failed, update UI appropriately
                // [START_EXCLUDE]
                //TODO: updateUI(null);
                // [END_EXCLUDE]
            }
        } else {
            Log.e(TAG, "mCallBackManager");
            // Pass the activity result back to the Facebook SDK
            mCallbackManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    // [START auth_with_google]
    private void firebaseAuthWithGoogle(GoogleSignInAccount acct) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.getId());
        // [START_EXCLUDE silent]
        //TODO showProgressDialog();
        // [END_EXCLUDE]

        AuthCredential credential = GoogleAuthProvider.getCredential(acct.getIdToken(), null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
//                            finish();
                            //TODO: updateUI(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredentialGoogle:failure", task.getException());
                            Toast.makeText(SignInActivity.this, "Authentication failed Google.", Toast.LENGTH_SHORT).show();
                            //TODO: updateUI(null);
                        }

                        // [START_EXCLUDE]
                        //TODO: hideProgressDialog();
                        // [END_EXCLUDE]
                    }
                });
    }
    // [END auth_with_google]

    // [START auth_with_facebook]
    private void handleFacebookAccessToken(AccessToken token) {
        Log.d(TAG, "handleFacebookAccessToken:" + token.getToken());
        // [START_EXCLUDE silent]
        //TODO: showProgressDialog();
        // [END_EXCLUDE]

        AuthCredential credential = FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // Sign in success, update UI with the signed-in user's information
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            updateUI(user);
                        } else {
                            // If sign in fails, display a message to the user.
                            Log.w(TAG, "signInWithCredentialFB:failure", task.getException());
                            Toast.makeText(SignInActivity.this, "Authentication failed FB.", Toast.LENGTH_SHORT).show();
                            //TODO: updateUI(null);
                        }

                        // [START_EXCLUDE]
                        //TODO: hideProgressDialog();
                        // [END_EXCLUDE]
                    }
                });

    }
    // [END auth_with_facebook]

    //TODO: Call this method from Tab3
    private void signOut() {
        // Firebase sign out
        mAuth.signOut();

        // Google sign out
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        //TODO: updateUI(null);
                        Log.e(TAG, "Google SignOut1");
                    }
                });
        Log.e(TAG, "Google SignOut");
    }

    //FB sign out
    private void fbSignOut(){
        mAuth.signOut();
        LoginManager.getInstance().logOut();
        //TODO: updateUI(null);
    }
    private void revokeAccess() {
        // Firebase sign out
        mAuth.signOut();

        // Google revoke access
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(@NonNull Status status) {
                        //TODO: updateUI(null);
                    }
                });
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(TAG, "onConnectionFailed:" + connectionResult);
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show();
    }

    public void updateUI(FirebaseUser user){
        String name = user.getDisplayName();
        String email = user.getEmail();
        Uri photoUri = user.getPhotoUrl();
        String userID = user.getUid();
        String SIGNED = null;
        String imageURL = null;

        for (UserInfo user1: user.getProviderData()) {
            Log.e(TAG, user.getProviderId());
            if (user1.getProviderId().equals("facebook.com")) {
                SIGNED = "FB";
                System.out.println("User is signed in with Facebook");
            } else if(user1.getProviderId().equals("google.com")){
                SIGNED = "GOOGLE";
                System.out.println("User is signed in with Google");
            }
        }

        Log.e(TAG, "Logging : " + name + email);

        Intent intent = new Intent(SignInActivity.this, MainActivity.class);
        intent.putExtra("NAME", name);
        intent.putExtra("EMAIL", email);
        intent.putExtra("PHOTO", imageURL);
        intent.putExtra("PHOTOURI", photoUri.toString());
        intent.putExtra("SIGNED", SIGNED);
        startActivity(intent);
    }
}
