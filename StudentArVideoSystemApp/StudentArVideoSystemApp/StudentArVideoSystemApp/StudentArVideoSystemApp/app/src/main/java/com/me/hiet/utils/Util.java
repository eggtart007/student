package com.me.hiet.utils;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.PointF;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.me.hiet.edit.record.RecordFxListItem;
import com.me.hiet.edit.record.SqAreaInfo;
import com.me.hiet.edit.view.CommonDialog;
import com.me.hiet.interfaces.TipsButtonClickListener;
import com.me.hiet.utils.dataInfo.CaptionInfo;
import com.me.hiet.utils.dataInfo.ClipInfo;
import com.me.hiet.utils.dataInfo.MusicCaptionInfo;
import com.meicam.sdk.NvsAVFileInfo;
import com.meicam.sdk.NvsSize;
import com.meicam.sdk.NvsStreamingContext;
import com.meicam.sdk.NvsTimelineCaption;
import com.meicam.sdk.NvsVideoFrameRetriever;
import com.meicam.sdk.NvsVideoResolution;
import com.meishe.sdkdemo.R;
import com.me.hiet.utils.asset.NvAsset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.meicam.sdk.NvsAVFileInfo.AV_FILE_TYPE_IMAGE;

public class Util {
    private final static String TAG = "Util";

    public static boolean isBackground(Context context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String packageName = context.getPackageName();

        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager
                .getRunningAppProcesses();
        if (appProcesses == null)
            return true;

        for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
            // The name of the process that this object is associated with.
            if (appProcess.processName.equals(packageName)
                    && appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return false;
            }
        }

        return true;

    }

    //把String转化为double
    public static double convertToDouble(String number, double defaultValue) {
        if (TextUtils.isEmpty(number)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(number);
        } catch (Exception e) {
            return defaultValue;
        }

    }

    public static ArrayList<Bitmap> getBitmapListFromClipList(Context context, ArrayList<ClipInfo> clipInfoArrayList) {
        ArrayList<Bitmap> bitmapArrayList = new ArrayList<>();
        for (int i = 0; i < clipInfoArrayList.size(); i++) {
            ClipInfo clipInfo = clipInfoArrayList.get(i);
            if (clipInfo == null) {
                bitmapArrayList.add(null);
                continue;
            }

            Bitmap bitmap = getBitmapFromClipInfo(context, clipInfo);
            bitmapArrayList.add(bitmap);
        }

        return bitmapArrayList;
    }

    public static Bitmap getBitmapFromClipInfo(Context context, ClipInfo clipInfo) {
        Bitmap bitmap = null;
        String clipPath = clipInfo.getFilePath();
        if (TextUtils.isEmpty(clipPath)) {
            return bitmap;
        }

        long timeStamp = clipInfo.getTrimIn();
        if (timeStamp < 0) {
            timeStamp = 0;
        }

        NvsStreamingContext streamingContext = NvsStreamingContext.getInstance();
        if (context == null)
            return bitmap;

        NvsAVFileInfo fileInfo = streamingContext.getAVFileInfo(clipPath);
        if (fileInfo == null) {
            bitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.edit_clip_default_bg);
            bitmap = Bitmap.createScaledBitmap(bitmap, 540, 960, true);
            return bitmap;
        }
        if (fileInfo.getAVFileType() == AV_FILE_TYPE_IMAGE) {
            bitmap = BitmapFactory.decodeFile(clipPath);
            bitmap = centerSquareScaleBitmap(bitmap, 180, 320);
        } else {
            NvsVideoFrameRetriever videoFrameRetriever = streamingContext.createVideoFrameRetriever(clipPath);
            if (videoFrameRetriever != null)
                bitmap = videoFrameRetriever.getFrameAtTime(timeStamp, NvsVideoFrameRetriever.VIDEO_FRAME_HEIGHT_GRADE_480);
            bitmap = centerSquareScaleBitmap(bitmap, 180, 320);
        }

        return bitmap;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and
            // keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    public static boolean getBundleFilterInfo(Context context, ArrayList<NvAsset> assetArrayList, String bundlePath) {
        if (context == null)
            return false;

        if (TextUtils.isEmpty(bundlePath))
            return false;

        try {
            InputStream nameListStream = context.getAssets().open(bundlePath);
            BufferedReader nameListBuffer = new BufferedReader(new InputStreamReader(nameListStream, "GBK"));

            String strLine;
            while ((strLine = nameListBuffer.readLine()) != null) {
                String[] strNameArray = strLine.split(",");
                if (strNameArray.length < 3)
                    continue;

                for (int i = 0; i < assetArrayList.size(); ++i) {
                    NvAsset assetItem = assetArrayList.get(i);
                    if (assetItem == null)
                        continue;

                    if (!assetItem.isReserved)
                        continue;

                    String packageId = assetItem.uuid;
                    if (TextUtils.isEmpty(packageId))
                        continue;

                    if (packageId.equals(strNameArray[0])) {
                        assetItem.name = strNameArray[1];
                        assetItem.aspectRatio = Integer.parseInt(strNameArray[2]);
                        break;
                    }

                }
            }
            nameListBuffer.close();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static Bitmap centerSquareScaleBitmap(Bitmap bitmap, int width, int height) {
        if (null == bitmap || width <= 0 || height <= 0) {
            return null;
        }

        Bitmap result = bitmap;
        int widthOrg = bitmap.getWidth();
        int heightOrg = bitmap.getHeight();

        if (widthOrg > width && heightOrg > height) {
            //压缩到一个最小长度是edgeLength的bitmap
            float wRemainder = widthOrg / (float) width;
            float hRemainder = heightOrg / (float) height;

            int scaledWidth;
            int scaledHeight;
            if (wRemainder > hRemainder) {
                scaledWidth = (int) (widthOrg / hRemainder);
                scaledHeight = (int) (heightOrg / hRemainder);
            } else {
                scaledWidth = (int) (widthOrg / wRemainder);
                scaledHeight = (int) (heightOrg / wRemainder);
            }

            Bitmap scaledBitmap;
            try {
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
            } catch (Exception e) {
                return null;
            }

            //从图中截取正中间的部分。
            int xTopLeft = (scaledWidth - width) / 2;
            int yTopLeft = (scaledHeight - height) / 2;

            try {
                result = Bitmap.createBitmap(scaledBitmap, xTopLeft, yTopLeft, width, height);
                scaledBitmap.recycle();
            } catch (Exception e) {
                return null;
            }
        }

        return result;
    }

    public static void showDialog(Context context, final String title, final String first_tip, final String second_tip) {
        final CommonDialog dialog = new CommonDialog(context, 1);
        dialog.setOnCreateListener(new CommonDialog.OnCreateListener() {
            @Override
            public void OnCreated() {
                dialog.setTitleTxt(title);
                dialog.setFirstTipsTxt(first_tip);
                dialog.setSecondTipsTxt(second_tip);
            }
        });
        dialog.setOnBtnClickListener(new CommonDialog.OnBtnClickListener() {
            @Override
            public void OnOkBtnClicked(View view) {
                dialog.dismiss();
            }

            @Override
            public void OnCancelBtnClicked(View view) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    public static void showDialog(Context context, final String title, final String first_tip) {
        final CommonDialog dialog = new CommonDialog(context, 1);
        dialog.setOnCreateListener(new CommonDialog.OnCreateListener() {
            @Override
            public void OnCreated() {
                dialog.setTitleTxt(title);
                dialog.setFirstTipsTxt(first_tip);
            }
        });
        dialog.setOnBtnClickListener(new CommonDialog.OnBtnClickListener() {
            @Override
            public void OnOkBtnClicked(View view) {
                dialog.dismiss();
            }

            @Override
            public void OnCancelBtnClicked(View view) {
                dialog.dismiss();
            }
        });
        dialog.show();
    }

    public static void showDialog(Context context, final String title, final String first_tip, final TipsButtonClickListener tipsButtonClickListener) {
        final CommonDialog dialog = new CommonDialog(context, 1);
        dialog.setOnCreateListener(new CommonDialog.OnCreateListener() {
            @Override
            public void OnCreated() {
                dialog.setTitleTxt(title);
                dialog.setFirstTipsTxt(first_tip);
            }
        });
        dialog.setOnBtnClickListener(new CommonDialog.OnBtnClickListener() {
            @Override
            public void OnOkBtnClicked(View view) {
                dialog.dismiss();
                if (tipsButtonClickListener != null)
                    tipsButtonClickListener.onTipsButtoClick(view);
            }

            @Override
            public void OnCancelBtnClicked(View view) {
                dialog.dismiss();
            }
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    /**
     * 根据x坐标进行排序
     */
    public static class PointXComparator implements Comparator<PointF> {

        @Override
        public int compare(PointF bean1, PointF bean2) {
            return (int) (bean1.x - bean2.x);
        }
    }

    /**
     * 根据x坐标进行排序
     */
    public static class PointYComparator implements Comparator<PointF> {

        @Override
        public int compare(PointF bean1, PointF bean2) {
            return (int) (bean1.y - bean2.y);
        }
    }

    /**
     * 根据声效rank排序
     */
    public static class RecordFxIndexComparator implements Comparator<RecordFxListItem> {

        @Override
        public int compare(RecordFxListItem bean1, RecordFxListItem bean2) {
            return bean1.index - bean2.index;
        }
    }

    /**
     * 根据InPoint排序
     */
    public static class AreaInComparator implements Comparator<SqAreaInfo> {

        @Override
        public int compare(SqAreaInfo bean1, SqAreaInfo bean2) {
            return (int) (bean1.getInPoint() - bean2.getInPoint());
        }
    }

    /**
     * 根据OutPoint排序
     */
    public static class AreaOutComparator implements Comparator<SqAreaInfo> {

        @Override
        public int compare(SqAreaInfo bean1, SqAreaInfo bean2) {
            return (int) (bean2.getOutPoint() - bean1.getOutPoint());
        }
    }


    //保存数据
    public static CaptionInfo saveCaptionData(NvsTimelineCaption caption) {
        if (caption == null)
            return null;
        CaptionInfo captionInfo = new CaptionInfo();
        long inPoint = caption.getInPoint();
        captionInfo.setInPoint(inPoint);
        long outPoint = caption.getOutPoint();
        long captionDuration = outPoint - inPoint;
        captionInfo.setDuration(captionDuration);
        captionInfo.setText(caption.getText());
        captionInfo.setCaptionZVal((int) caption.getZValue());
        captionInfo.setAnchor(caption.getAnchorPoint());
        PointF pointF = caption.getCaptionTranslation();
        captionInfo.setTranslation(pointF);
        captionInfo.setBold(caption.getBold());
        captionInfo.setItalic(caption.getItalic());
        captionInfo.setShadow(caption.getDrawShadow());
        float fontSize = caption.getFontSize();
        captionInfo.setCaptionSize(fontSize);
        return captionInfo;
    }

    /**
     * 获取json文件记录的录音特效列表
     */
    public static List<RecordFxListItem> listRecordFxFromJson(Context context) {
        List<RecordFxListItem> fileList = new ArrayList<>();
        try {
            InputStreamReader isr = new InputStreamReader(context.getAssets().open("record/record.json"), "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();
            isr.close();
            JSONObject data = new JSONObject(builder.toString());
            JSONArray array = data.getJSONArray("record_fx");
            for (int i = 0; i < array.length(); i++) {
                JSONObject role = array.getJSONObject(i);
                RecordFxListItem audioFxListItem = new RecordFxListItem();
                audioFxListItem.fxName = role.getString("name");
                audioFxListItem.fxID = role.getString("builtin_fx_name");
                audioFxListItem.index = role.getInt("rank");

                switch (i) {
                    case 0:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_1);
                        break;
                    case 1:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_2);
                        break;
                    case 2:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_3);
                        break;
                    case 3:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_4);
                        break;
                    case 4:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_5);
                        break;
                    case 5:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_6);
                        break;
                    case 6:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_7);
                        break;
                    case 7:
                        audioFxListItem.image_drawable = ContextCompat.getDrawable(context, R.drawable.record_fx_8);
                        break;
                    default:
                        break;
                }

                fileList.add(audioFxListItem);
            }
            Collections.sort(fileList, new RecordFxIndexComparator()); // 排序

        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return fileList;
    }

    /**
     * 清除录音文件
     */
    public static void clearRecordAudioData() {
        String dir = PathUtils.getAudioRecordFilePath();
        if (dir == null) {
            return;
        }
        File record_dir = new File(dir);
        if (!record_dir.exists()) {
            return;
        }
        for (File file : record_dir.listFiles()) {
            if (file.isFile()) {
                file.delete();
            }
        }
    }


    /**
     * 将bitmap保存到SD卡
     */
    public static boolean saveBitmapToSD(Bitmap bt, String target_path) {
        if (bt == null || target_path == null || target_path.isEmpty()) {
            return false;
        }
        File file = new File(target_path);
        try {
            FileOutputStream out = new FileOutputStream(file);
            bt.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
            return true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static NvsVideoResolution getVideoEditResolution(int ratio) {
        int compileRes = ParameterSettingValues.instance().getCompileVideoRes();
        NvsVideoResolution videoEditRes = new NvsVideoResolution();
        Point size = new Point();
        if (ratio == NvAsset.AspectRatio_16v9) {
            size.set(compileRes * 16 / 9, compileRes);
        } else if (ratio == NvAsset.AspectRatio_1v1) {
            size.set(compileRes, compileRes);
        } else if (ratio == NvAsset.AspectRatio_9v16) {
            size.set(compileRes, compileRes * 16 / 9);
        } else if (ratio == NvAsset.AspectRatio_3v4) {
            size.set(compileRes, compileRes * 4 / 3);
        } else if (ratio == NvAsset.AspectRatio_4v3) {
            size.set(compileRes * 4 / 3, compileRes);
        } else {
            size.set(1280, 720);
        }
        videoEditRes.imageWidth = size.x;
        videoEditRes.imageHeight = size.y;
        Logger.e("getVideoEditResolution   ", videoEditRes.imageWidth + "     " + videoEditRes.imageHeight);
        return videoEditRes;
    }

    public static NvsSize getLiveWindowSize(NvsSize timelineSize, NvsSize parentSize, boolean fullScreen) {
        if (timelineSize.height > timelineSize.width && fullScreen)
            return parentSize;

        NvsSize size = new NvsSize(timelineSize.width, timelineSize.height);

        float scaleWidth = (float) parentSize.width / timelineSize.width;
        float scaleHeight = (float) parentSize.height / timelineSize.height;
        if (scaleWidth < scaleHeight) {
            int width = parentSize.width;
            size.height = width * size.height / size.width;
            size.width = width;
            if (size.height > parentSize.height)
                size.height = parentSize.height;
        } else {
            int height = parentSize.height;
            size.width = height * size.width / size.height;
            size.height = height;

            if (size.width > parentSize.width)
                size.width = parentSize.width;
        }
        return size;
    }

    /**
     * 获取json文件的配置信息更新素材
     */
    public static boolean getBundleFilterInfoFromJson(Context context, List<NvAsset> assetArrayList, String bundlePath) {
        if (context == null) {
            return false;
        }
        if (assetArrayList == null || bundlePath == null || bundlePath.isEmpty()) {
            return false;
        }
        try {
            InputStreamReader isr = new InputStreamReader(context.getAssets().open(bundlePath), "UTF-8");
            BufferedReader br = new BufferedReader(isr);
            String line;
            StringBuilder builder = new StringBuilder();
            while ((line = br.readLine()) != null) {
                builder.append(line);
            }
            br.close();
            isr.close();
            JSONObject data = new JSONObject(builder.toString());
            JSONArray array = data.getJSONArray("fx");
            for (int i = 0; i < array.length(); i++) {
                JSONObject role = array.getJSONObject(i);
                String id = role.getString("package_id");
                String name = role.getString("name");
                int rank = role.getInt("rank");
                int ratio = role.getInt("aspect_ratio");

                for (int j = 0; j < assetArrayList.size(); ++j) {
                    NvAsset assetItem = assetArrayList.get(j);
                    if (assetItem == null) {
                        continue;
                    }
                    if (!assetItem.isReserved) {
                        continue;
                    }
                    if (assetItem.uuid == null || assetItem.uuid.isEmpty()) {
                        continue;
                    }
                    if (assetItem.uuid.contains(id)) {
                        assetItem.name = name;
                        assetItem.aspectRatio = ratio;
                        break;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return true;
    }

    //获取所有权限列表(相机权限，麦克风权限，存储权限)
    public static List<String> getAllPermissionsList() {
        ArrayList<String> newList = new ArrayList<>();
        newList.add(Manifest.permission.CAMERA);
        newList.add(Manifest.permission.RECORD_AUDIO);
        newList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        newList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        return newList;
    }

    /**
     * 解析LRC歌词文件
     *
     * @param lrcFile lrc文件路径
     * @return
     */

    public static List<Map<Long, String>> analysisLrcFile(Context context, String lrcFile) {
        Boolean isFromAsset = true;
        // 存储所有歌词信息的容器
        List<Map<Long, String>> list = new ArrayList<Map<Long, String>>();
        InputStreamReader read;
        String encoding = "utf-8"; // 字符编码，若与歌词文件编码不符将会出现乱码

        if (lrcFile.equals("") || lrcFile == null) {
            Log.e(TAG, "invaild lrc file!");
            return null;
        }

        if (lrcFile.indexOf("assets:") == 0) {
            isFromAsset = true;
        } else {
            isFromAsset = false;
        }
        if (isFromAsset) {
            list = analysisLrcFileFromAsset(context, lrcFile);
        } else {
            list = analysisLrcFileFromSD(lrcFile);
        }
        return list;
    }

    private static List<Map<Long, String>> analysisLrcFileFromSD(String path) {
        // 存储所有歌词信息的容器
        List<Map<Long, String>> list = new ArrayList<Map<Long, String>>();
        try {
            String encoding = "utf-8"; // 字符编码，若与歌词文件编码不符将会出现乱码
            //String encoding = "GBK";
            File file = new File(path);
            if (file.isFile() && file.exists()) { // 判断文件是否存在
                InputStreamReader read = new InputStreamReader(
                        new FileInputStream(file), encoding);
                BufferedReader bufferedReader = new BufferedReader(read);
                String regex = "\\[(\\d{1,2}):(\\d{1,2}).(\\d{1,2})\\]"; // 正则表达式
                Pattern pattern = Pattern.compile(regex); // 创建 Pattern 对象
                String lineStr = null; // 每次读取一行字符串
                while ((lineStr = bufferedReader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(lineStr);
                    while (matcher.find()) {
                        // 用于存储当前时间和文字信息的容器
                        Map<Long, String> map = new HashMap<Long, String>();
                        // System.out.println(m.group(0)); // 例：[02:34.94]
                        // [02:34.94] ----对应---> [分钟:秒.毫秒]
                        String min = matcher.group(1); // 分钟
                        String sec = matcher.group(2); // 秒
                        String mill = matcher.group(3); // 毫秒，注意这里其实还要乘以10
                        long time = getLongTime(min, sec, mill + "0");
                        // 获取当前时间的歌词信息
                        String text = lineStr.substring(matcher.end());
                        if (!text.equals("")) {
                            map.put(time, text); // 添加到容器中
                            list.add(map);
                        }
                    }
                }
                read.close();
                return list;
            } else {
                Log.e(TAG, "找不到指定的文件:" + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取文件出错!");
            e.printStackTrace();
        }
        return null;
    }


    private static List<Map<Long, String>> analysisLrcFileFromAsset(Context context, String path) {
        // 存储所有歌词信息的容器
        List<Map<Long, String>> list = new ArrayList<Map<Long, String>>();
        String lrcpath = "";
        if (path.length() > 8) {
            lrcpath = path.substring(8);
        } else {
            return null;
        }
        try {
            String encoding = "utf-8"; // 字符编码，若与歌词文件编码不符将会出现乱码
            //String encoding = "GBK";
            if (!path.equals("")) { // 判断文件是否存在
                InputStreamReader read = new InputStreamReader(
                        context.getAssets().open(lrcpath), encoding);
                BufferedReader bufferedReader = new BufferedReader(read);
                String regex = "\\[(\\d{1,2}):(\\d{1,2}).(\\d{1,2})\\]"; // 正则表达式
                Pattern pattern = Pattern.compile(regex); // 创建 Pattern 对象
                String lineStr = null; // 每次读取一行字符串
                while ((lineStr = bufferedReader.readLine()) != null) {
                    Matcher matcher = pattern.matcher(lineStr);
                    while (matcher.find()) {
                        // 用于存储当前时间和文字信息的容器
                        Map<Long, String> map = new HashMap<Long, String>();
                        // System.out.println(m.group(0)); // 例：[02:34.94]
                        // [02:34.94] ----对应---> [分钟:秒.毫秒]
                        String min = matcher.group(1); // 分钟
                        String sec = matcher.group(2); // 秒
                        String mill = matcher.group(3); // 毫秒，注意这里其实还要乘以10
                        long time = getLongTime(min, sec, mill + "0");
                        // 获取当前时间的歌词信息
                        String text = lineStr.substring(matcher.end());
                        text = text.trim();
                        if (!text.equals("")) {
                            map.put(time, text); // 添加到容器中
                            list.add(map);
                        }
                    }
                }
                read.close();
                return list;
            } else {
                Log.e(TAG, "找不到指定的文件:" + path);
            }
        } catch (Exception e) {
            Log.e(TAG, "读取文件出错!");
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 将以字符串形式给定的分钟、秒钟、毫秒转换成一个以毫秒为单位的long型数
     *
     * @param min  分钟
     * @param sec  秒钟
     * @param mill 毫秒
     * @return
     */
    static long getLongTime(String min, String sec, String mill) {
        // 转成整型
        int m = Integer.parseInt(min);
        int s = Integer.parseInt(sec);
        int ms = Integer.parseInt(mill);

        if (s >= 60) {

            Log.e("警告: 出现了一个时间不正确的项 -->", "[" + min + ":" + sec + "."
                    + mill.substring(0, 2) + "]");
        }
        // 组合成一个长整型表示的以毫秒为单位的时间
        long time = m * 60 * 1000 + s * 1000 + ms;
        return time;
    }

    /**
     * @param lrcList    存储所有歌词信息的容器
     * @param musicStart 音乐开始时间  单位微妙
     * @param musicEnd   音乐结束时间  单位微妙
     * @param offsetTime 音乐开始时间对应Timeline上的开始时间  单位微妙
     * @return 返回对应的时间段内的字幕信息
     */
    public static ArrayList<MusicCaptionInfo> getCaptionInfoList(List<Map<Long, String>> lrcList, long musicStart, long musicEnd, long offsetTime) {
        ArrayList<MusicCaptionInfo> infoLists = new ArrayList<MusicCaptionInfo>();
        ArrayList<MusicCaptionInfo> tmpInfoLists = new ArrayList<MusicCaptionInfo>();
        int startIndex = -1;
        int endIndex = -1;
        if (musicStart < 0 || musicEnd < 0 || offsetTime < 0 || musicStart > musicEnd) {
            Log.e(TAG, "invaild Time!");
            return null;
        }
        if (lrcList != null && lrcList.size() > 0) {
            for (int i = 0; i < lrcList.size(); i++) {
                Map<Long, String> map = lrcList.get(i);
                for (Map.Entry<Long, String> entry : map.entrySet()) {
                    MusicCaptionInfo tmpInfos = new MusicCaptionInfo();
                    tmpInfos.setCaptionText(entry.getValue().toString());
                    tmpInfos.setCaptionStartTime(Long.valueOf(entry.getKey().toString()) * 1000);
                    tmpInfoLists.add(tmpInfos);

                    long startTime = Long.valueOf(entry.getKey().toString());   //毫秒

                    if (startTime < musicStart / 1000) {
                        startIndex = i;
                    } else if (i == 0) {
                        startIndex = i;
                    }
                    if (endIndex < 0 && startTime >= musicEnd / 1000) {
                        if (i != 0) {
                            endIndex = i - 1;
                        } else {
                            endIndex = i;
                        }
                    }

                    if (endIndex < 0 && musicEnd / 1000 >= startTime && i == lrcList.size() - 1) {
                        endIndex = i;
                    }
                }
            }

            if (tmpInfoLists.size() > 0 && startIndex != -1 && endIndex != -1 && startIndex < lrcList.size() && endIndex < lrcList.size() && startIndex <= endIndex) {
                for (int i = startIndex; i <= endIndex; i++) {
                    long start = 0;
                    long durtion = 0;
                    if (i == startIndex) {
                        if (startIndex != 0) {
                            start = musicStart - (musicStart - offsetTime);
                            //continue;
                        } else {
                            if (musicStart < tmpInfoLists.get(i).getCaptionStartTime()) {
                                start = tmpInfoLists.get(i).getCaptionStartTime() - (musicStart - offsetTime);
                            } else {
                                start = musicStart - (musicStart - offsetTime);
                                //continue;
                            }
                        }
                    } else {
                        start = tmpInfoLists.get(i).getCaptionStartTime() - (musicStart - offsetTime);
                    }

                    if (startIndex == endIndex) {
                        durtion = musicEnd - (musicStart - offsetTime) - start;
                    } else if (i == endIndex) {
                        durtion = musicEnd - (musicStart - offsetTime) - start;
                    } else {
                        if (i != lrcList.size() - 1)
                            durtion = tmpInfoLists.get(i + 1).getCaptionStartTime() - (musicStart - offsetTime) - start;
                        else
                            durtion = musicEnd - (musicStart - offsetTime) - start;
                    }
                    MusicCaptionInfo infos = new MusicCaptionInfo();
                    infos.setCaptionText(tmpInfoLists.get(i).getCaptionText());
                    infos.setCaptionStartTime(start);
                    infos.setCaptionDurtion(durtion);
                    infoLists.add(infos);
                }
            } else {
                return null;
            }
        }

        return infoLists;
    }
}
