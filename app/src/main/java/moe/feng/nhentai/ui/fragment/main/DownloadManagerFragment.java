package moe.feng.nhentai.ui.fragment.main;

import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.util.ArrayList;

import moe.feng.nhentai.R;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.BookDetailsActivity;
import moe.feng.nhentai.ui.adapter.BookListRecyclerAdapter;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.ui.common.LazyFragment;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.Utility;

public class DownloadManagerFragment extends LazyFragment {

	private RecyclerView mRecyclerView;
	private BookListRecyclerAdapter mAdapter;

	private SwipeRefreshLayout mSwipeRefreshLayout;

	private ArrayList<Book> mBooks;

	public static final String TAG = DownloadManagerFragment.class.getSimpleName();
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public int getLayoutResId() {
		return R.layout.fragment_home_recycler;
	}

	@Override
	public void finishCreateView(Bundle state) {
		mSwipeRefreshLayout = $(R.id.swipe_refresh_layout);
		mRecyclerView = $(R.id.recycler_view);

		int mHorCardCount;
		if ((mHorCardCount = Settings.getInstance(getApplicationContext()).getInt(Settings.KEY_CARDS_COUNT, -1)) < 1) {
			mHorCardCount = Utility.getHorizontalCardCountInScreen(getActivity());
		}

		StaggeredGridLayoutManager mLayoutManager = new StaggeredGridLayoutManager(mHorCardCount, StaggeredGridLayoutManager.VERTICAL);
		mRecyclerView.setLayoutManager(mLayoutManager);
		mRecyclerView.setHasFixedSize(true);

		mBooks = new ArrayList<>();
		mAdapter = new BookListRecyclerAdapter(mRecyclerView, mBooks, getFavoritesManager(), mSets);
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
				new BooksGetTask().execute();
			}
		});

		mSwipeRefreshLayout.setRefreshing(true);
		new BooksGetTask().execute();
	}

	private void setRecyclerViewAdapter(final BookListRecyclerAdapter adapter) {

		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder viewHolder) {
				BookListRecyclerAdapter.ViewHolder holder = (BookListRecyclerAdapter.ViewHolder) viewHolder;
				Log.i(TAG, "You clicked position no." + position + " item, " +
						"its name is " + holder.mTitleTextView.getText().toString());
				BookDetailsActivity.launch(getActivity(), adapter.getItem(position), position, true);
			}
		});
		mRecyclerView.setAdapter(adapter);
		adapter.notifyDataSetChanged();
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return super.onOptionsItemSelected(item);
	}

	public void scrollToTop() {
		if (mAdapter.getItemCount() > 0) {
			mRecyclerView.smoothScrollToPosition(0);
		}
	}

	private class BooksGetTask extends AsyncTask<Void, Void, ArrayList<Book>> {

		@Override
		protected ArrayList<Book> doInBackground(Void... params) {
			return FileCacheManager.getInstance(getApplicationContext()).getExternalBooks();
		}

		@Override
		protected void onPostExecute(ArrayList<Book> books) {
			mSwipeRefreshLayout.setRefreshing(false);
			mBooks.clear();
			mBooks.addAll(books);
			mAdapter.notifyDataSetChanged();
		}

	}

	private FavoritesManager getFavoritesManager() {
			return FavoritesManager.getInstance(getApplicationContext());
	}



}
