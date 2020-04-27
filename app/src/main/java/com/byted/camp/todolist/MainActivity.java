package com.byted.camp.todolist;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.byted.camp.todolist.beans.Note;
import com.byted.camp.todolist.beans.State;
import com.byted.camp.todolist.db.TodoContract.TodoEntry;
import com.byted.camp.todolist.db.TodoDbHelper;
import com.byted.camp.todolist.operation.activity.DatabaseActivity;
import com.byted.camp.todolist.operation.activity.DebugActivity;
import com.byted.camp.todolist.operation.activity.SettingActivity;
import com.byted.camp.todolist.ui.NoteListAdapter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ADD = 1002;

    private RecyclerView recyclerView;
    private NoteListAdapter notesAdapter;

    private TodoDbHelper dbHelper;

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT =
            new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss", Locale.ENGLISH);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivityForResult(
                        new Intent(MainActivity.this, NoteActivity.class),
                        REQUEST_CODE_ADD);
            }
        });

        recyclerView = findViewById(R.id.list_todo);
        recyclerView.setLayoutManager(new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false));
        recyclerView.addItemDecoration(
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        notesAdapter = new NoteListAdapter(new NoteOperator() {
            @Override
            public void deleteNote(Note note) {
                MainActivity.this.deleteNote(note);
            }

            @Override
            public void updateNote(Note note) {
                MainActivity.this.updateNote(note);
            }
        });
        recyclerView.setAdapter(notesAdapter);

        dbHelper = new TodoDbHelper(this);
        new LoadTask().execute();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dbHelper.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingActivity.class));
                return true;
            case R.id.action_debug:
                startActivity(new Intent(this, DebugActivity.class));
                return true;
            case R.id.action_database:
                startActivity(new Intent(this, DatabaseActivity.class));
                return true;
            default:
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD
                && resultCode == Activity.RESULT_OK) {
            new LoadTask().execute();
        }
    }

    private void deleteNote(Note note) {
        // DONE 删除数据
        new DeleteTask().execute(note.id);
        new LoadTask().execute();
    }

    private void updateNote(Note note) {
        // DONE 更新数据
        new UpdateTask().execute(note);
        new LoadTask().execute();
    }

    class LoadTask extends AsyncTask<Integer, Integer, List<Note>> {
        // DONE 从数据库中查询数据，并转换成 JavaBeans
        @Override
        protected List<Note> doInBackground(Integer... integers) {
            List<Note> highList = new ArrayList<>();
            List<Note> normalList = new ArrayList<>();
            List<Note> lowList = new ArrayList<>();
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            String[] projection = {
                    BaseColumns._ID,
                    TodoEntry.COLUMN_NAME_STATE,
                    TodoEntry.COLUMN_NAME_CONTENT,
                    TodoEntry.COLUMN_NAME_DATE,
                    TodoEntry.COLUMN_NAME_PRIORITY
            };

            String sortOrder =
                    TodoEntry.COLUMN_NAME_DATE + " DESC";

            Cursor cursor = db.query(
                    TodoEntry.TABLE_NAME,
                    projection,
                    null,
                    null,
                    null,
                    null,
                    sortOrder
            );
            while (cursor.moveToNext()) {
                long itemId = cursor.getLong(cursor.getColumnIndexOrThrow(TodoEntry._ID));
                int state = cursor.getInt(cursor.getColumnIndexOrThrow(TodoEntry.COLUMN_NAME_STATE));
                String content = cursor.getString(cursor.getColumnIndex(TodoEntry.COLUMN_NAME_CONTENT));
                int priority = cursor.getInt(cursor.getColumnIndex(TodoEntry.COLUMN_NAME_PRIORITY));
                try {
                    Date date = SIMPLE_DATE_FORMAT.parse(cursor.getString(cursor.getColumnIndex(TodoEntry.COLUMN_NAME_DATE)));
                    Note note = new Note(itemId);
                    note.setState(State.from(state));
                    note.setContent(content);
                    note.setDate(date);
                    note.setPriority(priority);
                    switch (priority) {
                        //Low
                        case 0:
                            lowList.add(note);
                            break;
                        //High
                        case 2:
                            highList.add(note);
                            break;
                        //Normal/Others
                        default:
                            normalList.add(note);
                            break;
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            cursor.close();
            //按优先级将list合并
            normalList.addAll(lowList);
            highList.addAll(normalList);
            return highList;
        }

        @Override
        protected void onPostExecute(List<Note> notes) {
            super.onPostExecute(notes);
            notesAdapter.refresh(notes);
        }
    }

    class UpdateTask extends AsyncTask<Note, Integer, Integer> {

        @Override
        protected Integer doInBackground(Note... notes) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            int state = notes[0].getState().intValue;
            ContentValues values = new ContentValues();
            values.put(TodoEntry.COLUMN_NAME_STATE, state);

            String selection = TodoEntry._ID + " = ?";
            String[] selectionArgs = {String.valueOf(notes[0].id)};

            db.update(
                    TodoEntry.TABLE_NAME,
                    values,
                    selection,
                    selectionArgs);
            return null;
        }
    }

    class DeleteTask extends AsyncTask<Long, Integer, Integer> {

        @Override
        protected Integer doInBackground(Long... longs) {
            SQLiteDatabase db = dbHelper.getWritableDatabase();

            String selection = TodoEntry._ID + " = ?";
            String[] selectionArgs = {String.valueOf(longs[0])};
            db.delete(TodoEntry.TABLE_NAME, selection, selectionArgs);
            return null;
        }
    }
}
