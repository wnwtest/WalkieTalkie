package jp.co.shiratsuki.walkietalkie.activity;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.kevin.crop.UCrop;
import com.pkmmte.view.CircularImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.co.shiratsuki.walkietalkie.R;
import jp.co.shiratsuki.walkietalkie.activity.settings.LanguageActivity;
import jp.co.shiratsuki.walkietalkie.activity.settings.SetMessageServerActivity;
import jp.co.shiratsuki.walkietalkie.activity.settings.SetPersonalInfoActivity;
import jp.co.shiratsuki.walkietalkie.activity.settings.SetVoiceServerActivity;
import jp.co.shiratsuki.walkietalkie.adapter.MalfunctionAdapter;
import jp.co.shiratsuki.walkietalkie.bean.WebSocketData;
import jp.co.shiratsuki.walkietalkie.broadcast.BaseBroadcastReceiver;
import jp.co.shiratsuki.walkietalkie.constant.NetWork;
import jp.co.shiratsuki.walkietalkie.contentprovider.SPHelper;
import jp.co.shiratsuki.walkietalkie.interfaces.OnPictureSelectedListener;
import jp.co.shiratsuki.walkietalkie.service.IVoiceCallback;
import jp.co.shiratsuki.walkietalkie.service.IVoiceService;
import jp.co.shiratsuki.walkietalkie.service.VoiceService;
import jp.co.shiratsuki.walkietalkie.service.WebSocketService;
import jp.co.shiratsuki.walkietalkie.utils.ActivityController;
import jp.co.shiratsuki.walkietalkie.utils.LogUtils;
import jp.co.shiratsuki.walkietalkie.utils.NotificationsUtil;
import jp.co.shiratsuki.walkietalkie.utils.PermissionUtil;
import jp.co.shiratsuki.walkietalkie.utils.StatusBarUtil;
import jp.co.shiratsuki.walkietalkie.utils.WifiUtil;
import jp.co.shiratsuki.walkietalkie.webrtc.WebRTCHelper;
import jp.co.shiratsuki.walkietalkie.widget.RecyclerViewDivider;
import jp.co.shiratsuki.walkietalkie.widget.SelectPicturePopupWindow;
import jp.co.shiratsuki.walkietalkie.widget.SwipeItemLayout;
import jp.co.shiratsuki.walkietalkie.widget.dialog.CommonWarningDialog;

/**
 * 主页面
 * Created at 2019/1/11 17:16
 *
 * @author Li Yuliang
 * @version 1.0
 */

public class MainActivity extends BaseActivity implements SelectPicturePopupWindow.OnSelectedListener {

    private static final String TAG = "MainActivity";
    private Context mContext;
    private DrawerLayout drawerLayout;
    private NavigationView navigation;
    private LinearLayout llMain, llNotification;
    private CircularImageView ivIcon, ivUserIcon;
    private TextView tvCompanyName, tvDepartment, tvUserName;
    private ImageView ivRight;
    private List<WebSocketData> malfunctionList;
    private MalfunctionAdapter malfunctionAdapter;
    private MyReceiver myReceiver;
    private boolean sIsScrolling = false;
    private IVoiceService iVoiceService;
    private WebSocketService webSocketService;

    private CommonWarningDialog commonWarningDialog;

    private boolean isInRoom = false, isSpeaking = false, isUseSpeaker = false;
    private TextView tvSSID, tvIp, tvEnterRoom, tvMessage, tvSpeaker;
    private Button btnEnterRoom, btnSpeak, btnSpeaker;

    // 振动电机
    private Vibrator vibrator;

    private static final int GALLERY_REQUEST_CODE = 0;
    private static final int CAMERA_REQUEST_CODE = 1;
    protected static final int REQUEST_STORAGE_READ_ACCESS_PERMISSION = 101;
    protected static final int REQUEST_STORAGE_WRITE_ACCESS_PERMISSION = 102;
    protected static final int REQUEST_CODE_SET_VOICE_SERVER = 103;
    protected static final int REQUEST_CODE_SET_MESSAGE_SERVER = 104;
    private String mTempPhotoPath;
    private Uri mDestinationUri;
    private SelectPicturePopupWindow mSelectPicturePopupWindow;
    private OnPictureSelectedListener mOnPictureSelectedListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        initView();
        initWebSocket();
        initBroadcastReceiver();

        //权限检查
        PermissionUtil.isNeedRequestPermission(this);

        if (iVoiceService == null) {
            Intent intent = new Intent(mContext, VoiceService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        // 初始化耳机按键标记
        SPHelper.save("KEY_STATUS_UP", true);

        // 设置裁剪图片结果监听
        setOnPictureSelectedListener(onPictureSelectedListener);
        mDestinationUri = Uri.fromFile(new File(getExternalFilesDir("Icons"), "cropImage.jpeg"));
        mTempPhotoPath = Environment.getExternalStorageDirectory() + File.separator + "photo.jpeg";
        mSelectPicturePopupWindow = new SelectPicturePopupWindow(mContext, (findViewById(android.R.id.content)));
        mSelectPicturePopupWindow.setOnSelectedListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showOrHideNotification();

        if (WifiUtil.WifiConnected(mContext)) {
            tvSSID.setText(WifiUtil.getSSID(mContext));
            tvIp.setText(WifiUtil.getLocalIPAddress());
        } else {
            //提示是否连接WiFi
            showConnectWifiDialog();
        }

        tvUserName.setText(SPHelper.getString("UserName", "unknown"));
        tvCompanyName.setText(SPHelper.getString("Company", "unknown"));
        tvDepartment.setText(SPHelper.getString("Department", "unknown"));
        String photoPath = SPHelper.getString("UserIconPath", "");
        // 加载头像
        RequestOptions options = new RequestOptions().error(R.drawable.photo_user).placeholder(R.drawable.photo_user).dontAnimate();
//        Glide.with(this).load(photoPath).apply(options).into(ivUserIcon);
//        Glide.with(this).load(photoPath).apply(options).into(ivIcon);

        Intent intent1 = new Intent(mContext, WebSocketService.class);
        bindService(intent1, serviceConnection1, BIND_AUTO_CREATE);

        Intent intent2 = new Intent(mContext, VoiceService.class);
        bindService(intent2, serviceConnection2, BIND_AUTO_CREATE);
    }

    @Override
    protected void setStatusBar() {
        int mColor = getResources().getColor(R.color.colorBluePrimary);
        StatusBarUtil.setColorForDrawerLayout(this, findViewById(R.id.drawer_layout), mColor);
    }

    private void initView() {

        navigation = findViewById(R.id.navigation);
        ViewGroup.LayoutParams params = navigation.getLayoutParams();
        params.width = mWidth * 4 / 5;
        navigation.setLayoutParams(params);

        ivRight = findViewById(R.id.iv_right);
        ivRight.setOnClickListener(onClickListener);

        drawerLayout = findViewById(R.id.drawer_layout);
        drawerLayout.addDrawerListener(drawerListener);
        llMain = findViewById(R.id.ll_main);
        ivIcon = findViewById(R.id.iv_icon);
        ivUserIcon = findViewById(R.id.iv_userIcon);
        ivIcon.setOnClickListener(onClickListener);
        llNotification = findViewById(R.id.ll_notification);
        llNotification.setOnClickListener(onClickListener);
        ivUserIcon.setOnClickListener(onClickListener);

        tvCompanyName = findViewById(R.id.tvCompanyName);
        tvDepartment = findViewById(R.id.tvDepartment);
        tvUserName = findViewById(R.id.tvUserName);

        RecyclerView rvMalfunction = findViewById(R.id.rvMalfunction);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(mContext);
        linearLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        rvMalfunction.setLayoutManager(linearLayoutManager);
        rvMalfunction.addItemDecoration(new RecyclerViewDivider(mContext, LinearLayoutManager.HORIZONTAL, 1, ContextCompat.getColor(mContext, R.color.gray_slight)));

        rvMalfunction.addOnItemTouchListener(new SwipeItemLayout.OnSwipeItemTouchListener(this));

        malfunctionList = new ArrayList<>();
        malfunctionAdapter = new MalfunctionAdapter(mContext, malfunctionList);
        malfunctionAdapter.setOnItemClickListener(onItemClickListener);
        rvMalfunction.setAdapter(malfunctionAdapter);
        rvMalfunction.addOnScrollListener(onScrollListener);

        btnEnterRoom = findViewById(R.id.btnEnterRoom);
        btnEnterRoom.setOnClickListener(onClickListener);

        // 下拉列表刷新
        SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        swipeRefreshLayout.setColorSchemeColors(Color.RED);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeRefreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                }, 1000);
            }
        });

        tvSSID = findViewById(R.id.tvSSID);
        tvIp = findViewById(R.id.tvIp);
        tvEnterRoom = findViewById(R.id.tvEnterRoom);
        tvMessage = findViewById(R.id.tvMessage);
        tvSpeaker = findViewById(R.id.tvSpeaker);
        btnEnterRoom = findViewById(R.id.btnEnterRoom);
        btnSpeak = findViewById(R.id.btnSpeak);
        btnSpeaker = findViewById(R.id.btnSpeaker);
        btnEnterRoom.setOnClickListener(onClickListener);
        btnSpeak.setOnClickListener(onClickListener);
        btnSpeaker.setOnClickListener(onClickListener);

        findViewById(R.id.llPersonalInformation).setOnClickListener(onClickListener);
        findViewById(R.id.llLanguageSettings).setOnClickListener(onClickListener);
        findViewById(R.id.llVoiceServer).setOnClickListener(onClickListener);
        findViewById(R.id.llMessageServer).setOnClickListener(onClickListener);
        findViewById(R.id.llWifiSetting).setOnClickListener(onClickListener);
        findViewById(R.id.llVersion).setOnClickListener(onClickListener);
        findViewById(R.id.llUpdate).setOnClickListener(onClickListener);
        findViewById(R.id.llShare).setOnClickListener(onClickListener);
        findViewById(R.id.btnExit).setOnClickListener(onClickListener);
    }

    /**
     * 显示连接Wifi的弹窗
     */
    private void showConnectWifiDialog() {
        if (commonWarningDialog == null) {
            commonWarningDialog = new CommonWarningDialog(mContext, getString(R.string.notification_connect_wifi));
            commonWarningDialog.setCancelable(false);
            commonWarningDialog.setOnDialogClickListener(new CommonWarningDialog.OnDialogClickListener() {
                @Override
                public void onOKClick() {
                    //进入WiFi连接页面
                    Intent wifiSettingsIntent = new Intent("android.settings.WIFI_SETTINGS");
                    startActivity(wifiSettingsIntent);
                }

                @Override
                public void onCancelClick() {
                    ActivityController.finishActivity(MainActivity.this);
                }
            });
        }
        if (!commonWarningDialog.isShowing()) {
            commonWarningDialog.show();
        }
    }

    /**
     * 取消显示连接Wifi的弹窗
     */
    private void dismissWifiDialog() {
        if (commonWarningDialog != null && commonWarningDialog.isShowing()) {
            commonWarningDialog.dismiss();
        }
    }

    private DrawerLayout.DrawerListener drawerListener = new DrawerLayout.DrawerListener() {
        @Override
        public void onDrawerSlide(@NonNull View drawerView, float slideOffset) {
            llMain.scrollTo(-(int) (navigation.getWidth() * slideOffset), 0);
        }

        @Override
        public void onDrawerOpened(@NonNull View drawerView) {

        }

        @Override
        public void onDrawerClosed(@NonNull View drawerView) {

        }

        @Override
        public void onDrawerStateChanged(int newState) {

        }
    };

    private void initWebSocket() {
        Intent intent = new Intent(mContext, WebSocketService.class);
        intent.putExtra("ServerHost", SPHelper.getString("MessageServerIP", NetWork.WEBSOCKET_IP));
        intent.putExtra("WebSocketPort", SPHelper.getString("MessageServerPort", NetWork.WEBSOCKET_PORT));
        intent.putExtra("WebSocketName", String.valueOf(NetWork.WEBSOCKET_NAME));
        mContext.startService(intent);
    }

    private void initBroadcastReceiver() {
        myReceiver = new MyReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("RECEIVE_MALFUNCTION");
        intentFilter.addAction("CURRENT_PLAYING");
        intentFilter.addAction("NO_LONGER_PLAYING");
        intentFilter.addAction("KEY_DOWN");
        intentFilter.addAction("KEY_UP");
        intentFilter.addAction("WIFI_CONNECTED");
        intentFilter.addAction("WIFI_DISCONNECTED");
        intentFilter.addAction("MESSAGE_WEBSOCKET_CLOSED");
        mContext.registerReceiver(myReceiver, intentFilter);
    }

    /**
     * 显示或隐藏“打开悬浮窗”的提示
     */
    private void showOrHideNotification() {
        if (NotificationsUtil.isNotificationEnabled(this)) {
            llNotification.setVisibility(View.GONE);
        } else {
            llNotification.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return false;
            } else {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addCategory(Intent.CATEGORY_HOME);
                startActivity(intent);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private MalfunctionAdapter.OnItemClickListener onItemClickListener = (position) -> {

    };

    /**
     * onServiceConnected和onServiceDisconnected运行在UI线程中
     */
    private ServiceConnection serviceConnection1 = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketService.WebSocketServiceBinder binder = (WebSocketService.WebSocketServiceBinder) service;
            webSocketService = binder.getService();
            LogUtils.d(TAG, "绑定Service成功");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            webSocketService = null;
        }
    };

    /**
     * onServiceConnected和onServiceDisconnected运行在UI线程中
     */
    private ServiceConnection serviceConnection2 = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            iVoiceService = IVoiceService.Stub.asInterface(service);
            try {
                iVoiceService.registerCallback(iVoiceCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            LogUtils.d(TAG, "绑定Service成功");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            iVoiceService = null;
        }
    };

    /**
     * 被调用的方法运行在Binder线程池中，不能更新UI
     */
    private IVoiceCallback iVoiceCallback = new IVoiceCallback.Stub() {
        @Override
        public void enterRoomSuccess() throws RemoteException {
            // 加入房间成功
            runOnUiThread(new Thread(() -> {
                isInRoom = true;
                showToast(getString(R.string.InChatRoom));
                tvEnterRoom.setText(getString(R.string.releaseToExitChat));
                btnEnterRoom.setBackgroundResource(R.drawable.icon_chat_normal);
            }));
        }

        @Override
        public void leaveGroupSuccess() throws RemoteException {
            // 离开房间成功
            runOnUiThread(new Thread(() -> {
                isInRoom = false;
                showToast(getString(R.string.ExitChatRoom));
                tvEnterRoom.setText(getString(R.string.pressToJoinChat));
                btnEnterRoom.setBackgroundResource(R.drawable.icon_chat_pressed);
            }));
        }

        @Override
        public void startRecordSuccess() throws RemoteException {
            // 打开麦克风成功
            runOnUiThread(new Thread(() -> {
                isSpeaking = true;
                tvMessage.setText(getString(R.string.releaseFinish));
                btnSpeak.setBackgroundResource(R.drawable.icon_speak_normal);
            }));
        }

        @Override
        public void stopRecordSuccess() throws RemoteException {
            // 关闭麦克风成功
            runOnUiThread(new Thread(() -> {
                isSpeaking = false;
                tvMessage.setText(getString(R.string.pressToSpeak));
                btnSpeak.setBackgroundResource(R.drawable.icon_speak_pressed);
            }));
        }

        @Override
        public void useSpeakerSuccess() throws RemoteException {
            // 使用扬声器成功
            runOnUiThread(new Thread(() -> {
                isUseSpeaker = true;
                tvSpeaker.setText(getString(R.string.releaseToUseEarpiece));
                btnSpeaker.setBackgroundResource(R.drawable.icon_speaker_normal);
            }));
        }

        @Override
        public void useEarpieceSuccess() throws RemoteException {
            // 使用听筒成功
            runOnUiThread(new Thread(() -> {
                isUseSpeaker = false;
                tvSpeaker.setText(getString(R.string.pressToUseSpeaker));
                btnSpeaker.setBackgroundResource(R.drawable.icon_speaker_pressed);
            }));
        }

        @Override
        public void findNewUser(String ipAddress, String name, String speakStatus) {
            // 发送到主线程更新UI

        }

        @Override
        public void removeUser(String ipAddress, String name, String speakStatus) {
            // 发送到主线程更新UI

        }
    };

    private RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            // 先判断mContext是否为空，防止Activity已经onDestroy导致的java.lang.IllegalArgumentException: You cannot start a load for a destroyed activity
            if (mContext != null) {
                // 如果快速滑动，停止Glide的加载，停止滑动后恢复加载
                if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    sIsScrolling = true;
                    Glide.with(mContext).pauseRequests();
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (sIsScrolling) {
                        Glide.with(mContext).resumeRequests();
                    }
                    sIsScrolling = false;
                }
            }
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
        }
    };

    private View.OnClickListener onClickListener = (view) -> {
        Intent intent;
        switch (view.getId()) {
            case R.id.iv_right:

                break;
            case R.id.iv_icon:
                drawerLayout.openDrawer(GravityCompat.START);
                break;
            case R.id.ll_notification:
                intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("package:" + getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                startActivity(intent);
                break;
            case R.id.btnEnterRoom:
                vibrator.vibrate(50);
                // 进入/退出聊天
                if (isInRoom) {
                    if (iVoiceService != null) {
                        try {
                            iVoiceService.leaveGroup();
                            // 重置标记位
                            isInRoom = false;
                            if (isSpeaking) {
                                isSpeaking = false;
                                tvMessage.setText(getString(R.string.pressToSpeak));
                                btnSpeak.setBackgroundResource(R.drawable.icon_speak_pressed);
                            }
                            if (isUseSpeaker) {
                                isUseSpeaker = false;
                                tvSpeaker.setText(getString(R.string.pressToUseSpeaker));
                                btnSpeaker.setBackgroundResource(R.drawable.icon_speaker_pressed);
                            }

                            // 以下部分必须写，WebRTCHelper无法复用Context，因此需要重新创建VoiceService对象
                            restartVoiceService();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (!WifiUtil.WifiConnected(mContext)) {
                        showToast(getString(R.string.please_connect_wifi));
                    } else {
                        if (iVoiceService != null) {
                            try {
                                iVoiceService.enterGroup();
                            } catch (RemoteException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                break;
            case R.id.btnSpeak:
                vibrator.vibrate(50);
                // 打开/关闭麦克风
                if (isSpeaking) {
                    if (!isInRoom) {
                        showToast(getString(R.string.OutChatRoom));
                    }
                    if (iVoiceService != null) {
                        try {
                            iVoiceService.stopRecord();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    if (!isInRoom) {
                        showToast(getString(R.string.OutChatRoom));
                    }
                    if (iVoiceService != null) {
                        try {
                            iVoiceService.startRecord();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            case R.id.btnSpeaker:
                vibrator.vibrate(50);
                // 使用扬声器/听筒
                if (isInRoom) {
                    if (iVoiceService != null) {
                        try {
                            if (isUseSpeaker) {
                                iVoiceService.useEarpiece();
                            } else {
                                iVoiceService.useSpeaker();
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    showToast(getString(R.string.OutChatRoom));
                }
                break;
            case R.id.iv_userIcon:
                // 头像设置
                showSelectPicturePopupWindow(findViewById(R.id.drawer_layout));
                break;
            case R.id.llPersonalInformation:
                // 个人信息设置
                openActivity(SetPersonalInfoActivity.class);
                break;
            case R.id.llLanguageSettings:
                // 语言设置
                openActivity(LanguageActivity.class);
                break;
            case R.id.llVoiceServer:
                // 语音服务器设置（修改语音服务器的话需要重启Service）
                intent = new Intent(MainActivity.this, SetVoiceServerActivity.class);
                startActivityForResult(intent, REQUEST_CODE_SET_VOICE_SERVER);
                openActivity(SetVoiceServerActivity.class);
                break;
            case R.id.llMessageServer:
                // 消息服务器设置
                intent = new Intent(MainActivity.this, SetMessageServerActivity.class);
                startActivityForResult(intent, REQUEST_CODE_SET_MESSAGE_SERVER);
                openActivity(SetMessageServerActivity.class);
                break;
            case R.id.llWifiSetting:
                // 无线网设置
                Intent wifiSettingsIntent = new Intent("android.settings.WIFI_SETTINGS");
                startActivity(wifiSettingsIntent);
                break;
            case R.id.llVersion:
                // 版本查看
                break;
            case R.id.llUpdate:
                // 版本更新
                break;
            case R.id.llShare:
                // 版本分享
                break;
            case R.id.btnExit:
                // 彻底退出程序
                ActivityController.exit();
                break;
            default:
                break;
        }
    };

    /**
     * 重启VoiceService服务
     */
    private void restartVoiceService() {
        unbindService(serviceConnection2);
        Intent intent0 = new Intent(mContext, VoiceService.class);
        stopService(intent0);

        // 重新打开VoiceService
        Intent intent = new Intent(mContext, VoiceService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Intent intent1 = new Intent(mContext, VoiceService.class);
        bindService(intent1, serviceConnection2, BIND_AUTO_CREATE);
    }

    /**
     * 重启WebSocketService服务
     */
    private void restartWebSocketService() {
        if (webSocketService != null) {
            webSocketService.closeWebSocket();
            unbindService(serviceConnection1);
            Intent intent0 = new Intent(mContext, WebSocketService.class);
            stopService(intent0);

            // 重新打开VoiceService
            initWebSocket();
            Intent intent1 = new Intent(mContext, WebSocketService.class);
            bindService(intent1, serviceConnection1, BIND_AUTO_CREATE);
        }
    }

    private class MyReceiver extends BaseBroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            super.onReceive(context, intent);
            if (intent != null && intent.getAction() != null) {
                switch (intent.getAction()) {
                    case "RECEIVE_MALFUNCTION":
                        WebSocketData webSocketData = (WebSocketData) intent.getSerializableExtra("data");
                        if (webSocketData.isStatus()) {
                            malfunctionList.add(malfunctionList.size(), webSocketData);
                            malfunctionAdapter.notifyItemInserted(malfunctionList.size() - 1);
                        } else {
                            int position = -1;
                            for (int i = 0; i < malfunctionList.size(); i++) {
                                if (malfunctionList.get(i).getListNo() == webSocketData.getListNo()) {
                                    position = i;
                                    break;
                                }
                            }
                            if (position != -1) {
                                malfunctionList.remove(position);
                                malfunctionAdapter.notifyItemRemoved(position);
                            }
                        }
                        break;
                    case "CURRENT_PLAYING": {
                        int listNo = intent.getIntExtra("number", -1);
                        if (listNo == -1) {
                            for (int i = 0; i < malfunctionList.size(); i++) {
                                if (malfunctionList.get(i).isPalying()) {
                                    malfunctionList.get(i).setPalying(false);
                                    malfunctionAdapter.notifyItemChanged(i, false);
                                    break;
                                }
                            }
                        } else {
                            for (int i = 0; i < malfunctionList.size(); i++) {
                                if (listNo == malfunctionList.get(i).getListNo()) {
                                    if (!malfunctionList.get(i).isPalying()) {
                                        malfunctionList.get(i).setPalying(true);
                                        malfunctionAdapter.notifyItemChanged(i, true);
                                    }
                                } else {
                                    if (malfunctionList.get(i).isPalying()) {
                                        malfunctionList.get(i).setPalying(false);
                                        malfunctionAdapter.notifyItemChanged(i, false);
                                    }
                                }
                            }
                        }
                    }
                    break;
                    case "NO_LONGER_PLAYING": {
                        int listNo = intent.getIntExtra("number", -1);
                        if (listNo != -1) {
                            for (int i = 0; i < malfunctionList.size(); i++) {
                                if (listNo == malfunctionList.get(i).getListNo()) {
                                    malfunctionList.get(i).setPalying(false);
                                    LogUtils.d(TAG, "Activity中List的长度：" + malfunctionList.size() + "，不再播放位置：" + i);
                                    malfunctionAdapter.notifyItemChanged(i, i);
                                    break;
                                }
                            }
                        }
                    }
                    break;
                    case "KEY_DOWN":
                    case "KEY_UP":
                        vibrator.vibrate(50);
                        if (!isInRoom) {
                            showToast(getString(R.string.OutChatRoom));
                        }
                        break;
                    case "WIFI_DISCONNECTED":
                        // 清除TextView内容，弹出Dialog
                        tvIp.setText("");
                        tvMessage.setText("");
                        tvSSID.setText("");
                        showConnectWifiDialog();
                        break;
                    case "WIFI_CONNECTED":
                        // 取消显示Dialog
                        dismissWifiDialog();
                        // TextView显示网络信息
                        if (WifiUtil.WifiConnected(mContext)) {
                            tvSSID.setText(WifiUtil.getSSID(mContext));
                            tvIp.setText(WifiUtil.getLocalIPAddress());
                        }
                        break;
                    case "MESSAGE_WEBSOCKET_CLOSED":
                        // 异常信息推送的WebSocket断开了，清空列表
                        malfunctionList.clear();
                        malfunctionAdapter.notifyDataSetChanged();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    protected void showSelectPicturePopupWindow(View parent) {
        mSelectPicturePopupWindow.showPopupWindow(parent);
    }

    /**
     * 设置图片选择的回调监听
     *
     * @param l 监听器
     */
    public void setOnPictureSelectedListener(OnPictureSelectedListener l) {
        this.mOnPictureSelectedListener = l;
    }

    @Override
    public void onSelected(View v, int position) {
        switch (position) {
            case 0:
                // "拍照"按钮被点击了
                takePhoto();
                break;
            case 1:
                // "从相册选择"按钮被点击了
                pickFromGallery();
                break;
            case 2:
                // "取消"按钮被点击了
                mSelectPicturePopupWindow.dismissPopupWindow();
                break;
            default:
                break;
        }
    }

    /**
     * 打开相机拍照
     */
    private void takePhoto() {
        // Permission was added in API Level 16
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    getString(R.string.permission_write_storage_rationale),
                    REQUEST_STORAGE_WRITE_ACCESS_PERMISSION);
        } else {
            mSelectPicturePopupWindow.dismissPopupWindow();
            Intent takeIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            //下面这句指定调用相机拍照后的照片存储的路径
            takeIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(new File(mTempPhotoPath)));
            startActivityForResult(takeIntent, CAMERA_REQUEST_CODE);
            overridePendingTransition(R.anim.right_in, R.anim.left_out);
        }
    }

    /**
     * 从相册选择照片
     */
    private void pickFromGallery() {
        // Permission was added in API Level 16
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN
                && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE,
                    getString(R.string.permission_read_storage_rationale),
                    REQUEST_STORAGE_READ_ACCESS_PERMISSION);
        } else {
            mSelectPicturePopupWindow.dismissPopupWindow();
            Intent pickIntent = new Intent(Intent.ACTION_PICK, null);
            // 如果限制上传到服务器的图片类型时可以直接写如："image/jpeg 、 image/png等的类型"
            pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
            startActivityForResult(pickIntent, GALLERY_REQUEST_CODE);
            overridePendingTransition(R.anim.right_in, R.anim.left_out);
        }
    }

    /**
     * 图片裁剪完成的监听
     */
    private OnPictureSelectedListener onPictureSelectedListener = (fileUri, bitmap) -> {
        String filePath = fileUri.getEncodedPath();
        String imagePath = Uri.decode(filePath);
        LogUtils.d(TAG, "imagePath:" + imagePath);
        SPHelper.save("UserIconPath", imagePath);
//        String time = (new SimpleDateFormat("yyyyMMddHHmmss", Locale.CHINA)).format(new Date());
//        //以“.webp”格式作为图片扩展名
//        String type = "webp";
//        //将本软件的包路径+文件名拼接成图片绝对路径
//        String newFile = getExternalFilesDir("Icons") + "/" + time + "." + type;
    };

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case CAMERA_REQUEST_CODE:
                    // 调用相机拍照
                    File temp = new File(mTempPhotoPath);
                    startCropActivity(Uri.fromFile(temp));
                    overridePendingTransition(R.anim.left_in, R.anim.right_out);
                    break;
                case GALLERY_REQUEST_CODE:
                    // 直接从相册获取
                    startCropActivity(data.getData());
                    overridePendingTransition(R.anim.left_in, R.anim.right_out);
                    break;
                case UCrop.REQUEST_CROP:
                    // 裁剪图片结果
                    handleCropResult(data);
                    break;
                case UCrop.RESULT_ERROR:
                    // 裁剪图片错误
                    handleCropError(data);
                    break;
                case REQUEST_CODE_SET_VOICE_SERVER:
                    restartVoiceService();
                    break;
                case REQUEST_CODE_SET_MESSAGE_SERVER:
                    restartWebSocketService();
                    break;
                default:
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * 裁剪图片方法实现
     *
     * @param uri 图片路径
     */
    public void startCropActivity(Uri uri) {
        UCrop.of(uri, mDestinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(512, 512)
                .withTargetActivity(CropActivity.class)
                .start(this);
    }

    /**
     * 处理剪切成功的返回值
     *
     * @param result 返回值
     */
    private void handleCropResult(Intent result) {
        deleteTempPhotoFile();
        final Uri resultUri = UCrop.getOutput(result);
        if (null != resultUri && null != mOnPictureSelectedListener) {
            Bitmap bitmap = null;
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), resultUri);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mOnPictureSelectedListener.onPictureSelected(resultUri, bitmap);
            overridePendingTransition(R.anim.left_in, R.anim.right_out);
        } else {
            showToast("无法剪切选择图片");
        }
    }

    /**
     * 处理剪切失败的返回值
     *
     * @param result 返回值
     */
    private void handleCropError(Intent result) {
        deleteTempPhotoFile();
        Throwable cropError = UCrop.getError(result);
        if (cropError != null) {
            showToast(cropError.getMessage());
        } else {
            showToast("无法剪切选择图片");
        }
    }

    /**
     * 删除拍照临时文件
     */
    private void deleteTempPhotoFile() {
        File tempFile = new File(mTempPhotoPath);
        if (tempFile.exists() && tempFile.isFile()) {
            boolean deleteResult = tempFile.delete();
            if (deleteResult) {
                LogUtils.d("文件删除成功");
            }
        }
    }

    /**
     * 请求权限
     * 如果权限被拒绝过，则提示用户需要权限
     */
    protected void requestPermission(String permission, String rationale, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(permission)) {
                showAlertDialog(getString(R.string.permission_title_rationale), rationale,
                        (dialog, which) -> {
                            requestPermissions(new String[]{permission}, requestCode);
                        }, getString(R.string.label_ok));
            } else {
                requestPermissions(new String[]{permission}, requestCode);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        for (int i = 0; i < permissions.length; i++) {
            LogUtils.d(WebRTCHelper.TAG, "[Permission] " + permissions[i] + " is " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "granted" : "denied"));
            if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                finish();
                break;
            }
        }
        switch (requestCode) {
            case REQUEST_STORAGE_READ_ACCESS_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickFromGallery();
                }
                break;
            case REQUEST_STORAGE_WRITE_ACCESS_PERMISSION:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto();
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    protected void showAlertDialog(String title, String message, DialogInterface.OnClickListener onPositiveButtonClickListener, String positiveText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(positiveText, onPositiveButtonClickListener);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webSocketService != null) {
            webSocketService.closeWebSocket();
        }
        if (iVoiceService != null) {
            try {
                iVoiceService.unRegisterCallback(iVoiceCallback);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        if (myReceiver != null) {
            mContext.unregisterReceiver(myReceiver);
        }
    }

}