package moe.feng.nhentai.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.Gson;

import java.util.Random;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.BookApi;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.dao.LatestBooksKeeper;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.common.AbsActivity;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.view.WheelProgressView;

public class RandomActivity extends AbsActivity {

	private FavoritesManager mFM;

	private LatestBooksKeeper mLatestKeeper;

	private FloatingActionButton mFAB;
	private ImageView mCoverView, mLangFieldView;
	private TextView mTitleView;
	private WheelProgressView mWheel;
	private ImageButton mLikeButton;

	private Book book;

	private RandomThread mThread;

	private static final String ELEMENT_FAB = "fab";

	private static final int MSG_CHANGE_TEXT = 0, MSG_CHANGE_COVER = 1, MSG_FAILED = 2, MSG_BOOK_SET = 3;

	public static final String TAG = RandomActivity.class.getSimpleName();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		mLatestKeeper = LatestBooksKeeper.getInstance(getApplicationContext());
		mFM = FavoritesManager.getInstance(getApplicationContext());

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_random);

		mActionBar.setDisplayHomeAsUpEnabled(true);

		ViewCompat.setTransitionName(mFAB, ELEMENT_FAB);

		mFAB.callOnClick();
	}

	@Override
	public void onBackPressed(){
		super.onBackPressed();
		mCoverView.setImageBitmap(null);
		mCoverView.invalidate();

		mLangFieldView.setImageBitmap(null);
		mLangFieldView.invalidate();
	}
	@Override
	protected void setUpViews() {
		mFAB = $(R.id.fab);
		mCoverView = $(R.id.iv_cover);
		mTitleView = $(R.id.book_title);
		mLangFieldView = $(R.id.book_lang_field);
		mWheel = $(R.id.wheel_progress);
		mLikeButton = $(R.id.btn_like);
		$(R.id.btn_open).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (book != null) {
					BookDetailsActivity.launch(getBaseContext(), book, 0);
				} else {
					Snackbar.make(
							getWindow().getDecorView().getRootView(),
							R.string.random_no_data,
							Snackbar.LENGTH_SHORT
					).show();
				}
			}
		});
		mCoverView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				$(R.id.btn_open).callOnClick();
			}
		});
		mLikeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if (book != null) {
					if (mFM.contains(book.bookId)) {
						mLikeButton.setImageResource(R.drawable.ic_favorite_outline_white_24dp);
						mFM.remove(mFM.find(book.bookId));
					} else {
						mLikeButton.setImageResource(R.drawable.ic_favorite_white_24dp);
						mFM.add(book);
					}
				} else {
					Snackbar.make(
							getWindow().getDecorView().getRootView(),
							R.string.random_no_data,
							Snackbar.LENGTH_SHORT
					).show();
				}
			}
		});

		mFAB.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (mThread != null && mThread.isAlive()) {
					mThread.stopRandom();
				}
				mWheel.setVisibility(View.VISIBLE);
				mWheel.spin();
				mThread = new RandomThread();
				mThread.start();
			}
		});
	}

	public static void launch(Activity activity, FloatingActionButton fab) {
		ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(activity, fab, ELEMENT_FAB);
		Intent intent = new Intent(activity, RandomActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		ActivityCompat.startActivity(activity, intent, options.toBundle());
	}

	private Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_CHANGE_TEXT:
					break;
				case MSG_CHANGE_COVER:
					break;

				case MSG_FAILED:
					mCoverView.setVisibility(View.GONE);
					mLangFieldView.setVisibility(View.GONE);
					mTitleView.setText(R.string.random_failed_set);
					mWheel.stopSpinning();
					mWheel.setVisibility(View.GONE);
					break;
				case MSG_BOOK_SET:
					mWheel.stopSpinning();
					mWheel.setVisibility(View.GONE);
					book = Book.toBookFromJson(msg.getData().getString("book"));
					mCoverView.setVisibility(View.VISIBLE);
					mLangFieldView.setVisibility(View.VISIBLE);
					String text ="     " + book.getAvailableTitle();
					mTitleView.setText(text);
					switch (book.langField) {
						case Book.LANG_GB:
							mLangFieldView.setImageResource(R.drawable.ic_lang_gb);
							break;
						case Book.LANG_CN:
							mLangFieldView.setImageResource(R.drawable.ic_lang_cn);
							break;
						case Book.LANG_JP:
						default:
							mLangFieldView.setImageResource(R.drawable.ic_lang_jp);
							break;
					}
					mLikeButton.setImageResource(
							mFM.contains(book.bookId) ?
									R.drawable.ic_favorite_white_24dp :
									R.drawable.ic_favorite_outline_white_24dp
					);
					break;
			}
		}
	};

	private class CoverTask extends AsyncTask<Book, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(Book... params) {
			return PageApi.getPageOriginImage(getApplicationContext(), params[0],1);
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			mCoverView.setImageBitmap(null);
			mCoverView.setImageBitmap(result);
			mCoverView.invalidate();
		}
	}

	private class RandomThread extends Thread {

		private boolean shouldStop = false;

		private int bookId;

		@Override
		public void run() {
			int maxId = 166430;
			if (mLatestKeeper.getUpdatedMiles() != -1 && mLatestKeeper.getData() != null) {
				maxId = Integer.valueOf(mLatestKeeper.getData().get(0).bookId);
			}

			Random r = new Random();
			bookId = r.nextInt(maxId);
			if (shouldStop) return;
			int repeatCount = 0;
			BaseMessage data = null;
			Book book2= new Book();
			book2.bookId = String.valueOf(bookId);
			while (repeatCount < 9) {
				data = BookApi.getBook(getApplicationContext(),  book2);
				if (data.getCode() == 0) break;
				repeatCount++;
			}
			if (shouldStop) return;
			if (data.getCode() == 0) {
				new CoverTask().execute((Book) data.getData());
				Message msg = new Message();
				msg.what = MSG_BOOK_SET;
				Bundle bundle = new Bundle();
				bundle.putString("book", new Gson().toJson(data.getData()));
				msg.setData(bundle);
				mHandler.sendMessage(msg);
			} else {
				mHandler.sendEmptyMessage(MSG_FAILED);
			}
		}

		public void stopRandom() {
			shouldStop = true;
			try {
				this.interrupt();
			} catch (Exception e) {
				Log.d(TAG, "stopRandom: Interruption Error");
			}
		}
	}

	@Override
	public void onDestroy(){
		super.onDestroy();

		mCoverView.setImageBitmap(null);
		mLangFieldView.setImageBitmap(null);
	}
}
