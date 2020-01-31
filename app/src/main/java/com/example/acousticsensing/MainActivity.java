package com.example.acousticsensing;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Process;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.acousticsensing.Audio.PcmToWavUtil;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.AudioFormat.CHANNEL_IN_MONO;
import static android.media.AudioFormat.ENCODING_PCM_16BIT;

public class MainActivity extends AppCompatActivity {
    // UI component
    private Button button_send;
    private Button button_receive;
    private Button button_together;

    //声音参数
    private int frequence;                       //采样率
    private int channel;                         //通道数
    private int audioEncoding;                   //编码方式

    //
    private boolean isRecording;                 //是否正在录音
    private boolean stop = true;
    private int recordBuffer;
    private int playBuffer;
    private AudioRecord audioRecord;
    private AudioTrack audioTrack;

    //文件和保存路径
    private String sdCardDir;
    private String fName_record;                 //保存文件名(pcm文件)
    private String fName_result;                 //转换后得文件名（wav文件）

    //调试参数
    private static String tag = "test";
    private int cnt = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UI_Init();
        parameter_Init();
    }

    public void UI_Init(){
        button_send = (Button)findViewById(R.id.button_send);
        button_receive = (Button) findViewById(R.id.button_receive);
        button_together = (Button)findViewById(R.id.button_together);

        button_send.setOnClickListener(new buttonClickListener());
        button_receive.setOnClickListener(new buttonClickListener());
        button_together.setOnClickListener(new buttonClickListener());

        requestRecordAudioPermission();
    }

    public void UI_change(String component,String state){
        switch (component){
            case "button_recieve":{
                button_receive.setText(state);
                break;
            }
            case "button_together":{
                button_together.setText(state);
                break;
            }
        }
    }

    public void parameter_Init(){
        frequence = 48000;
        channel = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
    }
    //按钮事件
   class buttonClickListener implements View.OnClickListener{
        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.button_send: {
                    Toast.makeText(getApplicationContext(), "开始播放", Toast.LENGTH_SHORT).show();
                    startPlayingNow();
                    break;
                }
                case R.id.button_receive:{
                    if ((cnt % 2) == 0){
                        button_receive.setText("Stop");
                        startRecordingNow();
                        Toast.makeText(getApplicationContext(), "开始录制", Toast.LENGTH_SHORT).show();
                    }else{
                        stop = false;
                        button_receive.setText("Recieve");
                        Toast.makeText(getApplicationContext(), "录制结束", Toast.LENGTH_SHORT).show();
                    }
                    cnt++;
                    Log.w(tag,"cnt = " + String.valueOf(cnt));
                    break;
                }
                case R.id.button_together:{
                    Toast.makeText(getApplicationContext(), "开始播放并录制", Toast.LENGTH_SHORT).show();
                    startPlayingNow();
                    startRecordingNow();
                    break;
                }
            }
        }
    }

    /*
        AudioRecord是Android系统提供的用于实现录音的功能类。
        AudioRecord录制的是PCM格式的音频文件，需要用AudioTrack来播放。也可以使用MediaCodec编码成播放器可以播放的音频文件。
     */

    //录音权限申请
    private void requestRecordAudioPermission() {
        //check API version, do nothing if API version < 23!
        int currentapiVersion = android.os.Build.VERSION.SDK_INT;
        if (currentapiVersion > android.os.Build.VERSION_CODES.LOLLIPOP){

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {

                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {

                    // Show an expanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.

                } else {

                    // No explanation needed, we can request the permission.

                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                }
            }
        }
    }

    //开始播放
    public void startPlayingNow(){
        Log.w(tag,"startPlayingNow");
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                try {
                    keepAudioPlaying();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void keepAudioPlaying() throws IOException {
        Log.w(tag,"keepAudioPlaying");
        //构造AudioTrack
        playBuffer = AudioTrack.getMinBufferSize(frequence,channel,audioEncoding);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,frequence,channel,audioEncoding,playBuffer,AudioTrack.MODE_STREAM);

        //读取文件
        InputStream mDis = null;
        mDis = getResources().openRawResource(R.raw.wave);
        //mDis = getResources().openRawResource(R.raw.test);

        //开始播放
        byte data[] = new byte[playBuffer];

        int readCount = 0;
        while (mDis.available() > 0) {
            readCount= mDis.read(data);
            //一边播放一边写入语音数据
            if (readCount != 0 && readCount != -1) {
                audioTrack.play();
                audioTrack.write(data, 0, readCount);
            }
        }
        //播放完就停止播放
        stopPlaying();
    }

    //停止播放
    public void stopPlaying(){
        Log.w(tag,"stopPlaying");
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
        stop = false;
    }

    //开始录音
    public void startRecordingNow(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
                keepAudioRecording();
                Log.w(tag,"startRecordingNow");
            }
        }).start();
    }

    public void keepAudioRecording(){
        Log.w(tag,"keepAudioRecording");
        //构造AudioRecord对象
        recordBuffer = AudioRecord.getMinBufferSize(frequence,channel,audioEncoding);
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,frequence,channel,audioEncoding,recordBuffer);

        //创建文件
        sdCardDir = Environment.getExternalStorageDirectory().getAbsolutePath();
        Log.w(tag,"路径 : "+ sdCardDir);
        fName_record = "/record.pcm";
        fName_result = "/record.wav";
        createFile(sdCardDir,fName_record);
        createFile(sdCardDir,fName_result);

        //开始录音
        byte data[] = new byte[recordBuffer];       //声音数据缓冲区
        audioRecord.startRecording();
        isRecording = true;

        //getRecordingState获取当前AudioReroding是否正在采集数据的状态
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(sdCardDir + fName_record);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        while (isRecording && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING){
            //重置录音状态，便于重复使用
            if (stop == false){
                stop = true;
                break;
            }
            int byteRead = audioRecord.read(data, 0, recordBuffer);
            try {
                os.write(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        //停止录音，并将所得的pcm文件转化为wav文件
        stopRecord();
        PcmToWavUtil pcmToWavUtil = new PcmToWavUtil(frequence,CHANNEL_IN_MONO,ENCODING_PCM_16BIT);
        pcmToWavUtil.pcmToWav(sdCardDir+fName_record,sdCardDir+fName_result);

        /*final String s = "Play & Record";
        button_together.post(new Runnable() {
            @Override
            public void run() {
                button_together.setText(s);
            }
        });*/
    }

    //停止录音
    public void stopRecord(){
        Log.w(tag,"stopRecording");
        //关闭数据流
        isRecording = false;
        //停止录音并释放资源
        audioRecord.stop();
        audioRecord.release();
        audioRecord = null;
    }

    //创建文件
    public void createFile(String path,String filename){
        Log.w(tag,"createFile"+ filename);
        File file = new File(path+filename);
        //如果已存在则删除原文件
        if(file.exists()){
            file.delete();
        }try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
