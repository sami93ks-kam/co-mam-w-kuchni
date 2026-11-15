package pl.kamilitsolution.kuchnia;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

final class KitchenStore {
    enum Category {
        FOOD("Spożywcze"),
        CLEANING("Chemia");

        final String label;

        Category(String label) {
            this.label = label;
        }
    }

    enum Place {
        FRIDGE("Lodówka", "Lodówce"),
        PANTRY("Spiżarnia", "Spiżarni"),
        CUPBOARDS("Szafki", "Szafkach");

        final String label;
        final String location;

        Place(String label, String location) {
            this.label = label;
            this.location = location;
        }
    }

    static final int MAX_NAME = 100;
    static final int MAX_UNIT = 30;
    private static final int FILE_VERSION = 2;

    static final class Product {
        final String id;
        final String name;
        final Place place;
        final int quantity;
        final String unit;
        final boolean onShoppingList;
        final Category category;

        Product(String id, String name, Place place, int quantity, String unit, boolean onShoppingList,
                Category category) {
            if (name.isEmpty() || name.length() > MAX_NAME || unit.length() > MAX_UNIT
                    || quantity < 0 || (onShoppingList && quantity != 0)) {
                throw new IllegalArgumentException("Nieprawidłowe dane produktu");
            }
            this.id = id;
            this.name = name;
            this.place = place;
            this.quantity = quantity;
            this.unit = unit;
            this.onShoppingList = onShoppingList;
            this.category = category;
        }

        Product withQuantity(int quantity) {
            return new Product(id, name, place, quantity, unit,
                    quantity == 0 && (this.quantity > 0 || onShoppingList), category);
        }
    }

    static final class Change {
        final Product before;
        final Product after;

        Change(Product before, Product after) {
            this.before = before;
            this.after = after;
        }
    }

    private final Path file;
    private List<Product> products;

    KitchenStore(Path file) throws IOException {
        this.file = file;
        products = load();
    }

    List<Product> products() {
        return Collections.unmodifiableList(products);
    }

    void save(Product original, String name, Place place, int quantity, String unit, Category category)
            throws IOException {
        if (original == null) {
            Product added = new Product(UUID.randomUUID().toString(), name, place, quantity, unit, false, category);
            List<Product> updated = new ArrayList<>(products);
            updated.add(added);
            persist(updated);
            return;
        }
        Product withQuantity = original.withQuantity(quantity);
        replace(original, new Product(original.id, name, place, quantity, unit, withQuantity.onShoppingList, category));
    }

    Change adjust(Product product, int delta) throws IOException {
        long quantity = (long) product.quantity + delta;
        if (quantity < 0 || quantity > Integer.MAX_VALUE || quantity == product.quantity) {
            return null;
        }
        Product updated = product.withQuantity((int) quantity);
        replace(product, updated);
        return new Change(product, updated);
    }

    void purchase(Product product, int quantity) throws IOException {
        if (!product.onShoppingList || quantity <= 0) {
            throw new IllegalArgumentException("Nieprawidłowa ilość zakupów");
        }
        replace(product, product.withQuantity(quantity));
    }

    void undo(Change change) throws IOException {
        if (products.contains(change.after)) {
            replace(change.after, change.before);
        }
    }

    private void replace(Product original, Product replacement) throws IOException {
        int index = products.indexOf(original);
        if (index < 0) {
            throw new IllegalStateException("Produkt został już zmieniony");
        }
        List<Product> updated = new ArrayList<>(products);
        updated.set(index, replacement);
        persist(updated);
    }

    private List<Product> load() throws IOException {
        List<Product> loaded = new ArrayList<>();
        DataInputStream source;
        try {
            source = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
        } catch (NoSuchFileException missing) {
            return loaded;
        }
        try (DataInputStream input = source) {
            int version = input.readInt();
            if (version != 1 && version != FILE_VERSION) {
                throw new IOException("Nieobsługiwana wersja zapisu");
            }
            int count = input.readInt();
            if (count < 0) {
                throw new IOException("Nieprawidłowa liczba produktów");
            }
            for (int index = 0; index < count; index++) {
                String id = input.readUTF();
                String name = input.readUTF();
                Place place = Place.valueOf(input.readUTF());
                int quantity = input.readInt();
                String unit = input.readUTF();
                boolean onShoppingList = input.readBoolean();
                Category category = version == 1 ? Category.FOOD : Category.valueOf(input.readUTF());
                loaded.add(new Product(id, name, place, quantity, unit, onShoppingList, category));
            }
            return loaded;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Nieprawidłowy zapis produktów", invalid);
        }
    }

    private void persist(List<Product> updated) throws IOException {
        Path temporary = Files.createTempFile(file.getParent(), "zapasy-", ".tmp");
        try {
            try (FileOutputStream disk = new FileOutputStream(temporary.toFile());
                 DataOutputStream output = new DataOutputStream(new BufferedOutputStream(disk))) {
                output.writeInt(FILE_VERSION);
                output.writeInt(updated.size());
                for (Product product : updated) {
                    output.writeUTF(product.id);
                    output.writeUTF(product.name);
                    output.writeUTF(product.place.name());
                    output.writeInt(product.quantity);
                    output.writeUTF(product.unit);
                    output.writeBoolean(product.onShoppingList);
                    output.writeUTF(product.category.name());
                }
                output.flush();
                disk.getFD().sync();
            }
            // Both the quantity and its shopping-list status become visible in one replacement.
            Files.move(temporary, file, REPLACE_EXISTING, ATOMIC_MOVE);
            products = updated;
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
}
