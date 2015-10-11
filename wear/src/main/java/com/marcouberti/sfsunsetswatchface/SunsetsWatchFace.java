package com.marcouberti.sfsunsetswatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class SunsetsWatchFace extends CanvasWatchFaceService {

    private static final String TAG = "NatureGradientsFace";

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD);
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static long INTERACTIVE_UPDATE_RATE_MS = 20;
    private static final long FLUID_MODE_UPDATE_RATE_MS = 20;
    private static final long BATTERY_SAVING_MODE_UPDATE_RATE_MS = 1000*10;

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    Shader shaderBackground, shaderSky;
    float[] frontOffset = new float[]{0,1};
    float[] skyOffset = new float[]{0,0.1f,0.25f,0.4f,0.7f,1};

    Calendar mCalendar;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
        Paint mBackgroundPaint;
        Paint mDatePaint;
        Paint bitmapPaint, paint;
        Paint skyPaint;
        Paint mTimePaint;
        boolean mAmbient;
        Time mTime;
        boolean mIsRound =false;


        int[] frontGradient;
        int[] skyGradient;
        Bitmap maskFront;
        Bitmap front;
        Bitmap ambient;
        Canvas frontCanvas;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        GoogleApiClient mGoogleApiClient = new GoogleApiClient.Builder(SunsetsWatchFace.this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mIsRound = insets.isRound();
            if(mIsRound) {
                mTimePaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_round));
            }else{
                mTimePaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_square));
            }
        }

        int INFO_DETAILS_MODE = 0;
        @Override
        public void onTapCommand(@TapType int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case WatchFaceService.TAP_TYPE_TAP:
                    //switch between date infos
                    if(INFO_DETAILS_MODE == 0) INFO_DETAILS_MODE =1;
                    else INFO_DETAILS_MODE = 0;
                    invalidate();
                    break;

                case WatchFaceService.TAP_TYPE_TOUCH:
                    break;
                case WatchFaceService.TAP_TYPE_TOUCH_CANCEL:
                    break;

                default:
                    super.onTapCommand(tapType, x, y, eventTime);
                    break;
            }
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunsetsWatchFace.this)
                    .setAcceptsTapEvents(true)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false).
                    setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .build());

            Resources resources = SunsetsWatchFace.this.getResources();

            maskFront = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.front);
            ambient = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.drawable.ambient);

            frontGradient = resources.getIntArray(R.array.front_array);
            skyGradient = resources.getIntArray(R.array.sky_array);

            bitmapPaint = new Paint();
            bitmapPaint.setAntiAlias(true);
            paint = new Paint();
            paint.setAntiAlias(true);
            skyPaint = new Paint();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setAntiAlias(true);
            mBackgroundPaint.setColor(Color.YELLOW);


            mTimePaint = new Paint();
            mTimePaint.setColor(resources.getColor(R.color.time_date_color));
            mTimePaint.setAntiAlias(true);
            mTimePaint.setStrokeCap(Paint.Cap.BUTT);
            mTimePaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Light.ttf"));
            mTimePaint.setTextSize(getResources().getDimension(R.dimen.font_size_time_round));
            //mTimePaint.setShadowLayer(1, 1, 1, resources.getColor(R.color.front_orange));

            mDatePaint = new Paint();
            mDatePaint.setColor(resources.getColor(R.color.time_date_color));
            mDatePaint.setAntiAlias(true);
            mDatePaint.setStrokeCap(Paint.Cap.BUTT);
            mDatePaint.setTypeface(NORMAL_TYPEFACE);
            mDatePaint.setTextSize(getResources().getDimension(R.dimen.font_size_date));
            mDatePaint.setTypeface(Typeface.createFromAsset(getApplicationContext().getAssets(), "fonts/Dolce Vita Light.ttf"));

            mTime = new Time();
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                }
                //invalidate();
            }
            invalidate();
            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            int width = bounds.width();
            int height = bounds.height();

            float secondsRotation;

            //final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;


            //Battery saving mode - sun fixed at specific hours in the sky
            if(INTERACTIVE_UPDATE_RATE_MS == BATTERY_SAVING_MODE_UPDATE_RATE_MS) {
                int hours = mTime.hour;
                secondsRotation = 360f * hours / 23f + 180f;
            }else {
                /*
                 * These calculations reflect the rotation in degrees per unit of time, e.g.,
                 * 360 / 60 = 6 and 360 / 12 = 30.
                 */
                final float seconds = (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
                secondsRotation = seconds * 6f;
            }

            frontGradient = getResources().getIntArray(R.array.front_array);
            skyGradient = getResources().getIntArray(R.array.sky_array);

            int SUN_FROM_OUT = bounds.width()/4;
            int SUN_RADIUS = bounds.width()/8;

            double X =+(SUN_FROM_OUT)*Math.sin(Math.toRadians(secondsRotation)) +bounds.width()/2;
            double Y =-(SUN_FROM_OUT)*Math.cos(Math.toRadians(secondsRotation)) +bounds.height()/2;


            shaderBackground = new RadialGradient(
                    (int)X, (int)Y, bounds.width(),
                    frontGradient,
                    frontOffset,
                    Shader.TileMode.MIRROR);

            mBackgroundPaint.setShader(shaderBackground);

            shaderSky = new RadialGradient(
                    (int)X, (int)Y, bounds.width(),
                    skyGradient,
                    skyOffset,
                    Shader.TileMode.MIRROR);

            skyPaint.setShader(shaderSky);

            if(front == null) {
                front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                frontCanvas = new Canvas(front);
            }

            //AMBIET MODE
            if (!mAmbient && !hide) {
                canvas.drawRect(bounds,skyPaint);
                canvas.save();
                canvas.rotate(secondsRotation,bounds.width()/2f,bounds.height()/2f);
                paint.setColor(getResources().getColor(R.color.sun_color));
                canvas.drawCircle(bounds.width() / 2, bounds.width()/2-SUN_FROM_OUT, SUN_RADIUS,paint);
                canvas.restore();
                //generate front bitmap
                frontCanvas.drawRect(bounds, mBackgroundPaint);
                bitmapPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
                frontCanvas.drawBitmap(maskFront, new Rect(0, 0, maskFront.getWidth(), maskFront.getHeight()), bounds, bitmapPaint);

                canvas.drawBitmap(front, new Rect(0,0,front.getWidth(),front.getHeight()), bounds, paint);
            }else {
                //BLACK BG TO SAVE ENERGY
                //canvas.drawColor(Color.BLACK);
                canvas.drawBitmap(ambient, new Rect(0,0,ambient.getWidth(),ambient.getHeight()), bounds, paint);
            }
            //TIME TEXT
            Rect fontBounds = new Rect();
            String TIME_FORMAT = "hh:mm";
            if(android.text.format.DateFormat.is24HourFormat(getApplicationContext())) {
                TIME_FORMAT = "HH:mm";
            }

            //TIME TEXT
            String word  = new SimpleDateFormat(TIME_FORMAT).format(Calendar.getInstance().getTime());
            //remove first 0
            if(word.startsWith("0")){
                word = word.substring(1);
            }
            mTimePaint.getTextBounds(word, 0, word.length(), fontBounds);

            int left = (int)((double)width/(double)2 - (double)fontBounds.width()/(double)2);
            //int top = (int)((double)height/2 + (double)(fontBounds.height()/2));
            int top = (int)(height/1.3f);
            canvas.drawText(word, left, top, mTimePaint);

            //compute the height of an A
            Rect singleCharBounds = new Rect();
            mDatePaint.getTextBounds("A", 0, "A".length(), singleCharBounds);
            int A_HEIGHT = singleCharBounds.height();

            //MODE DAY WEEK AND DATE
            if(INFO_DETAILS_MODE == 0) {
                //WEEK DAY AND DAY OF MONTH
                String weekDay = new SimpleDateFormat("EEEE d").format(Calendar.getInstance().getTime()).toUpperCase();

                Rect dateBounds = new Rect();
                String format = weekDay.toUpperCase();
                mDatePaint.getTextBounds(format, 0, format.length(), dateBounds);

                int dateLeft = (int) ((double) width / (double) 2 - (double) dateBounds.width() / (double) 2);
                canvas.drawText(format, dateLeft, top + A_HEIGHT * 2, mDatePaint);
            } else if(INFO_DETAILS_MODE == 1) {
                //DATE TEXT
                Rect dateBounds = new Rect();
                Locale current = getResources().getConfiguration().locale;
                DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG, current);
                //String pattern       = ((SimpleDateFormat)formatter).toPattern();
                String localPattern = ((SimpleDateFormat) formatter).toLocalizedPattern();
                String format = new SimpleDateFormat(localPattern).format(Calendar.getInstance().getTime()).toUpperCase();
                mDatePaint.getTextBounds(format, 0, format.length(), dateBounds);

                int dateLeft = (int) ((double) width / (double) 2 - (double) dateBounds.width() / (double) 2);
                canvas.drawText(format, dateLeft, top + A_HEIGHT * 2, mDatePaint);
            } else {
                //No data show
            }
        }

        boolean hide = false;
        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                hide = false;
                mGoogleApiClient.connect();
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                hide= true;
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunsetsWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunsetsWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void updateConfigDataItemAndUiOnStartup() {
            SunsetsWatchFaceUtil.fetchConfigDataMap(mGoogleApiClient,
                    new SunsetsWatchFaceUtil.FetchConfigDataMapCallback() {
                        @Override
                        public void onConfigDataMapFetched(DataMap startupConfig) {
                            // If the DataItem hasn't been created yet or some keys are missing,
                            // use the default values.
                            setDefaultValuesForMissingConfigKeys(startupConfig);
                            SunsetsWatchFaceUtil.putConfigDataItem(mGoogleApiClient, startupConfig);

                            updateUiForConfigDataMap(startupConfig);
                        }
                    }
            );
        }

        private void setDefaultValuesForMissingConfigKeys(DataMap config) {
            addIntKeyIfMissing(config, SunsetsWatchFaceUtil.KEY_BACKGROUND_COLOR,
                    SunsetsWatchFaceUtil.COLOR_VALUE_DEFAULT_AND_AMBIENT_BACKGROUND);
        }

        private void addIntKeyIfMissing(DataMap config, String key, int color) {
            if (!config.containsKey(key)) {
                config.putInt(key, color);
            }
        }

        @Override // DataApi.DataListener
        public void onDataChanged(DataEventBuffer dataEvents) {
            for (DataEvent dataEvent : dataEvents) {
                if (dataEvent.getType() != DataEvent.TYPE_CHANGED) {
                    continue;
                }

                DataItem dataItem = dataEvent.getDataItem();
                if (!dataItem.getUri().getPath().equals(
                        SunsetsWatchFaceUtil.PATH_WITH_FEATURE)) {
                    continue;
                }

                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItem);
                DataMap config = dataMapItem.getDataMap();
                Log.d(TAG, "Config DataItem updated:" + config);

                updateUiForConfigDataMap(config);
            }
        }

        private void updateUiForConfigDataMap(final DataMap config) {
            boolean uiUpdated = false;
            for (String configKey : config.keySet()) {
                if (!config.containsKey(configKey)) {
                    continue;
                }
                int color = config.getInt(configKey);
                Log.d(TAG, "Found watch face config key: " + configKey + " -> "
                            + color);

                if (updateUiForKey(configKey, color)) {
                    uiUpdated = true;
                }
            }
            if (uiUpdated) {
                invalidate();
            }
        }

        /**
         * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
         * {@code configKey} isn't recognized.
         *
         * @return whether UI has been updated
         */
        private boolean updateUiForKey(String configKey, int value) {
            if (configKey.equals(SunsetsWatchFaceUtil.KEY_BACKGROUND_COLOR)) {
                setBitmapID(value);
            }
            else if (configKey.equals(SunsetsWatchFaceUtil.KEY_FLUID_MODE)) {
                setFluidMode(value);
            }
            else {
                Log.w(TAG, "Ignoring unknown config key: " + configKey);
                return false;
            }
            return true;
        }

        private void setBitmapID(int color) {
            Log.d("Bitmap ID=",color+"");
            maskFront = BitmapFactory.decodeResource(getApplicationContext().getResources(), BitmapWatchFaceUtils.getBitmapResource(getApplicationContext(), color));
        }

        private void setFluidMode(int mode) {
            Log.d("FLUID MODE=",mode+"");
            if(mode == 0) {//fluid
                INTERACTIVE_UPDATE_RATE_MS = FLUID_MODE_UPDATE_RATE_MS;
            }else {//battery saving
                INTERACTIVE_UPDATE_RATE_MS = BATTERY_SAVING_MODE_UPDATE_RATE_MS;
            }
            updateTimer();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnected: " + connectionHint);
            }
            Wearable.DataApi.addListener(mGoogleApiClient, Engine.this);
            updateConfigDataItemAndUiOnStartup();
        }

        @Override  // GoogleApiClient.ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionSuspended: " + cause);
            }
        }

        @Override  // GoogleApiClient.OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onConnectionFailed: " + result);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunsetsWatchFace.Engine> mWeakReference;

        public EngineHandler(SunsetsWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunsetsWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

}
