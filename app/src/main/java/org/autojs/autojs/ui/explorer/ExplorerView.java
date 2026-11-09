package org.autojs.autojs.ui.explorer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import androidx.annotation.Nullable;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.stardust.pio.PFiles;

import org.autojs.autoxjs.R;
import org.autojs.autojs.model.explorer.Explorer;
import org.autojs.autojs.model.explorer.ExplorerChangeEvent;
import org.autojs.autojs.model.explorer.ExplorerDirPage;
import org.autojs.autojs.model.explorer.ExplorerFileItem;
import org.autojs.autojs.model.explorer.ExplorerItem;
import org.autojs.autojs.model.explorer.ExplorerPage;
import org.autojs.autojs.model.explorer.ExplorerProjectPage;
import org.autojs.autojs.model.explorer.ExplorerSampleItem;
import org.autojs.autojs.model.explorer.ExplorerSamplePage;
import org.autojs.autojs.model.explorer.Explorers;
import org.autojs.autojs.model.script.ScriptFile;
import org.autojs.autojs.model.script.Scripts;
import org.autojs.autojs.tool.Observers;
import org.autojs.autojs.ui.build.BuildActivity;
import org.autojs.autojs.ui.common.ScriptLoopDialog;
import org.autojs.autojs.ui.common.ScriptOperations;
import org.autojs.autojs.ui.viewmodel.ExplorerItemList;
import org.autojs.autojs.ui.widget.BindableViewHolder;
import org.autojs.autojs.theme.widget.ThemeColorSwipeRefreshLayout;

import org.autojs.autojs.workground.WrapContentGridLayoutManger;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnLongClick;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

/**
 * Created by Stardust on 2017/8/21.
 */
@Deprecated
public class ExplorerView extends ThemeColorSwipeRefreshLayout implements SwipeRefreshLayout.OnRefreshListener, PopupMenu.OnMenuItemClickListener {

    private static final String LOG_TAG = "ExplorerView";

    public interface OnItemClickListener {
        void onItemClick(View view, ExplorerItem item);
    }

    public interface OnItemOperatedListener {
        void OnItemOperated(ExplorerItem item);
    }

    protected static final int VIEW_TYPE_ITEM = 0;
    protected static final int VIEW_TYPE_PAGE = 1;
    //category是类别，也即"文件", "文件夹"那两个
    protected static final int VIEW_TYPE_CATEGORY = 2;

    private static final int positionOfCategoryDir = 0;

    private ExplorerItemList mExplorerItemList = new ExplorerItemList();
    private RecyclerView mExplorerItemListView;
    private ExplorerProjectToolbar mProjectToolbar;
    private ExplorerAdapter mExplorerAdapter = new ExplorerAdapter();
    protected OnItemClickListener mOnItemClickListener;
    private Function<ExplorerItem, Boolean> mFilter;
    private OnItemOperatedListener mOnItemOperatedListener;
    protected ExplorerItem mSelectedItem;
    // build27 UI 改版：右滑后保持展开的操作按钮（按文件路径记录，避免位置变化后错乱）
    private final Set<String> mRevealedItemPaths = new HashSet<>();
    // 右滑展开的宽度（dp）：删除 48 + 间距 8 + 更多 48 + 左边距 4 ≈ 108，取 112 留一点余量
    private static final int REVEAL_WIDTH_DP = 112;
    private Explorer mExplorer;
    private Stack<ExplorerPageState> mPageStateHistory = new Stack<>();
    private ExplorerPageState mCurrentPageState = new ExplorerPageState();
    private boolean mDirSortMenuShowing = false;
    private int mDirectorySpanSize = 2;

    public ExplorerView(Context context) {
        super(context);
        init();
    }

    public ExplorerView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ExplorerPage getCurrentPage() {
        return mCurrentPageState.page;
    }

    public void setRootPage(ExplorerPage page) {
        mPageStateHistory.clear();
        setCurrentPageState(new ExplorerPageState(page));
        loadItemList();
    }

    private void setCurrentPageState(ExplorerPageState currentPageState) {
        mCurrentPageState = currentPageState;
        if (mCurrentPageState.page instanceof ExplorerProjectPage) {
            mProjectToolbar.setVisibility(VISIBLE);
            mProjectToolbar.setProject(currentPageState.page.toScriptFile());
        } else {
            mProjectToolbar.setVisibility(GONE);
        }
    }

    protected void enterDirectChildPage(ExplorerPage childItemGroup) {
        mCurrentPageState.scrollY = ((LinearLayoutManager) mExplorerItemListView.getLayoutManager()).findLastCompletelyVisibleItemPosition();
        mPageStateHistory.push(mCurrentPageState);
        setCurrentPageState(new ExplorerPageState(childItemGroup));
        loadItemList();
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        mOnItemClickListener = onItemClickListener;
    }

    public void setSortConfig(ExplorerItemList.SortConfig sortConfig) {
        mExplorerItemList.setSortConfig(sortConfig);
    }

    public ExplorerItemList.SortConfig getSortConfig() {
        return mExplorerItemList.getSortConfig();
    }

    public void setExplorer(Explorer explorer, ExplorerPage rootPage) {
        if (mExplorer != null)
            mExplorer.unregisterChangeListener(this);
        mExplorer = explorer;
        setRootPage(rootPage);
        mExplorer.registerChangeListener(this);
    }

    public void setExplorer(Explorer explorer, ExplorerPage rootPage, ExplorerPage currentPage) {
        if (mExplorer != null)
            mExplorer.unregisterChangeListener(this);
        mExplorer = explorer;
        mPageStateHistory.clear();
        setCurrentPageState(new ExplorerPageState(rootPage));
        mExplorer.registerChangeListener(this);
        enterChildPage(currentPage);
    }

    public void enterChildPage(ExplorerPage childPage) {
        ScriptFile root = mCurrentPageState.page.toScriptFile();
        ScriptFile dir = childPage.toScriptFile();
        Stack<ScriptFile> dirs = new Stack<>();
        while (!dir.equals(root)) {
            dir = dir.getParentFile();
            if (dir == null) {
                break;
            }
            dirs.push(dir);
        }
        ExplorerDirPage parent = null;
        while (!dirs.empty()) {
            dir = dirs.pop();
            ExplorerDirPage dirPage = new ExplorerDirPage(dir, parent);
            mPageStateHistory.push(new ExplorerPageState(dirPage));
            parent = dirPage;
        }
        setCurrentPageState(new ExplorerPageState(childPage));
        loadItemList();
    }

    public void setOnItemOperatedListener(OnItemOperatedListener onItemOperatedListener) {
        mOnItemOperatedListener = onItemOperatedListener;
    }

    public boolean canGoBack() {
        return !mPageStateHistory.empty();
    }

    public void goBack() {
        setCurrentPageState(mPageStateHistory.pop());
        loadItemList();
    }

    public void setDirectorySpanSize(int directorySpanSize) {
        mDirectorySpanSize = directorySpanSize;
    }

    public void setFilter(Function<ExplorerItem, Boolean> filter) {
        mFilter = filter;
        reload();
    }

    public void reload() {
        loadItemList();
    }

    private void init() {
        Log.d(LOG_TAG, "item bg = " + Integer.toHexString(ContextCompat.getColor(getContext(), R.color.item_background)));
        setOnRefreshListener(this);
        inflate(getContext(), R.layout.explorer_view, this);
        mExplorerItemListView = findViewById(R.id.explorer_item_list);
        mProjectToolbar = findViewById(R.id.project_toolbar);
        initExplorerItemListView();
    }

    private void initExplorerItemListView() {
        mExplorerItemListView.setAdapter(mExplorerAdapter);
        // build27 UI 改版：右滑文件行露出“删除 + ⋮”（仅横向滑动，不与下拉刷新冲突）
        new ItemTouchHelper(new ExplorerSwipeCallback()).attachToRecyclerView(mExplorerItemListView);
        WrapContentGridLayoutManger manager = new WrapContentGridLayoutManger(getContext(), 2);
        manager.setDebugInfo("ExplorerView");
        manager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                //For directories
                if (position > positionOfCategoryDir && position < positionOfCategoryFile()) {
                    return mDirectorySpanSize;
                }
                //For files and category
                return 2;
            }
        });
        mExplorerItemListView.setLayoutManager(manager);
    }

    private int positionOfCategoryFile() {
        if (mCurrentPageState.dirsCollapsed)
            return 1;
        return mExplorerItemList.groupCount() + 1;
    }

    @SuppressLint("CheckResult")
    private void loadItemList() {
        setRefreshing(true);
        mExplorer.fetchChildren(mCurrentPageState.page)
                .subscribeOn(Schedulers.io())
                .flatMapObservable(page -> {
                    mCurrentPageState.page = page;
                    return Observable.fromIterable(page);
                })
                .filter(f -> mFilter == null ? true : mFilter.apply(f))
                .collectInto(mExplorerItemList.cloneConfig(), ExplorerItemList::add)
                .observeOn(Schedulers.computation())
                .doOnSuccess(ExplorerItemList::sort)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(list -> {
                    mExplorerItemList = list;
                    mExplorerAdapter.notifyDataSetChanged();
                    setRefreshing(false);
                    post(() ->
                            mExplorerItemListView.scrollToPosition(mCurrentPageState.scrollY)
                    );
                });
    }

    @Subscribe( threadMode = ThreadMode.MAIN)
    public void onExplorerChange(ExplorerChangeEvent event) {
        Log.d(LOG_TAG, "on explorer change: " + event);
        if ((event.getAction() == ExplorerChangeEvent.ALL)) {
            loadItemList();
            return;
        }
        String currentDirPath = mCurrentPageState.page.getPath();
        String changedDirPath = event.getPage().getPath();
        ExplorerItem item = event.getItem();
        String changedItemPath = item == null ? null : item.getPath();
        if (currentDirPath.equals(changedItemPath) || (currentDirPath.equals(changedDirPath) &&
                event.getAction() == ExplorerChangeEvent.CHILDREN_CHANGE)) {
            loadItemList();
            return;
        }
        if (currentDirPath.equals(changedDirPath)) {
            int i;
            switch (event.getAction()) {
                case ExplorerChangeEvent.CHANGE:
                    i = mExplorerItemList.update(item, event.getNewItem());
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemChanged(item, i);
                    }
                    break;
                case ExplorerChangeEvent.CREATE:
                    mExplorerItemList.insertAtFront(event.getNewItem());
                    mExplorerAdapter.notifyItemInserted(event.getNewItem(), 0);
                    break;
                case ExplorerChangeEvent.REMOVE:
                    i = mExplorerItemList.remove(item);
                    if (i >= 0) {
                        mExplorerAdapter.notifyItemRemoved(item, i);
                    }
                    break;
            }
        }
    }

    @Override
    public void onRefresh() {
        mExplorer.notifyChildrenChanged(mCurrentPageState.page);
        mProjectToolbar.refresh();
    }


    public ScriptFile getCurrentDirectory() {
        return getCurrentPage().toScriptFile();
    }

    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        return onMenuItemSelected(item.getItemId());
    }

    // build27 UI 改版：把菜单处理按 id 拆出来，长按弹出的菜单行直接复用同一套逻辑
    private boolean onMenuItemSelected(int itemId) {
        switch (itemId) {
            case R.id.rename:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .rename((ExplorerFileItem) mSelectedItem)
                        .subscribe(Observers.emptyObserver());
                break;
            case R.id.delete:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .delete(mSelectedItem.toScriptFile());
                break;
            case R.id.run_repeatedly:
                new ScriptLoopDialog(getContext(), mSelectedItem.toScriptFile())
                        .show();
                notifyOperated();
                break;
            case R.id.create_shortcut:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .createShortcut(mSelectedItem.toScriptFile());
                break;
            case R.id.open_by_other_apps:
                Scripts.INSTANCE.openByOtherApps(mSelectedItem.toScriptFile());
                notifyOperated();
                break;
            case R.id.send:
                Scripts.INSTANCE.send(mSelectedItem.toScriptFile());
                notifyOperated();
                break;
            case R.id.timed_task:
                new ScriptOperations(getContext(), this, getCurrentPage())
                        .timedTask(mSelectedItem.toScriptFile());
                notifyOperated();
                break;
            case R.id.action_build_apk:
                BuildActivity.Companion.start(getContext(),mSelectedItem.getPath());
                notifyOperated();
                break;
            case R.id.action_sort_by_date:
                sort(ExplorerItemList.SORT_TYPE_DATE, mDirSortMenuShowing);
                break;
            case R.id.action_sort_by_type:
                sort(ExplorerItemList.SORT_TYPE_TYPE, mDirSortMenuShowing);
                break;
            case R.id.action_sort_by_name:
                sort(ExplorerItemList.SORT_TYPE_NAME, mDirSortMenuShowing);
                break;
            case R.id.action_sort_by_size:
                sort(ExplorerItemList.SORT_TYPE_SIZE, mDirSortMenuShowing);
                break;
            case R.id.reset:
                Explorers.Providers.workspace().resetSample(mSelectedItem.toScriptFile())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(ignored -> {
                            Snackbar.make(this, R.string.text_reset_succeed, Snackbar.LENGTH_SHORT).show();
                        }, Observers.toastMessage());
                break;
            default:
                return false;
        }
        return true;
    }

    private int getRevealWidthPx() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return (int) (REVEAL_WIDTH_DP * dm.density + 0.5f);
    }

    private int findFileAdapterPosition(String path) {
        if (path == null) return -1;
        for (int i = 0; i < mExplorerItemList.itemCount(); i++) {
            ExplorerItem item = mExplorerItemList.getItem(i);
            if (item != null && path.equals(item.getPath())) {
                return i + positionOfCategoryFile() + 1;
            }
        }
        return -1;
    }

    // build27 UI 改版：右滑文件行露出“删除 + ⋮”，滑开后保持展开；已展开的行左滑或点一下收起
    private class ExplorerSwipeCallback extends ItemTouchHelper.SimpleCallback {

        ExplorerSwipeCallback() {
            super(0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT);
        }

        @Override
        public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            if (!(viewHolder instanceof ExplorerItemViewHolder)) {
                return 0;
            }
            ExplorerItem item = ((ExplorerItemViewHolder) viewHolder).mExplorerItem;
            int swipeDirs = ItemTouchHelper.RIGHT;
            if (item != null && mRevealedItemPaths.contains(item.getPath())) {
                swipeDirs |= ItemTouchHelper.LEFT;
            }
            return makeMovementFlags(0, swipeDirs);
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            if (!(viewHolder instanceof ExplorerItemViewHolder)) {
                return;
            }
            ExplorerItem item = ((ExplorerItemViewHolder) viewHolder).mExplorerItem;
            if (item == null) {
                return;
            }
            String path = item.getPath();
            if (direction == ItemTouchHelper.RIGHT) {
                // 一次只展开一行，先收起之前展开的
                for (String p : new HashSet<>(mRevealedItemPaths)) {
                    if (!p.equals(path)) {
                        mRevealedItemPaths.remove(p);
                        int pos = findFileAdapterPosition(p);
                        if (pos >= 0) {
                            mExplorerAdapter.notifyItemChanged(pos);
                        }
                    }
                }
                mRevealedItemPaths.add(path);
            } else {
                mRevealedItemPaths.remove(path);
            }
            int pos = findFileAdapterPosition(path);
            if (pos >= 0) {
                mExplorerAdapter.notifyItemChanged(pos);
            }
        }

        @Override
        public void onChildDraw(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder instanceof ExplorerItemViewHolder) {
                View foreground = viewHolder.itemView.findViewById(R.id.foreground);
                if (foreground != null) {
                    int revealWidth = getRevealWidthPx();
                    ExplorerItem item = ((ExplorerItemViewHolder) viewHolder).mExplorerItem;
                    boolean revealed = item != null && mRevealedItemPaths.contains(item.getPath());
                    float clamped;
                    if (revealed) {
                        // 已展开时左滑收起：从展开宽度往 0 回滑
                        clamped = Math.max(0, Math.min(revealWidth + dX, revealWidth));
                    } else {
                        clamped = Math.max(0, Math.min(dX, revealWidth));
                    }
                    foreground.setTranslationX(clamped);
                    return;
                }
            }
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        }

        @Override
        public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
            int width = viewHolder.itemView.getWidth();
            if (width <= 0) {
                return 0.5f;
            }
            // 滑过展开宽度的一半就触发展开/收起
            return (getRevealWidthPx() * 0.5f) / width;
        }
    }

    // build27 UI 改版：长按文件弹出菜单（半透明遮罩 + 圆角卡片），选项与 ⋮ 菜单相同
    private void showLongPressMenu(View anchor, ExplorerItem item) {
        if (item == null) {
            return;
        }
        mSelectedItem = item;
        // 与 ⋮ 菜单相同的选项（把“更多”子菜单展开成平铺列表），条件过滤也相同
        List<int[]> options = new ArrayList<>();
        boolean executable = item.isExecutable();
        if (executable) {
            options.add(new int[]{R.id.run_repeatedly, R.string.text_run_repeatedly});
        }
        if (item.canRename()) {
            options.add(new int[]{R.id.rename, R.string.text_rename});
        }
        if (item.canDelete()) {
            options.add(new int[]{R.id.delete, R.string.text_delete});
        }
        options.add(new int[]{R.id.send, R.string.text_send});
        if (item instanceof ExplorerSampleItem) {
            options.add(new int[]{R.id.reset, R.string.text_reset_to_initial_content});
        }
        if (executable) {
            options.add(new int[]{R.id.timed_task, R.string.text_timed_task});
            options.add(new int[]{R.id.create_shortcut, R.string.text_send_shortcut});
            options.add(new int[]{R.id.open_by_other_apps, R.string.text_open_by_other_apps});
            options.add(new int[]{R.id.action_build_apk, R.string.text_build_apk});
        }
        if (options.isEmpty()) {
            return;
        }

        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_file_long_press, null);
        android.widget.LinearLayout optionList = popupView.findViewById(R.id.option_list);
        final PopupWindow popupWindow = new PopupWindow(
                popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        for (int[] option : options) {
            final int itemId = option[0];
            TextView row = (TextView) LayoutInflater.from(getContext())
                    .inflate(R.layout.popup_file_long_press_item, optionList, false);
            row.setText(option[1]);
            row.setOnClickListener(v -> {
                popupWindow.dismiss();
                onMenuItemSelected(itemId);
            });
            optionList.addView(row);
        }
        popupView.findViewById(R.id.dim_background).setOnClickListener(v -> popupWindow.dismiss());

        // 把卡片放在被长按的行附近（下方，空间不够时放上方）
        View card = popupView.findViewById(R.id.popup_card);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float density = dm.density;
        int cardWidth = (int) (280 * density + 0.5f);
        card.measure(
                View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int cardHeight = card.getMeasuredHeight();
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int margin = (int) (16 * density + 0.5f);
        int x = Math.max(margin, Math.min(loc[0], dm.widthPixels - cardWidth - margin));
        int y = loc[1] + anchor.getHeight() + (int) (8 * density + 0.5f);
        if (y + cardHeight > dm.heightPixels - margin) {
            y = loc[1] - cardHeight - (int) (8 * density + 0.5f);
        }
        y = Math.max(margin, y);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
        lp.leftMargin = x;
        lp.topMargin = y;
        card.setLayoutParams(lp);
        popupWindow.showAtLocation(anchor, Gravity.NO_GRAVITY, 0, 0);
    }

    protected void notifyOperated() {
        if (mOnItemOperatedListener != null) {
            mOnItemOperatedListener.OnItemOperated(mSelectedItem);
        }
    }

    @SuppressLint("CheckResult")
    private void sort(final int sortType, final boolean isDir) {
        setRefreshing(true);
        Observable.fromCallable(() -> {
            if (isDir) {
                mExplorerItemList.sortItemGroup(sortType);
            } else {
                mExplorerItemList.sortFile(sortType);
            }
            return mExplorerItemList;
        })

                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(o -> {
                    mExplorerAdapter.notifyDataSetChanged();
                    setRefreshing(false);
                });
    }


    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mExplorer != null)
            mExplorer.registerChangeListener(this);

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mExplorer.unregisterChangeListener(this);
    }


    protected BindableViewHolder<?> onCreateViewHolder(LayoutInflater inflater, ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_ITEM) {
            return new ExplorerItemViewHolder(inflater.inflate(R.layout.script_file_list_file, parent, false));
        } else if (viewType == VIEW_TYPE_PAGE) {
            return new ExplorerPageViewHolder(inflater.inflate(R.layout.script_file_list_directory, parent, false));
        } else {
            return new CategoryViewHolder(inflater.inflate(R.layout.script_file_list_category, parent, false));
        }
    }

    protected RecyclerView getExplorerItemListView() {
        return mExplorerItemListView;
    }

    private class ExplorerAdapter extends RecyclerView.Adapter<BindableViewHolder<?>> {

        @Override
        public BindableViewHolder<?> onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getContext());
            return ExplorerView.this.onCreateViewHolder(inflater, parent, viewType);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(BindableViewHolder<?> holder, int position) {
            int positionOfCategoryFile = positionOfCategoryFile();
            BindableViewHolder bindableViewHolder = (BindableViewHolder) holder;
            if (position == positionOfCategoryDir || position == positionOfCategoryFile) {
                bindableViewHolder.bind(position == positionOfCategoryDir, position);
                return;
            }
            if (position < positionOfCategoryFile) {
                bindableViewHolder.bind(mExplorerItemList.getItemGroup(position - 1), position);
                return;
            }
            bindableViewHolder.bind(mExplorerItemList.getItem(position - positionOfCategoryFile - 1), position);
        }

        @Override
        public int getItemViewType(int position) {
            int positionOfCategoryFile = positionOfCategoryFile();
            if (position == positionOfCategoryDir || position == positionOfCategoryFile) {
                return VIEW_TYPE_CATEGORY;
            } else if (position < positionOfCategoryFile) {
                return VIEW_TYPE_PAGE;
            } else {
                return VIEW_TYPE_ITEM;
            }
        }

        int getItemPosition(ExplorerItem item, int i) {
            if (item instanceof ExplorerPage) {
                return i + positionOfCategoryDir + 1;
            }
            return i + positionOfCategoryFile() + 1;
        }

        public void notifyItemChanged(ExplorerItem item, int i) {
            notifyItemChanged(getItemPosition(item, i));
        }

        public void notifyItemRemoved(ExplorerItem item, int i) {
            notifyItemRemoved(getItemPosition(item, i));
        }

        public void notifyItemInserted(ExplorerItem item, int i) {
            notifyItemInserted(getItemPosition(item, i));
        }

        @Override
        public int getItemCount() {
            int count = 0;
            if (!mCurrentPageState.dirsCollapsed) {
                count += mExplorerItemList.groupCount();
            }
            if (!mCurrentPageState.filesCollapsed) {
                count += mExplorerItemList.itemCount();
            }
            return count + 2;
        }
    }

    protected class ExplorerItemViewHolder extends BindableViewHolder<ExplorerItem> {

        @BindView(R.id.name)
        TextView mName;
        @BindView(R.id.first_char)
        TextView mFirstChar;
        @BindView(R.id.desc)
        TextView mDesc;
        @BindView(R.id.more)
        View mOptions;
        @BindView(R.id.swipe_more)
        View mSwipeMore;
        @BindView(R.id.edit)
        View mEdit;
        @BindView(R.id.run)
        View mRun;

        private ExplorerItem mExplorerItem;

        ExplorerItemViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(ExplorerItem item, int position) {
            mExplorerItem = item;
            mName.setText(ExplorerViewHelper.getDisplayName(item));
            mDesc.setText(PFiles.getHumanReadableSize(item.getSize()));
            // build27 UI 改版：黄色圆角方块 js 标志（所有文件行统一用 js 徽标，与效果图一致）
            mFirstChar.setText("js");
            mEdit.setVisibility(item.isEditable() ? VISIBLE : GONE);
            mRun.setVisibility(item.isExecutable() ? VISIBLE : GONE);
            // 右滑展开过的行在复用时保持展开状态
            View foreground = itemView.findViewById(R.id.foreground);
            if (foreground != null) {
                foreground.setTranslationX(mRevealedItemPaths.contains(item.getPath()) ? getRevealWidthPx() : 0);
            }
        }

        @OnClick(R.id.item)
        void onItemClick() {
            // 已展开操作按钮时，点一下先收起，不打开文件
            if (mRevealedItemPaths.remove(mExplorerItem.getPath())) {
                mExplorerAdapter.notifyItemChanged(getAdapterPosition());
                return;
            }
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(itemView, mExplorerItem);
            }
            notifyOperated();
        }

        @OnLongClick(R.id.item)
        boolean onItemLongClick() {
            showLongPressMenu(itemView, mExplorerItem);
            return true;
        }

        @OnClick(R.id.run)
        void run() {
            Scripts.INSTANCE.run(new ScriptFile(mExplorerItem.getPath()));
            notifyOperated();
        }

        @OnClick(R.id.edit)
        void edit() {
            Scripts.INSTANCE.edit(getContext(), new ScriptFile(mExplorerItem.getPath()));
            notifyOperated();
        }

        @OnClick(R.id.swipe_delete)
        void onSwipeDelete() {
            mRevealedItemPaths.remove(mExplorerItem.getPath());
            new ScriptOperations(getContext(), ExplorerView.this, getCurrentPage())
                    .delete(mExplorerItem.toScriptFile());
        }

        @OnClick(R.id.swipe_more)
        void onSwipeMore() {
            showOptionMenu(mSwipeMore);
        }

        @OnClick(R.id.more)
        void showOptionMenu() {
            showOptionMenu(mOptions);
        }

        void showOptionMenu(View anchor) {
            mSelectedItem = mExplorerItem;
            PopupMenu popupMenu = new PopupMenu(getContext(), anchor);
            popupMenu.inflate(R.menu.menu_script_options);
            Menu menu = popupMenu.getMenu();
            if (!mExplorerItem.isExecutable()) {
                menu.removeItem(R.id.run_repeatedly);
                menu.removeItem(R.id.more);
            }
            if (!mExplorerItem.canDelete()) {
                menu.removeItem(R.id.delete);
            }
            if (!mExplorerItem.canRename()) {
                menu.removeItem(R.id.rename);
            }
            if (!(mExplorerItem instanceof ExplorerSampleItem)) {
                menu.removeItem(R.id.reset);
            }
            popupMenu.setOnMenuItemClickListener(ExplorerView.this);
            popupMenu.show();
        }
    }

    protected class ExplorerPageViewHolder extends BindableViewHolder<ExplorerPage> {

        @BindView(R.id.name)
        public TextView mName;

        @BindView(R.id.more)
        public View mOptions;

        @BindView(R.id.icon)
        public ImageView mIcon;

        private ExplorerPage mExplorerPage;

        ExplorerPageViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(ExplorerPage data, int position) {
            mName.setText(ExplorerViewHelper.getDisplayName(data));
            mIcon.setImageResource(ExplorerViewHelper.getIcon(data));
            mOptions.setVisibility(data instanceof ExplorerSamplePage ? GONE : VISIBLE);
            mExplorerPage = data;

        }

        @OnClick(R.id.item)
        void onItemClick() {
            enterDirectChildPage(mExplorerPage);
        }

        @OnClick(R.id.more)
        void showOptionMenu() {
            mSelectedItem = mExplorerPage;
            PopupMenu popupMenu = new PopupMenu(getContext(), mOptions);
            popupMenu.inflate(R.menu.menu_dir_options);
            popupMenu.setOnMenuItemClickListener(ExplorerView.this);
            popupMenu.show();
        }
    }

    class CategoryViewHolder extends BindableViewHolder<Boolean> {

        @BindView(R.id.title)
        TextView mTitle;

        @BindView(R.id.sort)
        ImageView mSort;

        @BindView(R.id.order)
        ImageView mSortOrder;

        @BindView(R.id.back)
        ImageView mGoBack;

        @BindView(R.id.collapse)
        ImageView mArrow;

        private boolean mIsDir;

        CategoryViewHolder(View itemView) {
            super(itemView);
            ButterKnife.bind(this, itemView);
        }

        @Override
        public void bind(Boolean isDirCategory, int position) {
            mTitle.setText(isDirCategory ? R.string.text_directory : R.string.text_file);
            mIsDir = isDirCategory;
            if (isDirCategory && canGoBack()) {
                mGoBack.setVisibility(VISIBLE);
            } else {
                mGoBack.setVisibility(GONE);
            }
            if (isDirCategory) {
                mArrow.setRotation(mCurrentPageState.dirsCollapsed ? -90 : 0);
                mSortOrder.setImageResource(mExplorerItemList.isDirSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
            } else {
                mArrow.setRotation(mCurrentPageState.filesCollapsed ? -90 : 0);
                mSortOrder.setImageResource(mExplorerItemList.isFileSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
            }
        }

        @OnClick(R.id.order)
        void changeSortOrder() {
            if (mIsDir) {
                mSortOrder.setImageResource(mExplorerItemList.isDirSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
                mExplorerItemList.setDirSortedAscending(!mExplorerItemList.isDirSortedAscending());
                sort(mExplorerItemList.getDirSortType(), mIsDir);
            } else {
                mSortOrder.setImageResource(mExplorerItemList.isFileSortedAscending() ?
                        R.drawable.ic_ascending_order : R.drawable.ic_descending_order);
                mExplorerItemList.setFileSortedAscending(!mExplorerItemList.isFileSortedAscending());
                sort(mExplorerItemList.getFileSortType(), mIsDir);
            }
        }

        @OnClick(R.id.sort)
        void showSortOptions() {
            PopupMenu popupMenu = new PopupMenu(getContext(), mSort);
            popupMenu.inflate(R.menu.menu_sort_options);
            popupMenu.setOnMenuItemClickListener(ExplorerView.this);
            mDirSortMenuShowing = mIsDir;
            popupMenu.show();

        }

        @OnClick(R.id.back)
        void back() {
            if (canGoBack()) {
                goBack();
            }
        }

        @OnClick(R.id.title_container)
        void collapseOrExpand() {
            if (mIsDir) {
                mCurrentPageState.dirsCollapsed = !mCurrentPageState.dirsCollapsed;
            } else {
                mCurrentPageState.filesCollapsed = !mCurrentPageState.filesCollapsed;
            }
            mExplorerAdapter.notifyDataSetChanged();
        }
    }

    private static class ExplorerPageState {

        ExplorerPage page;

        boolean dirsCollapsed;

        boolean filesCollapsed;

        int scrollY;

        ExplorerPageState() {
        }

        ExplorerPageState(ExplorerPage page) {
            this.page = page;
        }
    }
}
