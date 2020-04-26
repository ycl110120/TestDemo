package com.chatdemo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.AnimationDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chatdemo.adapter.ChatAdapter;
import com.chatdemo.bean.AudioMsgBody;
import com.chatdemo.bean.FileMsgBody;
import com.chatdemo.bean.ImageMsgBody;
import com.chatdemo.bean.MsgSendStatus;
import com.chatdemo.bean.MsgType;
import com.chatdemo.bean.MyMessage;
import com.chatdemo.bean.TextMsgBody;
import com.chatdemo.bean.VideoMsgBody;
import com.chatdemo.utils.ChatUiHelper;
import com.chatdemo.utils.FileUtils;
import com.chatdemo.utils.HttpRequester;
import com.chatdemo.utils.LogUtil;
import com.chatdemo.utils.PictureFileUtil;
import com.chatdemo.view.RecordButton;
import com.chatdemo.view.StateButton;
import com.chatdemo.widget.Constants;
import com.chatdemo.widget.MediaManager;
import com.luck.picture.lib.PictureSelector;
import com.luck.picture.lib.entity.LocalMedia;
import com.nbsp.materialfilepicker.ui.FilePickerActivity;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import okhttp3.Call;
import okhttp3.MediaType;


public class ChatActivity extends Activity implements  SwipeRefreshLayout.OnRefreshListener {


    private ChatAdapter mAdapter;

    public static final String mSenderId = "right";
    public static final String mTargetId = "left";

    @BindView(R.id.ivAudio)
    ImageView mIvAudio;//录音图片
    @BindView(R.id.btnAudio)
    RecordButton mBtnAudio;//录音按钮
    @BindView(R.id.rv_chat_list)
    RecyclerView mRvChat;
    @BindView(R.id.llContent)
    LinearLayout mLlContent;
    @BindView(R.id.btn_send)
    StateButton mBtnSend;//发送按钮
    @BindView(R.id.et_content)
    EditText mEtContent;
    @BindView(R.id.bottom_layout)
    RelativeLayout mRlBottomLayout;//表情,添加底部布局
    //    LinearLayout mLlEmotion;//表情布局
    @BindView(R.id.llAdd)
    LinearLayout mLlAdd;//添加布局
    @BindView(R.id.ivAdd)
    ImageView mIvAdd;
    //    ImageView mIvEmo;
    @BindView(R.id.swipe_chat)
    SwipeRefreshLayout mSwipeRefresh;//下拉刷新

    public static final int REQUEST_CODE_IMAGE = 0000;
    public static final int REQUEST_CODE_VEDIO = 1111;
    public static final int REQUEST_CODE_FILE = 2222;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        //绑定初始化ButterKnife
        ButterKnife.bind(this);
        initView();
    }

    private ImageView ivAudio;

    private void initView() {
//        mLlEmotion = (LinearLayout) findViewById(R.id.rlEmotion);
//        rlVideo = (RelativeLayout) mLlAdd.findViewById(R.id.rlVideo);
//        rlFile = (RelativeLayout) mLlAdd.findViewById(R.id.rlFile);
//        rlVideo.setOnClickListener(this);
//        rlFile.setOnClickListener(this);
//        mIvEmo = (ImageView) findViewById(R.id.ivEmo);
        mSwipeRefresh.setOnRefreshListener(this);

        mAdapter = new ChatAdapter(this, new ArrayList<MyMessage>());
        LinearLayoutManager mLinearLayout = new LinearLayoutManager(this);
        mRvChat.setLayoutManager(mLinearLayout);
        mRvChat.setAdapter(mAdapter);

        initChatUi();//

        mAdapter.setOnItemChildClickListener(new BaseQuickAdapter.OnItemChildClickListener() {
            @Override
            public void onItemChildClick(BaseQuickAdapter adapter, View view, int position) {
                if (ivAudio != null) {
                    ivAudio.setBackgroundResource(R.mipmap.audio_animation_list_right_3);
                    ivAudio = null;
                    MediaManager.reset();
                } else {
                    ivAudio = view.findViewById(R.id.ivAudio);
                    MediaManager.reset();
                    ivAudio.setBackgroundResource(R.drawable.audio_animation_right_list);
                    AnimationDrawable drawable = (AnimationDrawable) ivAudio.getBackground();
                    drawable.start();
                    MediaManager.playSound(ChatActivity.this, ((AudioMsgBody) mAdapter.getData().get(position).getBody()).getLocalPath(), new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            LogUtil.d("开始播放结束");
                            ivAudio.setBackgroundResource(R.mipmap.audio_animation_list_right_3);
                            MediaManager.release();
                        }
                    });
                }
            }
        });
    }

    private void initChatUi() {
        final ChatUiHelper mUiHelper = ChatUiHelper.with(this);
        mUiHelper.bindContentLayout(mLlContent)
                .bindttToSendButton(mBtnSend)
                .bindEditText(mEtContent)
                .bindBottomLayout(mRlBottomLayout)
//                .bindEmojiLayout(mLlEmotion)
                .bindAddLayout(mLlAdd)
                .bindToAddButton(mIvAdd)
//                .bindToEmojiButton(mIvEmo)
                .bindAudioBtn(mBtnAudio)
                .bindAudioIv(mIvAudio);
//                .bindEmojiData();
        //底部布局弹出,聊天列表上滑
        mRvChat.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (bottom < oldBottom) {
                    mRvChat.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mAdapter.getItemCount() > 0) {
                                mRvChat.smoothScrollToPosition(mAdapter.getItemCount() - 1);
                            }
                        }
                    });
                }
            }
        });
        //点击空白区域关闭键盘
        mRvChat.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mUiHelper.hideBottomLayout(false);
                mUiHelper.hideSoftInput();
                mEtContent.clearFocus();
//                mIvEmo.setImageResource(R.mipmap.ic_emoji);
                return false;
            }
        });
        //TODO 录音
        ((RecordButton) mBtnAudio).setOnFinishedRecordListener(new RecordButton.OnFinishedRecordListener() {
            @Override
            public void onFinishedRecord(String audioPath, int time) {
                LogUtil.d("录音结束回调");
                File file = new File(audioPath);
                if (file.exists()) {
                    sendAudioMessage(audioPath, time);
                }
            }
        });
    }

    private MyMessage getBaseReceiveMessage(MsgType msgType) {
        MyMessage mMessgae = new MyMessage();
        mMessgae.setUuid(UUID.randomUUID() + "");
        mMessgae.setSenderId(mTargetId);
        mMessgae.setTargetId(mSenderId);
        mMessgae.setSentTime(System.currentTimeMillis());
        mMessgae.setSentStatus(MsgSendStatus.SENDING);
        mMessgae.setMsgType(msgType);
        return mMessgae;
    }

    private MyMessage getBaseSendMessage(MsgType msgType) {
        MyMessage mMessgae = new MyMessage();
        mMessgae.setUuid(UUID.randomUUID() + "");
        mMessgae.setSenderId(mSenderId);
        mMessgae.setTargetId(mTargetId);
        mMessgae.setSentTime(System.currentTimeMillis());
        mMessgae.setSentStatus(MsgSendStatus.SENDING);
        mMessgae.setMsgType(msgType);
        return mMessgae;
    }

    private void updateMsg(final MyMessage mMessgae) {
        mRvChat.scrollToPosition(mAdapter.getItemCount() - 1);
        //模拟2秒后发送成功
        new Handler().postDelayed(new Runnable() {
            public void run() {
                int position = 0;
                mMessgae.setSentStatus(MsgSendStatus.SENT);
                //更新单个子条目
                for (int i = 0; i < mAdapter.getData().size(); i++) {
                    MyMessage mAdapterMyMessage = mAdapter.getData().get(i);
                    if (mMessgae.getUuid().equals(mAdapterMyMessage.getUuid())) {
                        position = i;
                    }
                }
                mAdapter.notifyItemChanged(position);
            }
        }, 2000);
    }

    //语音消息
    private void sendAudioMessage(final String path, int time) {
        final MyMessage mMessgae = getBaseSendMessage(MsgType.AUDIO);
        AudioMsgBody mFileMsgBody = new AudioMsgBody();
        mFileMsgBody.setLocalPath(path);
        mFileMsgBody.setDuration(time);
        mMessgae.setBody(mFileMsgBody);
        //开始发送
        mAdapter.addData(mMessgae);
        //模拟两秒后发送成功
        updateMsg(mMessgae);
    }

    //文本消息
    private void sendTextMsg(String hello) {
        final MyMessage mMessgae = getBaseSendMessage(MsgType.TEXT);
        TextMsgBody mTextMsgBody = new TextMsgBody();
        mTextMsgBody.setMessage(hello);
        mMessgae.setBody(mTextMsgBody);
        //开始发送
        mAdapter.addData(mMessgae);
        //模拟两秒后发送成功
        updateMsg(mMessgae);
    }


    //图片消息
    private void sendImageMessage(final LocalMedia media) {
        final MyMessage mMessgae = getBaseSendMessage(MsgType.IMAGE);
        ImageMsgBody mImageMsgBody = new ImageMsgBody();
        mImageMsgBody.setThumbUrl(media.getCompressPath());
        mMessgae.setBody(mImageMsgBody);
        //开始发送
        mAdapter.addData(mMessgae);
        //模拟两秒后发送成功
        updateMsg(mMessgae);
    }


    //视频消息
    private void sendVedioMessage(final LocalMedia media) {
        final MyMessage mMessgae = getBaseSendMessage(MsgType.VIDEO);
        //生成缩略图路径
        String vedioPath = media.getPath();
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mediaMetadataRetriever.setDataSource(vedioPath);
        Bitmap bitmap = mediaMetadataRetriever.getFrameAtTime();
        String imgname = System.currentTimeMillis() + ".jpg";
        String urlpath = Environment.getExternalStorageDirectory() + "/" + imgname;
        File f = new File(urlpath);
        try {
            if (f.exists()) {
                f.delete();
            }
            FileOutputStream out = new FileOutputStream(f);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            LogUtil.d("视频缩略图路径获取失败：" + e.toString());
            e.printStackTrace();
        }
        VideoMsgBody mVideoMsgBody = new VideoMsgBody();
        mVideoMsgBody.setExtra(urlpath);
        mMessgae.setBody(mVideoMsgBody);
        //开始发送
        mAdapter.addData(mMessgae);
        //模拟两秒后发送成功
        updateMsg(mMessgae);
    }

    //文件消息
    private void sendFileMessage(String from, String to, final String path) {
        final MyMessage mMessgae = getBaseSendMessage(MsgType.FILE);
        FileMsgBody mFileMsgBody = new FileMsgBody();
        mFileMsgBody.setLocalPath(path);
        mFileMsgBody.setDisplayName(FileUtils.getFileName(path));
        mFileMsgBody.setSize(FileUtils.getFileLength(path));
        mMessgae.setBody(mFileMsgBody);
        //开始发送
        mAdapter.addData(mMessgae);
        //模拟两秒后发送成功
        updateMsg(mMessgae);
    }

    @OnClick({R.id.btn_send,R.id.rlPhoto})
    public void onViewClicked(View v) {
        switch (v.getId()) {
            case R.id.btn_send:
                //TODO 文字
                sendTextMsg(mEtContent.getText().toString());
                JSONObject js = new JSONObject();
                try {
                    js.put("message", mEtContent.getText().toString());
                    js.put("sender", 11);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                postTextData(js);
                mEtContent.setText("");
                break;
            case R.id.rlPhoto:
                PictureFileUtil.openGalleryPic(ChatActivity.this, REQUEST_CODE_IMAGE);
                break;
//            case R.id.rlVideo:
//                PictureFileUtil.openGalleryAudio(ChatActivity.this, REQUEST_CODE_VEDIO);
//                break;
//            case R.id.rlFile:
//                PictureFileUtil.openFile(ChatActivity.this, REQUEST_CODE_FILE);
//                break;
//            case R.id.rlLocation:
//                break;
        }
    }

    private void postTextData(JSONObject json) {
        OkHttpUtils
                .postString()
                .url(Constants.URL_CHAT)
                .content(json.toString())
                .mediaType(MediaType.parse("application/json; charset=utf-8"))
                .build()
                .execute(new StringCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        LogUtil.e("onError:" + e);
                    }

                    @Override
                    public void onResponse(String response, int id) {
//                        LogUtil.e("onResponse:" + response);
                        try {
                            JSONObject o = new JSONObject(response.replaceAll("[\\[\\]]", ""));
                            LogUtil.e("text:" + o.getString("text"));

                            MyMessage mMessgaeText = getBaseReceiveMessage(MsgType.TEXT);
                            TextMsgBody mTextMsgBody = new TextMsgBody();
                            mTextMsgBody.setMessage(o.getString("text"));
                            mMessgaeText.setBody(mTextMsgBody);
                            mAdapter.addData(mMessgaeText);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_IMAGE:
                    // TODO 图片选择结果回调
                    List<LocalMedia> selectListPic = PictureSelector.obtainMultipleResult(data);
                    for (LocalMedia media : selectListPic) {
                        LogUtil.e("获取图片路径成功:" + media.getPath());
//                        new MyTask().execute(media.getPath());
                        postImgData(media.getPath());
                        sendImageMessage(media);
                    }
                    break;
                case REQUEST_CODE_FILE:
                    String filePath = data.getStringExtra(FilePickerActivity.RESULT_FILE_PATH);
                    LogUtil.d("获取到的文件路径:" + filePath);
                    sendFileMessage(mSenderId, mTargetId, filePath);
                    break;
                case REQUEST_CODE_VEDIO:
                    // 视频选择结果回调
                    List<LocalMedia> selectListVideo = PictureSelector.obtainMultipleResult(data);
                    for (LocalMedia media : selectListVideo) {
                        LogUtil.d("获取视频路径成功:" + media.getPath());
                        sendVedioMessage(media);
                    }
                    break;
            }
        }
    }

    private void postImgData(String path) {
        File imgFile = new File(path);
        OkHttpUtils
                .post()
                .url("")
                .addFile("imgFile", imgFile.getName(), imgFile)
                .addHeader("Content-Disposition", "multipart/form-data")
                .build()
                .execute(new StringCallback() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        LogUtil.e("onError:" + e);
                    }

                    @Override
                    public void onResponse(String response, int id) {
                        LogUtil.e("onResponse:" + response);
                    }
                });
    }

    class MyTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... params) {
//            Map<String, String> param = new HashMap<String, String>();
//            param.put("type", "android");
            Map<String, File> maps = new HashMap<String, File>();
            maps.put("image", new File(params[0]));
            LogUtil.e("图片路径:" + params[0]);
            InputStream is = HttpRequester.post("http://192.168.199.2/Test/Upload/upload", null, maps);
            try {
                return new String(HttpRequester.read(is));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);
            LogUtil.e("onPostExecute:" + result);
        }
    }

    @Override
    public void onRefresh() {
        //下拉刷新模拟获取历史消息
        List<MyMessage> mReceiveMsgList = new ArrayList<MyMessage>();
        //构建文本消息
        MyMessage mMessgaeText = getBaseReceiveMessage(MsgType.TEXT);
        TextMsgBody mTextMsgBody = new TextMsgBody();
        mTextMsgBody.setMessage("收到的消息");
        mMessgaeText.setBody(mTextMsgBody);
        mReceiveMsgList.add(mMessgaeText);
        //构建图片消息
        MyMessage mMessgaeImage = getBaseReceiveMessage(MsgType.IMAGE);
        ImageMsgBody mImageMsgBody = new ImageMsgBody();
        mImageMsgBody.setThumbUrl("http://pic19.nipic.com/20120323/9248108_173720311160_2.jpg");
        mMessgaeImage.setBody(mImageMsgBody);
        mReceiveMsgList.add(mMessgaeImage);
        //构建文件消息
        MyMessage mMessgaeFile = getBaseReceiveMessage(MsgType.FILE);
        FileMsgBody mFileMsgBody = new FileMsgBody();
        mFileMsgBody.setDisplayName("收到的文件");
        mFileMsgBody.setSize(12);
        mMessgaeFile.setBody(mFileMsgBody);
        mReceiveMsgList.add(mMessgaeFile);
        mAdapter.addData(0, mReceiveMsgList);
        mSwipeRefresh.setRefreshing(false);
    }
}
