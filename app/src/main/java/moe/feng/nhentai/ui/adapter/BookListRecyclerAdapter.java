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
import moe.feng.nhentai.dao.HistoryManager;
import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.common.AbsRecyclerViewAdapter;
import moe.feng.nhentai.util.AsyncTask;
import moe.feng.nhentai.util.ColorGenerator;
import moe.feng.nhentai.util.Settings;
import moe.feng.nhentai.util.TextDrawable;
import moe.feng.nhentai.util.Utility;

public class BookListRecyclerAdapter extends AbsRecyclerViewAdapter {
    private List<Book> mData;
    private Settings mSettings;
    private ColorGenerator mColorGenerator;

    public static final String TAG = BookListRecyclerAdapter.class.getSimpleName();

    public void update(FavoritesManager fm) {
        mData = fm.toList();
    }

    public void update(HistoryManager hm) {
        mData = hm.toList();
    }

    public BookListRecyclerAdapter(RecyclerView recyclerView, FavoritesManager fm, Settings sets) {
        this(recyclerView, null, fm, sets);
    }

    public BookListRecyclerAdapter(RecyclerView recyclerView, HistoryManager hm, Settings sets) {
        this(recyclerView, null, hm, sets);
    }

    public BookListRecyclerAdapter(RecyclerView recyclerView, ArrayList<Book> data, FavoritesManager fm, Settings sets) {
        super(recyclerView);
        if (data == null) {
            mData = fm.toList();
        } else {
            mData = data;
        }

        mSettings = sets;
        mColorGenerator = ColorGenerator.MATERIAL;
        setHasStableIds(true);
    }

    public BookListRecyclerAdapter(RecyclerView recyclerView, ArrayList<Book> data, HistoryManager hm, Settings sets) {
        super(recyclerView);
        if (data == null) {
            mData = hm.toList();
        } else {
            mData = data;
        }

        mSettings = sets;
        mColorGenerator = ColorGenerator.MATERIAL;
        setHasStableIds(true);
    }


    @Override
    public void onViewRecycled(ClickableViewHolder holder) {
        super.onViewRecycled(holder);
        ViewHolder viewHolder = (ViewHolder) holder;
        viewHolder.labelView.remove();
        viewHolder.mPreviewImageView.setImageBitmap(null);
        viewHolder.mPreviewImageView.invalidate();
        viewHolder.mLangFieldView.setImageBitmap(null);
        viewHolder.mLangFieldView.invalidate();
    }

    @Override
    public ClickableViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        bindContext(viewGroup.getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.list_item_book_card, viewGroup, false);
        return new ViewHolder(view, new LabelView(getContext()));
    }

    @Override
    public void onBindViewHolder(final ClickableViewHolder holder, final int position) {
        super.onBindViewHolder(holder, position);
        if (holder instanceof ViewHolder) {
            final ViewHolder mHolder = (ViewHolder) holder;
            String text = "        " + mData.get(position).getAvailableTitle();
            mHolder.mTitleTextView.setText(text);
            String previewImageUrl = mData.get(position).previewImageUrl;

            switch (mData.get(position).langField) {
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

            int color = mColorGenerator.getColor(mData.get(position).getAvailableTitle() != null ? mData.get(position).getAvailableTitle() : "Doujin");
            TextDrawable drawable = TextDrawable.builder().buildRect(Utility.getFirstCharacter(mData.get(position).getAvailableTitle() != null ? mData.get(position).getAvailableTitle() : "Doujin"), color);
            mHolder.mPreviewImageView.setImageDrawable(drawable);
            mHolder.mImagePlaceholder = drawable;

            if (previewImageUrl != null) {
                new ImageDownloader(getItem(position)).execute(mHolder.getParentView());
            }

            Book book = mData.get(position);

            if (FavoritesManager.getInstance(getContext()).contains(book.bookId)) {
                Log.i(TAG, "Find favorite: " + book.bookId);
                mHolder.labelView.setText(R.string.label_added_to_favorite);
                mHolder.labelView.setBackgroundResource(R.color.blue_500);
                mHolder.labelView.setTargetView(mHolder.mPreviewImageView, 10, LabelView.Gravity.RIGHT_TOP);
            }
        }
    }

    @Override
    public long getItemId(int position) {
        return Long.valueOf(mData.get(position).galleryId);
    }

    public Book getItem(int position) {
        return mData.get(position);
    }


    @Override
    public int getItemCount() {
        return mData.size();
    }

    private class ImageDownloader extends AsyncTask<Object, Object, Void> {
        private Book mBook;

        ImageDownloader(Book book) {
            mBook = book;
        }

        @Override
        protected Void doInBackground(Object[] params) {
            View v = (View) params[0];
            ViewHolder h = (ViewHolder) v.getTag();

            if (!TextUtils.isEmpty(mBook.previewImageUrl)) {
                ImageView imageView = h.mPreviewImageView;
                boolean useHdImage = mSettings != null && mSettings.getBoolean(Settings.KEY_LIST_HD_IMAGE, false);
                Bitmap img = useHdImage ? BookApi.getCover(getContext(), mBook) : BookApi.getThumb(getContext(), mBook);

                if (img != null) {
                    publishProgress(v, img, imageView, mBook);
                }
            }

            return null;
        }

        @Override
        protected void onProgressUpdate(final Object[] values) {
            super.onProgressUpdate(values);

            View v = (View) values[0];
            Bitmap img = (Bitmap) values[1];
            ImageView imageView = (ImageView) values[2];
            if (!(v.getTag() instanceof ViewHolder) || mBook != null &&
                    !mBook.bookId.equals(((Book) values[3]).bookId)) {
                return;
            }

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
