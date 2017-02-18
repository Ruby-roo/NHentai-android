package moe.feng.nhentai.ui;

import android.animation.Animator;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.ShareActionProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.grobas.animation.MovingViewAnimator;
import net.grobas.view.MovingImageView;

import java.io.File;
import java.util.ArrayList;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.BookApi;
import moe.feng.nhentai.api.PageApi;
import moe.feng.nhentai.api.common.NHentaiUrl;
import moe.feng.nhentai.cache.file.FileCacheManager;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.dao.HistoryManager;
import moe.feng.nhentai.drawable.RoundSideRectDrawable;
import moe.feng.nhentai.model.BaseMessage;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.model.Category;
import moe.feng.nhentai.ui.adapter.BookGridRecyclerAdapter;
import moe.feng.nhentai.ui.adapter.BookPreviewGridAdapter;
import moe.feng.nhentai.ui.common.AbsActivity;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.ColorGenerator;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.TextDrawable;
import moe.feng.nhentai.util.Utility;
import moe.feng.nhentai.util.task.BookDownloader;
import moe.feng.nhentai.view.AutoWrapLayout;
import moe.feng.nhentai.view.ObservableScrollView;
import moe.feng.nhentai.view.WheelProgressView;

public class BookDetailsActivity extends AbsActivity implements ObservableScrollView.OnScrollChangeListener {

	private ObservableScrollView mScrollView;
	private FrameLayout mAppBarContainer, mImageContainer;
	private ImageView mImagePlaceholderView;
	private MovingImageView mImageView;
	private FloatingActionButton mFAB;
	private TextView mTitleText;
	private TextView mTitlePretty;
	private LinearLayout mTagsLayout;
	private LinearLayout mContentView, mAppBarBackground;
	private WheelProgressView mProgressWheel;
	private RecyclerView mPreviewList, mRecommendList;
	private ArrayList<Book> Recommend;
	private boolean isPlayingFABAnimation = false;
	private boolean mIsFromSaved = false;

	private int APP_BAR_HEIGHT, TOOLBAR_HEIGHT, STATUS_BAR_HEIGHT = 0, minHeight = 0;

	private MenuItem mActionDownload;
	private ShareActionProvider mShareActionProvider;

	private Book book;
	private int fromPosition;

	private boolean isFavorite = false, originFavorite = false, isFromExternal = false;
	private boolean isDownloaded = false;

	private final static String EXTRA_BOOK_DATA = "book_data", EXTRA_POSITION = "item_position", EXTRA_IS_SAVED = "is_saved";
	private NotificationManager mNotifyManager;
	private NotificationCompat.Builder mBuilder;
	public final static int REQUEST_MAIN = 1001, RESULT_HAVE_FAV = 100;
	public final static int NOTIFICATION_ID_FINISH = 10000;

	private AlertDialog mDialogDel, mDialogDownload, mDialogDelOrDownload;
	private ProgressDialog mDialogDownloading;

	private BookDownloader mDownloader;
	private FileCacheManager mFileCacheManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		APP_BAR_HEIGHT = getResources().getDimensionPixelSize(R.dimen.appbar_parallax_max_height);
		TOOLBAR_HEIGHT = getResources().getDimensionPixelSize(R.dimen.abc_action_bar_default_height_material);
		if (Build.VERSION.SDK_INT >= 19) {
			STATUS_BAR_HEIGHT = Utility.getStatusBarHeight(getApplicationContext());
		}
		minHeight = APP_BAR_HEIGHT - TOOLBAR_HEIGHT - STATUS_BAR_HEIGHT;
		setContentView(R.layout.activity_book_details);
		Intent intent = getIntent();
		if (Intent.ACTION_VIEW.equals(intent.getAction())) {
			String url = intent.getData().toString();
			book = new Book ();
			book.bookId = url;

			isFromExternal = true;
		} else {
			book = Book.toBookFromJson(intent.getStringExtra(EXTRA_BOOK_DATA));
			fromPosition = intent.getIntExtra(EXTRA_POSITION, 0);
		}

		mFileCacheManager = FileCacheManager.getInstance(getApplicationContext());
		mIsFromSaved = intent.getBooleanExtra(EXTRA_IS_SAVED, false);

		isFavorite = originFavorite = FavoritesManager.getInstance(getApplicationContext()).contains(book.bookId);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			ActivityOptions.makeTaskLaunchBehind();
		}


		TextDrawable textDrawable;
		if (book.title != null) {
			int color = ColorGenerator.MATERIAL.getColor(book.getAvailableTitle());
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mSets.getBoolean(Settings.KEY_ALLOW_STANDALONE_TASK, true)) {
				ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(
						book.getAvailableTitle(),
						null,
						getResources().getColor(R.color.deep_purple_500)
				);
				setTaskDescription(taskDesc);
			}
			textDrawable = TextDrawable.builder().buildRect(Utility.getFirstCharacter(book.getAvailableTitle()), color);
		} else {
			textDrawable = TextDrawable.builder().buildRect("", getResources().getColor(R.color.deep_purple_500));
		}
		mImagePlaceholderView.setImageDrawable(textDrawable);

		new CoverTask().execute(book);
		mImageView.getMovingAnimator().setSpeed(100);
		mImageView.getMovingAnimator().setMovementType(MovingViewAnimator.VERTICAL_MOVE);

		startBookGet();

		if (!isFromExternal) checkIsDownloaded();
	}

	@Override
	protected void setUpViews() {
		mAppBarContainer = $(R.id.appbar_container);
		mAppBarBackground = $(R.id.appbar_background);
		mImageContainer = $(R.id.image_container);
		mImageView = $(R.id.preview_image);
		mImagePlaceholderView = $(R.id.preview_placeholder);
		mFAB = $(R.id.fab);
		mTitleText = $(R.id.tv_title);
		mTitlePretty = $(R.id.pretty_title);
		mTagsLayout = $(R.id.book_tags_layout);
		mContentView = $(R.id.book_content);
		mProgressWheel = $(R.id.wheel_progress);
		mPreviewList = $(R.id.preview_list);
		mScrollView = $(R.id.scroll_view);
		mRecommendList = $(R.id.recommend_list);

		mPreviewList.setHasFixedSize(true);
		mPreviewList.setLayoutManager(new GridLayoutManager(getApplicationContext(), 2, LinearLayoutManager.HORIZONTAL, false));
		mRecommendList.setHasFixedSize(true);
		mRecommendList.setLayoutManager(new LinearLayoutManager(getApplicationContext(), LinearLayoutManager.HORIZONTAL, false));

		float fabTranslationY = -getResources().getDimension(R.dimen.floating_action_button_size_half);
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
			fabTranslationY -= getResources().getDimension(R.dimen.fab_transition_z) * 2;
		}
		mFAB.setTranslationY(fabTranslationY);
		mScrollView.addOnScrollChangeListener(this);
	}

	@Override
	public void onScrollChanged(ObservableScrollView view, int x, int y, int oldx, int oldy) {
		setViewsTranslation(Math.min(minHeight, y));
	}

	private void setViewsTranslation(int target) {
		mAppBarContainer.setTranslationY(-target);
		mAppBarBackground.setTranslationY(target);
		float alpha = Math.min(1, -mAppBarContainer.getTranslationY() / (float) minHeight);
		mAppBarBackground.setAlpha(alpha);

		mFAB.setTranslationY(-getResources().getDimension(R.dimen.floating_action_button_size_half)-target);
		if (alpha > 0.8f && !isPlayingFABAnimation) {
			hideFAB();
		} else if (alpha < 0.65f && !isPlayingFABAnimation) {
			showFAB();
		}

		mImageContainer.setTranslationY(target * 0.7f);
	}

	public static void launch(Context context, Book book, int fromPosition) {
		launch(context, book, fromPosition, false);
	}

	public static void launch(Context context, Book book, int fromPosition, boolean isFromSaved) {
		Intent intent = new Intent(context, BookDetailsActivity.class);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
				&& Settings.getInstance(context).getBoolean(Settings.KEY_ALLOW_STANDALONE_TASK, true)) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
		} else {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}
		intent.putExtra(EXTRA_BOOK_DATA, book.toJSONString());
		intent.putExtra(EXTRA_POSITION, fromPosition);
		intent.putExtra(EXTRA_IS_SAVED, isFromSaved);
		context.startActivity(intent);
	}

	private void updateUIContent() {
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setTitle(book.getAvailableTitle());
		$(R.id.toolbar).invalidate();

		mFAB.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				GalleryActivity.launch(BookDetailsActivity.this, book, 0);
			}
		});
		$(R.id.appbar_container).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				GalleryActivity.launch(BookDetailsActivity.this, book, 0);
			}
		});

		updateDetailsContent();


	}

	private void updateDetailsContent() {
		mProgressWheel.setVisibility(View.GONE);
		mContentView.setVisibility(View.VISIBLE);
		mContentView.animate().alphaBy(0f).alpha(1f).setDuration(1500).start();

		if (book.titleJP == null){
			if (book.titlePretty!=null){
				mTitleText.setText(book.titlePretty);
			}
			else {
				mTitleText.setText(book.title);
			}
		}

		else if (!book.titleJP.equals("null"))mTitleText.setText(book.titleJP);
		else mTitleText.setText(book.titlePretty);

		mTitlePretty.setText(book.title);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mSets.getBoolean(Settings.KEY_ALLOW_STANDALONE_TASK, true)) {
			ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(
					book.getAvailableTitle(),
					null,
					getResources().getColor(R.color.deep_purple_500)
			);
			setTaskDescription(taskDesc);
		}
		if (isFromExternal) {
			new CoverTask().execute(book);
		}

		Recommend = new ArrayList<>();

		checkIsDownloaded();
		setUpShareAction();
		updatePreviewList();
		updateTagsContent();

		if(!HistoryManager.getInstance(getApplicationContext()).contains(book)){
			HistoryManager.getInstance(getApplicationContext()).add(book);
			HistoryManager.getInstance(getApplicationContext()).save();
		}
	}

	private void updatePreviewList() {
		BookPreviewGridAdapter adapter = new BookPreviewGridAdapter(mPreviewList, book);
		adapter.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder holder) {
				GalleryActivity.launch(BookDetailsActivity.this, book, position);
			}
		});
		mPreviewList.setAdapter(adapter);

		BookGridRecyclerAdapter adapter1 = new BookGridRecyclerAdapter(
				mRecommendList, Recommend, FavoritesManager.getInstance(getApplicationContext()), mSets
		);
		adapter1.setOnItemClickListener(new AbsRecyclerViewAdapter.OnItemClickListener() {
			@Override
			public void onItemClick(int position, AbsRecyclerViewAdapter.ClickableViewHolder holder) {
				BookDetailsActivity.launch(getBaseContext(), Recommend.get(position), position);
			}
		});
		mRecommendList.setAdapter(adapter1);
		new RecommendGetTask().execute(book.bookId);

	}

	private void updateTagsContent() {
		int x = getResources().getDimensionPixelSize(R.dimen.tag_margin_x);
		int y = getResources().getDimensionPixelSize(R.dimen.tag_margin_y);
		int min_width = getResources().getDimensionPixelSize(R.dimen.tag_title_width);
		int color = getResources().getColor(R.color.deep_purple_800);

		// Add Parodies Tags
		if (!TextUtils.isEmpty(book.parodies)) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_parodies);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
			tagView.setText(book.parodies);
			tagView.setBackgroundDrawable(new RoundSideRectDrawable(color));
			tagView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					CategoryActivity.launch(
							BookDetailsActivity.this,
							new Category(Category.Type.PARODY, book.parodies, book.parodiesID)
					);
				}
			});
			AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
			alp.setMargins(x, y, x, y);
			tagLayout.addView(tagView, alp);
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Characters
		if (!book.characters.isEmpty()) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_characters);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			for (int z=0; z <book.characters.size();z++) {
				final String tag = book.characters.get(z);
				final String tagID = book.charactersID.get(z);

				TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
				tagView.setText(tag);
				tagView.setBackgroundDrawable(new RoundSideRectDrawable(color));
				tagView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						CategoryActivity.launch(
								BookDetailsActivity.this,
								new Category(Category.Type.CHARACTER, tag, tagID)
						);
					}
				});
				AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
				alp.setMargins(x, y, x, y);
				tagLayout.addView(tagView, alp);
			}
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Tags
		if (!book.tags.isEmpty()) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_tag);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			for (int z=0; z <book.tags.size();z++) {
				final String tag = book.tags.get(z);
				final String tagID = book.tagID.get(z);
				TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
				tagView.setText(tag);
				tagView.setBackgroundDrawable(new RoundSideRectDrawable(color));
				tagView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						CategoryActivity.launch(
								BookDetailsActivity.this,
								new Category(Category.Type.TAG, tag,tagID)
						);
					}
				});
				AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
				alp.setMargins(x, y, x, y);
				tagLayout.addView(tagView, alp);
			}
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Artist Tag
		if (!book.artists.isEmpty()) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_artists);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			for (int z=0; z <book.artists.size();z++) {
				final String tag = book.artists.get(z);
				final String tagID = book.artistsID.get(z);
				TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
				tagView.setText(tag);
				tagView.setBackgroundDrawable(new RoundSideRectDrawable(color));
				tagView.setOnClickListener(new View.OnClickListener() {
					@Override
					public void onClick(View v) {
						CategoryActivity.launch(
								BookDetailsActivity.this,
								new Category(Category.Type.ARTIST, tag, tagID)
						);
					}
				});
				AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
				alp.setMargins(x, y, x, y);
				tagLayout.addView(tagView, alp);
			}
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Groups Tag
		if (!TextUtils.isEmpty(book.group)) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setMinWidth(min_width);
			groupNameView.setText(R.string.tag_type_group);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
			tagView.setText(book.group);
			tagView.setBackgroundDrawable(new RoundSideRectDrawable(color));
			tagView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					CategoryActivity.launch(
							BookDetailsActivity.this,
							new Category(Category.Type.GROUP, book.group, book.groupID)
					);
				}
			});
			AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
			alp.setMargins(x, y, x, y);
			tagLayout.addView(tagView, alp);
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}

		// Add Language Tag
		if (!TextUtils.isEmpty(book.language)) {
			LinearLayout tagGroupLayout = new LinearLayout(this);
			tagGroupLayout.setOrientation(LinearLayout.HORIZONTAL);
			AutoWrapLayout tagLayout = new AutoWrapLayout(this);

			TextView groupNameView = new TextView(this);
			groupNameView.setPadding(0, y, 0, y);
			groupNameView.setText(R.string.tag_type_language);
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.WRAP_CONTENT,
					LinearLayout.LayoutParams.WRAP_CONTENT);
			lp.setMargins(x, y, x, y);
			lp.width = min_width;
			tagGroupLayout.addView(groupNameView, lp);

			TextView tagView = (TextView) View.inflate(getApplicationContext(), R.layout.layout_tag, null);
			tagView.setText(book.language);
			tagView.setBackgroundDrawable(new RoundSideRectDrawable(color));
			tagView.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					CategoryActivity.launch(
							BookDetailsActivity.this,
							new Category(Category.Type.LANGUAGE, book.language ,book.langField)
					);
				}
			});
			AutoWrapLayout.LayoutParams alp = new AutoWrapLayout.LayoutParams();
			alp.setMargins(x, y, x, y);
			tagLayout.addView(tagView, alp);
			tagGroupLayout.addView(tagLayout);
			mTagsLayout.addView(tagGroupLayout);
		}
	}

	private void setUpShareAction() {
		String sendingText = String.format(getString(R.string.action_share_send_text),
				book.getAvailableTitle(),
				NHentaiUrl.getBookDetailsUrl(book.bookId)
		);
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(Intent.EXTRA_TEXT, sendingText);
		intent.setType("text/plain");
		if (mShareActionProvider != null) {
			mShareActionProvider.setShareHistoryFileName("custom_share_history.xml");
			mShareActionProvider.setShareIntent(intent);
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.clear();
		getMenuInflater().inflate(R.menu.menu_details, menu);
		Log.d("Nice", "onPrepareOptionsMenu: " + isFavorite);
		MenuItem mFavItem = menu.findItem(R.id.action_fav);
		mFavItem.setIcon(isFavorite ? R.drawable.ic_favorite_white_24dp : R.drawable.ic_favorite_outline_white_24dp);
		mFavItem.setTitle(isFavorite ? R.string.action_favorite_true : R.string.action_favorite_false);

		MenuItem mShareItem = menu.findItem(R.id.action_share);
		mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(mShareItem);
		setUpShareAction();

		mActionDownload = menu.findItem(R.id.action_download);
		mActionDownload.setIcon(isDownloaded ? R.drawable.ic_cloud_done_white_24dp : R.drawable.ic_cloud_download_white_24dp);
		mActionDownload.setTitle(isDownloaded ? R.string.action_download_okay : R.string.action_download_none);

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == android.R.id.home) {
			this.onBackPressed();
			return true;
		}
		if (id == R.id.action_fav) {
			FavoritesManager fm = FavoritesManager.getInstance(getApplicationContext());
			if (isFavorite) {
				fm.remove(fm.find(book));
			} else {
				fm.add(book);
			}
			fm.save();

			isFavorite = !isFavorite;
			Snackbar.make(
					$(R.id.main_content),
					isFavorite ? R.string.favorite_add_finished : R.string.favorite_remove_finished,
					Snackbar.LENGTH_LONG
			).show();
			supportInvalidateOptionsMenu();
			return true;
		}
		if (id == R.id.action_download) {
			new Thread() {
				@Override
				public void run() {
					onActionDownloadClick();
				}
			}.start();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed() {
		if (originFavorite != isFavorite && fromPosition >= 0) {
			Intent intent = new Intent();
			intent.putExtra("position", fromPosition);
			setResult(RESULT_HAVE_FAV, intent);
		}
		mImageView.setImageBitmap(null);
		mImageView.invalidate();

		Recommend.clear();
		mRecommendList.getAdapter().notifyDataSetChanged();
		mRecommendList.setAdapter(null);


		book = null;
		mPreviewList.getAdapter().notifyDataSetChanged();
		mPreviewList.setAdapter(null);
		Runtime.getRuntime().gc();

		super.onBackPressed();

	}

	private void startBookGet() {
		mContentView.setVisibility(View.GONE);
		mProgressWheel.setVisibility(View.VISIBLE);
		mProgressWheel.spin();

		new BookGetTask(book, mIsFromSaved).execute();
	}

	private void onActionDownloadClick() {
		final String downloadPath = mFileCacheManager.getExternalPath(book);
		final int count = mFileCacheManager.getExternalBookDownloadedCount(book);
		if (mFileCacheManager.externalBookExists(book)) {
			if (mFileCacheManager.isExternalBookAllDownloaded(book)) {
				isDownloaded = true;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidateOptionsMenu();
						showDeleteDialog();
					}
				});
			} else {
				isDownloaded = false;
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidateOptionsMenu();
						showDeleteOrDownloadDialog(count, downloadPath);
					}
				});
			}
		} else {
			Log.i("TAG", "Couldn\'t find downloaded info.");
			isDownloaded = false;
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					invalidateOptionsMenu();
					showDownloadDialog(count, downloadPath);
				}
			});
		}
	}

	private void checkIsDownloaded() {

				isDownloaded = mFileCacheManager.externalBookExists(book)
						&& mFileCacheManager.isExternalBookAllDownloaded(book);
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						invalidateOptionsMenu();
					}
				});

	}

	private void showDeleteDialog() {
		if (mDialogDel == null) {
			mDialogDel = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_ask_delete_title)
					.setMessage(R.string.dialog_ask_delete_summary)
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							new DeleteTask().execute();
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mDialogDel.dismiss();
						}
					})
					.create();
		}

		mDialogDel.show();
	}

	private void showDownloadDialog(final int count, String downloadPath) {
		if (mDialogDownload == null) {
			mDialogDownload = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_ask_download_title)
					.setMessage(getString(R.string.dialog_ask_download_summary, downloadPath))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

									mBuilder= new NotificationCompat.Builder(getApplicationContext());
									mBuilder.setContentTitle(getString(R.string.dialog_download_title) + book.titlePretty)
											.setContentText(getString(R.string.dialog_download_progress))
											.setSmallIcon(R.drawable.ic_file_download_white_24dp);

									mNotifyManager.notify(Integer.valueOf(book.bookId),mBuilder.build());

									if (mDownloader == null) {

										mDownloader = new BookDownloader(getApplicationContext(), book);
										mDownloader.setOnDownloadListener(new BookDownloader.OnDownloadListener() {
											@Override
											public void onFinish(final int position, final int progress) {
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
														if (progress != position) {
															mBuilder.setProgress(position, progress, false);
															mBuilder.setContentText(getString(R.string.dialog_download_progress) +"		" + progress +" / " +position);

														} else {
															mBuilder.setProgress(0, 0, false);
															mFileCacheManager.saveBookDataToExternalPath(mDownloader.getBook());
															mBuilder.setContentText(getString(R.string.dialog_download_finished));
															isDownloaded=true;
															if(book != null) invalidateOptionsMenu();
														}
														mNotifyManager.notify(Integer.valueOf(mDownloader.getBook().bookId), mBuilder.build());
													}
												});

											}

											@Override
											public void onError(int position, int errorCode) {
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
															mBuilder.setProgress(0, 0, false);
															mBuilder.setContentText(getString(R.string.dialog_download_error));
															mDownloader.stop();
															mNotifyManager.notify(1, mBuilder.build());
														}
												});
											}

											@Override
											public void onStateChange(int state, final int progress) {
												switch (state) {
													case BookDownloader.STATE_STOP:
														break;
													case BookDownloader.STATE_PAUSE:
														break;

												}
											}
										});
									}
									mDownloader.start();
									mDialogDownload.dismiss();
								}
							});
						}
						})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mDialogDownload.dismiss();
						}
					})
					.create();
		}

		mDialogDownload.show();
	}

	private void showDeleteOrDownloadDialog(final int count, String downloadPath) {
		if (mDialogDelOrDownload == null) {
			mDialogDelOrDownload = new AlertDialog.Builder(this)
					.setTitle(R.string.dialog_ask_d_or_d_title)
					.setMessage(getString(R.string.dialog_ask_d_or_d_summary, count, downloadPath))
					.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

									mBuilder= new NotificationCompat.Builder(getApplicationContext());
									mBuilder.setContentTitle(getString(R.string.dialog_download_title))
											.setContentText(getString(R.string.dialog_download_progress))
											.setSmallIcon(R.drawable.ic_file_download_white_24dp);

									mNotifyManager.notify(Integer.valueOf(mDownloader.getBook().bookId),mBuilder.build());

									if (mDownloader == null) {

										mDownloader = new BookDownloader(getApplicationContext(), book);
										mDownloader.setOnDownloadListener(new BookDownloader.OnDownloadListener() {
											@Override
											public void onFinish(final int position, final int progress) {
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
														if (progress != position) {
															mBuilder.setProgress(position, progress, false);
															mBuilder.setContentText(getString(R.string.dialog_download_progress) + "		" + progress +" / " + position);
														} else {
															mBuilder.setProgress(0, 0, false);
															mFileCacheManager.saveBookDataToExternalPath(mDownloader.getBook());
														mBuilder.setContentText(getString(R.string.dialog_download_finished));
															isDownloaded=true;
															if(book != null) invalidateOptionsMenu();
														}
														mNotifyManager.notify(Integer.valueOf(mDownloader.getBook().bookId), mBuilder.build());
													}
												});

											}

											@Override
											public void onError(int position, int errorCode) {
												runOnUiThread(new Runnable() {
													@Override
													public void run() {
														mBuilder.setProgress(0, 0, false);
														mBuilder.setContentText(getString(R.string.dialog_download_error));
														mDownloader.stop();
														mNotifyManager.notify(Integer.valueOf(mDownloader.getBook().bookId), mBuilder.build());
													}
												});
											}

											@Override
											public void onStateChange(int state, final int progress) {
												switch (state) {
													case BookDownloader.STATE_STOP:
														break;
													case BookDownloader.STATE_PAUSE:
														break;

												}
											}
										});
									}
									mDownloader.start();
									mDialogDelOrDownload.dismiss();
								}
							});
						}
					})
					.setNeutralButton(R.string.dialog_ask_d_or_d_delete, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							new DeleteTask().execute();
							mDialogDelOrDownload.dismiss();
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							mDialogDelOrDownload.dismiss();
						}
					})
					.create();
		}

		mDialogDelOrDownload.show();
	}

	private void showFAB() {
		mFAB.animate().scaleX(1f).scaleY(1f)
				.setInterpolator(new OvershootInterpolator())
				.setListener(new Animator.AnimatorListener() {
					@Override
					public void onAnimationStart(Animator animator) {
						isPlayingFABAnimation = true;
					}

					@Override
					public void onAnimationEnd(Animator animator) {
						isPlayingFABAnimation = false;
						if (mAppBarBackground.getAlpha() > 0.8f) {
							hideFAB();
						}
					}

					@Override
					public void onAnimationCancel(Animator animator) {
						isPlayingFABAnimation = false;
					}

					@Override
					public void onAnimationRepeat(Animator animator) {

					}
				})
				.start();
	}

	private void hideFAB() {
		mFAB.animate().scaleX(0f).scaleY(0f)
				.setInterpolator(new AnticipateInterpolator())
				.setListener(new Animator.AnimatorListener() {
					@Override
					public void onAnimationStart(Animator animator) {
						isPlayingFABAnimation = true;
					}

					@Override
					public void onAnimationEnd(Animator animator) {
						isPlayingFABAnimation = false;
						if (mAppBarBackground.getAlpha() < 0.65f) {
							showFAB();
						}
					}

					@Override
					public void onAnimationCancel(Animator animator) {
						isPlayingFABAnimation = false;
					}

					@Override
					public void onAnimationRepeat(Animator animator) {

					}
				})
				.start();
	}

	private class BookGetTask extends AsyncTask<String, Void, BaseMessage> {
		private Book mBook;
		private boolean mIsFromSaved;

		BookGetTask(Book book, boolean isFromSaved) {
			mBook = book;
			mIsFromSaved = isFromSaved;
		}

		@Override
		protected BaseMessage doInBackground(String... params) {
			return BookApi.getBook(getApplicationContext(), mBook);
		}

		@Override
		protected void onPostExecute(BaseMessage result) {
			if (result.getCode() == 0) {
				book = mIsFromSaved ? mBook : (Book) result.getData();
				updateUIContent();
			} else {
				mProgressWheel.setVisibility(View.GONE);

				Snackbar.make(
						$(R.id.main_content),
						R.string.tips_network_error,
						Snackbar.LENGTH_LONG
				).setAction(
						R.string.snack_action_try_again,
						new View.OnClickListener() {
							@Override
							public void onClick(View view) {
								startBookGet();
							}
						}).show();
			}
		}

	}

	private class RecommendGetTask extends AsyncTask<String, Void, BaseMessage> {

		@Override
		protected BaseMessage doInBackground(String... params) {

			return PageApi.getPageList(NHentaiUrl.getBookRecommendUrl(book.bookId));
		}

		@Override
		protected void onPostExecute(BaseMessage result) {
			if (result.getCode() == 0) {
				Recommend.addAll((ArrayList<Book>)result.getData());

				for (Book b : Recommend){
					mFileCacheManager.createCacheFromBook(b);
				}

				 if (mRecommendList.getAdapter()!=null)
					 mRecommendList.getAdapter().notifyDataSetChanged();
			}
		}

	}

	private class CoverTask extends AsyncTask<Book, Void, Bitmap> {

		@Override
		protected Bitmap doInBackground(Book... params) {
				return PageApi.getPageOriginImage(getApplicationContext(), params[0], 1);

		}

		@Override
		protected void onPostExecute(Bitmap result) {
			mImageView.setImageDrawable(null);
			mImageView.setImageBitmap(result);
			mImageView.invalidate();


		}
	}

	private class DeleteTask extends AsyncTask<Void, Integer, Void> {

		private ProgressDialog dialog;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			dialog = new ProgressDialog(BookDetailsActivity.this);
			dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			dialog.setCancelable(false);
			dialog.setCanceledOnTouchOutside(false);
			dialog.setMax(100);

			dialog.show();
		}

		@Override
		protected Void doInBackground(Void... params) {
			File parentDir = new File(mFileCacheManager.getExternalPath(book));
			File[] files = parentDir.listFiles();
			int count = 0;
			for (File file : files) {
				file.delete();
				count++;
				this.onProgressUpdate(Utility.calcProgress(count, files.length));
			}
			parentDir.delete();
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... ints) {
			super.onProgressUpdate(ints);
			dialog.setProgress(ints[0]);
		}

		@Override
		protected void onPostExecute(Void args) {
			isDownloaded = false;
			invalidateOptionsMenu();
			dialog.dismiss();
		}

	}

	protected <T extends View> T $(int id) {
		return (T) findViewById(id);
	}

}
