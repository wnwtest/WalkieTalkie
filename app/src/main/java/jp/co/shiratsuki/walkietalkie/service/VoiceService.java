package jp.co.shiratsuki.walkietalkie.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;

import org.webrtc.MediaStream;

import java.io.IOException;

import jp.co.shiratsuki.walkietalkie.R;
import jp.co.shiratsuki.walkietalkie.activity.MainActivity;
import jp.co.shiratsuki.walkietalkie.broadcast.BaseBroadcastReceiver;
import jp.co.shiratsuki.walkietalkie.broadcast.MediaButtonReceiver;
import jp.co.shiratsuki.walkietalkie.broadcast.SettingsContentObserver;
import jp.co.shiratsuki.walkietalkie.contentprovider.SPHelper;
import jp.co.shiratsuki.walkietalkie.utils.LogUtils;
import jp.co.shiratsuki.walkietalkie.utils.WifiUtil;
import jp.co.shiratsuki.walkietalkie.webrtc.IWebRTCHelper;
import jp.co.shiratsuki.walkietalkie.webrtc.WebRTCHelper;
import jp.co.shiratsuki.walkietalkie.constant.WebRTC;

/**
 * 局域网通信服务
 * Created at 2018/12/12 13:06
 *
 * @author LiYuliang
 * @version 1.0
 */

public class VoiceService extends Service implements IWebRTCHelper {

    private boolean isInRoom = false;

    enum TYPE {
        EnterGroup, LeaveGroup, StartRecord, StopRecord, UseSpeaker, UseEarpiece
    }

    private final static String TAG = "VoiceService";
    private WebRTCHelper helper;

    private KeyEventBroadcastReceiver keyEventBroadcastReceiver;

    private SettingsContentObserver mSettingsContentObserver;

    private RemoteCallbackList<IVoiceCallback> mCallbackList = new RemoteCallbackList<>();

    private AudioManager mAudioManager;
    private ComponentName mComponentName;
    private MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        LogUtils.d(TAG, "VoiceService——————生命周期——————:onCreate");
        keyEventBroadcastReceiver = new KeyEventBroadcastReceiver();
        IntentFilter filter1 = new IntentFilter();
        filter1.addAction("KEY_DOWN");
        filter1.addAction("KEY_UP");
        registerReceiver(keyEventBroadcastReceiver, filter1);

        IntentFilter filter3 = new IntentFilter();
        filter3.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        filter3.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(headsetPlugReceiver, filter3);

        showNotification();
        if (helper == null) {
            helper = new WebRTCHelper(this, this, WebRTC.iceServers);
        }

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        //当应用开始播放的时候首先需要请求焦点，调用该方法后，原先获取焦点的应用会释放焦点
        mAudioManager.requestAudioFocus(focusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        //对媒体播放按钮进行封装
        if (mComponentName == null) {
            mComponentName = new ComponentName(getPackageName(), MediaButtonReceiver.class.getName());
        }
        //注册封装的ComponentName
        mAudioManager.registerMediaButtonEventReceiver(mComponentName);

        // 记录当前媒体音量
        SPHelper.save("defaultVolume", mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        registerVolumeChangeReceiver();

        mediaPlayer = new MediaPlayer();
    }

    //焦点问题
    private AudioManager.OnAudioFocusChangeListener focusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:// 长时间失去
                    mAudioManager.unregisterMediaButtonEventReceiver(mComponentName);
                    mAudioManager.abandonAudioFocus(focusChangeListener);//放弃焦点监听
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:// 短时间失去
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:// 短时间失去，但可以共用
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:// 获得音频焦点
                    mAudioManager.registerMediaButtonEventReceiver(mComponentName);
                    break;
                default:
                    break;
            }
        }
    };


    @Override
    public IBinder onBind(Intent intent) {
        LogUtils.d(TAG, "VoiceService——————生命周期——————:onBind");
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.d(TAG, "VoiceService——————生命周期——————:onStartCommand");
        if (helper == null) {
            LogUtils.d(TAG, "VoiceService——————生命周期——————:创建WebRTCHelper");
            helper = new WebRTCHelper(this, this, WebRTC.iceServers);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private BroadcastReceiver headsetPlugReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (BluetoothProfile.STATE_DISCONNECTED == adapter.getProfileConnectionState(BluetoothProfile.HEADSET)) {
                    // 蓝牙耳机移除
                    LogUtils.d(TAG, "蓝牙耳机移除");
                }
            } else if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
                // 有线耳机移除，标记耳机按键状态为抬起并发送广播，停止录音
                Intent intent1 = new Intent();
                intent1.setAction("KEY_UP");
                context.sendBroadcast(intent1);
                SPHelper.save("KEY_STATUS_UP", true);
                LogUtils.d(TAG, "有线耳机移除");
            }
        }
    };

    public IVoiceService.Stub mBinder = new IVoiceService.Stub() {

        @Override
        public void enterGroup() {
            // 进入房间
            if (helper == null) {
                helper = new WebRTCHelper(VoiceService.this, VoiceService.this, WebRTC.iceServers);
            }

            String ip = SPHelper.getString("VoiceServerIP", WebRTC.WEBRTC_SERVER_IP);
            String port = SPHelper.getString("VoiceServerPort", WebRTC.WEBRTC_SERVER_PORT);
            String roomId = SPHelper.getString("VoiceRoomId", WebRTC.WEBRTC_SERVER_ROOM);

            String signal = "ws://" + ip + ":" + port;
            helper.initSocket(signal, roomId, WifiUtil.getLocalIPAddress(), false);
        }

        @Override
        public void leaveGroup() {
            // 离开房间
            isInRoom = false;
            helper.exitRoom();
            broadcastCallback(TYPE.LeaveGroup);
        }

        @Override
        public void startRecord() {
            if (isInRoom) {
                // 打开麦克风
                helper.sendSpeakStatus(true);
                helper.toggleMute(true);
                broadcastCallback(TYPE.StartRecord);
                // 播放提示音
                try {
                    Uri setDataSourceuri = Uri.parse("android.resource://jp.co.shiratsuki.walkietalkie/" + R.raw.dingdong);
                    mediaPlayer.setDataSource(VoiceService.this, setDataSourceuri);
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setScreenOnWhilePlaying(true);
                    mediaPlayer.setOnPreparedListener(mediaPlayer -> mediaPlayer.start());
                    mediaPlayer.setOnCompletionListener(mediaPlayer -> {
                        mediaPlayer.reset();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
                SPHelper.save("KEY_STATUS_UP", false);
            } else {
                try {
                    Uri setDataSourceuri = Uri.parse("android.resource://jp.co.shiratsuki.walkietalkie/" + R.raw.du);
                    mediaPlayer.setDataSource(VoiceService.this, setDataSourceuri);
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setScreenOnWhilePlaying(true);
                    mediaPlayer.setOnPreparedListener(mediaPlayer -> mediaPlayer.start());
                    mediaPlayer.setOnCompletionListener(mediaPlayer -> {
                        mediaPlayer.reset();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
                SPHelper.save("KEY_STATUS_UP", true);
            }
        }

        @Override
        public void stopRecord() {
            if (isInRoom) {
                // 关闭麦克风
                helper.sendSpeakStatus(false);
                helper.toggleMute(false);
                broadcastCallback(TYPE.StopRecord);
                // 播放提示音
                try {
                    Uri setDataSourceuri = Uri.parse("android.resource://jp.co.shiratsuki.walkietalkie/" + R.raw.du);
                    mediaPlayer.setDataSource(VoiceService.this, setDataSourceuri);
                    mediaPlayer.prepareAsync();
                    mediaPlayer.setScreenOnWhilePlaying(true);
                    mediaPlayer.setOnPreparedListener(mediaPlayer -> mediaPlayer.start());
                    mediaPlayer.setOnCompletionListener(mediaPlayer -> {
                        mediaPlayer.reset();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
                SPHelper.save("KEY_STATUS_UP", true);
            }
        }

        @Override
        public void useSpeaker() {
            helper.toggleSpeaker(true);
            broadcastCallback(TYPE.UseSpeaker);
        }

        @Override
        public void useEarpiece() {
            helper.toggleSpeaker(false);
            broadcastCallback(TYPE.UseEarpiece);
        }

        @Override
        public void registerCallback(IVoiceCallback callback) {
            mCallbackList.register(callback);
        }

        @Override
        public void unRegisterCallback(IVoiceCallback callback) {
            mCallbackList.unregister(callback);
        }
    };

    /**
     * 前台Service
     */
    private void showNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        Notification notification = new NotificationCompat.Builder(getApplicationContext())
                .setContentTitle("对讲机")
                .setTicker("对讲机")
                .setContentText("正在使用对讲机")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(Bitmap.createScaledBitmap(icon, 128, 128, false))
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();

        startForeground(101, notification);
    }

    // 按键事件广播
    private class KeyEventBroadcastReceiver extends BaseBroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            if (("KEY_DOWN").equals(intent.getAction())) {
                LogUtils.d(TAG, "收到KEY_DOWN广播");
                try {
                    mBinder.startRecord();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else if (("KEY_UP").equals(intent.getAction())) {
                LogUtils.d(TAG, "收到KEY_UP广播");
                try {
                    mBinder.stopRecord();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onSetLocalStream(MediaStream stream, String socketId) {

    }

    @Override
    public void onAddRemoteStream(MediaStream stream, String socketId) {

    }

    @Override
    public void onEnterRoom() {
        isInRoom = true;
        broadcastCallback(TYPE.EnterGroup);
        helper.toggleMute(false);
        helper.toggleSpeaker(false);
    }

    @Override
    public void onLeaveRoom() {
        isInRoom = false;
        broadcastCallback(TYPE.LeaveGroup);
    }

    @Override
    public void onCloseWithId(String socketId) {

    }

    @Override
    public void receiveSpeakStatus(boolean someoneSpeaking) {
        int defaultVolume = SPHelper.getInt("defaultVolume", mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        // 如果所有人都不讲话了
        if (!someoneSpeaking) {
            LogUtils.d(TAG, "没有人讲话，设置音量值：" + defaultVolume);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume, AudioManager.FLAG_VIBRATE);
            SPHelper.save("SomeoneSpeaking", false);
        } else {
            LogUtils.d(TAG, "有人讲话，设置音量值：" + defaultVolume / 2);
            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, defaultVolume / 2, AudioManager.FLAG_VIBRATE);
            SPHelper.save("SomeoneSpeaking", true);
        }
    }

    // 回调公共方法
    private void broadcastCallback(TYPE type) {
        final int size = mCallbackList.beginBroadcast();
        for (int i = 0; i < size; i++) {
            IVoiceCallback callback = mCallbackList.getBroadcastItem(i);
            if (callback != null) {
                try {
                    if (type == TYPE.EnterGroup) {
                        callback.enterRoomSuccess();
                    } else if (type == TYPE.LeaveGroup) {
                        callback.leaveGroupSuccess();
                    } else if (type == TYPE.StartRecord) {
                        callback.startRecordSuccess();
                    } else if (type == TYPE.StopRecord) {
                        callback.stopRecordSuccess();
                    } else if (type == TYPE.UseSpeaker) {
                        callback.useSpeakerSuccess();
                    } else if (type == TYPE.UseEarpiece) {
                        callback.useEarpieceSuccess();
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        mCallbackList.finishBroadcast();
    }

    private void registerVolumeChangeReceiver() {
        mSettingsContentObserver = new SettingsContentObserver(this, new Handler());
        getApplicationContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI, true, mSettingsContentObserver);
    }

    private void unregisterVolumeChangeReceiver() {
        getApplicationContext().getContentResolver().unregisterContentObserver(mSettingsContentObserver);
    }

    //网络状态广播
    public class NetworkStatusReceiver extends BaseBroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 监听wifi的打开与关闭，与wifi的连接无关
            if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, 0);
                if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                    //wifi关闭
                    LogUtils.d(TAG, "wifi已关闭");
                    Intent intent1 = new Intent();
                    intent1.setAction("WIFI_DISCONNECTED");
                    VoiceService.this.sendBroadcast(intent1);
                    VoiceService.this.onDestroy();
                } else if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                    //wifi开启
                    LogUtils.d(TAG, "wifi已开启");
                } else if (wifiState == WifiManager.WIFI_STATE_ENABLING) {
                    //wifi开启中
                    LogUtils.d(TAG, "wifi开启中");
                } else if (wifiState == WifiManager.WIFI_STATE_DISABLING) {
                    //wifi关闭中
                    LogUtils.d(TAG, "wifi关闭中");
                }
            }
            // 监听wifi的连接状态即是否连上了一个有效无线路由
            if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                Parcelable parcelableExtra = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (parcelableExtra != null) {
                    NetworkInfo networkInfo = (NetworkInfo) parcelableExtra;
                    if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {
                        //已连接网络
                        LogUtils.d(TAG, "wifi 已连接网络");
                        if (networkInfo.isAvailable()) {//并且网络可用
                            LogUtils.d(TAG, "wifi 已连接网络，并且可用");
                        } else {//并且网络不可用
                            LogUtils.d(TAG, "wifi 已连接网络，但不可用");
                        }
                        Intent intent1 = new Intent();
                        intent1.setAction("WIFI_CONNECTED");
                        VoiceService.this.sendBroadcast(intent1);
                    } else {
                        //网络未连接
                        LogUtils.d(TAG, "wifi 未连接网络");
                    }
                } else {
                    LogUtils.d(TAG, "wifi parcelableExtra为空");
                }
            }
            // 监听网络连接，总网络判断，即包括wifi和移动网络的监听
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
                //连上的网络类型判断：wifi还是移动网络
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    LogUtils.d(TAG, "总网络 连接的是wifi网络");
                } else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    LogUtils.d(TAG, "总网络 连接的是移动网络");
                }
                //具体连接状态判断
                checkNetworkStatus(networkInfo);
            }
        }

        private void checkNetworkStatus(NetworkInfo networkInfo) {
            if (networkInfo != null) {
                LogUtils.d(TAG, "总网络 info非空");
                if (networkInfo.getState() == NetworkInfo.State.CONNECTED) {//已连接网络
                    LogUtils.d(TAG, "总网络 已连接网络");
                    if (networkInfo.isAvailable()) {//并且网络可用
                        LogUtils.d(TAG, "总网络 已连接网络，并且可用");
                    } else {//并且网络不可用
                        LogUtils.d(TAG, "总网络 已连接网络，但不可用");
                    }
                } else if (networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {//网络未连接
                    LogUtils.d(TAG, "总网络 未连接网络");
                }
            } else {
                LogUtils.d(TAG, "总网络 info为空");
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        helper.exitRoom();
        if (keyEventBroadcastReceiver != null) {
            unregisterReceiver(keyEventBroadcastReceiver);
        }
        if (headsetPlugReceiver != null) {
            unregisterReceiver(headsetPlugReceiver);
        }
        mAudioManager.unregisterMediaButtonEventReceiver(mComponentName);
        unregisterVolumeChangeReceiver();
        // 停止前台Service
        stopForeground(true);
        stopSelf();
    }
}