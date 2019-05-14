package com.netease.nim.chatroom.demo.education.fragment.tab;

import com.netease.nim.chatroom.demo.R;
import com.netease.nim.chatroom.demo.education.fragment.OnlinePeopleFragment;

/**
 * Created by hzxuwen on 2016/2/29.
 */
public class OnlinePeopleTabFragment extends ChatRoomTabFragment {
    private OnlinePeopleFragment fragment;

    @Override
    protected void onInit() {
        findViews();
    }

    @Override
    public void onCurrent() {
        super.onCurrent();
        if (fragment != null) {
            fragment.onCurrent();
        }
    }

    private void findViews() {
        fragment = (OnlinePeopleFragment) getActivity().getSupportFragmentManager().findFragmentById(R.id.online_people_fragment);
    }
}
