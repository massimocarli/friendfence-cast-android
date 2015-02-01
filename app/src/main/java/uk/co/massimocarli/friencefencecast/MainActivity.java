package uk.co.massimocarli.friencefencecast;

import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.MediaRouteActionProvider;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.RemoteMediaPlayer;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import java.io.IOException;

/**
 * The Activity we use to start the ChromeCast
 */
public class MainActivity extends ActionBarActivity {

    /**
     * The Tag for the Log
     */
    private static final String TAG_LOG = MainActivity.class.getName();

    /**
     * The id  for the Cast App
     */
    private static final String APP_ID = "3E2097DE";

    /**
     * The Url for the Video to see
     */
    private static final String VIDEO_URL = "3E2097DE";

    /**
     * The Mime Type of the Video
     */
    private static final String MP4_MEDIA_TYPE = "video/mp4";

    /**
     * The MediaRouter class
     */
    private MediaRouter mMediaRouter;

    /**
     * The selected CastDevice
     */
    private CastDevice mCastDevice;

    /**
     * The Client to interact with Google Play Services
     */
    private GoogleApiClient mGoogleApiClient;

    /**
     * The MediaRouteSelector to filter out application
     */
    private MediaRouteSelector mMediaRouteSelector;

    /**
     * This variable permits us to know if the application has successfully started
     */
    private boolean mApplicationStarted;

    /**
     * The session identifier
     */
    private String mSessionId;

    /**
     * The reference to the callback for the channel events
     */
    private LogChannelCallback mLogChannelCallback;

    /**
     * The EditText we use to input the message
     */
    private EditText mMessageEditText;

    /**
     * The reference to the RemoteMediaPlayer
     */
    private RemoteMediaPlayer mRemoteMediaPlayer;

    /**
     * This is the callback interface we have to implement to get notification about
     * the device selected from the menu
     */
    private final MediaRouter.Callback mMediaRouterCallback = new MediaRouter.Callback() {

        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo info) {
            // We save the reference to the selected device
            mCastDevice = CastDevice.getFromBundle(info.getExtras());
            // We get the related routeId
            String routeId = info.getId();
            // We launch the Receiver
            launchReceiver(mCastDevice);
            Log.d(TAG_LOG, "Discovered device " + mCastDevice);
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo info) {
            Log.d(TAG_LOG, "Disconnected device " + mCastDevice);
            // We release the reference to the device
            clearCast();
            // We clear the reference
            mCastDevice = null;
        }
    };

    /**
     * This is the implementation of the listener for CastDevice
     */
    private final Cast.Listener castListener = new Cast.Listener() {
        @Override
        public void onApplicationStatusChanged() {
            if (mGoogleApiClient != null) {
                // We get the new status
                final String newStatus = Cast.CastApi.getApplicationStatus(mGoogleApiClient);
                Log.d(TAG_LOG, "onApplicationStatusChanged: new Status" + newStatus);
            }
        }

        @Override
        public void onVolumeChanged() {
            if (mGoogleApiClient != null) {
                // We get the information related to the volume
                final double newVolume = Cast.CastApi.getVolume(mGoogleApiClient);
                Log.d(TAG_LOG, "onVolumeChanged: new value" + newVolume);
            }
        }

        @Override
        public void onApplicationDisconnected(int errorCode) {
            // We close the application
            clearCast();
        }
    };

    /**
     * The Callback implementation to manage connection events
     */
    private final GoogleApiClient.ConnectionCallbacks mConnectionCallback =
            new GoogleApiClient.ConnectionCallbacks() {
                @Override
                public void onConnected(Bundle bundle) {
                    manageConnectedState(bundle);
                }

                @Override
                public void onConnectionSuspended(int cause) {
                    // We clear the CastDevice info
                    clearCast();
                }
            };

    /**
     * The logic to implement the disconnection with the GoogleApiClient object
     */
    private final GoogleApiClient.OnConnectionFailedListener mConnectionFailedListener =
            new GoogleApiClient.OnConnectionFailedListener() {
                @Override
                public void onConnectionFailed(ConnectionResult connectionResult) {
                    Log.w(TAG_LOG, "Connection failed " + connectionResult);
                    // In this case we just release resources
                    clearCast();
                }
            };

    /**
     * The class that describes a Channel that logs messages
     */
    private static class LogChannelCallback implements Cast.MessageReceivedCallback {

        /**
         * @return The namespace for this MessageReceivedCallback
         */
        public String getNamespace() {
            return "urn:x-cast:uk.co.massimocarli.friendfence";
        }

        @Override
        public void onMessageReceived(CastDevice castDevice, String namespace,
                                      String message) {
            Log.d(TAG_LOG, "onMessageReceived from " + castDevice +
                    " with namespace " + namespace
                    + " message:" + message);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // We initialize the MediaRouter
        mMediaRouter = MediaRouter.getInstance(getApplicationContext());
        // We have to find devices that can launch the Receiver associated to
        // our application
        mMediaRouteSelector = new MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(APP_ID))
                .build();
        // We get the reference to the EditText
        mMessageEditText = (EditText) findViewById(R.id.message_input_message);
        // We attach the event to the button
        findViewById(R.id.message_send_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // We check for the message to send
                final Editable msg = mMessageEditText.getText();
                if (!TextUtils.isEmpty(msg)) {
                    sendCustomMessage(msg.toString());
                } else {
                    Toast.makeText(MainActivity.this, R.string.channel_empty_message,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        // We register the event for the play button
        findViewById(R.id.play_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendVideo();
            }
        });
        // We register the event for the pause button
        findViewById(R.id.pause_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pauseVideo();
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // We inflate the Menu
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // We get reference to the MediaRoute menu item
        MenuItem mediaRouteMenuItem = menu.findItem(R.id.media_route_menu_item);
        // We get the reference to the MediaRouteActionProvider
        MediaRouteActionProvider mediaRouteActionProvider =
                (MediaRouteActionProvider) MenuItemCompat.getActionProvider(mediaRouteMenuItem);
        // We set the filter for our application
        mediaRouteActionProvider.setRouteSelector(mMediaRouteSelector);
        // It's the only voice at the moment
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // We register the MediaRouterCallback interface
        mMediaRouter.addCallback(mMediaRouteSelector, mMediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY);
    }

    @Override
    protected void onPause() {
        // We remove the MediaRouterCallback interface if the Activity is finishing
        if (isFinishing()) {
            mMediaRouter.removeCallback(mMediaRouterCallback);
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        mMediaRouter.removeCallback(mMediaRouterCallback);
        super.onStop();
    }

    /**
     * This utility method implements the code to starts the Receiver on the connected device
     *
     * @param castDevice The connected CastDevice
     */
    private void launchReceiver(final CastDevice castDevice) {
        // We initialize the Builder o create the Cast connection
        Cast.CastOptions.Builder apiOptionsBuilder = Cast.CastOptions
                .builder(castDevice, castListener);
        // We initialize the GoogleApiClient object
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Cast.API, apiOptionsBuilder.build())
                .addConnectionCallbacks(mConnectionCallback)
                .addOnConnectionFailedListener(mConnectionFailedListener)
                .build();
        // We connect
        mGoogleApiClient.connect();
    }

    /**
     * Clean the MediaRouter data
     */
    private void clearCast() {
        // If the GoogleApiClient is null we skip the operation
        if (mGoogleApiClient == null) {
            Log.w(TAG_LOG, "GoogleApiClient not correctly initialized!");
            return;
        }
        // We stop the application only if started
        if (mApplicationStarted) {
            if (mGoogleApiClient.isConnected() || mGoogleApiClient.isConnecting()) {
                // We stop the application given its sessionId
                Cast.CastApi.stopApplication(mGoogleApiClient, mSessionId);
                try {
                    Cast.CastApi.stopApplication(mGoogleApiClient, mSessionId);
                    if (mLogChannelCallback != null) {
                        Cast.CastApi.removeMessageReceivedCallbacks(
                                mGoogleApiClient,
                                mLogChannelCallback.getNamespace());
                        mLogChannelCallback = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG_LOG, "Exception while removing channel", e);
                }
                try {
                    if (mRemoteMediaPlayer != null) {
                        Cast.CastApi.removeMessageReceivedCallbacks(
                                mGoogleApiClient,
                                mRemoteMediaPlayer.getNamespace());
                        mRemoteMediaPlayer = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG_LOG, "Exception while removing channel", e);
                }
                mGoogleApiClient.disconnect();
            }
            mApplicationStarted = false;
        }
        // We reset all the objects
        mGoogleApiClient = null;
        mCastDevice = null;
        mSessionId = null;
    }

    /**
     * Utility method that we use to manage the connected state
     *
     * @param data The data from the connected state
     */
    private void manageConnectedState(final Bundle data) {
        // In this case we're connected so we can launch the application
        Cast.CastApi.launchApplication(mGoogleApiClient, APP_ID, false)
                .setResultCallback(new ResultCallback<Cast.ApplicationConnectionResult>() {
                    @Override
                    public void onResult(Cast.ApplicationConnectionResult result) {
                        if (result.getStatus().isSuccess()) {
                            Log.d(TAG_LOG, "Successfully launched");
                            mApplicationStarted = true;
                            // The session Id
                            mSessionId = result.getSessionId();
                            // We create the channel
                            mLogChannelCallback = new LogChannelCallback();
                            try {
                                Cast.CastApi.setMessageReceivedCallbacks(mGoogleApiClient,
                                        mLogChannelCallback.getNamespace(),
                                        mLogChannelCallback);
                            } catch (IOException e) {
                                Log.e(TAG_LOG, "Creation channel error!", e);
                            }
                            // We initialize the RemoteMediaPLayer
                            initRemoteMediaPlayer();
                        } else {
                            Log.d(TAG_LOG, "Launch failed!");
                            mApplicationStarted = false;
                        }
                    }
                });
    }

    /**
     * Utility method that init the RemoteMediaPlayer
     */
    private void initRemoteMediaPlayer() {
        // We create the RemoteMediaPLayer
        mRemoteMediaPlayer = new RemoteMediaPlayer();
        try {
            Cast.CastApi.setMessageReceivedCallbacks(mGoogleApiClient,
                    mRemoteMediaPlayer.getNamespace(),
                    mRemoteMediaPlayer);
        } catch (IOException e) {
            Log.e(TAG_LOG, "mRemoteMediaPlayer channel error!", e);
        }
        // We have to synch the state
        mRemoteMediaPlayer
                .requestStatus(mGoogleApiClient)
                .setResultCallback(
                        new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                            @Override
                            public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                                if (result.getStatus().isSuccess()) {
                                    Log.d(TAG_LOG, "Sync successful");
                                } else {
                                    Log.e(TAG_LOG, "Failed to request status.");
                                }
                            }
                        });
    }

    /**
     * Utility method that send a message on the custom channel
     *
     * @param message The message to send
     */
    public void sendCustomMessage(final String message) {
        // We check for the GoogleApiClient and the Channel callback implementation
        if (mGoogleApiClient == null || mLogChannelCallback == null) {
            Log.e(TAG_LOG, "Initialization failed!");
            return;
        }
        try {
            Cast.CastApi.sendMessage(mGoogleApiClient, mLogChannelCallback.getNamespace(), message)
                    .setResultCallback(
                            new ResultCallback<Status>() {
                                @Override
                                public void onResult(Status result) {
                                    if (result.isSuccess()) {
                                        Toast.makeText(MainActivity.this,
                                                R.string.channel_send_message_success,
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(MainActivity.this,
                                                R.string.channel_send_message_error,
                                                Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
        } catch (Exception e) {
            Log.e(TAG_LOG, "Error sending message", e);
            Toast.makeText(this, R.string.channel_send_message_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Utility method to send a message to play a video
     */
    private void sendVideo() {
        if (mGoogleApiClient == null) {
            return;
        }
        // We create the MediaMetadata object to set the info about the Video
        MediaMetadata mediaMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, "FriendFence Video");
        MediaInfo mediaInfo = new MediaInfo.Builder(VIDEO_URL)
                .setContentType(MP4_MEDIA_TYPE)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
        try {
            mRemoteMediaPlayer.load(mGoogleApiClient, mediaInfo, true)
                    .setResultCallback(new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                        @Override
                        public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                            if (result.getStatus().isSuccess()) {
                                Toast.makeText(MainActivity.this,
                                        R.string.channel_send_message_success,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(MainActivity.this,
                                        R.string.channel_send_message_error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } catch (IllegalStateException e) {
            Log.e(TAG_LOG, "Problem occurred with media during loading", e);
        } catch (Exception e) {
            Log.e(TAG_LOG, "Problem opening media during loading", e);
        }
    }

    /**
     * Utility method to send a message to pause a video
     */
    private void pauseVideo() {
        if (mGoogleApiClient == null) {
            return;
        }
        mRemoteMediaPlayer.pause(mGoogleApiClient).setResultCallback(
                new ResultCallback<RemoteMediaPlayer.MediaChannelResult>() {
                    @Override
                    public void onResult(RemoteMediaPlayer.MediaChannelResult result) {
                        if (result.getStatus().isSuccess()) {
                            Toast.makeText(MainActivity.this,
                                    R.string.channel_paused_success,
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this,
                                    R.string.channel_paused_error,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

}
