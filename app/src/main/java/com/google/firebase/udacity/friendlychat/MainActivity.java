/**
 * Copyright Google Inc. All Rights Reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.*;
import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.vistrav.ask.Ask;
import com.vistrav.ask.annotations.AskDenied;
import com.vistrav.ask.annotations.AskGranted;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import id.zelory.compressor.Compressor;
import pl.aprilapps.easyphotopicker.DefaultCallback;
import pl.aprilapps.easyphotopicker.EasyImage;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int RC_SIGN_IN = 1;
    private static final int RC_PHOTO_PICKER =  2;

    public static final String ANONYMOUS = "anonymous";
    public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
    public static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

    private ListView mMessageListView;
    private MessageAdapter mMessageAdapter;
    private ProgressBar mProgressBar;
    private ImageButton mPhotoPickerButton;
    private EditText mMessageEditText;
    private Button mSendButton;

    private FirebaseDatabase firebaseDatabase;
    private DatabaseReference databaseReference;
    private ChildEventListener childEventListener;

    private FirebaseAuth firebaseAuth;
    private FirebaseAuth.AuthStateListener authStateListener;
    private FirebaseStorage firebaseStorage;
    private StorageReference storageReference;
    private FirebaseRemoteConfig firebaseRemoteConfig;

    private ProgressDialog progress;
    private File file;

    private String mUsername;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsername = ANONYMOUS;

        //init firebase instance
        firebaseDatabase = FirebaseDatabase.getInstance();
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseStorage = FirebaseStorage.getInstance();
        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

        EasyImage.configuration(this)
                .setImagesFolderName("img") //images folder name, default is "EasyImage"
                .saveInAppExternalFilesDir() //if you want to use root internal memory for storying images
                .saveInRootPicturesDirectory();

        databaseReference = firebaseDatabase.getReference().child("messages");
        storageReference = firebaseStorage.getReference().child("chat_photos");

        // Initialize references to views
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mMessageListView = (ListView) findViewById(R.id.messageListView);
        mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
        mMessageEditText = (EditText) findViewById(R.id.messageEditText);
        mSendButton = (Button) findViewById(R.id.sendButton);

        // Initialize message ListView and its adapter
        List<FriendlyMessage> friendlyMessages = new ArrayList<>();
        mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
        mMessageListView.setAdapter(mMessageAdapter);

        // Initialize progress bar
        mProgressBar.setVisibility(ProgressBar.INVISIBLE);

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //handle permissions
                Ask.on(MainActivity.this)
                        .forPermissions(android.Manifest.permission.CAMERA
                                , android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        .withRationales("Camera permission need for capture your image",
                                "In order to save image you will need to grant storage permission")
                        .go();

                /*Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/jpeg");
                intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);*/
            }
        });

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (charSequence.toString().trim().length() > 0) {
                    mSendButton.setEnabled(true);
                } else {
                    mSendButton.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // TODO: Send messages on click

                FriendlyMessage friendlyMessage = new FriendlyMessage(mMessageEditText.getText().toString(),mUsername,null);
                databaseReference.push().setValue(friendlyMessage);

                // Clear input box
                mMessageEditText.setText("");
            }
        });

        authStateListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = firebaseAuth.getCurrentUser();
                if(user != null){
                    //loged in
                    Toast.makeText(MainActivity.this, "You are signed in!", Toast.LENGTH_SHORT).show();
                    onSignedInInit(user.getDisplayName());
                }else{
                    //loged off
                    onSignedOutInit();
                    startActivityForResult(
                            AuthUI.getInstance()
                                    .createSignInIntentBuilder()
                                    .setIsSmartLockEnabled(false) //not save user credential automaticily (locked in)
                                    .setProviders(Arrays.asList(new AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                                            new AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build()))
                                    .build(),
                            RC_SIGN_IN);
                }
            }
        };

        FirebaseRemoteConfigSettings ConfigSettings = new FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG)
                .build();
        firebaseRemoteConfig.setConfigSettings(ConfigSettings);

        Map<String, Object> defaultConfigMap = new HashMap<>();
        defaultConfigMap.put(FRIENDLY_MSG_LENGTH_KEY,DEFAULT_MSG_LENGTH_LIMIT);

        fetchConfig();
    }

    @AskGranted(android.Manifest.permission.CAMERA)
    public void cameraAccessGranted() {
        Log.i(TAG, "CAMERA GRANTED");
        EasyImage.openChooserWithGallery(MainActivity.this,null,RC_PHOTO_PICKER);
    }

    @AskDenied(android.Manifest.permission.CAMERA)
    public void cameraAccessDenied() {Log.i(TAG, "CAMERA DENIED");}

    @AskGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void writeAccessGranted() {Log.i(TAG, "WRITE_EXTERNAL_STORAGE GRANTED");}

    @AskDenied(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    public void writeAccessDenied() {Log.i(TAG, "WRITE_EXTERNAL_STORAGE DENIED");}

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode == RC_SIGN_IN){
            if(resultCode == RESULT_OK){
                Toast.makeText(this, "Signed In!", Toast.LENGTH_SHORT).show();
            }else if(resultCode == RESULT_CANCELED){
                Toast.makeText(this, "Sign In Canceled!", Toast.LENGTH_SHORT).show();
                finish();
            }
        }else{
            Toast.makeText(this, "picked", Toast.LENGTH_SHORT).show();
            EasyImage.handleActivityResult(requestCode, resultCode, data, this, new DefaultCallback() {
                @Override
                public void onImagePickerError(Exception e, EasyImage.ImageSource source, int type) {
                    if(e.getMessage()!=null){
                        Log.e(TAG,e.getMessage());
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }else{
                        Log.e(TAG,"something wrong when pick image");
                    }
                }

                @Override
                public void onImagePicked(File imageFile, EasyImage.ImageSource source, int type) {
                    file = imageFile;
                    new CompressImage().execute();
                }
            });


            /*Uri uri = data.getData();

            // Get a reference to store file at chat_photos/<FILENAME>
            StorageReference photoRef = storageReference.child(uri.getLastPathSegment());

            //upload to storage
            photoRef.putFile(uri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Uri downloadURL = taskSnapshot.getDownloadUrl();
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null,mUsername,downloadURL.toString());
                    databaseReference.push().setValue(friendlyMessage);
                }
            });*/
        }
    }

    @Override
    protected void onResume(){
        super.onResume();
        firebaseAuth.addAuthStateListener(authStateListener);
    }

    @Override
    protected void onPause(){
        super.onPause();
        if(authStateListener != null){
            firebaseAuth.removeAuthStateListener(authStateListener);
        }
        detachDatabaseReadListener();
        mMessageAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.sign_out_menu:
                //sign out
                AuthUI.getInstance().signOut(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void onSignedInInit(String username){
        mUsername = username;
        attachDatabaseReadListener();
    }

    private void onSignedOutInit(){
        mUsername = ANONYMOUS;
        mMessageAdapter.clear();
        detachDatabaseReadListener();
    }

    private void attachDatabaseReadListener(){
        if(childEventListener == null){
            childEventListener = new ChildEventListener() {
                @Override
                public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                    FriendlyMessage newFriendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
                    mMessageAdapter.add(newFriendlyMessage);
                }

                @Override
                public void onChildChanged(DataSnapshot dataSnapshot, String s) {}
                @Override
                public void onChildRemoved(DataSnapshot dataSnapshot) {}
                @Override
                public void onChildMoved(DataSnapshot dataSnapshot, String s) {}
                @Override
                public void onCancelled(DatabaseError databaseError) {}
            };
            databaseReference.addChildEventListener(childEventListener);
        }

    }

    private void detachDatabaseReadListener(){
        if(childEventListener != null){
            databaseReference.removeEventListener(childEventListener);
            childEventListener = null;
        }
    }

    public void fetchConfig(){
        long cacheExpiratiob = 3600;
        if(firebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()){
            cacheExpiratiob = 0;
        }
        firebaseRemoteConfig.fetch(cacheExpiratiob)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                firebaseRemoteConfig.activateFetched();
                applyRetrievedLengthLimit();
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w(TAG,e.getMessage());
                applyRetrievedLengthLimit();
            }
        });
    }

    private void applyRetrievedLengthLimit(){
        long friendly_msg_length = firebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
        mMessageEditText.setFilters(new InputFilter[]{new InputFilter.LengthFilter((int) friendly_msg_length)});
    }

    private class CompressImage extends AsyncTask<Object, Object, File> {

        protected void onPreExecute() {
            progress = new ProgressDialog(MainActivity.this);
            progress.setMessage("Compressing Image");
            progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progress.setIndeterminate(true);
            progress.setCancelable(true);
            progress.show();
        }

        @Override
        protected File doInBackground(Object... params) {
            //compress image
            File compressedImageFile = Compressor.getDefault(MainActivity.this).compressToFile(file);
            return compressedImageFile;
        }

        @Override
        protected void onPostExecute(File result) {
            progress.dismiss();
            galleryAddPic(result);
        }
    }

    private void galleryAddPic(File f) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
        uploadToStorage(contentUri);
    }

    private void uploadToStorage(Uri uri){
            // Get a reference to store file at chat_photos/<FILENAME>
            StorageReference photoRef = storageReference.child(uri.getLastPathSegment());

            //upload to storage
            photoRef.putFile(uri).addOnSuccessListener(this, new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                    Uri downloadURL = taskSnapshot.getDownloadUrl();
                    FriendlyMessage friendlyMessage = new FriendlyMessage(null,mUsername,downloadURL.toString());
                    databaseReference.push().setValue(friendlyMessage);
                }
            });
    }
}
