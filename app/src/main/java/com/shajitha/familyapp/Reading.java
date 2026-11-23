package com.shajitha.familyapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.shajitha.familyapp.data.AppDatabase;
import com.shajitha.familyapp.data.Book;
import com.shajitha.familyapp.data.FamilyDao;

import java.util.List;

/** Reading Log: books the family is reading, with pages read and progress. */
public class Reading extends BaseActivity {

    private FamilyDao dao;
    private TextView empty;
    private View statBooks, statPages, statFinished;
    private final BookAdapter adapter = new BookAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reading);
        dao = AppDatabase.get(this).dao();

        empty = findViewById(R.id.readingEmpty);
        statBooks = findViewById(R.id.statBooks);
        statPages = findViewById(R.id.statPages);
        statFinished = findViewById(R.id.statFinished);
        RecyclerView list = findViewById(R.id.readingList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.readingAdd).setOnClickListener(v -> showBookDialog(null));
        dao.books().observe(this, this::show);
    }

    private void show(List<Book> books) {
        adapter.submitList(books);
        int pages = 0, finished = 0;
        for (Book b : books) {
            pages += b.pagesRead;
            if (b.isFinished()) {
                finished++;
            }
        }
        setStat(statBooks, books.size(), R.string.reading_stat_books);
        setStat(statPages, pages, R.string.reading_stat_pages);
        setStat(statFinished, finished, R.string.reading_stat_finished);
        ((TextView) findViewById(R.id.readingSummary)).setText(getString(R.string.reading_summary, books.size(), pages));
        empty.setVisibility(books.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setStat(View stat, int value, int label) {
        ((TextView) stat.findViewById(R.id.statValue)).setText(String.valueOf(value));
        ((TextView) stat.findViewById(R.id.statLabel)).setText(label);
    }

    /** Adds a book (book == null) or edits one: title, pages read and total pages. */
    void showBookDialog(Book book) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_book, null);
        TextInputLayout titleLayout = view.findViewById(R.id.bookTitleLayout);
        TextInputLayout readLayout = view.findViewById(R.id.bookReadLayout);
        TextInputEditText title = view.findViewById(R.id.bookTitleInput);
        TextInputEditText read = view.findViewById(R.id.bookReadInput);
        TextInputEditText total = view.findViewById(R.id.bookTotalInput);
        if (book != null) {
            title.setText(book.title);
            read.setText(String.valueOf(book.pagesRead));
            if (book.totalPages > 0) {
                total.setText(String.valueOf(book.totalPages));
            }
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(book == null ? R.string.reading_add_book : R.string.reading_edit_book)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String titleText = Dialogs.text(title);
            int pagesRead = Dialogs.number(read, 0);
            int totalPages = Dialogs.number(total, 0);
            titleLayout.setError(titleText.isEmpty() ? getString(R.string.reading_need_title) : null);
            readLayout.setError(totalPages > 0 && pagesRead > totalPages ? getString(R.string.reading_too_many_pages) : null);
            if (titleLayout.getError() != null || readLayout.getError() != null) {
                return;
            }
            Book b = book == null ? new Book() : copy(book);
            long now = System.currentTimeMillis();
            if (book == null) {
                b.createdAt = now;
            }
            b.title = titleText;
            b.pagesRead = pagesRead;
            b.totalPages = totalPages;
            b.updatedAt = now;
            AppDatabase.write(() -> {
                if (book == null) {
                    dao.insert(b);
                } else {
                    dao.update(b);
                }
            });
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
    }

    /** Quick progress update: how many pages have been read so far. */
    private void updatePages(Book book) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = view.findViewById(R.id.dialogInputLayout);
        TextInputEditText input = view.findViewById(R.id.dialogInput);
        layout.setHint(book.totalPages > 0
                ? getString(R.string.reading_pages_of, book.totalPages)
                : getString(R.string.reading_pages_read_hint));
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(book.pagesRead));
        input.selectAll();
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(book.title)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int pages = Dialogs.number(input, -1);
            if (pages < 0) {
                layout.setError(getString(R.string.error_required));
                return;
            }
            if (book.totalPages > 0 && pages > book.totalPages) {
                layout.setError(getString(R.string.reading_too_many_pages));
                return;
            }
            Book b = copy(book);
            b.pagesRead = pages;
            b.updatedAt = System.currentTimeMillis();
            AppDatabase.write(() -> dao.update(b));
            dialog.dismiss();
        }));
        Dialogs.showWithKeyboard(dialog);
    }

    private static Book copy(Book book) {
        Book b = new Book();
        b.id = book.id;
        b.title = book.title;
        b.totalPages = book.totalPages;
        b.pagesRead = book.pagesRead;
        b.createdAt = book.createdAt;
        b.updatedAt = book.updatedAt;
        return b;
    }

    private final class BookAdapter extends ListAdapter<Book, RecyclerView.ViewHolder> {
        BookAdapter() {
            super(new DiffUtil.ItemCallback<Book>() {
                @Override
                public boolean areItemsTheSame(@NonNull Book a, @NonNull Book b) {
                    return a.id == b.id;
                }

                @Override
                public boolean areContentsTheSame(@NonNull Book a, @NonNull Book b) {
                    return a.title.equals(b.title) && a.pagesRead == b.pagesRead && a.totalPages == b.totalPages;
                }
            });
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book, parent, false)) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Book book = getItem(position);
            View v = holder.itemView;
            ((TextView) v.findViewById(R.id.bookTitle)).setText(book.title);
            v.findViewById(R.id.bookFinished).setVisibility(book.isFinished() ? View.VISIBLE : View.GONE);
            LinearProgressIndicator progress = v.findViewById(R.id.bookProgress);
            TextView status = v.findViewById(R.id.bookStatus);
            if (book.totalPages > 0) {
                progress.setVisibility(View.VISIBLE);
                progress.setProgress((int) (1000L * Math.min(book.pagesRead, book.totalPages) / book.totalPages));
                int percent = (int) (100L * Math.min(book.pagesRead, book.totalPages) / book.totalPages);
                status.setText(getString(R.string.reading_progress, book.pagesRead, book.totalPages, percent));
            } else {
                progress.setVisibility(View.GONE);
                status.setText(getResources().getQuantityString(R.plurals.reading_pages_read, book.pagesRead, book.pagesRead));
            }
            v.findViewById(R.id.bookUpdate).setVisibility(book.isFinished() ? View.GONE : View.VISIBLE);
            v.findViewById(R.id.bookUpdate).setOnClickListener(x -> updatePages(book));
            v.findViewById(R.id.bookEdit).setOnClickListener(x -> showBookDialog(book));
            v.findViewById(R.id.bookDelete).setOnClickListener(x -> Dialogs.confirmDelete(Reading.this,
                    getString(R.string.reading_delete_title), book.title,
                    () -> AppDatabase.write(() -> dao.delete(book))));
        }
    }
}
