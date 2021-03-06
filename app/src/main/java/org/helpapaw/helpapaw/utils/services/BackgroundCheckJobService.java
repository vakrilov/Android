package org.helpapaw.helpapaw.utils.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.helpapaw.helpapaw.R;
import org.helpapaw.helpapaw.data.models.Signal;
import org.helpapaw.helpapaw.data.repositories.SignalRepository;
import org.helpapaw.helpapaw.signalsmap.SignalsMapActivity;
import org.helpapaw.helpapaw.utils.Injection;
import org.helpapaw.helpapaw.utils.StatusUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.helpapaw.helpapaw.data.models.Signal.SOLVED;
import static org.helpapaw.helpapaw.data.models.Signal.SOMEBODY_ON_THE_WAY;
import static org.helpapaw.helpapaw.signalsmap.SignalsMapPresenter.DEFAULT_SEARCH_RADIUS;

/**
 * Created by milen on 20/08/17.
 */

public class BackgroundCheckJobService extends JobService {

    public static final String TAG = BackgroundCheckJobService.class.getSimpleName();
    static final String CURRENT_NOTIFICATION_IDS = "CurrentNotificationIds";

    HashSet<String>     mCurrentNotificationIds = new HashSet<>();
    NotificationManager mNotificationManager;
    SharedPreferences   mSharedPreferences;

    @Override
    public boolean onStartJob(final JobParameters job) {

        Log.d(TAG, "onStartJob called");

        mNotificationManager    = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        mSharedPreferences      = getApplicationContext().getSharedPreferences(TAG, Context.MODE_PRIVATE);

        // Do some work here
        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            FusedLocationProviderClient mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            mFusedLocationClient.getLastLocation()
            .addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    //Got last known location. In some rare situations this can be null.
                    if (location != null) {
                        getSignalsForLastKnownLocation(location, job);
                    }
                    else {
                        Log.d(TAG, "got callback but last location is null");
                        jobFinished(job, true);
                    }
                }
            })
            .addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    Log.d(TAG, "failed to get location");
                    jobFinished(job, true);
                }
            });
        }
        else {
            Log.d(TAG, "No location permission");
        }

        return true; // Answers the question: "Is there still work going on?"
    }

    @Override
    public boolean onStopJob(JobParameters job) {
        return true; // Answers the question: "Should this job be retried?"
    }

    private void getSignalsForLastKnownLocation(Location location, final JobParameters job) {

        Injection.getSignalRepositoryInstance().getAllSignals(location.getLatitude(), location.getLongitude(), DEFAULT_SEARCH_RADIUS, new SignalRepository.LoadSignalsCallback() {
                @Override
                public void onSignalsLoaded(List<Signal> signals) {

                    Log.d(TAG, "got signals");

                    if (signals != null && !signals.isEmpty()) {

                        for (Signal signal : signals) {
                            if (signal.getStatus() < SOLVED) {

                                showNotificationForSignal(signal);
                                mCurrentNotificationIds.add(signal.getId());
                            }
                        }
                    }

                    // Cancel all previous notifications that are not currently present
                    Set<String> oldNotificationIds = mSharedPreferences.getStringSet(CURRENT_NOTIFICATION_IDS, null);
                    if (oldNotificationIds != null) {
                        for (String id : oldNotificationIds) {
                            if (!mCurrentNotificationIds.contains(id)) {
                                mNotificationManager.cancel(id.hashCode());
                            }
                        }
                    }

                    // Save ids of current notifications
                    SharedPreferences.Editor editor = mSharedPreferences.edit();
                    editor.putStringSet(CURRENT_NOTIFICATION_IDS, mCurrentNotificationIds);
                    editor.apply();

                    jobFinished(job, false);
                }

                @Override
                public void onSignalsFailure(String message) {
                    Log.d(TAG, "there was a problem obtaining signals: " + message);
                    jobFinished(job, true);
                }
            });

    }

    private void showNotificationForSignal(Signal signal) {

        Context context = getApplicationContext();
        Integer signalCode = signal.getId().hashCode();

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(getApplicationContext());
        mBuilder.setSmallIcon(R.drawable.ic_paw_notif);
        mBuilder.setTicker(getString(R.string.txt_new_signal));
        mBuilder.setContentTitle(signal.getTitle());
        mBuilder.setDefaults(Notification.DEFAULT_ALL);
        mBuilder.setWhen(signal.getDateSubmitted().getTime());
        mBuilder.setOngoing(true);
        mBuilder.setAutoCancel(false);
        mBuilder.setOnlyAlertOnce(true);

        String status = "Status: ";
        if (signal.getStatus() == SOMEBODY_ON_THE_WAY) {
            status += getString(R.string.txt_somebody_is_on_the_way);
        }
        else {
            status += getString(R.string.txt_you_help_is_needed);
        }

        Bitmap pin = BitmapFactory.decodeResource(getResources(), StatusUtils.getPinResourceForCode(signal.getStatus()));
        Bitmap largeIcon = scaleBitmapForLargeIcon(pin);

        mBuilder.setContentText(status);
        mBuilder.setLargeIcon(largeIcon);

        Intent resultIntent = new Intent(context, SignalsMapActivity.class);
        resultIntent.putExtra(Signal.KEY_FOCUSED_SIGNAL_ID, signal.getId());

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(signalCode, PendingIntent.FLAG_UPDATE_CURRENT);

        mBuilder.setContentIntent(resultPendingIntent);

        mNotificationManager.notify(signalCode, mBuilder.build());
    }

    private Bitmap scaleBitmapForLargeIcon(Bitmap bmp) {
        Resources res = this.getResources();
        double ratio = (double)bmp.getHeight() / (double)bmp.getWidth();
        int height = (int) res.getDimension(android.R.dimen.notification_large_icon_height);
        int width = (int) (height / ratio);

        return addTransparentSideBorder(bmp, height - width);
    }

    //https://stackoverflow.com/a/15525394/2781218
    private Bitmap addTransparentSideBorder(Bitmap bmp, int borderSize) {
        Bitmap bmpWithBorder = Bitmap.createBitmap(bmp.getWidth() + borderSize, bmp.getHeight(), bmp.getConfig());
        Canvas canvas = new Canvas(bmpWithBorder);
        canvas.drawColor(Color.TRANSPARENT);
        canvas.drawBitmap(bmp, borderSize / 2, 0, null);
        return bmpWithBorder;
    }
}
