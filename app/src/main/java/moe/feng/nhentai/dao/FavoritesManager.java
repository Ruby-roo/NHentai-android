package moe.feng.nhentai.dao;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.util.Utility;

public class FavoritesManager {

	private MyArray books;
	private Context context;

	public static final String TAG = FavoritesManager.class.getSimpleName();

	private static final String FILE_NAME = "favorite_books.json";

	private static FavoritesManager sInstance;

	public static FavoritesManager getInstance(Context context) {
		if (sInstance == null) {
			sInstance = new FavoritesManager(context);
			sInstance.reload(context);
		}
		return sInstance;
	}

	private FavoritesManager(Context context) {
		this.context = context;
		this.books = new MyArray();
	}

	public void reload(Context context) {
		String json;
		try {
			json = Utility.readStringFromFile(context, FILE_NAME);
		} catch (IOException e) {
			e.printStackTrace();
			json = "{\"data\":[]}";
		}

		books = new Gson().fromJson(json, MyArray.class);

		ArrayList<Book> toRemove = new ArrayList<>();
		for (Book book : books.data){
			if (book.bookId == null){
				toRemove.add(book);
			}
		}
		books.data.removeAll(toRemove);
		save();
	}

	public Book get(int position) {
		return books.get(position);
	}

	public void remove(int position) {
		books.remove(position);
	}

	public void add(Book book) {
		books.add(book);
	}

	public void add(int position, Book book) {
		books.add(position, book);
	}

	public void set(int position, Book book) {
		books.set(position, book);
	}

	public int size() {
		return books.size();
	}

	public List<Book> toList() {
		return books.data;
	}

	public int find(Book book) {
		for (int i = 0; i < books.size(); i++) {
			if (book.bookId.equals(books.get(i).bookId)) {
				return i;
			}
		}
		return -1;
	}

	public int find(String bookId) {
		for (int i = 0; i < books.size(); i++) {
			if (bookId.equals(books.get(i).bookId)) {
				return i;
			}
		}
		return -1;
	}

	public boolean contains(Book book) {
		return find(book) != -1;
	}

	public boolean contains(String bookId) {
		return find(bookId) != -1;
	}

	public void save() {
		try {
			Utility.saveStringToFile(context, FILE_NAME, books.toJSONString());
		} catch (IOException e) {
			e.printStackTrace();
		}
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

}
