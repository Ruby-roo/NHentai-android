package moe.feng.nhentai.ui.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.lid.lib.LabelView;

import java.util.ArrayList;
import java.util.List;

import moe.feng.nhentai.R;
import moe.feng.nhentai.api.BookApi;
import moe.feng.nhentai.dao.FavoritesManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.ColorGenerator;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.TextDrawable;
import moe.feng.nhentai.util.Utility;

public class BookGridRecyclerAdapter extends AbsRecyclerViewAdapter {

	private ArrayList<Book> data;
	private FavoritesManager fm;
	private Settings sets;
	private ColorGenerator mColorGenerator;

	public static final String TAG = BookGridRecyclerAdapter.class.getSimpleName();

	@SuppressWarnings("unused")
	public BookGridRecyclerAdapter(RecyclerView recyclerView, FavoritesManager fm, Settings sets) {
		this(recyclerView, null, fm, sets);
	}

	public BookGridRecyclerAdapter(RecyclerView recyclerView, ArrayList<Book> data, FavoritesManager fm, Settings sets) {
		super(recyclerView);
		this.data = data;
		this.fm = fm;
		this.sets = sets;
		mColorGenerator = ColorGenerator.MATERIAL;
	}

	@Override
	public void onViewRecycled(ClickableViewHolder holder) {
		super.onViewRecycled(holder);
		((ViewHolder) holder).labelView.remove();
		((ViewHolder) holder).mPreviewImageView.setImageBitmap(null);
		((ViewHolder) holder).mPreviewImageView.invalidate();

		((ViewHolder) holder).mLangFieldView.setImageBitmap(null);
		((ViewHolder) holder).mLangFieldView.invalidate();
	}

	@Override
	public ClickableViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
		bindContext(viewGroup.getContext());
		View view = LayoutInflater.from(getContext()).inflate(R.layout.list_item_book_grid, viewGroup, false);
		return new ViewHolder(view, new LabelView(getContext()));
	}
	
	@Override
	public void onBindViewHolder(final ClickableViewHolder holder,final int position) {
		super.onBindViewHolder(holder, position);
		if (holder instanceof ViewHolder) {
			List<Book> data = this.data == null ? fm.toList() : this.data;
			final ViewHolder mHolder = (ViewHolder) holder;
			String text = "        "+ data.get(position).getAvailableTitle();
			mHolder.mTitleTextView.setText(text);
			String previewImageUrl = data.get(position).previewImageUrl;

            switch (data.get(position).langField) {
                case Book.LANG_GB:
                    mHolder.mLangFieldView.setImageResource(R.drawable.ic_lang_gb);
                    break;
                case Book.LANG_CN:
                    mHolder.mLangFieldView.setImageResource(R.drawable.ic_lang_cn);
                    break;
                case Book.LANG_JP:
                default:
                    mHolder.mLangFieldView.setImageResource(R.drawable.ic_lang_jp);
                    break;
            }

			int color = mColorGenerator.getColor(data.get(position).getAvailableTitle());
			TextDrawable drawable = TextDrawable.builder().buildRect(Utility.getFirstCharacter(data.get(position).getAvailableTitle()), color);
			mHolder.mPreviewImageView.setImageDrawable(drawable);
			mHolder.mImagePlaceholder = drawable;

			if (previewImageUrl != null) {
				new ImageDownloader().execute(mHolder.getParentView());
			}

			mHolder.book = data.get(position);

			if (fm.contains(mHolder.book.bookId)) {
				Log.i(TAG, "Find favorite: " + mHolder.book.bookId);
				mHolder.labelView.setText(R.string.label_added_to_favorite);
				mHolder.labelView.setBackgroundResource(R.color.blue_500);
				mHolder.labelView.setTargetView(mHolder.mPreviewImageView, 10, LabelView.Gravity.RIGHT_TOP);
			}
		}
	}

	@Override
	public int getItemCount() {
		List<Book> data = this.data == null ? fm.toList() : this.data;
		return data.size();
	}

	private class ImageDownloader extends AsyncTask<Object, Object, Void> {

		@Override
		protected Void doInBackground(Object[] params) {
			View v = (View) params[0];
			ViewHolder h = (ViewHolder) v.getTag();
			Book book = h.book;

			if (!TextUtils.isEmpty(book.previewImageUrl)) {
				ImageView imageView = h.mPreviewImageView;
				boolean useHdImage = sets != null && sets.getBoolean(Settings.KEY_LIST_HD_IMAGE, false);
				Bitmap img = useHdImage ? BookApi.getCover(getContext(), book) : BookApi.getThumb
						(getContext(), book);

				if (img != null) {
					publishProgress(v, img, imageView, book);
				}
			}

			return null;
		}

		@Override
		protected void onProgressUpdate(final Object[] values) {
			super.onProgressUpdate(values);

			View v = (View) values[0];

			if (!(v.getTag() instanceof ViewHolder) || (((ViewHolder) v.getTag()).book != null &&
					!((ViewHolder) v.getTag()).book.bookId.equals(((Book) values[3]).bookId))) {
				return;
			}
			Bitmap img = (Bitmap) values[1];
			ImageView imageView = (ImageView) values [2];
			imageView.setVisibility(View.VISIBLE);
			imageView.setTag(false);
			imageView.setImageBitmap(img);
			imageView.invalidate();
		}


	}

	public class ViewHolder extends ClickableViewHolder {

		public ImageView mPreviewImageView, mLangFieldView;
		public TextView mTitleTextView;

		Drawable mImagePlaceholder;

		public Book book;

		LabelView labelView;

		public ViewHolder(View itemView, LabelView labelView) {
			super(itemView);
			mPreviewImageView = (ImageView) itemView.findViewById(R.id.book_preview);
            mLangFieldView = (ImageView) itemView.findViewById(R.id.book_lang_field);
			mTitleTextView = (TextView) itemView.findViewById(R.id.book_title);

			this.labelView = labelView;

			itemView.setTag(this);
		}

	}

}
