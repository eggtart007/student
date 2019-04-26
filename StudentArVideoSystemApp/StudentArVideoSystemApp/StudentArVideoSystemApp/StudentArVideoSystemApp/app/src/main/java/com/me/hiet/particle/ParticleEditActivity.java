package com.me.hiet.particle;

import android.content.Context;
import android.content.Intent;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.meicam.sdk.NvsAVFileInfo;
import com.meicam.sdk.NvsAssetPackageParticleDescParser;
import com.meicam.sdk.NvsAudioResolution;
import com.meicam.sdk.NvsMultiThumbnailSequenceView;
import com.meicam.sdk.NvsParticleSystemContext;
import com.meicam.sdk.NvsRational;
import com.meicam.sdk.NvsSize;
import com.meicam.sdk.NvsStreamingContext;
import com.meicam.sdk.NvsTimeline;
import com.meicam.sdk.NvsTimelineVideoFx;
import com.meicam.sdk.NvsVideoClip;
import com.meicam.sdk.NvsVideoResolution;
import com.meicam.sdk.NvsVideoStreamInfo;
import com.meicam.sdk.NvsVideoTrack;
import com.meishe.sdkdemo.R;
import com.me.hiet.hite.BaseActivity;
import com.me.hiet.douyin.bean.FilterFxInfo;
import com.me.hiet.edit.CompileVideoFragment;
import com.me.hiet.edit.data.FilterItem;
import com.me.hiet.edit.record.SqView;
import com.me.hiet.interfaces.TipsButtonClickListener;
import com.me.hiet.utils.AppManager;
import com.me.hiet.utils.ScreenUtils;
import com.me.hiet.utils.TimeFormatUtil;
import com.me.hiet.utils.TimelineUtil;
import com.me.hiet.utils.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class ParticleEditActivity extends BaseActivity {
    private final String TAG = "ParticleEditActivity";
    private final int TIME_BASE = 1000000;
    private Context mContext;
    private final int MSG_TYPE_UPDATE_PLAY_SEEKBAR = 2;
    private final int MSG_TYPE_PARTICLE_FX = 3;
    private final int FRAME_RATION = 25;

    // 顶部操作栏
    private RelativeLayout mBackBtn;
    private Button mCancelBtn;
    private RelativeLayout mSaveBtn;
    private ImageButton mPlayBtn;

    // 视频相关
    private LiveWindow mLiveWindow;
    private RelativeLayout mLiveWindowLayout, mRevertLayout;
    private ParticleSqLayout mSequenceLayout;
    private List<NvsVideoClip> mClipList = new ArrayList<>();
    private NvsStreamingContext mStreamingContext;
    private NvsTimeline mTimeline;
    private NvsVideoTrack mVideoTrack;
    private SqView mSequenceView;
    private FilterFxInfo mCurFilterFxInfo;

    // 粒子特效
    private RecyclerView mEffectRecyclerView;
    private Map<String, FilterItem> mParticleMap;
    private List<FilterItem> mParticleFxList;
    private String mCurrentParticleFxId = "";
    private int mCurrentParticlePos = 0;
    private NvsTimelineVideoFx mCurrentVideoFx;
    private int mCurrentEmitter = 0;
    private List<String> mCurrentEmitterList;
    private LinearLayout mParticleRangeLayout;
    private SeekBar mParticleRangeSeekBar, mParticleSizeRangeSeekBar;
    private TextView mParticleRangeText, mParticleSizeRangeText;
    private float mParticleSizeValue = 1.0f, mParticleRateValue = 1.0f;
    private float mCurrentX, mCurrentY;
    private long mMoveBegin, mSeekBegin, mMoveSystemBegin;

    // 生成视频
    private RelativeLayout mCompilePage;
    private CompileVideoFragment mCompileVideoFragment;
    private NvsSize mVideoDimension;
    private ArrayList<String> mPath = new ArrayList<>();
    private int mTimeLineWidth = 0, mTimeLineHeight = 0;
    private int mVideoWidth = 0, mVideoHeight = 0;
    private Timer mFxTimer;

    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            // TODO Auto-generated method stub
            switch (msg.what) {
                case MSG_TYPE_UPDATE_PLAY_SEEKBAR:
                    if (mStreamingContext == null)
                        break;
                    seekTimeline(0, 0);
                    mSeekBegin = 0;
                    mPlayBtn.setBackgroundResource(R.drawable.particle_video_play);
                    mSequenceLayout.updateIndicator(0);
                    break;
                case MSG_TYPE_PARTICLE_FX:
                    if (mStreamingContext == null)
                        break;
                    long time_total = mTimeline.getDuration(); // 总时长
                    long time_cur = mSeekBegin; // 当前时长
                    long time_each_frame = 1000 * 1000 / 25; // 每帧时长
                    long time_each_seek = 1 * time_each_frame;

                    if (time_cur + time_each_seek < time_total) {
                        // 转化成时间线坐标
                        PointF nowPointF = mLiveWindow.mapViewToCanonical(new PointF(mCurrentX, mCurrentY));
                        if (mCurrentParticleFxId != null && !mCurrentParticleFxId.isEmpty() && mCurrentVideoFx != null && mCurrentEmitterList != null && mCurrentEmitterList.size() > 0) {
                            // 发射器名字
                            if (mCurrentEmitter >= mCurrentEmitterList.size())
                                mCurrentEmitter = 0;
                            NvsParticleSystemContext particleContext = mCurrentVideoFx.getParticleSystemContext();
                            PointF particlePointF = mCurrentVideoFx.mapPointFromCanonicalToParticleSystem(nowPointF);
                            for (int i = 0; i < mCurrentEmitterList.size(); ++i) {
                                String emitterName = mCurrentEmitterList.get(i);
                                particleContext.appendPositionToEmitterPositionCurve(emitterName, (float) (time_cur + time_each_seek - mMoveBegin) / 1000000, particlePointF.x, particlePointF.y);
                            }
                        }
                        // 播放器前进40ms
                        seekTimeline(time_cur + time_each_seek, 0);
                        updateSeekBarPosition(mStreamingContext.getTimelineCurrentPosition(mTimeline));

                        mSeekBegin = mSeekBegin + time_each_seek;
                        mMoveSystemBegin = TimeFormatUtil.getCurrentTimeMS();

                        // 下一个发射器
                        ++mCurrentEmitter;
                    } else {
                        Log.i(TAG, "over: stop timer");
                        stopParticleTimer();
                    }
                default:
                    break;
            }
            return false;
        }
    });
    private TextView mCompileTv;

    @Override
    protected int initRootView() {
        initStreamingContext(); // 初始化流媒体上下文
        mContext = this;
        return R.layout.activity_particle_edit;
    }

    @Override
    protected void initViews() {
        mLiveWindow = (LiveWindow) findViewById(R.id.live_window);
        mLiveWindowLayout = (RelativeLayout) findViewById(R.id.live_window_layout);
        mRevertLayout = (RelativeLayout) findViewById(R.id.seekbar_layout);
        mSequenceLayout = (ParticleSqLayout) findViewById(R.id.sequence_layout);
        mPlayBtn = (ImageButton) findViewById(R.id.play_video);
        mBackBtn = (RelativeLayout) findViewById(R.id.btn_back);
        mCancelBtn = (Button) findViewById(R.id.btn_cancel);
        mSaveBtn = (RelativeLayout) findViewById(R.id.compileBtn);
        mCompileTv = (TextView) findViewById(R.id.compile_to_tv);
        mCompileTv.getPaint().setFakeBoldText(true);
        mEffectRecyclerView = (RecyclerView) findViewById(R.id.particle_effect_recyclerview);
        mParticleRangeLayout = (LinearLayout) findViewById(R.id.particle_range_layout);
        mParticleRangeSeekBar = (SeekBar) findViewById(R.id.particle_range_seekBar);
        mParticleRangeText = (TextView) findViewById(R.id.particle_cur_range_text);
        mParticleSizeRangeSeekBar = (SeekBar) findViewById(R.id.particle_size_seekBar);
        mParticleSizeRangeText = (TextView) findViewById(R.id.particle_cur_size_text);
        mCompilePage = (RelativeLayout) findViewById(R.id.compilePage);
        mParticleRangeSeekBar.setMax(30);
        mParticleRangeSeekBar.setProgress(10);
        mParticleSizeRangeSeekBar.setMax(30);
        mParticleSizeRangeSeekBar.setProgress(10);
    }

    @Override
    protected void initTitle() {

    }

    @Override
    protected void initData() {
        Intent intent = this.getIntent();
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                ArrayList<String> paths = bundle.getStringArrayList("video_paths");
                if (paths != null) {
                    mPath = paths;
                    getVideoPixelAspectRatio(paths);
                }
                mParticleMap = (Map<String, FilterItem>) bundle.getSerializable("fx_touch");
                if (mParticleMap != null) {
                    mParticleFxList = new ArrayList(mParticleMap.values());
                }
            }
        }
        // 初始化TimeLine
        initTimeline();

        initRecyclerView();
        initCompileVideoFragment();
        loadVideoClipFailTips();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mFxTimer != null) {
            mFxTimer.cancel();
            mFxTimer = null;
        }
        if (mCurrentVideoFx != null)
            mCurrentVideoFx = null;
    }

    @Override
    public void onBackPressed() {
        finishActivity();
        AppManager.getInstance().finishActivity();
        super.onBackPressed();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.live_window_layout:
            case R.id.seekbar_layout:
            case R.id.sequence_layout:
                showParticleParamPage();
                break;
            default:
                break;
        }
    }

    @Override
    protected void initListener() {
        mBackBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishActivity();
                AppManager.getInstance().finishActivity();
            }
        });

        mCancelBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showParticleParamPage();
                if (mTimeline != null) {
                    NvsTimelineVideoFx lastVideoFx = mTimeline.getLastTimelineVideoFx();
                    if (lastVideoFx != null) {
                        long last_fx_in_point = lastVideoFx.getInPoint();
                        mTimeline.removeTimelineVideoFx(lastVideoFx);
                        seekTimeline(last_fx_in_point, 0);
                        updateSeekBarPosition(last_fx_in_point);
                        mSequenceLayout.updateIndicator(last_fx_in_point);
                        mSequenceLayout.deleteFxView(last_fx_in_point);
                    }
                }
            }
        });

        mSaveBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showParticleParamPage();
                mStreamingContext.stop();

                // stop是异步操作，所以在此延迟执行500ms
                new Handler().postDelayed(new Runnable() {
                    public void run() {
                        mCompilePage.setVisibility(View.VISIBLE);
                        mCompileVideoFragment.compileVideo();
                    }
                }, 500);
            }
        });

        mPlayBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showParticleParamPage();
                int state = mStreamingContext.getStreamingEngineState();
                if (NvsStreamingContext.STREAMING_ENGINE_STATE_SEEKING == state || NvsStreamingContext.STREAMING_ENGINE_STATE_STOPPED == state) {
                    mStreamingContext.playbackTimeline(mTimeline, mStreamingContext.getTimelineCurrentPosition(mTimeline), -1, NvsStreamingContext.VIDEO_PREVIEW_SIZEMODE_LIVEWINDOW_SIZE, true, 0);
                } else if (NvsStreamingContext.STREAMING_ENGINE_STATE_PLAYBACK == state) {
                    mStreamingContext.stop();
                }
            }
        });

        mCompilePage.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });

        mCompileVideoFragment.setCompileVideoListener(new CompileVideoFragment.OnCompileVideoListener() {
            @Override
            public void compileProgress(NvsTimeline timeline, int progress) {

            }

            @Override
            public void compileFinished(NvsTimeline timeline) {
                mCompilePage.setVisibility(View.GONE);
            }

            @Override
            public void compileFailed(NvsTimeline timeline) {
                mCompilePage.setVisibility(View.GONE);
            }

            @Override
            public void compileCompleted(NvsTimeline nvsTimeline, boolean isCanceled) {
                mCompilePage.setVisibility(View.GONE);
            }

            @Override
            public void compileVideoCancel() {
                mCompilePage.setVisibility(View.GONE);
            }
        });
        mSequenceLayout.setOndataChanged(new ParticleSqLayout.OnDataChangedListener() {
            @Override
            public void onDataChange(long time) {
                showParticleParamPage();
                seekTimeline(time, 0);
            }
        });

        mParticleSizeRangeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    float fRange = (float) progress / 10;
                    if (fRange <= 0.2f)
                        fRange = 0.2f;
                    mParticleSizeRangeText.setText("" + fRange + "X");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (seekBar.getProgress() <= 2) {
                    progress = 2;
                }
                mParticleSizeValue = (float) progress / 10.0f;
            }
        });

        mParticleRangeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean fromUser) {
                if (fromUser) {
                    float fRange = (float) i / 10;
                    if (fRange <= 0.2f)
                        fRange = 0.2f;
                    mParticleRangeText.setText("" + fRange + "X");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                if (seekBar.getProgress() <= 2) {
                    progress = 2;
                }
                mParticleRateValue = (float) progress / 10.0f;
            }
        });

        mLiveWindow.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                mCurrentX = motionEvent.getX();
                mCurrentY = motionEvent.getY();
                int state = mStreamingContext.getStreamingEngineState();
                switch (motionEvent.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        showParticleParamPage();
                        Log.i(TAG, "down: x = " + mCurrentX + " y: " + mCurrentY);
                        mSeekBegin = mStreamingContext.getTimelineCurrentPosition(mTimeline);
                        // 开始时间线特效
                        if (mCurrentParticleFxId != null && !mCurrentParticleFxId.isEmpty() && mStreamingContext != null) {
                            mMoveSystemBegin = TimeFormatUtil.getCurrentTimeMS();
                            mMoveBegin = mStreamingContext.getTimelineCurrentPosition(mTimeline);
                            mCurrentVideoFx = mTimeline.addPackagedTimelineVideoFx(mMoveBegin, mTimeline.getDuration(), mCurrentParticleFxId);
                            mCurrentVideoFx.setFloatVal("Tail Fading Duration", 0.5); // 拖尾效果的时长（s）

                            // 发射器列表
                            if (mParticleMap != null) {
                                FilterItem xml = mParticleMap.get(mCurrentParticleFxId);
                                if (xml != null) {
                                    NvsAssetPackageParticleDescParser parser = new NvsAssetPackageParticleDescParser(xml.getAssetDescription());
                                    mCurrentEmitterList = parser.GetParticlePartitionEmitter(0);

                                    //设置第一关键帧
                                    PointF nowPointF = mLiveWindow.mapViewToCanonical(new PointF(mCurrentX, mCurrentY));
                                    PointF particlePointF = mCurrentVideoFx.mapPointFromCanonicalToParticleSystem(nowPointF);
                                    Log.i(TAG, "particle x: " + particlePointF.x + " new y: " + particlePointF.y);
                                    if (mCurrentEmitterList != null) {
                                        NvsParticleSystemContext particleContext = mCurrentVideoFx.getParticleSystemContext();
                                        for (int i = 0; i < mCurrentEmitterList.size(); i++) {
                                            particleContext.appendPositionToEmitterPositionCurve(mCurrentEmitterList.get(i), 0, particlePointF.x, particlePointF.y);
                                            particleContext.setEmitterRateGain(mCurrentEmitterList.get(i), mParticleRateValue);
                                            particleContext.setEmitterParticleSizeGain(mCurrentEmitterList.get(i), mParticleSizeValue);
                                        }
                                    }
                                    long cur_ms_system = TimeFormatUtil.getCurrentTimeMS();
                                    long time_each_seek = (cur_ms_system - mMoveSystemBegin) * 1000;
                                    long seek_time = mSeekBegin + time_each_seek;
                                    seekTimeline(seek_time, 0);
                                }
                            }
                            startDrawSequenceProgress();
                        }
                        // 开始刷新粒子特效
                        startParticleTimer();

                        break;
                    case MotionEvent.ACTION_MOVE:
                        long cur_us_timeline = mStreamingContext.getTimelineCurrentPosition(mTimeline); // 当前时长
                        long cur_ms_system = TimeFormatUtil.getCurrentTimeMS();
                        long time_each_seek = (cur_ms_system - mMoveSystemBegin) * 1000;
                        if (time_each_seek > 40000)
                            return false;
                        long seek_time = mSeekBegin + time_each_seek;

                        PointF nowPointF = mLiveWindow.mapViewToCanonical(new PointF(mCurrentX, mCurrentY));

                        if (mCurrentParticleFxId != null && !mCurrentParticleFxId.isEmpty() && mCurrentVideoFx != null && mCurrentEmitterList != null && mCurrentEmitterList.size() > 0) {
                            PointF particlePointF = mCurrentVideoFx.mapPointFromCanonicalToParticleSystem(nowPointF);
                            NvsParticleSystemContext particleContext = mCurrentVideoFx.getParticleSystemContext();
                            for (int i = 0; i < mCurrentEmitterList.size(); ++i) {
                                String emitterName = mCurrentEmitterList.get(i);
                                particleContext.appendPositionToEmitterPositionCurve(emitterName, (float) (cur_us_timeline + time_each_seek - mMoveBegin) / 1000000, particlePointF.x, particlePointF.y);
                            }
                        }
                        seekTimeline(seek_time, 0);
                        break;
                    case MotionEvent.ACTION_UP:
                        Log.i(TAG, "up: x = " + mCurrentX + " y: " + mCurrentY);

                        // 结束时间线特效
                        if (mCurrentParticleFxId != null && !mCurrentParticleFxId.isEmpty() && mCurrentVideoFx != null) {
                            mCurrentVideoFx.changeOutPoint(mStreamingContext.getTimelineCurrentPosition(mTimeline));
                        }

                        // 停止刷新粒子特效
                        stopParticleTimer();
                        endDrawSequenceProgress();

                        break;
                }
                return true;
            }
        });

        mLiveWindowLayout.setOnClickListener(this);
        if(mSequenceView != null)
            mSequenceView.setOnClickListener(this);
        mRevertLayout.setOnClickListener(this);
        mSequenceLayout.setOnClickListener(this);
    }

    private void loadVideoClipFailTips(){
        //导入视频无效，提示
        if(mTimeline == null || (mTimeline != null && mTimeline.getDuration() <= 0 )){
            String[] versionName = getResources().getStringArray(R.array.clip_load_failed_tips);
            Util.showDialog(ParticleEditActivity.this, versionName[0], versionName[1], new TipsButtonClickListener() {
                @Override
                public void onTipsButtoClick(View view) {
                    removeTimeline();
                    AppManager.getInstance().finishActivity();
                }
            });
        }
    }
    private void removeTimeline() {
        if(mStreamingContext != null && mTimeline != null){
            TimelineUtil.removeTimeline(mTimeline);
            mTimeline = null;
        }
    }

    private void finishActivity() {
        if (mFxTimer != null) {
            mFxTimer.cancel();
            mFxTimer = null;
        }
        removeTimeline();
        AppManager.getInstance().finishActivity();
    }

    private void initRecyclerView() {
        initSequenceView();
        mSequenceLayout.updateIndicator(0);
        // 初始化列表
        if (mParticleFxList != null) {
            initParticleRecyclerView();
        }
    }

    private void getVideoPixelAspectRatio(ArrayList<String> pathList) {
        if (pathList.size() != 0) {
            NvsAVFileInfo mFileInfo = mStreamingContext.getAVFileInfo(pathList.get(0));
            if(mFileInfo != null){
                mVideoDimension = mFileInfo.getVideoStreamDimension(0);
                mVideoWidth = mVideoDimension.width;
                mVideoHeight = mVideoDimension.height;
            }
        }
        for (int i = 0; i < pathList.size(); i++) {
            NvsAVFileInfo fileInfo = mStreamingContext.getAVFileInfo(pathList.get(i));
            if(fileInfo != null) {
                resetVideoRatioFromRotation(fileInfo);
            }
        }
    }

    private void initStreamingContext() {
        mStreamingContext = NvsStreamingContext.getInstance();

        mStreamingContext.setPlaybackCallback(new NvsStreamingContext.PlaybackCallback() {
            @Override
            public void onPlaybackPreloadingCompletion(NvsTimeline nvsTimeline) {

            }

            @Override
            public void onPlaybackStopped(NvsTimeline nvsTimeline) {

            }

            @Override
            public void onPlaybackEOF(NvsTimeline nvsTimeline) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                Message message = new Message();
                                message.what = MSG_TYPE_UPDATE_PLAY_SEEKBAR;
                                mHandler.sendMessage(message);
                            }
                        });
                    }
                }).start();
            }
        });

        mStreamingContext.setStreamingEngineCallback(new NvsStreamingContext.StreamingEngineCallback() {
            @Override
            public void onStreamingEngineStateChanged(int state) {
                if (state == mStreamingContext.STREAMING_ENGINE_STATE_STOPPED || state == mStreamingContext.STREAMING_ENGINE_STATE_SEEKING) {
                    mPlayBtn.setBackgroundResource(R.drawable.particle_video_play);
                } else if (state == mStreamingContext.STREAMING_ENGINE_STATE_PLAYBACK) {
                    mPlayBtn.setBackgroundResource(R.drawable.particle_video_pause);
                }
            }

            @Override
            public void onFirstVideoFramePresented(NvsTimeline nvsTimeline) {

            }
        });

        mStreamingContext.setPlaybackCallback2(new NvsStreamingContext.PlaybackCallback2() {
            @Override
            public void onPlaybackTimelinePosition(NvsTimeline nvsTimeline, long l) {
                mSequenceLayout.updateIndicator(l);
            }
        });
    }

    public void resetVideoRatioFromRotation(NvsAVFileInfo fileInfo) {
        int rotation = 0;
        if (fileInfo != null) {
            int videoStreamCount = fileInfo.getVideoStreamCount();
            if (videoStreamCount > 0) {
                rotation = fileInfo.getVideoStreamRotation(0);
                if (rotation == NvsVideoStreamInfo.VIDEO_ROTATION_90) {
                    int temp = mVideoWidth;
                    mVideoWidth = mVideoHeight;
                    mVideoHeight = temp;
                } else if (rotation == NvsVideoStreamInfo.VIDEO_ROTATION_180) {

                } else if (rotation == NvsVideoStreamInfo.VIDEO_ROTATION_270) {
                    int temp = mVideoWidth;
                    mVideoWidth = mVideoHeight;
                    mVideoHeight = temp;
                }
            }
        }
    }

    // 初始化粒子特效的RecyclerView
    private void initParticleRecyclerView() {
        LinearLayoutManager layoutManager = new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false);
        mEffectRecyclerView.setLayoutManager(layoutManager);
        ParticleFilterAdapter adapter = new ParticleFilterAdapter(mContext);
        mEffectRecyclerView.setAdapter(adapter);

        if (mParticleFxList != null) {
            FilterItem filterItem = new FilterItem();
            filterItem.setFilterName("无");
            filterItem.setImageId(R.mipmap.no);
            mParticleFxList.add(0, filterItem);
            adapter.setFilterDataList(mParticleFxList);
        }

        adapter.setOnItemClickListener(new ParticleFilterAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                String invalideFileTips = getResources().getString(R.string.invalide_file);
                if (mParticleFxList == null || mParticleFxList.size() == 0 || position < 0 || position >= mParticleFxList.size()) {
                    Toast.makeText(mContext, invalideFileTips, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (position == 0) {
                    mCurrentParticleFxId = null;
                    return;
                }
                mCurrentParticlePos = position;
                FilterItem filterItem = mParticleFxList.get(position);
                if (filterItem == null) {
                    Toast.makeText(mContext, invalideFileTips, Toast.LENGTH_SHORT).show();
                    return;
                }
                NvsAssetPackageParticleDescParser desc = new NvsAssetPackageParticleDescParser(filterItem.getAssetDescription());
                if (desc.GetParticleType() < 0) {
                    mCurrentParticleFxId = null;
                    return;
                }
                mCurrentParticleFxId = filterItem.getPackageId();
                resetSeekBarValue();
                int countClip = mVideoTrack.getClipCount();
                if (countClip <= 0)
                    return;

                int state = mStreamingContext.getStreamingEngineState();
                if (NvsStreamingContext.STREAMING_ENGINE_STATE_PLAYBACK == state) {
                    mPlayBtn.setBackgroundResource(R.drawable.particle_video_play);
                    mStreamingContext.stop();
                }
            }

            @Override
            public void onSameItemClick() {
                if (mCurrentParticleFxId != null && !mCurrentParticleFxId.isEmpty()) {
                    mParticleRangeLayout.setVisibility(View.VISIBLE);
                    mEffectRecyclerView.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    private void resetSeekBarValue() {
        mParticleRangeSeekBar.setProgress(10);
        mParticleSizeRangeSeekBar.setProgress(10);
        mParticleSizeRangeText.setText("1.0X");
        mParticleRangeText.setText("1.0X");
        mParticleSizeValue = 1.0f;
        mParticleRateValue = 1.0f;
    }

    private void initCompileVideoFragment() {
        mCompileVideoFragment = new CompileVideoFragment();
        mCompileVideoFragment.setTimeline(mTimeline);
        getFragmentManager().beginTransaction()
                .add(R.id.compilePage, mCompileVideoFragment)
                .commit();
        getFragmentManager().beginTransaction().show(mCompileVideoFragment);
    }

    // 初始化SequenceView
    private void initSequenceView() {
        if (mTimeline == null || mVideoTrack == null)
            return;
        mSequenceView = mSequenceLayout.getSqView();
        ArrayList<NvsMultiThumbnailSequenceView.ThumbnailSequenceDesc> infoDescArray = new ArrayList<>();
        for (int i = 0; i < mVideoTrack.getClipCount(); i++) {
            NvsVideoClip clip = mVideoTrack.getClipByIndex(i);
            if (clip == null)
                continue;

            NvsMultiThumbnailSequenceView.ThumbnailSequenceDesc infoDesc = new NvsMultiThumbnailSequenceView.ThumbnailSequenceDesc();
            infoDesc.mediaFilePath = clip.getFilePath();
            infoDesc.trimIn = clip.getTrimIn();
            infoDesc.trimOut = clip.getTrimOut();
            infoDesc.inPoint = clip.getInPoint();
            infoDesc.outPoint = clip.getOutPoint();
            infoDesc.stillImageHint = false;
            infoDescArray.add(infoDesc);
        }
        int screenWidth = ScreenUtils.getScreenWidth(this) - ScreenUtils.dip2px(this, 20);
        double duration = (double) mTimeline.getDuration();
        double pixelPerMicrosecond = screenWidth / duration;
        mSequenceView.setPixelPerMicrosecond(pixelPerMicrosecond);
        mSequenceView.setPixelPerMicrosecond2(pixelPerMicrosecond);
        mSequenceView.setThumbnailSequenceDescArray(infoDescArray);
        mSequenceLayout.initData((long) duration, infoDescArray);
    }

    // 初始化TimeLine（全部的视频内容）
    private void initTimeline() {
        if (null == mStreamingContext) {
            Log.e(TAG, "mStreamingContext is null!");
            return;
        }
        while (mVideoHeight * mVideoWidth >= 1920 * 1080) {
            float videoScale = (float) mVideoHeight / mVideoWidth;
            if (mVideoWidth >= mVideoHeight) {
                mVideoWidth = (int) (mVideoWidth * videoScale);
                mVideoHeight = (int) (mVideoHeight * videoScale);
            } else {
                mVideoWidth = (int) (mVideoWidth / videoScale);
                mVideoHeight = (int) (mVideoHeight / videoScale);
            }
        }
        NvsVideoResolution videoEditRes = new NvsVideoResolution();
        mTimeLineWidth = mVideoWidth - mVideoWidth % 4;
        mTimeLineHeight = mVideoHeight - mVideoHeight % 2;
        videoEditRes.imageWidth = mTimeLineWidth;
        videoEditRes.imageHeight = mTimeLineHeight;
        videoEditRes.imagePAR = new NvsRational(1, 1);
        NvsRational videoFps = new NvsRational(FRAME_RATION, 1);

        NvsAudioResolution audioEditRes = new NvsAudioResolution();
        audioEditRes.sampleRate = 44100;
        audioEditRes.channelCount = 2;

        mTimeline = mStreamingContext.createTimeline(videoEditRes, videoFps, audioEditRes);
        if (null == mTimeline) {
            Log.e(TAG, "mTimeline is null!");
            return;
        }
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) mLiveWindow.getLayoutParams();
        DisplayMetrics metric = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metric);
        int sceneWidth = metric.widthPixels;
        int sceneHeight = metric.heightPixels;
        float scale = (float) mTimeLineWidth / mTimeLineHeight;

        if (mVideoWidth <= mVideoHeight) {
            params.height = sceneHeight - ScreenUtils.dip2px(this, 213);
            params.width = (int) (params.height * scale);
        } else {
            params.width = sceneWidth;
            params.height = (int) (sceneWidth / scale);
        }
        mLiveWindow.setLayoutParams(params);
        mStreamingContext.connectTimelineWithLiveWindow(mTimeline, mLiveWindow);

        mVideoTrack = mTimeline.appendVideoTrack();
        if (null == mVideoTrack) {
            Log.e(TAG, "mVideoTrack is null!");
            return;
        }
        mVideoTrack.removeAllClips();
        for (int i = 0; i < mPath.size(); i++) {
            NvsVideoClip clip = mVideoTrack.appendClip(mPath.get(i));  //添加视频片段

            if (clip == null) {
                Toast.makeText(mContext, "Failed to Append Clip" + mPath.get(i), Toast.LENGTH_LONG).show();
                return;
            }
            mClipList.add(clip);
        }
        if (mTimeline == null) {
            Log.e(TAG, "mTimeline is null");
            return;
        }
        seekTimeline(0, 0);
    }

    // 对播放进度的seek
    private void seekTimeline(long timestamp, int seekShowMode) {
        mStreamingContext.seekTimeline(mTimeline, timestamp, NvsStreamingContext.VIDEO_PREVIEW_SIZEMODE_LIVEWINDOW_SIZE, seekShowMode);
    }

    // 手指在播放器上滑动时，开启画面seek的timer
    private void startParticleTimer() {
        if (mFxTimer == null) {
            mFxTimer = new Timer();
        }
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage(MSG_TYPE_PARTICLE_FX);
            }
        };
        mFxTimer.schedule(task, 0, 40);
    }

    // 手指在播放器滑动完并抬起时，停止画面seek的timer
    private void stopParticleTimer() {
        if (mFxTimer != null) {
            mFxTimer.cancel();
            mFxTimer = null;
        }
    }

    // 更新seekBar上滑块的位置
    private void updateSeekBarPosition(long time) {
        mSequenceLayout.updateFxView(mMoveBegin, time);
        mSequenceLayout.updateIndicator(time);
    }

    // 开始在SequenceView上绘制蒙版
    private void startDrawSequenceProgress() {
        if (mCurrentParticleFxId == null || mCurrentParticleFxId.isEmpty() || mCurrentVideoFx == null) {
            return;
        }
        long inPoint = mStreamingContext.getTimelineCurrentPosition(mTimeline);
        mCurFilterFxInfo = new FilterFxInfo();
        mCurFilterFxInfo.setInPoint(inPoint);
        mCurFilterFxInfo.setName(mCurrentVideoFx.getTimelineVideoFxPackageId());
        mCurFilterFxInfo.setAddResult(true);
        mSequenceLayout.addFxView(mMoveBegin, 0, mCurrentParticlePos);
    }

    // 停止在SequenceView上绘制蒙版
    private void endDrawSequenceProgress() {
        // 延迟一下，是因为可能定时器seek画面还没完全结束，导致蒙层有间隙
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                long outPoint = mStreamingContext.getTimelineCurrentPosition(mTimeline);
                if (mTimeline.getDuration() - outPoint < 0.05 * TIME_BASE) {
                    outPoint = mTimeline.getDuration();
                }
                mSequenceLayout.updateFxView(mMoveBegin, outPoint + 10);
            }
        }, 10);
    }

    private void showParticleParamPage() {
        mParticleRangeLayout.setVisibility(View.INVISIBLE);
        mEffectRecyclerView.setVisibility(View.VISIBLE);
    }
}
