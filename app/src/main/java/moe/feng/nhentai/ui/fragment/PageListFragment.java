package moe.feng.nhentai.ui.fragment;

import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import java.util.ArrayList;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.BookDetailsActivity;
import moe.feng.nhentai.ui.adapter.BookListRecyclerAdapter;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.ui.common.LazyFragment;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.Utility;

public class PageListFragment extends LazyFragment {

	private FavoritesManager mFM;
	private RecyclerView mRecyclerView;
	private BookListRecyclerAdapter mAdapter;
	private StaggeredGridLayoutManager mLayoutManager;
	private SwipeRefreshLayout mSwipeRefreshLayout;
	private ArrayList<Book> mBooks;
	private int mNowPage = 1, mHorCardCount = 2;
	private boolean isAllowToLoadNextPage = true;

	private static final int MSG_CODE_NO_MORE_RESULTS = 1;

	private static final String ARG_PAGE_URL = "args_page_url";

	public static PageListFragment newInstance(String pageUrl) {
		PageListFragment fragment = new PageListFragment();
		Bundle args = new Bundle();
		args.putString(ARG_PAGE_URL, pageUrl);
		fragment.setArguments(args);
		fragment.setHasOptionsMenu(true);
		return fragment;
	}

	@Override
	public int getLayoutResId() {
		return R.layout.fragment_category_page;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}
	@Override
	public void finishCreateView(Bundle state) {
		mFM = FavoritesManager.getInstance(getApplicationContext());

		mRecyclerView = $(R.id.recycler_view);
		mSwipeRefreshLayout = $(R.id.swipe_refresh_layout);

		if ((mHorCardCount = mSets.getInt(Settings.KEY_CARDS_COUNT, -1)) < 1) {
			mHorCardCount = Utility.getHorizontalCardCountInScreen(getApplicationContext());
		}

		mLayoutManager = new StaggeredGridLayoutManager(mHorCardCount, StaggeredGridLayoutManager.VERTICAL);
		mRecyclerView.setLayoutManager(mLayoutManager);
		mRecyclerView.setHasFixedSize(true);

		mBooks = new ArrayList<>();
		mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
		setRecyclerViewAdapter(mAdapter);

		mSwipeRefreshLayout.setColorSchemeResources(
				R.color.deep_purple_500, R.color.pink_500, R.color.orange_500, R.color.brown_500,
				R.color.indigo_500, R.color.blue_500, R.color.teal_500, R.color.green_500
		);
		mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
			@Override
			public void onRefresh() {
				if (!mSwipeRefreshLayout.isRefreshing()) {
					mSwipeRefreshLayout.setRefreshing(true);
				}

				isAllowToLoadNextPage = true;
				mBooks = new ArrayList<>();
				mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
				setRecyclerViewAdapter(mAdapter);
				mNowPage=1;
				new PageGetTask().execute(mNowPage);
			}
		});

		mSwipeRefreshLayout.setRefreshing(true);
		new PageGetTask().execute(mNowPage);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_load_next_page) {
			mSwipeRefreshLayout.setRefreshing(true);
			new PageGetTask().execute(++mNowPage);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void setRecyclerViewAdapter(final BookListRecyclerAdapter adapter) {
		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder viewHolder) {
				BookDetailsActivity.launch(getActivity(), adapter.getItem(position), position);
			}
		});
		adapter.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView rv, int dx, int dy) {
				if (!mSwipeRefreshLayout.isRefreshing() && mLayoutManager.findLastCompletelyVisibleItemPositions(new int[mHorCardCount])[0] >= mAdapter.getItemCount() - 2) {
					if (!isAllowToLoadNextPage) return;
					mSwipeRefreshLayout.setRefreshing(true);
					new PageGetTask().execute(++mNowPage);
				}
			}
		});
		if (mRecyclerView.getAdapter()== null){
			Log.d("Me", "set");
			mRecyclerView.setAdapter(adapter);
		}
		else{
			Log.d("Me", "swap");
			mRecyclerView.swapAdapter(adapter, false);
		}

	}

	private class PageGetTask extends AsyncTask<Integer, Void, BaseMessage> {

		@Override
		protected BaseMessage doInBackground(Integer... params) {
			mFM.reload(getApplicationContext());
			Log.d("Hey", "doInBackground: " + getArguments().getString(ARG_PAGE_URL) + "&page=" + mNowPage);
			BaseMessage msg = PageApi.getPageList(getArguments().getString(ARG_PAGE_URL) + "&page=" + mNowPage);
			if (msg.getCode() == 0 && msg.getData() != null) {
				ArrayList<Book> temp = msg.getData();
				if (temp.isEmpty()) {
					msg.setCode(MSG_CODE_NO_MORE_RESULTS);
				} else {
					Book firstBook = temp.get(0);
					boolean hasExist = false;
					for (int i = 0; i < mBooks.size() && !hasExist; i++) {
						hasExist = mBooks.get(i).bookId.equals(firstBook.bookId);
					}
					if (hasExist && !mSwipeRefreshLayout.isRefreshing()) {
						msg.setCode(MSG_CODE_NO_MORE_RESULTS);
					}
				}
			}
			return msg;
		}

		@Override
		protected void onPostExecute(BaseMessage msg) {
			mSwipeRefreshLayout.setRefreshing(false);
			if (msg != null) {
				switch (msg.getCode()) {
					case 0:
						if (msg.getData() != null) {
							ArrayList<Book> mArray = msg.getData();
							if (!mArray.isEmpty()) {
								if (mNowPage ==1) {
									mBooks.clear();
								}
								mBooks.addAll(mArray);

								mAdapter.notifyDataSetChanged();
								if (mNowPage == 1) {
									isAllowToLoadNextPage = true;
									BookListRecyclerAdapter newAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, mFM, mSets);
									setRecyclerViewAdapter(newAdapter);
								}
								else{
									mRecyclerView.getAdapter().notifyDataSetChanged();
								}
							} else {
								Snackbar.make(mRecyclerView, R.string.tips_no_result, Snackbar.LENGTH_LONG).show();
							}
						}
						break;
					case MSG_CODE_NO_MORE_RESULTS:
						isAllowToLoadNextPage = false;
						Snackbar.make(mRecyclerView, R.string.tips_no_more_results, Snackbar.LENGTH_LONG).show();
						break;
					default:
						if (mNowPage == 1) {
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

}
