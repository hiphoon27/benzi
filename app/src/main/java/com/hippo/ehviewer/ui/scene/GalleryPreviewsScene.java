/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.scene;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.hippo.easyrecyclerview.EasyRecyclerView;
import com.hippo.easyrecyclerview.MarginItemDecoration;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.ehviewer.client.EhClient;
import com.hippo.ehviewer.client.EhRequest;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.GalleryPreview;
import com.hippo.ehviewer.client.data.LargePreviewSet;
import com.hippo.ehviewer.client.exception.EhException;
import com.hippo.ehviewer.ui.GalleryActivity;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.scene.SceneFragment;
import com.hippo.widget.ContentLayout;
import com.hippo.widget.LoadImageView;
import com.hippo.widget.Slider;
import com.hippo.widget.recyclerview.AutoGridLayoutManager;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.LayoutUtils;
import com.hippo.yorozuya.SimpleHandler;
import com.hippo.yorozuya.ViewUtils;

import java.util.ArrayList;
import java.util.Locale;

public class GalleryPreviewsScene extends ToolbarScene implements EasyRecyclerView.OnItemClickListener {

    public static final String KEY_GALLERY_INFO = "gallery_info";
    private final static String KEY_HAS_FIRST_REFRESH = "has_first_refresh";

    /*---------------
     Whole life cycle
     ---------------*/
    @Nullable
    private EhClient mClient;
    @Nullable
    private GalleryInfo mGalleryInfo;

    /*---------------
     View life cycle
     ---------------*/
    @Nullable
    private GalleryPreviewAdapter mAdapter;
    @Nullable
    private GalleryPreviewHelper mHelper;

    private boolean mHasFirstRefresh = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext2();
        AssertUtils.assertNotNull(context);
        mClient = EhApplication.getEhClient(context);
        if (savedInstanceState == null) {
            onInit();
        } else {
            onRestore(savedInstanceState);
        }
    }

    private void onInit() {
        Bundle args = getArguments();
        if (args == null) {
            return;
        }

        mGalleryInfo = args.getParcelable(KEY_GALLERY_INFO);
    }

    private void onRestore(@NonNull Bundle savedInstanceState) {
        mGalleryInfo = savedInstanceState.getParcelable(KEY_GALLERY_INFO);
        mHasFirstRefresh = savedInstanceState.getBoolean(KEY_HAS_FIRST_REFRESH);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        boolean hasFirstRefresh;
        if (mHelper != null && 1 == mHelper.getShownViewIndex()) {
            hasFirstRefresh = false;
        } else {
            hasFirstRefresh = mHasFirstRefresh;
        }
        outState.putBoolean(KEY_HAS_FIRST_REFRESH, hasFirstRefresh);
        outState.putParcelable(KEY_GALLERY_INFO, mGalleryInfo);
    }

    @Nullable
    @Override
    public View onCreateView2(LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ContentLayout contentLayout = (ContentLayout) inflater.inflate(
                R.layout.scene_gallery_previews, container, false);
        contentLayout.hideFastScroll();
        EasyRecyclerView recyclerView = contentLayout.getRecyclerView();

        Context context = getContext2();
        AssertUtils.assertNotNull(context);
        Resources resources = context.getResources();

        mAdapter = new GalleryPreviewAdapter();
        recyclerView.setAdapter(mAdapter);
        int columnWidth = resources.getDimensionPixelOffset(R.dimen.preview_grid_column_width);
        AutoGridLayoutManager layoutManager = new AutoGridLayoutManager(context, columnWidth);
        layoutManager.setStrategy(AutoGridLayoutManager.STRATEGY_SUITABLE_SIZE);
        recyclerView.setLayoutManager(layoutManager);
        int padding = LayoutUtils.dp2pix(context, 4);
        recyclerView.setPadding(padding, padding, padding, padding);
        recyclerView.setClipToPadding(false);
        recyclerView.addItemDecoration(new MarginItemDecoration(padding));
        recyclerView.setOnItemClickListener(this);

        mHelper = new GalleryPreviewHelper();
        contentLayout.setHelper(mHelper);

        // Only refresh for the first time
        if (!mHasFirstRefresh) {
            mHasFirstRefresh = true;
            mHelper.firstRefresh();
        }

        return contentLayout;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (null != mHelper) {
            if (1 == mHelper.getShownViewIndex()) {
                mHasFirstRefresh = false;
            }
            mHelper = null;
        }

        mAdapter = null;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setTitle(R.string.gallery_previews);
        setNavigationIcon(R.drawable.v_arrow_left_dark_x24);
    }

    @Override
    public int getMenuResId() {
        return R.menu.scene_gallery_previews;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        Context context = getContext2();
        if (null == context) {
            return false;
        }

        int id = item.getItemId();
        switch (id) {
            case R.id.action_go_to:
                if (mHelper == null) {
                    return true;
                }
                int pages = mHelper.getPages();
                if (pages > 0 && mHelper.canGoTo()) {
                    GoToDialogHelper helper = new GoToDialogHelper(pages, mHelper.getPageForTop());
                    AlertDialog dialog = new AlertDialog.Builder(context).setTitle(R.string.go_to)
                            .setView(R.layout.dialog_go_to)
                            .setPositiveButton(android.R.string.ok, null)
                            .create();
                    dialog.show();
                    helper.setDialog(dialog);
                }
                return true;
        }
        return false;
    }

    @Override
    public void onNavigationClick() {
        onBackPressed();
    }

    @Override
    public boolean onItemClick(EasyRecyclerView parent, View view, int position, long id) {
        Context context = getContext2();
        if (null != context && null != mHelper && null != mGalleryInfo) {
            GalleryPreview p = mHelper.getDataAt(position);
            Intent intent = new Intent(context, GalleryActivity.class);
            intent.setAction(GalleryActivity.ACTION_EH);
            intent.putExtra(GalleryActivity.KEY_GALLERY_INFO, mGalleryInfo);
            intent.putExtra(GalleryActivity.KEY_PAGE, p.index);
            startActivity(intent);
        }
        return true;
    }

    private class GalleryPreviewHolder extends RecyclerView.ViewHolder {

        public LoadImageView image;
        public TextView text;

        public GalleryPreviewHolder(View itemView) {
            super(itemView);

            image = (LoadImageView) itemView.findViewById(R.id.image);
            text = (TextView) itemView.findViewById(R.id.text);
        }
    }

    private class GalleryPreviewAdapter extends RecyclerView.Adapter<GalleryPreviewHolder> {

        private final LayoutInflater mInflater;

        public GalleryPreviewAdapter() {
            mInflater = getLayoutInflater2();
            AssertUtils.assertNotNull(mInflater);
        }

        @Override
        public GalleryPreviewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new GalleryPreviewHolder(mInflater.inflate(R.layout.item_gallery_preview, parent, false));
        }

        @Override
        public void onBindViewHolder(GalleryPreviewHolder holder, int position) {
            if (null != mHelper && null != mGalleryInfo) {
                GalleryPreview preview = mHelper.getDataAt(position);
                holder.image.load(EhCacheKeyFactory.getLargePreviewKey(mGalleryInfo.gid, preview.index), preview.imageUrl);
                holder.text.setText(String.format(Locale.US, "%d", preview.index + 1));
            }
        }

        @Override
        public int getItemCount() {
            return mHelper != null ? mHelper.size() : 0;
        }
    }

    private class GalleryPreviewHelper extends ContentLayout.ContentHelper<GalleryPreview> {

        @Override
        protected void getPageData(final int taskId, int type, int page) {
            MainActivity activity = getActivity2();
            if (null == activity || null == mClient || null == mGalleryInfo) {
                onGetException(taskId, new EhException(getString(R.string.error_cannot_find_gallery)));
                return;
            }

            final LargePreviewSet previewSet = EhApplication.getLargePreviewSetCache(
                    getContext()).get(EhCacheKeyFactory.getLargePreviewSetKey(mGalleryInfo.gid, page));
            final Integer pages = EhApplication.getPreviewPagesCache(getContext()).get(mGalleryInfo.gid);
            if (previewSet != null && pages != null) {
                SimpleHandler.getInstance().post(new Runnable() {
                    @Override
                    public void run() {
                        onGetLargePreviewSetSuccess(Pair.create(previewSet, pages), taskId);
                    }
                });
                return;
            }

            String url = EhUrl.getGalleryDetailUrl(mGalleryInfo.gid, mGalleryInfo.token, page, false);
            EhRequest request = new EhRequest();
            request.setMethod(EhClient.METHOD_GET_LARGE_PREVIEW_SET);
            request.setCallback(new GetLargePreviewSetListener(getContext(),
                    activity.getStageId(), getTag(), taskId, mGalleryInfo.gid, page));
            request.setArgs(url);
            mClient.execute(request);
        }

        @Override
        protected Context getContext() {
            return GalleryPreviewsScene.this.getContext2();
        }

        @Override
        protected void notifyDataSetChanged() {
            if (mAdapter != null) {
                mAdapter.notifyDataSetChanged();
            }
        }

        @Override
        protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
            if (mAdapter != null) {
                mAdapter.notifyItemRangeRemoved(positionStart, itemCount);
            }
        }

        @Override
        protected void notifyItemRangeInserted(int positionStart, int itemCount) {
            if (mAdapter != null) {
                mAdapter.notifyItemRangeInserted(positionStart, itemCount);
            }
        }
    }

    private void onGetLargePreviewSetSuccess(Pair<LargePreviewSet, Integer> result, int taskId) {
        if (mHelper != null && mHelper.isCurrentTask(taskId) && isViewCreated()) {
            LargePreviewSet previewSet = result.first;
            int size = previewSet.size();
            ArrayList<GalleryPreview> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                GalleryPreview preview = new GalleryPreview();
                preview.imageUrl = previewSet.getImageUrlAt(i);
                preview.pageUrl = previewSet.getPageUrlAt(i);
                preview.index = previewSet.getIndexAt(i);
                list.add(preview);
            }

            mHelper.setPages(taskId, result.second);
            mHelper.onGetPageData(taskId, list);
        }
    }

    private void onGetLargePreviewSetFailure(Exception e, int taskId) {
        if (mHelper != null && mHelper.isCurrentTask(taskId) && isViewCreated()) {
            mHelper.onGetException(taskId, e);
        }
    }

    private static class GetLargePreviewSetListener extends EhCallback<GalleryPreviewsScene, Pair<LargePreviewSet, Integer>> {

        private final int mTaskId;
        private final long mGid;
        private final int mPage;

        public GetLargePreviewSetListener(Context context, int stageId, String sceneTag, int taskId, long gid, int page) {
            super(context, stageId, sceneTag);
            mTaskId = taskId;
            mGid = gid;
            mPage = page;
        }

        @Override
        public void onSuccess(Pair<LargePreviewSet, Integer> result) {
            EhApplication.getLargePreviewSetCache(getApplication()).put(
                    EhCacheKeyFactory.getLargePreviewSetKey(mGid, mPage), result.first);
            EhApplication.getPreviewPagesCache(getApplication()).put(mGid, result.second);

            GalleryPreviewsScene scene = getScene();
            if (scene != null) {
                scene.onGetLargePreviewSetSuccess(result, mTaskId);
            }
        }

        @Override
        public void onFailure(Exception e) {
            GalleryPreviewsScene scene = getScene();
            if (scene != null) {
                scene.onGetLargePreviewSetFailure(e, mTaskId);
            }
        }

        @Override
        public void onCancel() {

        }

        @Override
        public boolean isInstance(SceneFragment scene) {
            return scene instanceof GalleryPreviewsScene;
        }
    }

    private class GoToDialogHelper implements View.OnClickListener,
            DialogInterface.OnDismissListener {

        private final int mPages;
        private final int mCurrentPage;

        @Nullable
        private Slider mSlider;
        @Nullable
        private Dialog mDialog;

        private GoToDialogHelper(int pages, int currentPage) {
            mPages = pages;
            mCurrentPage = currentPage;
        }

        public void setDialog(@NonNull AlertDialog dialog) {
            mDialog = dialog;

            ((TextView) ViewUtils.$$(dialog, R.id.start)).setText(String.format(Locale.US, "%d", 1));
            ((TextView) ViewUtils.$$(dialog, R.id.end)).setText(String.format(Locale.US, "%d", mPages));
            mSlider = (Slider) ViewUtils.$$(dialog, R.id.slider);
            mSlider.setRange(1, mPages);
            mSlider.setProgress(mCurrentPage + 1);

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(this);
            dialog.setOnDismissListener(this);
        }

        @Override
        public void onClick(View v) {
            if (null == mSlider) {
                return;
            }

            int page = mSlider.getProgress() - 1;
            if (page >= 0 && page < mPages && mHelper != null) {
                mHelper.goTo(page);
                if (mDialog != null) {
                    mDialog.dismiss();
                    mDialog = null;
                }
            } else {
                showTip(R.string.error_out_of_range, LENGTH_SHORT);
            }
        }

        @Override
        public void onDismiss(DialogInterface dialog) {
            mDialog = null;
            mSlider = null;
        }
    }
}
