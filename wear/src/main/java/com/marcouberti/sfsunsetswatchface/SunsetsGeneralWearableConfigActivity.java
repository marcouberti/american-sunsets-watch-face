/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.marcouberti.sfsunsetswatchface;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.wearable.companion.WatchFaceCompanion;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.CircledImageView;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * The watch-side config activity for {@link SunsetsWatchFace}, which allows for setting the
 * background color.
 */
public class SunsetsGeneralWearableConfigActivity extends Activity implements
        WearableListView.ClickListener,
        WearableListView.OnScrollListener,
        DataApi.DataListener {
    private static final String TAG = "SunsetsGeneral";

    private static final String PATH_WITH_FEATURE = "/watch_face_config/Digital";
    private String mPeerId;
    private GoogleApiClient mGoogleApiClient;
    private TextView mHeader;
    WearableListView listView;
    String[] colors;
    int previewResource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_config);
        colors = new String[]{getResources().getString(R.string.sfondo),getResources().getString(R.string.fluid_motion)};
        mHeader = (TextView) findViewById(R.id.header);
        listView = (WearableListView) findViewById(R.id.color_picker);
        BoxInsetLayout content = (BoxInsetLayout) findViewById(R.id.content);
        // BoxInsetLayout adds padding by default on round devices. Add some on square devices.
        content.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                if (!insets.isRound()) {
                    v.setPaddingRelative(
                            (int) getResources().getDimensionPixelSize(R.dimen.content_padding_start),
                            v.getPaddingTop(),
                            v.getPaddingEnd(),
                            v.getPaddingBottom());
                }
                return v.onApplyWindowInsets(insets);
            }
        });

        listView.setHasFixedSize(true);
        listView.setClickListener(this);
        listView.addOnScrollListener(this);

        listView.setAdapter(new ColorListAdapter(colors));
        mPeerId = getIntent().getStringExtra(WatchFaceCompanion.EXTRA_PEER_ID);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(new GoogleApiClient.ConnectionCallbacks() {
                    @Override
                    public void onConnected(Bundle connectionHint) {
                        Log.d(TAG, "onConnected: " + connectionHint);
                        //Uri.Builder builder = new Uri.Builder();
                        //Uri uri = builder.scheme("wear").path(PATH_WITH_FEATURE).build();
                        //Wearable.DataApi.getDataItem(mGoogleApiClient, uri).setResultCallback(SunsetsGeneralWearableConfigActivity.this);
                        new Thread() {
                            @Override
                            public void run() {
                                PendingResult<DataItemBuffer> results = Wearable.DataApi.getDataItems(mGoogleApiClient,getUriForDataItem());
                                results.setResultCallback(new ResultCallback<DataItemBuffer>() {
                                    @Override
                                    public void onResult(DataItemBuffer dataItems) {
                                        if (dataItems.getCount() != 0) {
                                            for(int i=0; i<dataItems.getCount(); i++) {
                                                DataMapItem dataMapItem = DataMapItem.fromDataItem(dataItems.get(i));
                                                DataMap config = dataMapItem.getDataMap();
                                                Log.d(TAG, "startup setup UI...");
                                                updateUiForConfigDataMap(config);
                                            }
                                        }

                                        dataItems.release();
                                    }
                                });
                            }
                        }.start();

                        Wearable.DataApi.addListener(mGoogleApiClient, SunsetsGeneralWearableConfigActivity.this);
                    }

                    @Override
                    public void onConnectionSuspended(int cause) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnectionSuspended: " + cause);
                        }
                    }
                })
                .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                    @Override
                    public void onConnectionFailed(ConnectionResult result) {
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "onConnectionFailed: " + result);
                        }
                    }
                })
                .addApi(Wearable.API)
                .build();
    }

    private Uri getUriForDataItem() {
        // If you've put data on the local node
        String nodeId = getLocalNodeId();
        // Or if you've put data on the remote node
        // String nodeId = getRemoteNodeId();
        // Or If you already know the node id
        // String nodeId = "some_node_id";
        return new Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).authority(nodeId).path(SunsetsWatchFaceUtil.PATH_WITH_FEATURE).build();
    }

    private String getLocalNodeId() {
        NodeApi.GetLocalNodeResult nodeResult = Wearable.NodeApi.getLocalNode(mGoogleApiClient).await();
        return nodeResult.getNode().getId();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
        super.onStop();
    }

    @Override // WearableListView.ClickListener
    public void onClick(WearableListView.ViewHolder viewHolder) {
        ColorItemViewHolder colorItemViewHolder = (ColorItemViewHolder) viewHolder;
        int position = colorItemViewHolder.getAdapterPosition();

        if(position == 0) {//images
            Intent intent = new Intent(this, SunsetsBackgroundWearableConfigActivity.class);
            startActivity(intent);
        }else if(position == 1){//fluid motion
            int fluid_mode = 0;
            if(colorItemViewHolder.mColorItem.getColorName().equalsIgnoreCase(getResources().getString(R.string.fluid_motion))){
                colors[1] = getResources().getString(R.string.battery_saving);
                listView.getAdapter().notifyItemRangeChanged(1,1);
                fluid_mode = 1;
            }else {
                colors[1] = getResources().getString(R.string.fluid_motion);
                listView.getAdapter().notifyItemRangeChanged(1,1);
                fluid_mode = 0;
            }

            updateConfigDataItem(SunsetsWatchFaceUtil.KEY_FLUID_MODE,fluid_mode);
        }
    }

    @Override // WearableListView.ClickListener
    public void onTopEmptyRegionClick() {}

    @Override // WearableListView.OnScrollListener
    public void onScroll(int scroll) {}

    @Override // WearableListView.OnScrollListener
    public void onAbsoluteScrollChange(int scroll) {
        float newTranslation = Math.min(-scroll, 0);
        mHeader.setTranslationY(newTranslation);
    }

    @Override // WearableListView.OnScrollListener
    public void onScrollStateChanged(int scrollState) {}

    @Override // WearableListView.OnScrollListener
    public void onCentralPositionChanged(int centralPosition) {}

    private void updateConfigDataItem(String key, final int colorID) {
        DataMap configKeysToOverwrite = new DataMap();
        configKeysToOverwrite.putInt(key,
                colorID);
        SunsetsWatchFaceUtil.overwriteKeysInConfigDataMap(mGoogleApiClient, configKeysToOverwrite);
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
    }

    /**
     * Updates the color of a UI item according to the given {@code configKey}. Does nothing if
     * {@code configKey} isn't recognized.
     *
     * @return whether UI has been updated
     */
    private boolean updateUiForKey(String configKey, final int value) {
        if (configKey.equals(SunsetsWatchFaceUtil.KEY_FLUID_MODE)) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (value == 1) {
                        colors[1] = getResources().getString(R.string.battery_saving);
                        listView.getAdapter().notifyItemRangeChanged(1, 1);
                    } else {
                        colors[1] = getResources().getString(R.string.fluid_motion);
                        listView.getAdapter().notifyItemRangeChanged(1, 1);
                    }
                }
            });
        }else if (configKey.equals(SunsetsWatchFaceUtil.KEY_BACKGROUND_COLOR)) {
            /*
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    previewResource = GradientsUtils.getBitmapResource(getApplicationContext(),value);
                    listView.getAdapter().notifyItemRangeChanged(0,1);
                }
            });
            */
        } else {
            Log.w(TAG, "Ignoring unknown config key: " + configKey);
            return false;
        }
        return true;
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    private class ColorListAdapter extends WearableListView.Adapter {
        private final String[] mColors;

        public ColorListAdapter(String[] colors) {
            mColors = colors;
        }

        @Override
        public ColorItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ColorItemViewHolder(new ColorItem(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
            ColorItemViewHolder colorItemViewHolder = (ColorItemViewHolder) holder;
            String colorName = mColors[position];
            colorItemViewHolder.mColorItem.setColor(colorName);
            colorItemViewHolder.mColorItem.setBitmapResource(previewResource);

            RecyclerView.LayoutParams layoutParams =
                    new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            int colorPickerItemMargin = (int) getResources()
                    .getDimension(R.dimen.digital_config_color_picker_item_margin);
            // Add margins to first and last item to make it possible for user to tap on them.
            if (position == 0) {
                layoutParams.setMargins(0, colorPickerItemMargin, 0, 0);
            } else if (position == mColors.length - 1) {
                layoutParams.setMargins(0, 0, 0, colorPickerItemMargin);
            } else {
                layoutParams.setMargins(0, 0, 0, 0);
            }
            colorItemViewHolder.itemView.setLayoutParams(layoutParams);
        }

        @Override
        public int getItemCount() {
            return mColors.length;
        }
    }

    /** The layout of a color item including image and label. */
    private static class ColorItem extends LinearLayout implements
            WearableListView.OnCenterProximityListener {
        /** The duration of the expand/shrink animation. */
        private static final int ANIMATION_DURATION_MS = 150;
        /** The ratio for the size of a circle in shrink state. */
        private static final float SHRINK_CIRCLE_RATIO = .75f;

        private static final float SHRINK_LABEL_ALPHA = .5f;
        private static final float EXPAND_LABEL_ALPHA = 1f;

        private final TextView mLabel;
        private final CustomCircledImageView mColor;
        private String colorName;
        private int bitmapResource;

        private final float mExpandCircleRadius;
        private final float mShrinkCircleRadius;

        private final ObjectAnimator mExpandCircleAnimator;
        private final ObjectAnimator mExpandLabelAnimator;
        private final AnimatorSet mExpandAnimator;

        private final ObjectAnimator mShrinkCircleAnimator;
        private final ObjectAnimator mShrinkLabelAnimator;
        private final AnimatorSet mShrinkAnimator;

        public ColorItem(Context context) {
            super(context);
            View.inflate(context, R.layout.setting_picker_item, this);

            mLabel = (TextView) findViewById(R.id.label);
            mColor = (CustomCircledImageView) findViewById(R.id.color);

            mExpandCircleRadius = mColor.getCircleRadius();
            mShrinkCircleRadius = mExpandCircleRadius * SHRINK_CIRCLE_RATIO;

            mShrinkCircleAnimator = ObjectAnimator.ofFloat(mColor, "circleRadius",
                    mExpandCircleRadius, mShrinkCircleRadius);
            mShrinkLabelAnimator = ObjectAnimator.ofFloat(mLabel, "alpha",
                    EXPAND_LABEL_ALPHA, SHRINK_LABEL_ALPHA);
            mShrinkAnimator = new AnimatorSet().setDuration(ANIMATION_DURATION_MS);
            mShrinkAnimator.playTogether(mShrinkCircleAnimator, mShrinkLabelAnimator);

            mExpandCircleAnimator = ObjectAnimator.ofFloat(mColor, "circleRadius",
                    mShrinkCircleRadius, mExpandCircleRadius);
            mExpandLabelAnimator = ObjectAnimator.ofFloat(mLabel, "alpha",
                    SHRINK_LABEL_ALPHA, EXPAND_LABEL_ALPHA);
            mExpandAnimator = new AnimatorSet().setDuration(ANIMATION_DURATION_MS);
            mExpandAnimator.playTogether(mExpandCircleAnimator, mExpandLabelAnimator);
        }

        @Override
        public void onCenterPosition(boolean animate) {
            if (animate) {
                mShrinkAnimator.cancel();
                if (!mExpandAnimator.isRunning()) {
                    mExpandCircleAnimator.setFloatValues(mColor.getCircleRadius(), mExpandCircleRadius);
                    mExpandLabelAnimator.setFloatValues(mLabel.getAlpha(), EXPAND_LABEL_ALPHA);
                    mExpandAnimator.start();
                }
            } else {
                mExpandAnimator.cancel();
                mColor.setCircleRadius(mExpandCircleRadius);
                mLabel.setAlpha(EXPAND_LABEL_ALPHA);
            }
        }

        @Override
        public void onNonCenterPosition(boolean animate) {
            if (animate) {
                mExpandAnimator.cancel();
                if (!mShrinkAnimator.isRunning()) {
                    mShrinkCircleAnimator.setFloatValues(mColor.getCircleRadius(), mShrinkCircleRadius);
                    mShrinkLabelAnimator.setFloatValues(mLabel.getAlpha(), SHRINK_LABEL_ALPHA);
                    mShrinkAnimator.start();
                }
            } else {
                mShrinkAnimator.cancel();
                mColor.setCircleRadius(mShrinkCircleRadius);
                mLabel.setAlpha(SHRINK_LABEL_ALPHA);
            }
        }

        private void setBitmapResource(int resId) {
            bitmapResource = resId;
            //mColor.bitmapResource = resId;
        }

        private void setColor(String cName) {
            mLabel.setText(cName);
            colorName = cName;
            //cambio immagine
            if(cName.equalsIgnoreCase(getResources().getString(R.string.sfondo))) {
                mColor.setBitmapResource(R.drawable.config_choose_image);
            }else if(cName.equalsIgnoreCase(getResources().getString(R.string.battery_saving))) {
                mColor.setBitmapResource(R.drawable.config_battery_saving);
            }else if(cName.equalsIgnoreCase(getResources().getString(R.string.fluid_motion))) {
                mColor.setBitmapResource(R.drawable.config_battery_saving);
            }
        }

        private int getColor() {
            return mColor.getDefaultCircleColor();
        }

        public String getColorName() {
            return colorName;
        }
    }

    private static class ColorItemViewHolder extends WearableListView.ViewHolder {
        public final ColorItem mColorItem;

        public ColorItemViewHolder(ColorItem colorItem) {
            super(colorItem);
            mColorItem = colorItem;
        }
    }
}
