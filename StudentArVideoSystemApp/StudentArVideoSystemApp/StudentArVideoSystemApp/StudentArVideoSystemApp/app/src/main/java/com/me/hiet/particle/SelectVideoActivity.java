package com.me.hiet.particle;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.meishe.sdkdemo.R;
import com.me.hiet.hite.BaseActivity;
import com.me.hiet.edit.data.FilterItem;
import com.me.hiet.edit.view.CustomTitleBar;
import com.me.hiet.makecover.MakeCoverActivity;
import com.me.hiet.selectmedia.bean.MediaData;
import com.me.hiet.selectmedia.fragment.MediaFragment;
import com.me.hiet.selectmedia.interfaces.OnTotalNumChangeForActivity;
import com.me.hiet.selectmedia.view.CustomPopWindow;
import com.me.hiet.utils.AppManager;
import com.me.hiet.utils.Constants;
import com.me.hiet.utils.MediaConstant;
import com.me.hiet.utils.ToastUtil;
import com.me.hiet.utils.Util;
import com.me.hiet.utils.dataInfo.ClipInfo;
import com.me.hiet.utils.dataInfo.TimelineData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.me.hiet.utils.Constants.POINT16V9;
import static com.me.hiet.utils.Constants.POINT1V1;
import static com.me.hiet.utils.Constants.POINT3V4;
import static com.me.hiet.utils.Constants.POINT4V3;
import static com.me.hiet.utils.Constants.POINT9V16;
import static com.me.hiet.utils.MediaConstant.KEY_CLICK_TYPE;

public class SelectVideoActivity extends BaseActivity implements OnTotalNumChangeForActivity , CustomPopWindow.OnViewClickListener {
    private final String TAG = "SelectVideoActivity";
    private CustomTitleBar m_titleBar;
    private TextView sigleTvStartEdit;
    private List<MediaData> mediaDataList;
    private Map<String, FilterItem> particleMap;
    private int fromWhat = Constants.SELECT_IMAGE_FROM_WATER_MARK;

    @Override
    protected int initRootView() {
        return R.layout.activity_select_video;
    }

    @Override
    protected void initViews() {
        m_titleBar = (CustomTitleBar) findViewById(R.id.title_bar);
        sigleTvStartEdit = (TextView) findViewById(R.id.sigle_tv_startEdit);
        sigleTvStartEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(fromWhat == Constants.SELECT_VIDEO_FROM_PARTICLE) {
                    if(mediaDataList == null || mediaDataList.isEmpty()) {
                        String unselectVideoTips = getResources().getString(R.string.unselect_video);
                        ToastUtil.showToast(SelectVideoActivity.this, unselectVideoTips);
                        return;
                    }
                    ArrayList<String> video_paths = new ArrayList<>();
                    for(int i = 0; i < mediaDataList.size(); ++i) {
                        if(mediaDataList.get(i) != null && mediaDataList.get(i).getPath() != null) {
                            video_paths.add(mediaDataList.get(i).getPath());
                        }
                    }
                    Bundle bundle = new Bundle();
                    bundle.putStringArrayList("video_paths", video_paths);
                    bundle.putSerializable("fx_touch", (Serializable) particleMap);
                    AppManager.getInstance().jumpActivity(SelectVideoActivity.this, ParticleEditActivity.class, bundle);
                }
            }
        });
    }

    @Override
    protected void initTitle() {
        m_titleBar.setTextCenter(R.string.select_video);
    }

    @Override
    protected void initData() {
        Intent intent = getIntent();
        if(intent != null) {
            Bundle bundle = intent.getExtras();
            if(bundle != null) {
                fromWhat = bundle.getInt(Constants.SELECT_MEDIA_FROM, Constants.SELECT_VIDEO_FROM_PARTICLE);
                if(fromWhat == Constants.SELECT_VIDEO_FROM_PARTICLE) {
                    particleMap = (Map<String, FilterItem>) bundle.getSerializable("fx_touch");
                    m_titleBar.setTextCenter(R.string.select_video);
                }
            }
        }
        initVideoFragment(R.id.single_contain);
    }

    @Override
    public void onViewClick(CustomPopWindow popWindow, View view) {
        switch (view.getId()) {
            case R.id.button16v9:
                selectCreateRatio(POINT16V9);
                break;
            case R.id.button1v1:
                selectCreateRatio(POINT1V1);
                break;
            case R.id.button9v16:
                selectCreateRatio(POINT9V16);
                break;
            case R.id.button3v4:
                selectCreateRatio(POINT3V4);
                break;
            case R.id.button4v3:
                selectCreateRatio(POINT4V3);
            default:
                break;
        }
    }

    private void selectCreateRatio(int makeRatio) {
        ArrayList<ClipInfo> pathList = getClipInfoList();
        TimelineData.instance().setVideoResolution(Util.getVideoEditResolution(makeRatio));
        TimelineData.instance().setClipInfoData(pathList);
        TimelineData.instance().setMakeRatio(makeRatio);
        AppManager.getInstance().jumpActivity(SelectVideoActivity.this, MakeCoverActivity.class, null);
    }

    private ArrayList<ClipInfo> getClipInfoList() {
        ArrayList<ClipInfo> pathList = new ArrayList<>();
        if(mediaDataList != null) {
            mediaDataList.clear();
            for (MediaData mediaData : mediaDataList) {
                ClipInfo clipInfo = new ClipInfo();
                clipInfo.setFilePath(mediaData.getPath());
                pathList.add(clipInfo);
            }
        }
        return pathList;
    }

    private void initVideoFragment(int layoutId) {
        MediaFragment mediaFragment = new MediaFragment();
        Bundle bundle = new Bundle();
        bundle.putInt(MediaConstant.MEDIA_TYPE, MediaConstant.VIDEO);
        bundle.putInt(KEY_CLICK_TYPE, MediaConstant.TYPE_ITEMCLICK_MULTIPLE);
        mediaFragment.setArguments(bundle);
        getSupportFragmentManager().beginTransaction()
                .add(layoutId, mediaFragment)
                .commit();
        getSupportFragmentManager().beginTransaction().show(mediaFragment);
    }

    @Override
    protected void initListener() {

    }

    @Override
    public void onClick(View v) {

    }

    @Override
    public void onTotalNumChangeForActivity(List selectList, Object tag) {
        mediaDataList = selectList;
        sigleTvStartEdit.setVisibility(selectList.size() > 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
