package moe.feng.nhentai.ui.fragment.main;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.DimenRes;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.CardView;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;


import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.github.ksoichiro.android.observablescrollview.ObservableRecyclerView;
import com.github.ksoichiro.android.observablescrollview.ObservableScrollViewCallbacks;
import com.github.ksoichiro.android.observablescrollview.ScrollState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

import io.codetail.widget.RevealFrameLayout;
import moe.feng.nhentai.R;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.dao.LatestBooksKeeper;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.BookDetailsActivity;
import moe.feng.nhentai.ui.RandomActivity;
import moe.feng.nhentai.ui.SearchActivity;
import moe.feng.nhentai.ui.adapter.BookListRecyclerAdapter;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.ui.common.LazyFragment;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.Utility;


public class HomeFragment extends LazyFragment {
    private int mSectionType = SECTION_LATEST;
    private static final int SECTION_LATEST = 0;
    private ObservableRecyclerView mRecyclerView;
    private BookListRecyclerAdapter mAdapter;
    private StaggeredGridLayoutManager mLayoutManager;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ArrayList<Book> mBooks;
    private int mNowPage = 1, mHorCardCount = 2;
    public FileCacheManager mFileCacheManager;
    private LatestBooksKeeper mListKeeper;
    private FavoritesManager mFM;
    private Handler mHandler = new Handler();
    private FloatingActionButton mLuckyFAB;
    private RevealFrameLayout mSearchBar;
    private CardView mSearchBarCard;
    private boolean  isSearchBoxShowing = true;
    public static final String TAG = HomeFragment.class.getSimpleName();
    private int currentY = 0;

    // Title Bar
    private LinearLayout mTitleBarLayout;
    private AppCompatTextView mTitleMain, mTitleSub;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        mFileCacheManager = FileCacheManager.getInstance(getApplicationContext());
        mListKeeper = LatestBooksKeeper.getInstance(getApplicationContext());
        mFM = FavoritesManager.getInstance(getApplicationContext());
        setHasOptionsMenu(true);
    }
    @Override
    public int getLayoutResId() {
        return R.layout.fragment_home;
    }

    @Override
    public void finishCreateView(Bundle state) {
        mSwipeRefreshLayout = $(R.id.swipe_refresh_layout);
        mRecyclerView = $(R.id.recycler_view);
        mLuckyFAB = $(R.id.fab);
        mSearchBar = $(R.id.search_bar);
        mSearchBarCard = $(R.id.card_view);
        mTitleBarLayout = $(R.id.title_bar_layout);
        mTitleMain = $(R.id.tv_title_main);
        mTitleSub = $(R.id.tv_title_sub);

        if ((mHorCardCount = Settings.getInstance(getApplicationContext()).getInt(Settings.KEY_CARDS_COUNT, -1)) < 1) {
            mHorCardCount = Utility.getHorizontalCardCountInScreen(getActivity());
        }

        mLayoutManager = new StaggeredGridLayoutManager(mHorCardCount, StaggeredGridLayoutManager.VERTICAL);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setHasFixedSize(false);
        mRecyclerView.setScrollViewCallbacks(new ObservableScrollViewCallbacks() {
            @Override
            public void onScrollChanged(int i, boolean b, boolean b1) {
                currentY = i + getResources().getDimensionPixelOffset(R.dimen.list_margin_top);
                updateTranslation(currentY);
            }

            @Override
            public void onDownMotionEvent() {

            }

            @Override
            public void onUpOrCancelMotionEvent(ScrollState scrollState) {

            }
        });

        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                int deltaY = -dy;
                if (deltaY > 0 != isSearchBoxShowing) {
                    if (deltaY >= 0) {
                        showSearchBox();
                    } else {
                        hideSearchBox();
                    }
                }
            }
        });

        if (mListKeeper.getData() != null && !mListKeeper.getData().isEmpty() && mListKeeper.getUpdatedMiles() != -1) {
            mBooks = mListKeeper.getData();
            mNowPage = mListKeeper.getNowPage();
            mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
            setRecyclerAdapter(mAdapter);
        } else {
            mBooks = new ArrayList<>();
            mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
            setRecyclerAdapter(mAdapter);
            new PageGetTask().execute(mNowPage);
        }


        mSwipeRefreshLayout.setColorSchemeResources(
                R.color.deep_purple_500, R.color.pink_500, R.color.orange_500, R.color.brown_500,
                R.color.indigo_500, R.color.blue_500, R.color.teal_500, R.color.green_500
        );

        mSwipeRefreshLayout.setProgressViewOffset (true,0,180);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                mNowPage = 1;
                if (!mSwipeRefreshLayout.isRefreshing()) {
                    mSwipeRefreshLayout.setRefreshing(true);
                }
                if (mAdapter.getItemCount() >= 1) {
                    mRecyclerView.smoothScrollToPosition(0);
                }
                mBooks.clear();
                mAdapter.notifyDataSetChanged();
                new PageGetTask().execute(mNowPage = 1);
            }
        });

        mSearchBarCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SearchActivity.launch(getActivity(), mSearchBarCard);
            }
        });

        mLuckyFAB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RandomActivity.launch(getActivity(), mLuckyFAB);
            }
        });

        updateTitleBar(SECTION_LATEST);
    }

    private void updateTranslation(int currentY) {
        int titleBarDistance = calcDimens(R.dimen.title_bar_height);
        float titleAlpha = Math.min(currentY, titleBarDistance);
        titleAlpha /= (float) titleBarDistance;
        mTitleBarLayout.setAlpha(1 - titleAlpha);
        titleAlpha /= (float) titleBarDistance;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mSearchBarCard.setCardElevation(titleAlpha * calcDimens(R.dimen.searchbar_elevation_raised));
        }
    }

    private void updateTitleBar(int type) {
        switch (type) {
            case SECTION_LATEST:
                mTitleMain.setText(R.string.title_bar_main_recent);
                if (mListKeeper.getUpdatedMiles() != -1) {
                    if (System.currentTimeMillis() - mListKeeper.getUpdatedMiles() < 1 * 60 * 1000) {
                        mTitleSub.setText(R.string.title_bar_updated_time_just_now);
                    } else {
                        Calendar c = Calendar.getInstance();
                        c.setTimeInMillis(mListKeeper.getUpdatedMiles());
                        SimpleDateFormat format = new SimpleDateFormat("yyyy/M/d H:mm:ss");
                        String result;
                        try {
                            result = format.format(c.getTime());
                        } catch (Exception e) {
                            e.printStackTrace();
                            result = "null";
                        }
                        mTitleSub.setText(getString(R.string.title_bar_updated_time_at, result));
                    }
                } else {
                    mTitleSub.setText(R.string.title_bar_updated_time_null);
                }
                break;
        }
    }
    private void showSearchBox() {
        isSearchBoxShowing = true;
        if (currentY < 10) {
            mSearchBar.setTranslationY(0);
        } else {
            mSearchBar.setTranslationY(-calcDimens(R.dimen.logo_fade_out_translation_y));
            mSearchBar.animate()
                    .translationY(0)
                    .alpha(1f)
                    .setDuration(200)
                    .start();
        }
    }

    private void hideSearchBox() {
        if (currentY > calcDimens(R.dimen.background_delta_height)) {
            isSearchBoxShowing = false;
            mSearchBar.setTranslationY(0);
            mSearchBar.animate()
                    .translationY(-calcDimens(R.dimen.logo_fade_out_translation_y))
                    .alpha(0f)
                    .setDuration(100)
                    .start();
        }
    }

    private int calcDimens(@DimenRes int... dimenIds) {
        int result = 0;
        for (int dimenId : dimenIds) {
            result += getResources().getDimensionPixelSize(dimenId);
        }
        return result;
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        getActivity().getMenuInflater().inflate(R.menu.menu_main, menu);
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_load_next_page) {
            if(!mSwipeRefreshLayout.isRefreshing()){
                mSwipeRefreshLayout.setRefreshing(true);
            }

            new PageGetTask().execute(++mNowPage);
        }
        return super.onOptionsItemSelected(item);
    }

    private void setRecyclerAdapter(final BookListRecyclerAdapter adapter) {
        adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder viewHolder) {
                BookDetailsActivity.launch(getActivity(), adapter.getItem(position), position);
            }
        });
        adapter.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (!mSwipeRefreshLayout.isRefreshing() && mLayoutManager.findLastCompletelyVisibleItemPositions(new int[mHorCardCount])[0] >= mAdapter.getItemCount() - 3) {
                    mSwipeRefreshLayout.setRefreshing(true);
                    new PageGetTask().execute(++mNowPage);
                }
            }
        });

        mRecyclerView.setAdapter(adapter);
        adapter.notifyDataSetChanged();
    }

    public void scrollToTop() {
        if (mAdapter.getItemCount() > 0) {
            mRecyclerView.smoothScrollToPosition(0);
        }
    }

    private class PageGetTask extends AsyncTask<Integer, Void, BaseMessage> {

        @Override
        protected BaseMessage doInBackground(Integer... params) {
            Log.d(TAG, "doInBackground: mNowPage = " + params[0]);
            return PageApi.getHomePageList(params[0]);
        }

        @Override
        protected void onPostExecute(BaseMessage msg) {
            mSwipeRefreshLayout.setRefreshing(false);
            if (msg != null) {
                if (msg.getCode() == 0 && msg.getData() != null) {
                    if (!((ArrayList<Book>) msg.getData()).isEmpty()) {

                        for(Book b : (ArrayList<Book>) msg.getData()){
                            mFileCacheManager.createCacheFromBook(b);
                        }

                        mBooks.addAll((ArrayList<Book>) msg.getData());

                        mListKeeper.setData(mBooks);
                        mListKeeper.setUpdatedMiles(System.currentTimeMillis());
                        mListKeeper.setNowPage(mNowPage);
                        new Thread() {
                            @Override
                            public void run() {
                                mListKeeper.save();
                            }
                        }.start();
                        updateTitleBar(mSectionType);
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                            }
                        }, 500);
                    }
                } else if (mNowPage == 1) {
                    mListKeeper.setData(new ArrayList<Book>());
                    mListKeeper.setUpdatedMiles(-1);
                    mListKeeper.setNowPage(1);
                    Snackbar.make(
                            mRecyclerView,
                            R.string.tips_network_error,
                            Snackbar.LENGTH_LONG
                    ).setAction(
                            R.string.snack_action_try_again,
                            new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    mSwipeRefreshLayout.setRefreshing(true);
                                    new PageGetTask().execute(mNowPage);
                                }
                            }
                    ).show();
                }
            }
        }

    }

}
