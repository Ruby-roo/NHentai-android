package moe.feng.nhentai.ui.adapter;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.v13.app.FragmentPagerAdapter;

import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.ui.fragment.BookPageFragment;

public class GalleryPagerAdapter extends FragmentPagerAdapter {

	private Book mBook;
	private BookPageFragment[] mFragments;
	private FragmentManager mFragmentManager;

	public GalleryPagerAdapter(FragmentManager fragmentManager, Book book) {
		super(fragmentManager);
		mFragmentManager = fragmentManager;
		mBook = book;
		mFragments = new BookPageFragment[book.pageCount];
	}

	@Override
	public Fragment getItem(int position) {
		if (position < 0 || position >= mBook.pageCount) {
			return null;
		}

		if (mFragments[position] == null) {
			mFragments[position] = BookPageFragment.newInstance(mBook, position + 1);
		}

		return mFragments[position];
	}

	@Override
	public int getCount() {
		return mBook.pageCount;
	}

	public void eraseItem(int position) {
		BookPageFragment fragment = mFragments[position];
		if (fragment != null) {
			mFragmentManager.beginTransaction().remove(fragment).commit();
			mFragments[position] = null;
		}
	}

	public void notifyPageImageLoaded(int position, boolean isSucceed) {
		BookPageFragment fragment = mFragments[position];
		if (fragment != null && fragment.getHandler() != null) {
			if (fragment.getHandler() != null) {
				fragment.getHandler().sendEmptyMessage(isSucceed ? BookPageFragment.MSG_FINISHED_LOADING : BookPageFragment.MSG_ERROR_LOADING);
			}
		}
	}
}
