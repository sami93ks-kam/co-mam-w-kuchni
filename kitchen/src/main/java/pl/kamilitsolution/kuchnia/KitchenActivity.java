package pl.kamilitsolution.kuchnia;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static pl.kamilitsolution.kuchnia.KitchenStore.Category;
import static pl.kamilitsolution.kuchnia.KitchenStore.Change;
import static pl.kamilitsolution.kuchnia.KitchenStore.Place;
import static pl.kamilitsolution.kuchnia.KitchenStore.Product;

public final class KitchenActivity extends Activity {
    private static final int GREEN = 0xff163b2e;
    private static final int ACTION = 0xff21543e;
    private static final int INK = 0xff202d24;
    private static final int MUTED = 0xff526358;
    private static final int PAPER = 0xfff4f4ec;
    private static final int CARD = 0xfffffefa;
    private static final int BORDER = 0xffdce1d5;
    private static final int SOFT = 0xffe7eee2;
    private static final int LIME = 0xffe4eda6;
    private static final int WHITE = 0xffffffff;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable expireUndo = this::clearUndo;
    private KitchenStore store;
    private Place filter;
    private boolean chemistryOnly;
    private boolean shopping;
    private Change undo;
    private LinearLayout filters;
    private LinearLayout rows;
    private LinearLayout undoBar;
    private ScrollView scroll;
    private TextView title;
    private TextView summary;
    private LinearLayout addActions;
    private Button inventoryTab;
    private Button shoppingTab;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            shopping = state.getBoolean("shopping");
            chemistryOnly = state.getBoolean("chemistryOnly");
            String place = state.getString("filter");
            filter = place == null ? null : Place.valueOf(place);
        }
        try {
            store = new KitchenStore(getFilesDir().toPath().resolve("zapasy.bin"));
        } catch (IOException failure) {
            Log.e("Kuchnia", "Odczyt zapasów", failure);
            new AlertDialog.Builder(this)
                    .setTitle("Nie udało się otworzyć zapasów")
                    .setMessage("Uruchom aplikację ponownie, aby ponowić próbę.")
                    .setPositiveButton("Zamknij", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }
        createLayout();
        render(state != null ? state.getInt("scroll") : 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("shopping", shopping);
        state.putBoolean("chemistryOnly", chemistryOnly);
        state.putString("filter", filter == null ? null : filter.name());
        if (scroll != null) {
            state.putInt("scroll", scroll.getScrollY());
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(expireUndo);
        super.onDestroy();
    }

    private void createLayout() {
        LinearLayout root = column();
        root.setBackgroundColor(PAPER);
        setContentView(root);

        LinearLayout header = row();
        header.setBackgroundColor(GREEN);
        header.setPadding(dp(18), dp(12), dp(18), dp(14));
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_kitchen);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(dp(43), dp(43)));
        LinearLayout identity = column();
        identity.setPadding(dp(8), 0, 0, 0);
        identity.addView(label("Co mam w kuchni?", 23, WHITE, true));
        identity.addView(label("Kamil IT Solution", 12, LIME, false));
        header.addView(identity, weighted());
        root.addView(header);

        LinearLayout heading = column();
        heading.setPadding(dp(18), dp(16), dp(18), dp(10));
        title = label("", 31, GREEN, true);
        if (Build.VERSION.SDK_INT >= 28) {
            title.setAccessibilityHeading(true);
        }
        summary = label("", 14, MUTED, false);
        heading.addView(title);
        heading.addView(summary);
        root.addView(heading);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterScroll.setClipToPadding(false);
        filterScroll.setPadding(dp(18), 0, dp(18), dp(9));
        filters = row();
        filterScroll.addView(filters, new ScrollView.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        root.addView(filterScroll);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        rows = column();
        rows.setPadding(dp(18), dp(4), dp(18), dp(8));
        scroll.addView(rows, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));

        undoBar = row();
        undoBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams undoParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        undoParams.setMargins(dp(18), dp(2), dp(18), dp(8));
        root.addView(undoBar, undoParams);

        addActions = row();
        Button add = button("+  Dodaj produkt", WHITE, ACTION, 16, 16);
        add.setMinHeight(dp(54));
        add.setOnClickListener(view -> showProductForm(null, null, defaultCategory()));
        addActions.addView(add, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.55f));
        Button catalog = button("Katalog", ACTION, SOFT, 16, 16);
        catalog.setMinHeight(dp(54));
        catalog.setOnClickListener(view -> showCatalog());
        LinearLayout.LayoutParams catalogParams = weighted();
        catalogParams.setMarginStart(dp(8));
        addActions.addView(catalog, catalogParams);
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        addParams.setMargins(dp(18), 0, dp(18), dp(10));
        root.addView(addActions, addParams);

        LinearLayout nav = row();
        nav.setBackgroundColor(GREEN);
        nav.setPadding(dp(18), dp(8), dp(18), dp(8));
        inventoryTab = button("Zapasy", WHITE, GREEN, 16, 14);
        shoppingTab = button("Zakupy", WHITE, GREEN, 16, 14);
        inventoryTab.setOnClickListener(view -> switchTab(false));
        shoppingTab.setOnClickListener(view -> switchTab(true));
        nav.addView(inventoryTab, weighted());
        LinearLayout.LayoutParams shoppingParams = weighted();
        shoppingParams.setMarginStart(dp(8));
        nav.addView(shoppingTab, shoppingParams);
        root.addView(nav);
    }

    private void switchTab(boolean showShopping) {
        if (shopping != showShopping) {
            shopping = showShopping;
            render(0);
        }
    }

    private void render(int scrollY) {
        title.setText(shopping ? "Lista zakupów" : "Zapasy");
        ((View) filters.getParent()).setVisibility(shopping ? View.GONE : View.VISIBLE);
        addActions.setVisibility(shopping ? View.GONE : View.VISIBLE);
        styleSelection(inventoryTab, !shopping, LIME, GREEN, GREEN, WHITE);
        styleSelection(shoppingTab, shopping, LIME, GREEN, GREEN, WHITE);
        if (!shopping) {
            renderFilters();
        }

        rows.removeAllViews();
        List<Product> visible = new ArrayList<>();
        for (Product product : store.products()) {
            boolean inFilter = (filter == null || product.place == filter)
                    && (!chemistryOnly || product.category == Category.CLEANING);
            if (shopping ? product.onShoppingList : inFilter) {
                visible.add(product);
            }
        }
        summary.setText(shopping ? positionCount(visible.size()) : productCount(visible.size()));
        if (visible.isEmpty()) {
            showEmpty();
        }
        for (Product product : visible) {
            View card = shopping ? shoppingCard(product) : stockCard(product);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            params.bottomMargin = dp(8);
            rows.addView(card, params);
        }
        scroll.post(() -> scroll.scrollTo(0, scrollY));
    }

    private void renderFilters() {
        filters.removeAllViews();
        addFilter(null, false, "Wszystko");
        for (Place place : Place.values()) {
            addFilter(place, false, place.label);
        }
        addFilter(null, true, Category.CLEANING.label);
    }

    private void addFilter(Place place, boolean cleaning, String text) {
        Button chip = button(text, INK, SOFT, 13, 22);
        chip.setPadding(dp(9), dp(5), dp(9), dp(5));
        styleSelection(chip, filter == place && chemistryOnly == cleaning, ACTION, WHITE, SOFT, INK);
        chip.setOnClickListener(view -> {
            if (filter != place || chemistryOnly != cleaning) {
                filter = place;
                chemistryOnly = cleaning;
                render(0);
            }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        if (place != null || cleaning) {
            params.setMarginStart(dp(5));
        }
        filters.addView(chip, params);
    }

    private void styleSelection(Button button, boolean selected, int active, int activeText,
                                int inactive, int inactiveText) {
        button.setSelected(selected);
        button.setTextColor(selected ? activeText : inactiveText);
        button.setBackground(touchBackground(selected ? active : inactive, 16));
    }

    private LinearLayout stockCard(Product product) {
        LinearLayout card = card();
        LinearLayout top = row();
        top.addView(productIdentity(product), weighted());
        Button edit = button("Edytuj", ACTION, SOFT, 13, 14);
        edit.setContentDescription("Edytuj " + product.name);
        edit.setOnClickListener(view -> showProductForm(product, null, product.category));
        top.addView(edit);
        card.addView(top);

        LinearLayout quantity = row();
        quantity.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        quantity.setPadding(0, dp(7), 0, 0);
        Button minus = stepButton("−", "Zmniejsz ilość: " + product.name, false);
        minus.setEnabled(product.quantity > 0);
        minus.setAlpha(product.quantity > 0 ? 1f : 0.5f);
        minus.setOnClickListener(view -> adjust(product, -1));
        quantity.addView(minus, new LinearLayout.LayoutParams(dp(52), WRAP_CONTENT));

        LinearLayout amount = column();
        amount.setGravity(Gravity.CENTER);
        amount.setPadding(dp(6), 0, dp(6), 0);
        TextView number = label(Integer.toString(product.quantity), 24, GREEN, true);
        number.setGravity(Gravity.CENTER);
        amount.addView(number);
        if (!product.unit.isEmpty()) {
            TextView unit = label(product.unit, 13, MUTED, false);
            unit.setGravity(Gravity.CENTER);
            amount.addView(unit);
        }
        quantity.addView(amount, weighted());

        Button plus = stepButton("+", "Zwiększ ilość: " + product.name, true);
        plus.setEnabled(product.quantity < Integer.MAX_VALUE);
        plus.setAlpha(product.quantity < Integer.MAX_VALUE ? 1f : 0.5f);
        plus.setOnClickListener(view -> adjust(product, 1));
        quantity.addView(plus, new LinearLayout.LayoutParams(dp(52), WRAP_CONTENT));
        card.addView(quantity);
        return card;
    }

    private Button stepButton(String text, String description, boolean filled) {
        Button button = button(text, filled ? WHITE : ACTION, filled ? ACTION : SOFT, 26, 14);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(dp(52));
        button.setContentDescription(description);
        return button;
    }

    private LinearLayout shoppingCard(Product product) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(productIdentity(product), weighted());
        Button bought = button("Kupione", WHITE, ACTION, 14, 14);
        bought.setContentDescription("Kupione: " + product.name);
        bought.setOnClickListener(view -> showPurchaseForm(product));
        card.addView(bought);
        return card;
    }

    private LinearLayout productIdentity(Product product) {
        LinearLayout identity = column();
        identity.setPadding(0, 0, dp(8), 0);
        identity.addView(label(product.name, 19, INK, true));
        String location = product.category == Category.CLEANING
                ? product.place.label + " · " + product.category.label : product.place.label;
        identity.addView(label(location, 14, MUTED, false));
        return identity;
    }

    private void showEmpty() {
        LinearLayout empty = card();
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(16), dp(20), dp(16), dp(20));
        if (shopping) {
            ImageView boar = new ImageView(this);
            boar.setImageResource(R.drawable.art_boar);
            boar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            empty.addView(boar, new LinearLayout.LayoutParams(dp(88), dp(51)));
        }
        String message = shopping ? "Lista zakupów jest pusta" : "Brak produktów";
        TextView emptyTitle = label(message, 19, GREEN, true);
        emptyTitle.setGravity(Gravity.CENTER);
        empty.addView(emptyTitle);
        if (!shopping) {
            String tip = filter == null && !chemistryOnly
                    ? "Dodaj pierwszy produkt." : "Tutaj jeszcze nic nie zapisano.";
            TextView detail = label(tip, 14, MUTED, false);
            detail.setGravity(Gravity.CENTER);
            empty.addView(detail);
        }
        rows.addView(empty, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
    }

    private void adjust(Product product, int delta) {
        try {
            Change change = store.adjust(product, delta);
            if (change != null) {
                render(scroll.getScrollY());
                showUndo(change);
            }
        } catch (IOException failure) {
            showSaveError(failure);
        }
    }

    private void showUndo(Change change) {
        handler.removeCallbacks(expireUndo);
        undo = change;
        undoBar.removeAllViews();
        undoBar.setBackground(shape(GREEN, 16));
        undoBar.setPadding(dp(12), 0, dp(5), 0);
        String status = change.after.name + " · " + change.after.quantity;
        TextView message = label(status, 14, WHITE, true);
        message.setMaxLines(2);
        message.setEllipsize(TextUtils.TruncateAt.END);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        undoBar.addView(message, weighted());
        Button revert = button("Cofnij", LIME, GREEN, 14, 14);
        revert.setOnClickListener(view -> {
            try {
                store.undo(change);
                clearUndo();
                render(scroll.getScrollY());
            } catch (IOException failure) {
                showSaveError(failure);
            }
        });
        undoBar.addView(revert);
        undoBar.setVisibility(View.VISIBLE);
        int timeout = 8000;
        if (Build.VERSION.SDK_INT >= 29) {
            AccessibilityManager accessibility = getSystemService(AccessibilityManager.class);
            timeout = accessibility.getRecommendedTimeoutMillis(timeout,
                    AccessibilityManager.FLAG_CONTENT_TEXT | AccessibilityManager.FLAG_CONTENT_CONTROLS);
        }
        handler.postDelayed(expireUndo, timeout);
    }

    private void clearUndo() {
        handler.removeCallbacks(expireUndo);
        undo = null;
        undoBar.setVisibility(View.GONE);
    }

    private void clearUndoFor(Product product) {
        if (undo != null && product != null && undo.after.id.equals(product.id)) {
            clearUndo();
        }
    }

    private Category defaultCategory() {
        return chemistryOnly ? Category.CLEANING : Category.FOOD;
    }

    private void showCatalog() {
        LinearLayout body = column();
        body.setPadding(dp(18), dp(12), dp(18), 0);

        LinearLayout categories = row();
        Button food = button(Category.FOOD.label, INK, SOFT, 14, 16);
        Button cleaning = button(Category.CLEANING.label, INK, SOFT, 14, 16);
        categories.addView(food, weighted());
        LinearLayout.LayoutParams cleaningParams = weighted();
        cleaningParams.setMarginStart(dp(6));
        categories.addView(cleaning, cleaningParams);
        body.addView(categories);

        EditText search = new EditText(this);
        search.setTextSize(16);
        search.setSingleLine(true);
        search.setMinHeight(dp(48));
        search.setHint("Szukaj produktu");
        body.addView(search);

        FrameLayout results = new FrameLayout(this);
        ListView list = new ListView(this);
        ArrayAdapter<KitchenCatalog.Item> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        results.addView(list, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        TextView empty = label("Brak pasujących produktów", 15, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        results.addView(empty, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        list.setEmptyView(empty);
        int availableHeight = getResources().getConfiguration().screenHeightDp;
        body.addView(results, new LinearLayout.LayoutParams(MATCH_PARENT, dp(Math.min(350, availableHeight / 2))));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Katalog produktów")
                .setView(body)
                .setNegativeButton("Anuluj", null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        body.setFocusableInTouchMode(true);

        Runnable updateResults = () -> {
            Category selected = cleaning.isSelected() ? Category.CLEANING : Category.FOOD;
            adapter.setNotifyOnChange(false);
            adapter.clear();
            adapter.addAll(KitchenCatalog.search(selected, search.getText().toString()));
            adapter.notifyDataSetChanged();
            list.setSelection(0);
        };
        java.util.function.Consumer<Category> select = selected -> {
            styleSelection(food, selected == Category.FOOD, ACTION, WHITE, SOFT, INK);
            styleSelection(cleaning, selected == Category.CLEANING, ACTION, WHITE, SOFT, INK);
            updateResults.run();
        };
        food.setOnClickListener(view -> select.accept(Category.FOOD));
        cleaning.setOnClickListener(view -> select.accept(Category.CLEANING));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                updateResults.run();
            }
            @Override public void afterTextChanged(Editable text) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            KitchenCatalog.Item item = adapter.getItem(position);
            Category category = cleaning.isSelected() ? Category.CLEANING : Category.FOOD;
            dialog.dismiss();
            showProductForm(null, item, category);
        });
        select.accept(defaultCategory());
        dialog.show();
        body.requestFocus();
    }

    private void showProductForm(Product original, KitchenCatalog.Item item, Category initialCategory) {
        LinearLayout form = formBody();
        String initialName = item == null ? "" : item.name;
        EditText name = field(form, "Nazwa produktu", "np. Majonez",
                original == null ? initialName : original.name, InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, KitchenStore.MAX_NAME);

        List<String> places = new ArrayList<>();
        for (Place place : Place.values()) {
            places.add(place.label);
        }
        Place defaultPlace = initialCategory == Category.CLEANING ? Place.CUPBOARDS : Place.FRIDGE;
        Place initial = filter == null ? defaultPlace : filter;
        if (original != null) {
            initial = original.place;
        } else if (item != null) {
            initial = item.place;
        }
        Spinner placePicker = picker(form, "Miejsce", places, initial.ordinal());

        EditText quantity = field(form, "Ilość", "",
                original == null ? "1" : Integer.toString(original.quantity), InputType.TYPE_CLASS_NUMBER, 10);
        String initialUnit = item == null ? "" : item.unit;
        EditText unit = field(form, "Jednostka (opcjonalnie)", "np. słoiki",
                original == null ? initialUnit : original.unit, InputType.TYPE_CLASS_TEXT, KitchenStore.MAX_UNIT);
        List<String> categories = new ArrayList<>();
        for (Category category : Category.values()) {
            categories.add(category.label);
        }
        Spinner categoryPicker = picker(form, "Kategoria", categories, initialCategory.ordinal());
        AlertDialog dialog = showForm(original == null ? "Dodaj produkt" : "Edytuj produkt", form, "Zapisz");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String productName = name.getText().toString().trim();
            if (productName.isEmpty()) {
                name.setError("Wpisz nazwę produktu.");
                name.requestFocus();
                return;
            }
            Integer amount = readQuantity(quantity, 0);
            if (amount == null) {
                return;
            }
            Place place = Place.values()[placePicker.getSelectedItemPosition()];
            Category category = Category.values()[categoryPicker.getSelectedItemPosition()];
            try {
                store.save(original, productName, place, amount, unit.getText().toString().trim(), category);
                clearUndoFor(original);
                dialog.dismiss();
                if (filter != null) {
                    filter = place;
                }
                if (category != Category.CLEANING) {
                    chemistryOnly = false;
                }
                render(original == null ? 0 : scroll.getScrollY());
                if (original == null) {
                    scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
                }
            } catch (IOException failure) {
                showSaveError(failure);
            }
        });
    }

    private void showPurchaseForm(Product product) {
        LinearLayout form = formBody();
        TextView location = label("Zapas uzupełnisz w " + product.place.location + ".", 14, MUTED, false);
        LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        locationParams.bottomMargin = dp(12);
        form.addView(location, locationParams);
        EditText quantity = field(form, "Ile kupiono?", "", "1", InputType.TYPE_CLASS_NUMBER, 10);
        quantity.setSelectAllOnFocus(true);
        if (!product.unit.isEmpty()) {
            form.addView(label("Jednostka: " + product.unit, 14, MUTED, false));
        }
        AlertDialog dialog = showForm(product.name, form, "Uzupełnij zapas");
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Integer amount = readQuantity(quantity, 1);
            if (amount == null) {
                return;
            }
            try {
                store.purchase(product, amount);
                clearUndoFor(product);
                dialog.dismiss();
                render(scroll.getScrollY());
            } catch (IOException failure) {
                showSaveError(failure);
            }
        });
    }

    private LinearLayout formBody() {
        LinearLayout form = column();
        form.setPadding(dp(24), dp(14), dp(24), dp(4));
        form.setFocusableInTouchMode(true);
        return form;
    }

    private AlertDialog showForm(String heading, LinearLayout body, String confirmLabel) {
        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(body, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(heading)
                .setView(formScroll)
                .setNegativeButton("Anuluj", null)
                .setPositiveButton(confirmLabel, null)
                .create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        dialog.show();
        body.requestFocus();
        return dialog;
    }

    private EditText field(LinearLayout form, String title, String hint, String value,
                           int inputType, int maxLength) {
        TextView titleView = label(title, 14, GREEN, true);
        EditText input = new EditText(this);
        input.setId(View.generateViewId());
        titleView.setLabelFor(input.getId());
        input.setTextSize(16);
        input.setSingleLine();
        input.setInputType(inputType);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(maxLength) });
        input.setHint(hint);
        input.setText(value);
        input.setMinHeight(dp(48));
        form.addView(titleView);
        form.addView(input, formFieldParams());
        return input;
    }

    private Spinner picker(LinearLayout form, String title, List<String> values, int initial) {
        TextView titleView = label(title, 14, GREEN, true);
        Spinner spinner = new Spinner(this, Spinner.MODE_DROPDOWN);
        spinner.setId(View.generateViewId());
        titleView.setLabelFor(spinner.getId());
        spinner.setMinimumHeight(dp(48));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(initial);
        form.addView(titleView);
        form.addView(spinner, formFieldParams());
        return spinner;
    }

    private LinearLayout.LayoutParams formFieldParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.bottomMargin = dp(10);
        return params;
    }

    private Integer readQuantity(EditText field, int minimum) {
        try {
            int value = Integer.parseInt(field.getText().toString().trim());
            if (value >= minimum) {
                return value;
            }
        } catch (NumberFormatException invalid) {
            // Show the same field error for empty text and values outside the integer range.
        }
        field.setError(minimum == 0 ? "Wpisz liczbę całkowitą od 0 do 2 147 483 647."
                : "Wpisz liczbę całkowitą od 1 do 2 147 483 647.");
        field.requestFocus();
        return null;
    }

    private void showSaveError(IOException failure) {
        Log.e("Kuchnia", "Zapis zapasów", failure);
        Toast.makeText(this, "Nie udało się zapisać zmiany. Spróbuj ponownie.", Toast.LENGTH_LONG).show();
    }

    private String productCount(int count) {
        if (count == 1) {
            return "1 produkt";
        }
        return count + (pluralFew(count) ? " produkty" : " produktów");
    }

    private String positionCount(int count) {
        if (count == 1) {
            return "1 pozycja do kupienia";
        }
        return count + (pluralFew(count) ? " pozycje do kupienia" : " pozycji do kupienia");
    }

    private boolean pluralFew(int count) {
        int last = count % 10;
        int lastTwo = count % 100;
        return last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14);
    }

    private LinearLayout card() {
        LinearLayout card = column();
        GradientDrawable background = shape(CARD, 20);
        background.setStroke(dp(1), BORDER);
        card.setBackground(background);
        card.setPadding(dp(13), dp(9), dp(13), dp(9));
        return card;
    }

    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private LinearLayout row() {
        LinearLayout view = new LinearLayout(this);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private Button button(String text, int ink, int fill, int size, int radius) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(text);
        view.setTextColor(ink);
        view.setTextSize(size);
        view.setLetterSpacing(0);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setMinWidth(0);
        view.setMinimumWidth(0);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setPadding(dp(12), dp(4), dp(12), dp(4));
        view.setStateListAnimator(null);
        view.setBackground(touchBackground(fill, radius));
        return view;
    }

    private GradientDrawable shape(int fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private RippleDrawable touchBackground(int fill, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x260b3526), shape(fill, radius), null);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
