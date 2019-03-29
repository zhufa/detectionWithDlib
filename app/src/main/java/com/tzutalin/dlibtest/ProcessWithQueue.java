package com.tzutalin.dlibtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v7.app.AppCompatActivity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.media.Image;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.tzutalin.dlib.Constants;
import com.tzutalin.dlib.FaceDet;
import com.tzutalin.dlib.VisionDetRet;

import junit.framework.Assert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static android.content.Context.MODE_PRIVATE;

public class ProcessWithQueue extends Thread {
    private static final String TAG = "Queue";
    private static final String NoDetection = "noDetection";
    private static final String EyesBlinkDetection = "eyesBlinkDetection";
    private static final String headOrientationDetection = "headOrientationDetection";

    private LinkedBlockingQueue<Bitmap> mQueue;
    private LinkedBlockingQueue<Bitmap> frameForDisplay;

    private static String checkMode = null;

    private List<VisionDetRet> results;

    private Handler mInferenceHandler;
    private Context mContext;
    private FaceDet mFaceDet;
    private TrasparentTitleView mTransparentTitleView;
    private FloatingCameraWindow mWindow;
    private Paint mFaceLandmardkPaint;

    private int mframeNum = 0;

    private double ear = 0;
    private int x = 0;
    private boolean ear_array_removed = false;
    private boolean drop_array_appended = false;
    private double THRESH = 0.04;
    private double DROP_THRESH = 0.065;
    private ArrayList<Double> ear_array = new ArrayList<>();
    private ArrayList<Integer> ax = new ArrayList<>();
    private ArrayList<Double> ay = new ArrayList<>();
    private int continuous_Increment = 0;
    private int continuous_Decrement = 0;
    private double drop = 0;
    private ArrayList<Double> drop_array = new ArrayList<>();
    private int temp = 0;
    private double closeEyes_drop = -5;
    private double openEyes_drop = 0;
    private int closeEyes_end = 0;
    private int openEyes_start = 0;
    private int blink = 0;
    private int frames_notFoundFace = 0;
    private Point keyPoint_right = null;
    private Point keyPoint_left = null;
    private Point keyPoint_nose = null;
    private double rightHalfFace = 0;
    private double leftHalfFace = 0;
    private String headToward = "front";

    private double ratio = 0;

    private SharedPreferences mSharedPreferences;


    public ProcessWithQueue(LinkedBlockingQueue<Bitmap> frameQueue, LinkedBlockingQueue<Bitmap> frameQueueForDisplay, Context context, TrasparentTitleView scoreView, Handler handler) {
        this.mContext = context;
        this.mTransparentTitleView = scoreView;
        this.mInferenceHandler = handler;

        mQueue = frameQueue;
        frameForDisplay = frameQueueForDisplay;

        mFaceDet = new FaceDet(Constants.getFaceShapeModelPath());
        mWindow = new FloatingCameraWindow(mContext);

        mFaceLandmardkPaint = new Paint();
        mFaceLandmardkPaint.setColor(Color.GREEN);
        mFaceLandmardkPaint.setStrokeWidth(2);
        mFaceLandmardkPaint.setStyle(Paint.Style.STROKE);

        mSharedPreferences = context.getSharedPreferences("userInfo", MODE_PRIVATE);
        checkMode = mSharedPreferences.getString("detectionMode","");

        start();
    }

    public void release(){
        if (mFaceDet != null) {
            mFaceDet.release();
        }

        if (mWindow != null) {
            mWindow.release();
        }
    }

    @Override
    public void run() {
        while (true) {
            Bitmap frameData = null;
            Bitmap framefordisplay = null;
            try {
                frameData = mQueue.take();
                framefordisplay = frameForDisplay.take();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (frameData == null) {
                break;
            }
            processFrame(frameData, framefordisplay);
        }
    }

    private void processFrame(final Bitmap frameData, final Bitmap framefordisplay) {

        if(frameData != null){
            mInferenceHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {

                            if (!new File(Constants.getFaceShapeModelPath()).exists()) {
                                mTransparentTitleView.setText("Copying landmark model to " + Constants.getFaceShapeModelPath());
                                FileUtils.copyFileFromRawToOthers(mContext, R.raw.shape_predictor_68_face_landmarks, Constants.getFaceShapeModelPath());
                            }

                            mframeNum++;
//                          saveBitmap(frameData, "frames", String.valueOf(mframeNum) + ".jpg");

                            switch (checkMode){

                                case NoDetection:{
                                    mWindow.setRGBBitmap(framefordisplay);
                                    mWindow.setInformation("frame: " + String.valueOf(mframeNum));
                                    mTransparentTitleView.setText("noDetection");
                                }break;

                                case EyesBlinkDetection:{

                                    long startTime = System.currentTimeMillis();

                                    results = mFaceDet.detect(frameData);

                                    if (results.size() != 0) {
                                        for (final VisionDetRet ret : results) {
                                            float resizeRatio = 4f;
                                            Canvas canvas = new Canvas(framefordisplay);

                                            ArrayList<Point> landmarks = ret.getFaceLandmarks();

                                            int i = 1;

                                            //get the 6 key point from 68_face_landmarks
                                            Point[] leftEye = new Point[6];
                                            Point[] rightEye = new Point[6];

                                            for (Point point : landmarks) {
                                                if (i > 36 && i < 43) {
                                                    //for more efficient procession, the data we process were zoomed out
                                                    //So the point must be magnified , to display correctly in the original image.
                                                    int pointX = (int) (point.x * resizeRatio);
                                                    int pointY = (int) (point.y * resizeRatio);
                                                    leftEye[i - 37] = new Point(pointX, pointY);
                                                //canvas.drawCircle(pointX, pointY, 2, mFaceLandmardkPaint);
                                                } else if (i > 42 && i < 49) {
                                                    int pointX = (int) (point.x * resizeRatio);
                                                    int pointY = (int) (point.y * resizeRatio);
                                                    rightEye[i - 43] = new Point(pointX, pointY);
                                                //canvas.drawCircle(pointX, pointY, 2, mFaceLandmardkPaint);
                                                }
                                                if (i > 48) {
                                                    break;
                                                }
                                                i++;
                                            }

                                            canvas.drawPath(getPath(leftEye), mFaceLandmardkPaint);
                                            canvas.drawPath(getPath(rightEye), mFaceLandmardkPaint);
                                            //saveBitmap(frameData, "Pframes", String.valueOf(mframeNum) + ".jpg");

                                            double leftEAR = eye_aspect_ratio(leftEye);
                                            double rightEAR = eye_aspect_ratio(rightEye);
                                            ear = (leftEAR + rightEAR) / 2.0;
                                        }
                                    } else {
                                        frames_notFoundFace++;
                                        Log.i("frames_notFoundFace", String.valueOf(frames_notFoundFace));
                                    }

                                    if (ear != 0) {

                                        //codes below are difficult to read,but it dose work
                                        x += 1;
                                        ear_array.add(ear);
                                        ax.add(x);
                                        ay.add(ear);
                                        ear_array_removed = filter_unexpected_values(ear_array, ax, THRESH);

                                        if (ear_array.size() > 2 && !ear_array_removed) {
                                            if (ear_array.get(ear_array.size() - 2) > ear_array.get(ear_array.size() - 3)) {
                                                continuous_Increment += 1;
                                                if (continuous_Decrement != 0) {
                                                    drop = ear_array.get(ear_array.size() - 3) - ear_array.get(ear_array.size() - 3 - continuous_Decrement);
                                                    if (continuous_Decrement != 1) {
                                                        drop_array.add(drop);
                                                        drop_array_appended = true;
                                                    }
                                                    temp = continuous_Decrement;
                                                    continuous_Decrement = 0;
                                                }
                                            } else if (ear_array.get(ear_array.size() - 2) < ear_array.get(ear_array.size() - 3)) {
                                                continuous_Decrement += 1;
                                                if (continuous_Increment != 0) {
                                                    drop = ear_array.get(ear_array.size() - 3) - ear_array.get(ear_array.size() - 3 - continuous_Increment);
                                                    if (continuous_Increment != 1) {
                                                        drop_array.add(drop);
                                                        drop_array_appended = true;
                                                    }
                                                    temp = continuous_Increment;
                                                    continuous_Increment = 0;
                                                }
                                            }
                                        }

                                        if (drop_array_appended) {
                                            if (drop_array.get(drop_array.size() - 1) < -DROP_THRESH) {
                                                closeEyes_drop = drop_array.get(drop_array.size() - 1);
                                                closeEyes_end = ax.get(ax.size() - 3);
                                            }
                                            if (drop_array.get(drop_array.size() - 1) > DROP_THRESH) {
                                                openEyes_drop = drop_array.get(drop_array.size() - 1);
                                                openEyes_start = ax.get(ax.size() - 3 - temp);
                                                if (Math.abs(closeEyes_drop + openEyes_drop) < 0.1 && ear_array.get(ear_array.size() - 3 - temp) < 0.21
                                                        && openEyes_start - closeEyes_end < 20) {
                                                    blink += 1;
                                                    closeEyes_drop = -5;
                                                }
                                            }
                                        }

                                    }
                                    long endTime = System.currentTimeMillis();
                                    mTransparentTitleView.setText("noFace: " + String.valueOf(frames_notFoundFace) + " blink: " + String.valueOf(blink) + " TimeCost: " + String.valueOf((endTime - startTime) / 1000f));

                                    //save the ear data to local,for further analysis
                                    //When mframeNum is equal to 1, it means that the recognition is restarted, so the previous data is overwritten.
                                    //if(mframeNum == 1){
                                        //saveStringToTxt(String.valueOf(mframeNum) + "  " + doubleToString(ear), "ear.txt", false);
                                    //}else{
                                        //saveStringToTxt(String.valueOf(mframeNum) + "  " + doubleToString(ear), "ear.txt",true);
                                    //}

                                    Log.i("processingFrame", String.valueOf(mframeNum));
                                    mWindow.setRGBBitmap(framefordisplay);
                                    mWindow.setInformation("frame: " + String.valueOf(mframeNum));
                                    mWindow.setMoreInformation("ear: " + String.valueOf(ear));

                                }break;
                                case headOrientationDetection:{

                                    long startTime = System.currentTimeMillis();

                                    results = mFaceDet.detect(frameData);

                                    if (results.size() != 0) {
                                        for (final VisionDetRet ret : results) {
                                            float resizeRatio = 4f;
                                            Canvas canvas = new Canvas(framefordisplay);

                                            ArrayList<Point> landmarks = ret.getFaceLandmarks();


                                            for (Point point : landmarks) {
                                                int pointX = (int) (point.x * resizeRatio);
                                                int pointY = (int) (point.y * resizeRatio);
                                                canvas.drawCircle(pointX, pointY, 2, mFaceLandmardkPaint);
                                            }

                                            keyPoint_left = landmarks.get(2);
                                            keyPoint_right = landmarks.get(14);
                                            keyPoint_nose = landmarks.get(30);

                                            rightHalfFace = euclidean(keyPoint_nose, keyPoint_right);
                                            leftHalfFace = euclidean(keyPoint_nose, keyPoint_left);

                                            ratio = Math.min(rightHalfFace, leftHalfFace) / Math.max(rightHalfFace, leftHalfFace);
                                        }
                                    } else {
                                        frames_notFoundFace++;
                                        Log.i("frames_notFoundFace", String.valueOf(frames_notFoundFace));
                                    }

                                    if (ratio>0.6 && ratio<1){
                                        headToward = "front";
                                    }else {
                                        //if your right face is bigger than the left, then your head toward left
                                        headToward = rightHalfFace > leftHalfFace ? "left" : "right";
                                    }

                                    long endTime = System.currentTimeMillis();

                                    mTransparentTitleView.setText("headToward:" + headToward + " TimeCost: " + String.valueOf((endTime - startTime) / 1000f));
                                    Log.i("processingFrame", String.valueOf(mframeNum));
                                    mWindow.setInformation("frame: " + String.valueOf(mframeNum));
                                    mWindow.setMoreInformation("ratio: " + String.valueOf(ratio));
                                    mWindow.setRGBBitmap(framefordisplay);
                                }
                                break;

                            }
                        }

                    });
        }
    }

    //眼睛的高和长的比值
    private double eye_aspect_ratio(Point[] eye){
        double ear = 0;
        double A = euclidean(eye[1],eye[5]);
        double B = euclidean(eye[2],eye[4]);
        double C = euclidean(eye[0],eye[3]);
        ear = (A + B) / (2.0 * C);
        return  ear;
    }

    //两点间的欧式距离
    private double euclidean(Point p1, Point p2){
        double result = 0;
        result = Math.sqrt(Math.pow((p1.x-p2.x),2)+Math.pow((p1.y-p2.y),2));
        return result;
    }

    //保存数据到文件,追加or覆盖
    private static void saveStringToTxt(String str, String fileName, boolean appendOrNot){

        String filePath = null;

        boolean hasSDCard =Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);

        if (hasSDCard) {

            filePath =Environment.getExternalStorageDirectory().toString() + File.separator +fileName;

        } else

            filePath =Environment.getDownloadCacheDirectory().toString() + File.separator +fileName;
        try {
            File file = new File(filePath);

            if (!file.exists()) {

                file.createNewFile();

            }
            FileWriter fw = new FileWriter(file,appendOrNot);//SD卡中的路径
            fw.flush();
            fw.write(str+"\r\n");
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //double转string
    private static String doubleToString(double num){
        //使用0.00不足位补0，#.##仅保留有效位
        return new DecimalFormat("0.0000").format(num);
    }

    /**

     * 过滤异常值,以便更好的计算连续落差Calculate_continuous_drop()

     *

     * @param AL 存有ear值的ArrayList
     * @param ax ear值对应的帧，AL看作是Y轴的话，那么ax就是X轴，当AL过滤某一ear值时，其对应的帧也应删除
     * @param THRESH 阈值，小于它才过滤
     * @return 返回此次调用是否发生了过滤，过滤了返回true,无需过滤返回false

     */
    private static boolean filter_unexpected_values(ArrayList<Double> AL, ArrayList<Integer> ax, double THRESH){
        if(AL.size()>2){
            if(AL.get(AL.size()-3) < AL.get(AL.size()-1)){
                if(AL.get(AL.size()-3) > AL.get(AL.size()-2) && AL.get(AL.size()-3)-AL.get(AL.size()-2)>THRESH){
                    AL.remove(AL.size()-2);
                    ax.remove(AL.size()-2);
                    return true;
                }
            }else if(AL.get(AL.size()-3) > AL.get(AL.size()-1)){
                if(AL.get(AL.size()-2) > AL.get(AL.size()-3) && AL.get(AL.size()-2)-AL.get(AL.size()-3)>THRESH){
                    AL.remove(AL.size()-2);
                    ax.remove(AL.size()-2);
                    return true;
                }
            }
        }
        return false;
    }

    //返回点的闭合路径，通过canvas可画出
    private Path getPath(Point[] points){
        Path path = new Path();
        path.moveTo(points[0].x, points[0].y);//起点
        //添加中间连接点
        for(int i = 1; i < points.length; i++){
            path.lineTo(points[i].x, points[i].y);
        }
        path.close(); // 使这些点构成封闭的多边形
        return path;
    }

    private void saveBitmap(Bitmap bm, String directory, String fileName){
        String filePath = null;

        boolean hasSDCard =Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);

        if (hasSDCard) {

            filePath =Environment.getExternalStorageDirectory().toString() + File.separator + directory+ File.separator+ fileName;

        } else

            filePath =Environment.getDownloadCacheDirectory().toString() + File.separator + directory+ File.separator+ fileName;
        try {
            File file = new File(filePath);

            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(file);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
