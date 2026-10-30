

package com.example.masterskaya;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivity extends Activity {

    SQLiteDatabase db;
    LinearLayout root, list;
    boolean archived = false;
    String searchQuery = "";

    static final int EXPORT = 100;
    static final int IMPORT = 101;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Database(this).getWritableDatabase();
        showHome();
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(16, 12, 16, 12);
        return l;
    }

    TextView label(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(27, 40, 62));
        t.setPadding(10, 10, 10, 10);
        return t;
    }

    void addButton(
        LinearLayout parent,
        String title,
        Runnable action
    ) {
        Button b = new Button(this);
        b.setText(title);
        b.setAllCaps(false);
        parent.addView(b);
        b.setOnClickListener(v -> action.run());
    }

    EditText field(
        LinearLayout parent,
        String hint,
        String value
    ) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value == null ? "" : value);
        e.setSingleLine(true);
        parent.addView(e);
        return e;
    }

    String today() {
        return new SimpleDateFormat(
            "yyyy-MM-dd", Locale.US
        ).format(new Date());
    }

    String money(double amount) {
        return String.format(
            new Locale("ru", "RU"),
            "%.2f ₽", amount
        );
    }

    void toast(String message) {
        Toast.makeText(
            this, message, Toast.LENGTH_LONG
        ).show();
    }

    void showHome() {
        root = column();
        root.setBackgroundColor(
            Color.rgb(244, 247, 251)
        );
        setContentView(root);

        TextView heading = label("Мастерская", 28);
        heading.setTypeface(null, 1);
        root.addView(heading);

        addButton(
            root,
            "+ Добавить клиента",
            () -> editJob(null)
        );

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(tabs);

        Button active = new Button(this);
        active.setText("Активные");
        tabs.addView(
            active,
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        active.setOnClickListener(v -> {
            archived = false;
            showHome();
        });

        Button done = new Button(this);
        done.setText("Отработанные");
        tabs.addView(
            done,
            new LinearLayout.LayoutParams(0, -2, 1)
        );

        done.setOnClickListener(v -> {
            archived = true;
            showHome();
        });

        EditText search = field(
            root,
            "Поиск по клиенту или аппарату",
            searchQuery
        );

        search.addTextChangedListener(
            new TextWatcher() {
                public void beforeTextChanged(
                    CharSequence s, int a, int c, int f
                ) {}

                public void onTextChanged(
                    CharSequence s, int start,
                    int before, int count
                ) {
                    searchQuery = s.toString();
                    refresh();
                }

                public void afterTextChanged(
                    Editable e
                ) {}
            }
        );

        addButton(
            root,
            "Статистика",
            () -> statistics()
        );

        addButton(
            root,
            "Резервная копия",
            () -> backupMenu()
        );

        ScrollView scroll = new ScrollView(this);
        root.addView(
            scroll,
            new LinearLayout.LayoutParams(-1, 0, 1)
        );

        list = column();
        scroll.addView(list);

        refresh();
    }

    void refresh() {
        if (list == null) return;

        list.removeAllViews();

        String pattern = "%" + searchQuery + "%";

        Cursor c = db.rawQuery(
            "SELECT * FROM jobs " +
            "WHERE archived=? AND " +
            "(name LIKE ? OR phone LIKE ? " +
            "OR device LIKE ?) " +
            "ORDER BY id DESC",
            new String[]{
                archived ? "1" : "0",
                pattern, pattern, pattern
            }
        );

        if (c.getCount() == 0) {
            list.addView(
                label("Заявок пока нет", 17)
            );
        }

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String name = c.getString(1);
            String phone = c.getString(2);
            String date = c.getString(3);
            String device = c.getString(4);
            String problem = c.getString(5);
            double parts = c.getDouble(6);
            double labor = c.getDouble(7);

            LinearLayout card = column();
            card.setBackgroundColor(Color.WHITE);

            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, 0, 0, 14);

            list.addView(card, params);

            TextView title = label(device, 20);
            title.setTypeface(null, 1);
            card.addView(title);

            card.addView(label(
                name + " · " + date, 15
            ));

            card.addView(label(problem, 15));

            card.addView(label(
                "Запчасти: " + money(parts) +
                "\nРабота мастера: " + money(labor),
                15
            ));

            addButton(
                card,
                "Редактировать",
                () -> editJob(id)
            );

            if (phone != null &&
                !phone.trim().isEmpty()) {

                addButton(
                    card,
                    "Позвонить: " + phone,
                    () -> {
                        String clean = phone.replaceAll(
                            "[^+0-9]", ""
                        );

                        Intent intent = new Intent(
                            Intent.ACTION_DIAL,
                            Uri.parse("tel:" + clean)
                        );

                        try {
                            startActivity(intent);
                        } catch (Exception ex) {
                            toast(
                                "Не удалось открыть звонилку"
                            );
                        }
                    }
                );
            }

            addButton(
                card,
                archived
                    ? "Вернуть в активные"
                    : "Отправить в архив",
                () -> {
                    ContentValues values =
                        new ContentValues();

                    values.put(
                        "archived",
                        archived ? 0 : 1
                    );

                    db.update(
                        "jobs",
                        values,
                        "id=?",
                        new String[]{
                            String.valueOf(id)
                        }
                    );

                    refresh();
                }
            );
        }

        c.close();
    }

    void editJob(Long id) {
        String[] values = {
            "", "", today(), "", "", "0", "0"
        };

        if (id != null) {
            Cursor c = db.rawQuery(
                "SELECT * FROM jobs WHERE id=?",
                new String[]{String.valueOf(id)}
            );

            if (c.moveToFirst()) {
                for (int i = 0; i < 7; i++) {
                    values[i] = c.getString(i + 1);
                }
            }

            c.close();
        }

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = column();
        scroll.addView(form);

        EditText name = field(
            form, "Имя клиента", values[0]
        );

        EditText phone = field(
            form, "Телефон", values[1]
        );
        phone.setInputType(3);

        EditText date = field(
            form, "Дата приёма", values[2]
        );
        date.setFocusable(false);

        date.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();

            try {
                Date parsed = new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.US
                ).parse(date.getText().toString());

                if (parsed != null) {
                    cal.setTime(parsed);
                }
            } catch (Exception ignored) {}

            new DatePickerDialog(
                this,
                (picker, year, month, day) -> {
                    date.setText(String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        year, month + 1, day
                    ));
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });

        EditText device = field(
            form, "Название аппарата", values[3]
        );

        EditText problem = field(
            form, "Описание проблемы", values[4]
        );
        problem.setSingleLine(false);
        problem.setMinLines(3);

        EditText parts = field(
            form, "Стоимость запчастей", values[5]
        );
        parts.setInputType(8194);

        EditText labor = field(
            form, "Стоимость работы мастера", values[6]
        );
        labor.setInputType(8194);

        AlertDialog dialog =
            new AlertDialog.Builder(this)
                .setTitle(
                    id == null
                        ? "Новая заявка"
                        : "Редактировать заявку"
                )
                .setView(scroll)
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Сохранить", null)
                .create();

        dialog.setOnShowListener(v -> {
            dialog.getButton(-1).setOnClickListener(
                button -> {
                    if (device.getText()
                        .toString().trim().isEmpty()) {

                        device.setError(
                            "Укажите название аппарата"
                        );
                        return;
                    }

                    try {
                        double p = Double.parseDouble(
                            parts.getText().toString()
                                .replace(",", ".")
                        );

                        double l = Double.parseDouble(
                            labor.getText().toString()
                                .replace(",", ".")
                        );

                        ContentValues cv =
                            new ContentValues();

                        cv.put(
                            "name",
                            name.getText().toString()
                        );
                        cv.put(
                            "phone",
                            phone.getText().toString()
                        );
                        cv.put(
                            "date",
                            date.getText().toString()
                        );
                        cv.put(
                            "device",
                            device.getText().toString()
                        );
                        cv.put(
                            "problem",
                            problem.getText().toString()
                        );
                        cv.put("parts", Math.max(0, p));
                        cv.put("labor", Math.max(0, l));

                        if (id == null) {
                            cv.put("archived", 0);
                            db.insertOrThrow(
                                "jobs", null, cv
                            );
                        } else {
                            db.update(
                                "jobs",
                                cv,
                                "id=?",
                                new String[]{
                                    String.valueOf(id)
                                }
                            );
                        }

                        dialog.dismiss();
                        refresh();

                    } catch (Exception ex) {
                        toast(
                            "Проверьте стоимость запчастей " +
                            "и работы. Введите числа."
                        );
                    }
                }
            );
        });

        dialog.show();
    }

    void statistics() {
        LinearLayout form = column();

        EditText from = field(
            form,
            "Дата начала (ГГГГ-ММ-ДД)",
            today().substring(0, 7) + "-01"
        );

        EditText to = field(
            form,
            "Дата окончания (ГГГГ-ММ-ДД)",
            today()
        );

        TextView result = label("", 18);
        form.addView(result);

        Runnable calculate = () -> {
            Cursor c = db.rawQuery(
                "SELECT COUNT(*), " +
                "COALESCE(SUM(parts),0), " +
                "COALESCE(SUM(labor),0) " +
                "FROM jobs " +
                "WHERE archived=1 " +
                "AND date>=? AND date<=?",
                new String[]{
                    from.getText().toString(),
                    to.getText().toString()
                }
            );

            if (c.moveToFirst()) {
                result.setText(
                    "Отработано заявок: " +
                    c.getInt(0) +
                    "\n\nЗатраты на запчасти: " +
                    money(c.getDouble(1)) +
                    "\n\nЗаработок мастера: " +
                    money(c.getDouble(2))
                );
            }

            c.close();
        };

        addButton(
            form,
            "Рассчитать",
            calculate
        );

        calculate.run();

        new AlertDialog.Builder(this)
            .setTitle("Статистика")
            .setView(form)
            .setPositiveButton("Закрыть", null)
            .show();
    }

    void backupMenu() {
        new AlertDialog.Builder(this)
            .setTitle("Резервное копирование")
            .setItems(
                new String[]{
                    "Сохранить резервную копию",
                    "Восстановить из файла"
                },
                (dialog, index) -> {
                    Intent intent;

                    if (index == 0) {
                        intent = new Intent(
                            Intent.ACTION_CREATE_DOCUMENT
                        );
                        intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                        );
                        intent.setType(
                            "application/json"
                        );
                        intent.putExtra(
                            Intent.EXTRA_TITLE,
                            "masterskaya-backup.json"
                        );
                        startActivityForResult(
                            intent, EXPORT
                        );
                    } else {
                        intent = new Intent(
                            Intent.ACTION_OPEN_DOCUMENT
                        );
                        intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                        );
                        intent.setType("*/*");
                        startActivityForResult(
                            intent, IMPORT
                        );
                    }
                }
            ).show();
    }

    @Override
    protected void onActivityResult(
        int request,
        int result,
        Intent data
    ) {
        super.onActivityResult(
            request, result, data
        );

        if (result != RESULT_OK ||
            data == null ||
            data.getData() == null) {
            return;
        }

        Uri uri = data.getData();

        if (request == EXPORT) {
            exportBackup(uri);
        } else if (request == IMPORT) {
            new AlertDialog.Builder(this)
                .setTitle("Восстановление")
                .setMessage(
                    "Текущие заявки будут заменены " +
                    "данными из файла. Продолжить?"
                )
                .setNegativeButton("Отмена", null)
                .setPositiveButton(
                    "Восстановить",
                    (dialog, which) ->
                        importBackup(uri)
                )
                .show();
        }
    }

    void exportBackup(Uri uri) {
        try {
            JSONArray array = new JSONArray();

            Cursor c = db.rawQuery(
                "SELECT * FROM jobs", null
            );

            while (c.moveToNext()) {
                JSONObject o = new JSONObject();

                o.put("name", c.getString(1));
                o.put("phone", c.getString(2));
                o.put("date", c.getString(3));
                o.put("device", c.getString(4));
                o.put("problem", c.getString(5));
                o.put("parts", c.getDouble(6));
                o.put("labor", c.getDouble(7));
                o.put("archived", c.getInt(8));

                array.put(o);
            }

            c.close();

            try (OutputStream out =
                getContentResolver()
                    .openOutputStream(uri)) {

                out.write(
                    array.toString(2)
                        .getBytes("UTF-8")
                );
            }

            toast("Резервная копия сохранена");

        } catch (Exception e) {
            toast(
                "Ошибка сохранения: " +
                e.getMessage()
            );
        }
    }

    void importBackup(Uri uri) {
        try {
            ByteArrayOutputStream out =
                new ByteArrayOutputStream();

            try (InputStream in =
                getContentResolver()
                    .openInputStream(uri)) {

                byte[] buffer = new byte[4096];
                int n;

                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }
            }

            JSONArray array = new JSONArray(
                out.toString("UTF-8")
            );

            ArrayList<ContentValues> rows =
                new ArrayList<>();

            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                ContentValues cv =
                    new ContentValues();

                cv.put(
                    "name", o.optString("name")
                );
                cv.put(
                    "phone", o.optString("phone")
                );
                cv.put(
                    "date", o.optString("date")
                );
                cv.put(
                    "device", o.getString("device")
                );
                cv.put(
                    "problem", o.optString("problem")
                );
                cv.put(
                    "parts", o.optDouble("parts", 0)
                );
                cv.put(
                    "labor", o.optDouble("labor", 0)
                );
                cv.put(
                    "archived", o.optInt("archived", 0)
                );

                rows.add(cv);
            }

            db.beginTransaction();

            try {
                db.delete("jobs", null, null);

                for (ContentValues cv : rows) {
                    db.insertOrThrow(
                        "jobs", null, cv
                    );
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            toast("Данные восстановлены");
            showHome();

        } catch (Exception e) {
            toast(
                "Ошибка восстановления: " +
                e.getMessage()
            );
        }
    }

    static class Database
        extends SQLiteOpenHelper {

        Database(Context context) {
            super(
                context,
                "masterskaya.db",
                null,
                1
            );
        }

        @Override
        public void onCreate(
            SQLiteDatabase database
        ) {
            database.execSQL(
                "CREATE TABLE jobs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "phone TEXT," +
                "date TEXT," +
                "device TEXT," +
                "problem TEXT," +
                "parts REAL," +
                "labor REAL," +
                "archived INTEGER DEFAULT 0)"
            );
        }

        @Override
        public void onUpgrade(
            SQLiteDatabase database,
            int oldVersion,
            int newVersion
        ) {}
    }
}
