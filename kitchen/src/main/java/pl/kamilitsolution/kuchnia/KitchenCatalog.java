package pl.kamilitsolution.kuchnia;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import static pl.kamilitsolution.kuchnia.KitchenStore.Category;
import static pl.kamilitsolution.kuchnia.KitchenStore.Place;
import static pl.kamilitsolution.kuchnia.KitchenStore.Place.CUPBOARDS;
import static pl.kamilitsolution.kuchnia.KitchenStore.Place.FRIDGE;
import static pl.kamilitsolution.kuchnia.KitchenStore.Place.PANTRY;

final class KitchenCatalog {
    static final class Item {
        final String name;
        final Place place;
        final String unit;
        private final String searchName;

        Item(String name, Place place, String unit) {
            this.name = name;
            this.place = place;
            this.unit = unit;
            this.searchName = normalize(name);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final List<Item> FOOD = food();
    private static final List<Item> CLEANING = cleaning();

    private KitchenCatalog() {}

    static List<Item> search(Category category, String query) {
        String term = normalize(query.trim());
        List<Item> results = new ArrayList<>();
        for (Item item : category == Category.CLEANING ? CLEANING : FOOD) {
            if (item.searchName.contains(term)) {
                results.add(item);
            }
        }
        return results;
    }

    private static String normalize(String text) {
        String lowerCase = text.toLowerCase(Locale.ROOT).replace('ł', 'l');
        return MARKS.matcher(Normalizer.normalize(lowerCase, Normalizer.Form.NFD)).replaceAll("");
    }

    private static List<Item> food() {
        List<Item> items = new ArrayList<>();
        add(items, FRIDGE, "kartony", "Mleko", "Kefir", "Maślanka", "Śmietanka");
        add(items, FRIDGE, "opakowania", "Jogurt naturalny", "Jogurt grecki", "Skyr", "Śmietana",
                "Twaróg", "Serek wiejski", "Masło", "Ser żółty", "Mozzarella", "Feta", "Parmezan",
                "Szynka", "Kiełbasa", "Boczek", "Parówki");
        add(items, FRIDGE, "sztuki", "Jajka");

        add(items, PANTRY, "sztuki", "Ziemniaki", "Cebula", "Czosnek");
        add(items, FRIDGE, "sztuki", "Marchew", "Pietruszka (korzeń)", "Seler", "Por", "Buraki",
                "Kapusta biała", "Kapusta pekińska", "Ogórki", "Pomidory", "Papryka", "Cukinia",
                "Brokuł", "Kalafior", "Sałata", "Rzodkiewka", "Pieczarki", "Szpinak");

        add(items, PANTRY, "sztuki", "Jabłka", "Banany", "Gruszki", "Pomarańcze", "Mandarynki", "Cytryny",
                "Kiwi", "Brzoskwinie", "Śliwki", "Awokado");
        add(items, FRIDGE, "opakowania", "Winogrona", "Truskawki", "Borówki", "Filet z kurczaka",
                "Mięso mielone", "Schab", "Łosoś", "Dorsz", "Tofu", "Hummus");

        add(items, PANTRY, "sztuki", "Chleb", "Bułki");
        add(items, PANTRY, "opakowania", "Tortille", "Mąka pszenna", "Mąka ziemniaczana", "Cukier",
                "Sól", "Pieprz", "Ryż", "Makaron", "Kasza gryczana", "Kasza jęczmienna", "Kasza jaglana",
                "Płatki owsiane", "Płatki kukurydziane", "Bułka tarta", "Soczewica", "Fasola sucha",
                "Orzechy włoskie", "Rodzynki");

        add(items, PANTRY, "słoiki", "Majonez", "Musztarda", "Koncentrat pomidorowy", "Ogórki kiszone",
                "Dżem", "Miód");
        add(items, PANTRY, "butelki", "Ketchup", "Olej rzepakowy", "Oliwa z oliwek", "Ocet", "Sos sojowy",
                "Passata pomidorowa");
        add(items, PANTRY, "puszki", "Pomidory w puszce", "Kukurydza w puszce", "Groszek konserwowy",
                "Ciecierzyca w puszce", "Tuńczyk w puszce");
        add(items, PANTRY, "opakowania", "Herbata", "Kawa", "Kakao");

        add(items, PANTRY, "opakowania", "Oregano", "Bazylia", "Majeranek", "Tymianek", "Rozmaryn",
                "Koper suszony", "Natka pietruszki suszona", "Lubczyk", "Szałwia", "Estragon",
                "Liść laurowy");
        add(items, PANTRY, "opakowania", "Ziele angielskie", "Papryka słodka", "Papryka ostra",
                "Papryka wędzona", "Czosnek granulowany", "Cebula suszona", "Chili", "Kurkuma",
                "Kumin (kmin rzymski)", "Kminek", "Kolendra mielona", "Imbir mielony", "Cynamon",
                "Gałka muszkatołowa", "Goździki");
        add(items, PANTRY, "opakowania", "Przyprawa do gyrosa", "Curry", "Zioła prowansalskie",
                "Przyprawa do kurczaka", "Przyprawa do grilla", "Przyprawa do piernika");
        return sorted(items);
    }

    private static List<Item> cleaning() {
        List<Item> items = new ArrayList<>();
        add(items, CUPBOARDS, "butelki", "Płyn do naczyń", "Środek na pleśń", "Płyn do szyb",
                "Płyn do podłóg", "Płyn do czyszczenia kuchni", "Płyn do czyszczenia łazienki",
                "Żel do WC", "Odtłuszczacz", "Odkamieniacz", "Nabłyszczacz do zmywarki");
        add(items, CUPBOARDS, "opakowania", "Gąbki do naczyń", "Tabletki do zmywarki", "Sól do zmywarki",
                "Ściereczki z mikrofibry", "Rękawice do sprzątania");
        add(items, CUPBOARDS, "rolki", "Worki na śmieci");
        return sorted(items);
    }

    private static void add(List<Item> items, Place place, String unit, String... names) {
        for (String name : names) {
            items.add(new Item(name, place, unit));
        }
    }

    private static List<Item> sorted(List<Item> items) {
        Collator polish = Collator.getInstance(Locale.forLanguageTag("pl-PL"));
        items.sort((left, right) -> polish.compare(left.name, right.name));
        return items;
    }
}
