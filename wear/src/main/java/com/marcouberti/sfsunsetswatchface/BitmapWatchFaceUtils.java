package com.marcouberti.sfsunsetswatchface;

import android.content.Context;

import java.util.HashMap;

public class BitmapWatchFaceUtils {

    static HashMap<String, Integer> map = new HashMap<>();
    static {
        map.put("San Francisco",0);
        map.put("New York",2);
        map.put("Seattle",3);
        map.put("Chicago",4);
        map.put("Miami",5);
        map.put("St. Louis",6);
        map.put("Los Angeles",7);
    }

    public static int getBitmapResource(Context ctx, int imageID) {
        if (imageID == 2) {
            return R.drawable.new_york;
        }else if (imageID == 3) {
            return R.drawable.seattle;
        }else if (imageID == 4) {
            return R.drawable.chicago;
        }else if (imageID == 5) {
            return R.drawable.miami;
        }else if (imageID == 6) {
            return R.drawable.st_luis;
        }else if (imageID == 7) {
            return R.drawable.los_angeles;
        }else {
            return R.drawable.front;
        }
    }

    public static int getBitmapPreviewResource(Context ctx, int imageID) {
        if (imageID == 2) {
            return R.drawable.preview_new_york;
        }else if (imageID == 3) {
            return R.drawable.preview_seattle;
        }else if (imageID == 4) {
            return R.drawable.preview_chicago;
        }else if (imageID == 5) {
            return R.drawable.preview_miami;
        }else if (imageID == 6) {
            return R.drawable.preview_st_luis;
        }else if (imageID == 7) {
            return R.drawable.preview_los_angeles;
        }else {
            return R.drawable.preview_sf;
        }
    }


    public static int getBitmapResource(Context ctx, String colorName) {
        if(colorName == null || !map.containsKey(colorName)) return R.drawable.front;
        return getBitmapResource(ctx, map.get(colorName));
    }

    public static int getBitmapPreviewResource(Context ctx, String colorName) {
        if(colorName == null || !map.containsKey(colorName)) return R.drawable.preview_sf;
        return getBitmapPreviewResource(ctx, map.get(colorName));
    }


    public static int getColorID(String colorName) {
        return map.get(colorName);
    }
}
