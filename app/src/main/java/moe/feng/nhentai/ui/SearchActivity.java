package moe.feng.nhentai.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.dao.SearchHistoryManager;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.adapter.BookListRecyclerAdapter;
import moe.feng.nhentai.ui.adapter.HistoryRecyclerAdapter;
import moe.feng.nhentai.ui.common.AbsActivity;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.Utility;

public class SearchActivity extends AbsActivity {

	private CardView mTopBar;
	private AppCompatEditText mEditText;
	private SwipeRefreshLayout mSwipeRefreshLayout;

	private RecyclerView mHistoryList, mResultList;
	private HistoryRecyclerAdapter mHistoryAdapter;
	private StaggeredGridLayoutManager mLayoutManager;
	private Handler mHandler = new Handler();
	private ArrayList<Book> mBooks = new ArrayList<>();
	private int mNowPage = 1, mHorCardCount = 2;
	private String keyword;
	private FileCacheManager mFileCacheManager;
	private FavoritesManager mFM;
	private SearchHistoryManager mHM;
	private MyArray books;
	private InputMethodManager imm;

	private static final int MSG_CODE_NO_MORE_RESULTS = 1, MSG_CODE_DIRECT_TO_BOOK = 2;

	private static final String TRANSITION_NAME_CARD = "card_view";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mFM = FavoritesManager.getInstance(getApplicationContext());
		mHM = SearchHistoryManager.getInstance(getApplicationContext(), "all");
		imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

		setContentView(R.layout.activity_search);

		mActionBar.setDisplayHomeAsUpEnabled(true);

		ViewCompat.setTransitionName(mTopBar, TRANSITION_NAME_CARD);

		startKeywordEnter();
	}

	@Override
	public void onBackPressed(){
		super.onBackPressed();
		mBooks =null;
		if (mResultList.getAdapter()!=null){
			mResultList.getAdapter().notifyDataSetChanged();
		}
		mResultList.setAdapter(null);
		Runtime.getRuntime().gc();
	}

	@Override
	protected void setUpViews() {
		mTopBar = $(R.id.top_card_layout);
		mEditText = new AppCompatEditText(this);
		mResultList = $(R.id.rv_result);
		mHistoryList = $(R.id.rv_history);
		mSwipeRefreshLayout = $(R.id.swipe_refresh_layout);

		/** Init History List */
		LinearLayoutManager mHistoryLayoutManager = new LinearLayoutManager(this);
		mHistoryList.setLayoutManager(mHistoryLayoutManager);
		mHistoryList.setHasFixedSize(true);

		mHistoryAdapter = new HistoryRecyclerAdapter(mHistoryList, mHM);
		mHistoryAdapter.setAddTextListener(new HistoryRecyclerAdapter.AddTextListener() {
			@Override
			public void onTextAdd(String string) {
				mEditText.setText(string);
				mEditText.setSelection(string.length());
			}
		});
		setHistoryListAdapter(mHistoryAdapter);

		/** Init Result List */
		if ((mHorCardCount = mSets.getInt(Settings.KEY_CARDS_COUNT, -1)) < 1) {
			mHorCardCount = Utility.getHorizontalCardCountInScreen(this);
		}

		mLayoutManager = new StaggeredGridLayoutManager(mHorCardCount, StaggeredGridLayoutManager.VERTICAL);
		mFileCacheManager = FileCacheManager.getInstance(getApplicationContext());
		mResultList.setLayoutManager(mLayoutManager);
		mResultList.setHasFixedSize(true);

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

				mNowPage=1;
				new PageGetTask().execute(getApplicationContext());
			}
		});

		mEditText.setTextAppearance(this, R.style.Base_Theme_NHBooks_Light);
		mEditText.setSingleLine(true);
		mEditText.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		mEditText.setHint(R.string.search_text_hint);
		mEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
		mEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
				if (i == EditorInfo.IME_ACTION_SEARCH) {
					if (mEditText.getText() == null) {
						Snackbar.make(
								$(R.id.root_layout),
								R.string.tips_search_text_empty,
								Snackbar.LENGTH_SHORT
						).show();
						return false;
					}
					if (TextUtils.isEmpty(mEditText.getText().toString())) {
						Snackbar.make(
								$(R.id.root_layout),
								R.string.tips_search_text_empty,
								Snackbar.LENGTH_SHORT
						).show();
						return false;
					}
					startSearchTask(mEditText.getText().toString());
					return true;
				}
				return false;
			}
		});

		/** Set up custom view on ActionBar */
		ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		mEditText.setLayoutParams(lp);

		ActionBar.LayoutParams lp2 = new ActionBar.LayoutParams(lp);
		mActionBar.setCustomView(mEditText, lp2);

		mToolbar.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mHistoryList.getVisibility() != View.VISIBLE) {
					startKeywordEnter();
				}
			}
		});
	}

	private void setResultListAdapter(final BookListRecyclerAdapter adapter) {
		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder viewHolder) {
				BookDetailsActivity.launch(getBaseContext(), adapter.getItem(position), position);
			}
		});
		adapter.addOnScrollListener(new RecyclerView.OnScrollListener() {
			@Override
			public void onScrolled(RecyclerView rv, int dx, int dy) {
				if (!mSwipeRefreshLayout.isRefreshing() && mLayoutManager.findLastCompletelyVisibleItemPositions(new int[mHorCardCount])[0] >= adapter.getItemCount() - 3) {
					mSwipeRefreshLayout.setRefreshing(true);
					++mNowPage;
					new PageGetTask().execute(getApplicationContext());
				}
			}
		});

		if (mResultList.getAdapter()== null){
			Log.d("Me", "set");
			mResultList.setAdapter(adapter);
		}
		else{
			Log.d("Me", "swap");
			mResultList.swapAdapter(adapter, false);
		}
	}

	private void setHistoryListAdapter(HistoryRecyclerAdapter adapter) {
		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder viewHolder) {
				startSearchTask(mHM.get(position));
			}
		});

		mHistoryList.setAdapter(adapter);
	}

	private void startSearchTask(String k){
		this.keyword = k;

		imm.hideSoftInputFromWindow(mEditText.getWindowToken(), 0);
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);

		mActionBar.setTitle(keyword);
		mActionBar.setDisplayShowCustomEnabled(false);
		mActionBar.setDisplayShowTitleEnabled(true);

		mHM.add(keyword);
		mHistoryAdapter.notifyDataSetChanged();

		mHistoryList.setVisibility(View.GONE);
		mSwipeRefreshLayout.setVisibility(View.VISIBLE);
		mResultList.setVisibility(View.VISIBLE);

		mSwipeRefreshLayout.setRefreshing(true);
		mNowPage=1;
		new PageGetTask().execute(getApplicationContext());

		invalidateOptionsMenu();
	}

	private void startKeywordEnter() {
		mEditText.setText("");

		mActionBar.setDisplayShowCustomEnabled(true);
		mActionBar.setDisplayShowTitleEnabled(false);

		imm.showSoftInput(mEditText, InputMethodManager.SHOW_IMPLICIT);

		mHM.reloadDatabase();
		mHistoryAdapter.notifyDataSetChanged();

		mHistoryList.setVisibility(View.VISIBLE);
		mSwipeRefreshLayout.setVisibility(View.GONE);
		mResultList.setVisibility(View.GONE);

		invalidateOptionsMenu();
	}

	private boolean isEggKeyword(String keyword) {
		return "most glorious".equalsIgnoreCase(keyword) || "劳动最光荣".equals(keyword) ||
				"勞動最光榮".equals(keyword);
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.menu_search, menu);

		boolean state = mResultList.getVisibility() == View.VISIBLE;
		menu.findItem(R.id.action_clear).setVisible(!state);
		menu.findItem(R.id.action_load_next_page).setVisible(state);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_clear) {
			mEditText.setText("");
			return true;
		} else if (id == R.id.action_load_next_page) {
			mSwipeRefreshLayout.setRefreshing(true);
			++mNowPage;
			new PageGetTask().execute(getApplicationContext());
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	private class PageGetTask extends AsyncTask<Context, Void, BaseMessage> {

		@Override
		protected BaseMessage doInBackground(Context... params) {
			mFM.reload(params[0]);
			BaseMessage msg;
			if (isEggKeyword(keyword)) {
				msg = new BaseMessage();
				if (mNowPage < 2) {
					msg.setCode(0);
					InputStream in = null;
					BufferedReader br = null;
					StringBuilder sb = new StringBuilder();
					sb.append("");
					try {
						in = getResources().openRawResource(R.raw.happylabor);
						String str;
						br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
						while ((str = br.readLine()) != null) {
							sb.append(str);
							sb.append("\n");
						}
					} catch (Resources.NotFoundException e) {
						e.printStackTrace();
						Log.d(SearchActivity.class.getSimpleName(), "doInBackground: Resource Not Found");
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
						Log.d(SearchActivity.class.getSimpleName(), "doInBackground: Error on Encoding");
					} catch (IOException e) {
						e.printStackTrace();
						Log.d(SearchActivity.class.getSimpleName(), "doInBackground: Error while Reading");
					} finally {
						try {
							if (in != null) {
								in.close();
							}
							if (br != null) {
								br.close();
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					books = new Gson().fromJson(sb.toString(), MyArray.class);
					new UpdateSearch().execute(getApplicationContext());

				} else {
					msg.setCode(MSG_CODE_NO_MORE_RESULTS);
				}
			} else {
				if (TextUtils.isDigitsOnly(keyword)) {
                    msg = new BaseMessage(MSG_CODE_DIRECT_TO_BOOK, keyword);
                    return msg;
                }

				msg = PageApi.getSearchPageList(keyword, mNowPage);
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
							ArrayList<Book> mArray =  msg.getData();
							if (!mArray.isEmpty()) {
								if (mNowPage ==1) {
									mBooks.clear();
								}
								mBooks.addAll(mArray);

								for(Book b : mBooks){
									mFileCacheManager.createCacheFromBook(b);
								}

								if (mNowPage ==1){
									BookListRecyclerAdapter newAdapter = new BookListRecyclerAdapter(mResultList, mBooks, mFM, mSets);
									setResultListAdapter(newAdapter);
								}
								else{
									mResultList.getAdapter().notifyDataSetChanged();
								}

							} else {
								Snackbar.make($(R.id.root_layout), R.string.tips_no_result, Snackbar.LENGTH_LONG).show();
							}
						}
						break;
					case MSG_CODE_NO_MORE_RESULTS:
						Snackbar.make($(R.id.root_layout), R.string.tips_no_more_results, Snackbar.LENGTH_LONG).show();
						break;
                    case MSG_CODE_DIRECT_TO_BOOK:
                        Intent intent = new Intent(SearchActivity.this, BookDetailsActivity.class);
                        intent.setAction(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse(keyword));
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                        } else {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        startActivity(intent);
                        break;
					default:
						if (mNowPage == 1) {
							Snackbar.make(
									$(R.id.root_layout),
									R.string.tips_network_error,
									Snackbar.LENGTH_LONG
							).setAction(
									R.string.snack_action_try_again,
									new View.OnClickListener() {
										@Override
										public void onClick(View view) {
											mSwipeRefreshLayout.setRefreshing(true);
											new PageGetTask().execute(getApplicationContext());
										}
									}
							).show();
						}
				}
			}
		}

	}

	public static void launch(Activity activity, View sharedCardView) {
		ActivityOptionsCompat options = ActivityOptionsCompat
				.makeSceneTransitionAnimation(activity, sharedCardView, TRANSITION_NAME_CARD);
		Intent intent = new Intent(activity, SearchActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		activity.startActivity(intent, options.toBundle());
	}


	private class MyArray {

		public ArrayList<Book> data = new ArrayList<>();

		public String toJSONString() {
			return new Gson().toJson(this);
		}

		public Book get(int position) {
			return data.get(position);
		}

		public int size() {
			return data.size();
		}

		public Book set(int position, Book book) {
			return data.set(position, book);
		}

		public boolean add(Book book) {
			return data.add(book);
		}

		public void add(int position, Book book) {
			data.add(position, book);
		}

		public void remove(int position) {
			data.remove(position);
		}

		public void updateBooksData(Context context) {
			if (data != null) {
				for (Book book : data) {
					book.updateDataFromOldData(context);
				}
			}
		}

	}

	public class UpdateSearch extends AsyncTask<Context, Void, BaseMessage> {
		@Override
		protected BaseMessage doInBackground(Context... params) {
			books.updateBooksData(params[0]);
			return null;
		}

		@Override
		protected void onPostExecute(BaseMessage msg) {
			msg.setData(books.data);
			msg.setCode(MSG_CODE_NO_MORE_RESULTS);
			Log.d(SearchActivity.class.getSimpleName(), "Search Books Update Complete ");
		}

	}

}
