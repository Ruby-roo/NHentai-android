package moe.feng.nhentai.dao;

import android.content.Context;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import moe.feng.nhentai.model.Book;
import moe.feng.nhentai.util.Utility;

public class HistoryManager {

    private MyArray books;
    private Context context;

    public static final String TAG = HistoryManager.class.getSimpleName();

    private static final String FILE_NAME = "history_books.json";

    private static HistoryManager sInstance;

    public static HistoryManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new HistoryManager(context);
            sInstance.reload(context);
        }
        return sInstance;
    }

    private HistoryManager(Context context) {
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

    public void removeAll() {
        books.removeAll();
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
        List<Book> reverse = new ArrayList<>(books.data);
        Collections.reverse(reverse);
        return reverse;
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

        public List<Book> data = new ArrayList<>();

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

        public void removeAll() {
            data.clear();
        }

    }

}
