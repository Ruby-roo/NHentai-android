package moe.feng.nhentai.ui.fragment;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.AppCompatTextView;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.google.gson.Gson;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.GalleryActivity;
import moe.feng.nhentai.ui.common.LazyFragment;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.task.PageDownloader;
import moe.feng.nhentai.view.WheelProgressView;
import uk.co.senab.photoview.PhotoViewAttacher;

public class BookPageFragment extends LazyFragment {

	private Book book;
	private int pageNum;
	private ImageView mImageView;
	private PhotoViewAttacher mPhotoViewAttacher;
	private WheelProgressView mWheelProgress;

	private static final String ARG_BOOK_DATA = "arg_book_data", ARG_PAGE_NUM = "arg_page_num";

	public static final String TAG = BookPageFragment.class.getSimpleName();
	public static final int MSG_FINISHED_LOADING = 1, MSG_ERROR_LOADING = 2;

	public static BookPageFragment newInstance(Book book, int pageNum) {
		BookPageFragment fragment = new BookPageFragment();
		Bundle data = new Bundle();
		data.putString(ARG_BOOK_DATA, book.toJSONString());
		data.putInt(ARG_PAGE_NUM, pageNum);
		fragment.setArguments(data);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Bundle data = getArguments();
		book = new Gson().fromJson(data.getString(ARG_BOOK_DATA), Book.class);
		pageNum = data.getInt(ARG_PAGE_NUM);

		setHandler(new MyHandler());
	}

	@Override
	public int getLayoutResId() {
		return R.layout.fragment_book_page;
	}

	@Override
	public void finishCreateView(Bundle state) {
		mImageView = $(R.id.image_view);
		mPhotoViewAttacher = new PhotoViewAttacher(mImageView);
		AppCompatTextView mPageNumText = $(R.id.page_number);
		mWheelProgress = $(R.id.wheel_progress);

		mPageNumText.setText(Integer.toString(pageNum));

		$(R.id.background_view).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (getActivity() instanceof GalleryActivity) {
					((GalleryActivity) getActivity()).toggleControlBar();
				}
			}
		});
	}

	@Override
	public void onPause() {
		super.onPause();
		try {
			Drawable toRecycle = mImageView.getDrawable();
			if ( toRecycle != null && toRecycle instanceof BitmapDrawable ) {

				if (((BitmapDrawable) mImageView.getDrawable()).getBitmap() != null){
					((BitmapDrawable) mImageView.getDrawable()).getBitmap().recycle();
				}
			}
			mImageView.setImageDrawable(null);
			mPhotoViewAttacher.cleanup();

		} catch (Exception e) {
			Log.d(TAG, "onPause: Error Recycling");
		}

	}

	@Override
	public void onResume() {
		super.onResume();
		new DownloadTask().execute();
	}

	private class DownloadTask extends AsyncTask<Void, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(Void... params) {
			return PageApi.getPageOriginImage(getApplicationContext(), book, pageNum);

		}

		@Override
		protected void onPostExecute(Bitmap result) {
			super.onPostExecute(result);
			Log.d(TAG, "onPostExecute: Donwload Image " + pageNum);
			if (result != null) {
				$(R.id.loading_content).setVisibility(View.GONE);
					mImageView.setImageBitmap(result);
					mPhotoViewAttacher.update();
					mPhotoViewAttacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
						@Override
						public void onViewTap(View view, float v, float v1) {
							if (getActivity() instanceof GalleryActivity) {
								((GalleryActivity) getActivity()).toggleControlBar();
							}
						}
					});

			}
		}

	}

	private class MyHandler extends Handler {

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MSG_FINISHED_LOADING:
					if (PageApi.isPageOriginImageLocalFileExist(getApplicationContext(), book, pageNum) || PageApi.isPageOriginImageLocalFileExist(getApplicationContext(),book,pageNum)) {
							$(R.id.loading_content).setVisibility(View.GONE);
							if (mImageView != null) {
								Log.d(TAG, "onPostExecute: Donwload Handler");
								mImageView.setImageBitmap(PageApi.getPageOriginImage(getApplicationContext(), book, pageNum));
								if (mPhotoViewAttacher != null) {
									mPhotoViewAttacher.update();
									mPhotoViewAttacher.setOnViewTapListener(new PhotoViewAttacher.OnViewTapListener() {
										@Override
										public void onViewTap(View view, float v, float v1) {
											if (getActivity() instanceof GalleryActivity) {
												((GalleryActivity) getActivity()).toggleControlBar();
											}
										}
									});
								}
							}
					} else {
						if (getActivity() != null && getActivity() instanceof GalleryActivity) {
							PageDownloader downloader = ((GalleryActivity) getActivity()).getPageDownloader();
							if (downloader != null) {
								downloader.setDownloaded(pageNum - 1, false);
								if (!downloader.isDownloading()) {
									downloader.start();
								}
							} else {
								new DownloadTask().execute();
								return;
							}
						} else {
							new DownloadTask().execute();
						}
					}
					break;
				case MSG_ERROR_LOADING:
					mWheelProgress.setVisibility(View.INVISIBLE);
					break;
			}
		}

	}

}